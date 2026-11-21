import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { collectionView } from "@/lib/collview";
import {
  SORT_PLACES,
  SORT_SKIPS_PREF,
  sortApply,
  sortPlacesOf,
  sortRulesOf,
  sortSetUp,
  sortSettledOf,
  sortSkipsOf,
  sortStartOver,
  sortSuggest,
  type SortMove,
  type SortPlace,
  type SortPlaceChoice,
  type SortRules,
} from "@/lib/collsort";
import { format } from "@/lib/money";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { binders, prefs, setPrefs, type SyncFile } from "@/lib/syncfile";

export const dynamic = "force-dynamic";

type Loaded = Awaited<ReturnType<typeof loadAll>>;
const loadAll = () => Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);

/** What the sort page shows: the plan, the rules, the three places' binders and the binders to pick from. */
function sortView(file: SyncFile, loaded: Loaded) {
  const [prices, details, ck] = loaded;
  const view = collectionView(file, prices, details, ck);
  const p = prefs(file);
  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const { plan, binders: places } = sortSuggest(file, prices, details, ck, Date.now(), money);
  const uids = sortPlacesOf(p);
  const taken = new Set(Object.values(uids).filter((uid): uid is string => !!uid));
  return {
    plan,
    rules: sortRulesOf(p),
    places,
    // The binders a place can be set to: every binder with a uid.
    binders: binders(file)
      .filter((b) => b.uid)
      .map((b) => ({ uid: b.uid!, name: b.name, used: taken.has(b.uid!) })),
    settled: sortSettledOf(p),
    declined: sortSkipsOf(p).size,
    currency: view.currency,
    perEuro: view.perEuro,
    /** A money line for the setup (the floor is in the same unit as the trade binder's minimum). */
    moneyExample: money(1),
    readOnly: view.readOnly,
  };
}

export type SortView = ReturnType<typeof sortView>;

export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, loaded] = await Promise.all([loadSync(a), loadAll()]);
    return json(sortView(file, loaded));
  } catch (e) {
    return errorResponse(e);
  }
}

/** A move as the page sends it back: its card kind and the two places (the app's SortMove). */
const moveKey = (m: SortMove) => `${m.key}>${m.from}>${m.to}`;

const okKey = (v: unknown): v is string => typeof v === "string" && v.length > 0 && v.length <= 500 && !/[\n\r]/.test(v);

const moves = (v: unknown, known: Map<string, SortMove>): SortMove[] | null => {
  if (!Array.isArray(v)) return null;
  const out: SortMove[] = [];
  for (const m of v) {
    if (!m || typeof m !== "object") return null;
    const key = okKey((m as { key?: unknown }).key) ? (m as { key: string }).key : null;
    const from = (m as { from?: unknown }).from;
    const to = (m as { to?: unknown }).to;
    if (key === null || typeof from !== "string" || typeof to !== "string") return null;
    const found = known.get(`${key}>${from}>${to}`);
    if (!found) return null;
    out.push(found);
  }
  return out;
};

function cleanRules(r: Partial<SortRules> | undefined): SortRules | null {
  if (!r || typeof r !== "object") return null;
  const int = (v: unknown, max: number) => (Number.isInteger(Number(v)) && Number(v) >= 0 && Number(v) <= max ? Number(v) : null);
  const maxTop = int(r.maxTop, 100000);
  const maxMid = int(r.maxMid, 100000);
  const maxBulk = int(r.maxBulk, 1000000);
  const mix = int(r.mix, 100);
  const weekly = int(r.weekly, 10000);
  const floor = Number(r.floor);
  if (maxTop === null || maxMid === null || maxBulk === null || mix === null || weekly === null || !Number.isFinite(floor) || floor < 0 || floor > 100000) return null;
  if (weekly < 1) return null;
  return { maxTop, maxMid, maxBulk, floor: Math.round(floor * 100) / 100, mix, weekly };
}

/** A place's choice: an existing binder's uid, or a name for a new one. */
function cleanPlaces(v: unknown): Partial<Record<SortPlace, SortPlaceChoice>> | null {
  if (!v || typeof v !== "object") return null;
  const out: Partial<Record<SortPlace, SortPlaceChoice>> = {};
  for (const place of SORT_PLACES) {
    const c = (v as Record<string, unknown>)[place];
    if (c === undefined || c === null) continue;
    if (typeof c !== "object") return null;
    const uid = (c as { uid?: unknown }).uid;
    const newName = (c as { newName?: unknown }).newName;
    if (okKey(uid)) out[place] = { uid };
    else if (typeof newName === "string" && newName.trim().length > 0 && newName.trim().length <= 200 && !/[\n\r]/.test(newName)) out[place] = { newName: newName.trim() };
    else return null;
  }
  return out;
}

/**
 * { action: "setup", places, rules } makes or picks the three binders and saves the rules;
 * { action: "apply", accepted, declined } moves the accepted copies and remembers the declined moves (given as the
 * plan's moves: { key, from, to, copies }); { action: "unskip" } suggests the declined moves again;
 * { action: "startover" } forgets the first sort, the rest periods and the declined moves.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { action?: string; places?: unknown; rules?: Partial<SortRules>; accepted?: unknown; declined?: unknown };
    // Only the plan (and its moves) needs the price files; the other actions just write the sync file.
    const loaded: Loaded | null = body?.action === "apply" || body?.action === "setup" ? await loadAll() : null;
    let change: (file: SyncFile, now: number) => void;
    if (body?.action === "setup") {
      const places = cleanPlaces(body.places);
      const rules = cleanRules(body.rules);
      if (!places || !rules) return json({ error: "Those settings can't be used." }, 400);
      change = (f, now) => void sortSetUp(f, places, rules, now);
    } else if (body?.action === "apply") {
      // The moves come back from the plan the page shows; only those can be applied.
      const { file } = await loadSync(a);
      const known = new Map(sortSuggest(file, loaded![0], loaded![1], loaded![2]).plan.moves.map((m) => [moveKey(m), m]));
      const accepted = moves(body.accepted, known);
      const declined = moves(body.declined, known);
      if (!accepted || !declined) return json({ error: "Those moves can't be used. Reload the page." }, 400);
      if (accepted.length + declined.length === 0) return json({ error: "Nothing chosen." }, 400);
      change = (f, now) => sortApply(f, accepted, declined, now);
    } else if (body?.action === "unskip") {
      change = (f, now) => setPrefs(f, { [SORT_SKIPS_PREF]: "" }, now);
    } else if (body?.action === "startover") {
      change = (f, now) => sortStartOver(f, now);
    } else return json({ error: "Unknown action." }, 400);
    const file = await editSync(a, (f, now) => {
      change(f, now);
      return f;
    });
    return json(sortView(file, loaded ?? (await loadAll())));
  } catch (e) {
    return errorResponse(e);
  }
}
