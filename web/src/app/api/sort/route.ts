import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { collectionView } from "@/lib/collview";
import {
  SORT_SKIPS_PREF,
  sortApply,
  sortSetUp,
  sortSettledOf,
  sortSkipsOf,
  sortStartOver,
  sortSuggest,
  type SortMove,
  type SortPlaceChoice,
  type SortSettingsInput,
  type SortTogether,
  type SortDeckCopies,
} from "@/lib/collsort";
import { format } from "@/lib/money";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { binders, prefs, setPrefs, type SyncFile } from "@/lib/syncfile";

export const dynamic = "force-dynamic";

type Loaded = Awaited<ReturnType<typeof loadAll>>;
const loadAll = () => Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);

/**
 * What the sort page shows: the plan, the settings, the levels' and Decks's binders, and the binders to pick from.
 * [ignoreCap] is the page's "sort everything now": this week's limit doesn't count for this run.
 */
function sortView(file: SyncFile, loaded: Loaded, ignoreCap = false) {
  const [prices, details, ck] = loaded;
  const view = collectionView(file, prices, details, ck);
  const p = prefs(file);
  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const { plan, levels, decks, settings, ownUserSet } = sortSuggest(file, prices, details, ck, Date.now(), money, ignoreCap);
  // The binders a place can be set to: every binder with a uid.
  const used = new Set([...levels.map((l) => l.uid), ...(decks ? [decks.uid] : [])]);
  return {
    plan,
    // The settings as the page shows them (the levels with their binders' names).
    settings,
    levels,
    decks,
    ownUserSet,
    binders: binders(file)
      .filter((b) => b.uid)
      .map((b) => ({ uid: b.uid!, name: b.name, used: used.has(b.uid!) })),
    settled: sortSettledOf(p),
    declined: sortSkipsOf(p).size,
    currency: view.currency,
    perEuro: view.perEuro,
    /** A money line for the setup (the minimum prices are in euros). */
    moneyExample: money(1),
    readOnly: view.readOnly,
  };
}

export type SortView = ReturnType<typeof sortView>;

export async function GET(req?: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const ignoreCap = req ? new URL(req.url).searchParams.get("ignoreCap") === "1" : false;
    const [{ file }, loaded] = await Promise.all([loadSync(a), loadAll()]);
    return json(sortView(file, loaded, ignoreCap));
  } catch (e) {
    return errorResponse(e);
  }
}

/** The setup that can't work, with the message for the page. */
class BadSetup extends Error {}

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

/** A place's binder: an existing binder's uid, or a name for a new one. */
function cleanChoice(v: unknown): SortPlaceChoice | null {
  if (!v || typeof v !== "object") return null;
  const uid = (v as { uid?: unknown }).uid;
  const newName = (v as { newName?: unknown }).newName;
  if (okKey(uid)) return { uid };
  if (typeof newName === "string" && newName.trim().length > 0 && newName.trim().length <= 200 && !/[\n\r]/.test(newName)) return { newName: newName.trim() };
  return null;
}

/** The setup page's setup: the levels (1 to 6, best first), the Decks binder (or none) and how the sort works. */
interface SetUpBody {
  levels: { choice: SortPlaceChoice; max: number; minPrice: number | null }[];
  decks: SortPlaceChoice | null;
  settings: SortSettingsInput;
}

function cleanSettings(v: unknown): SortSettingsInput | null {
  if (!v || typeof v !== "object") return null;
  const s = v as Record<string, unknown>;
  const int = (x: unknown, max: number) => (Number.isInteger(Number(x)) && Number(x) >= 0 && Number(x) <= max ? Number(x) : null);
  const mix = int(s.mix, 100);
  const weekly = int(s.weekly, 10000);
  const ratio = Number(s.outlierRatio);
  const gap = Number(s.outlierGap);
  const together = s.together;
  const deckCopies = s.deckCopies;
  if (mix === null || weekly === null || weekly < 1) return null;
  if (!Number.isFinite(ratio) || ratio < 1 || ratio > 100) return null;
  if (!Number.isFinite(gap) || gap < 0 || gap > 10000) return null;
  if (together !== "off" && together !== "printing" && together !== "name") return null;
  if (deckCopies !== "best" && deckCopies !== "cheapest") return null;
  return {
    mix,
    weekly,
    together: together as SortTogether,
    outlierRatio: Math.round(ratio * 100) / 100,
    outlierGap: Math.round(gap * 100) / 100,
    deckCopies: deckCopies as SortDeckCopies,
  };
}

/** The levels as the page sends them: at most six, each a binder, a limit and a minimum price (or none). */
function cleanLevels(v: unknown): SetUpBody["levels"] | null {
  if (!Array.isArray(v) || v.length < 1 || v.length > 6) return null;
  const out: SetUpBody["levels"] = [];
  for (const l of v) {
    if (!l || typeof l !== "object") return null;
    const choice = cleanChoice((l as { choice?: unknown }).choice);
    const max = Number((l as { max?: unknown }).max);
    const rawMin = (l as { minPrice?: unknown }).minPrice;
    const minPrice = rawMin === null || rawMin === undefined || rawMin === "" ? null : Number(rawMin);
    if (!choice || !Number.isInteger(max) || max < 0 || max > 1000000) return null;
    if (minPrice !== null && (!Number.isFinite(minPrice) || minPrice < 0 || minPrice > 100000)) return null;
    out.push({ choice, max, minPrice: minPrice === null ? null : Math.round(minPrice * 100) / 100 });
  }
  return out;
}

/**
 * { action: "setup", levels, decks, settings } makes or picks the binders and saves the settings;
 * { action: "apply", accepted, declined } moves the accepted copies and remembers the declined moves (given as the
 * plan's moves: { key, from, to, copies }); { action: "unskip" } suggests the declined moves again;
 * { action: "startover" } forgets the first sort, the rest periods and the declined moves.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { action?: string; levels?: unknown; decks?: unknown; settings?: unknown; accepted?: unknown; declined?: unknown };
    // Only the plan (and its moves) needs the price files; the other actions just write the sync file.
    const loaded: Loaded | null = body?.action === "apply" || body?.action === "setup" ? await loadAll() : null;
    let change: (file: SyncFile, now: number) => void;
    if (body?.action === "setup") {
      const levels = cleanLevels(body.levels);
      const settings = cleanSettings(body.settings);
      const decks = body.decks === null || body.decks === undefined ? null : cleanChoice(body.decks);
      if (!levels || !settings || (body.decks !== null && body.decks !== undefined && !decks)) return json({ error: "Those settings can't be used." }, 400);
      change = (f, now) => {
        // The binders' checks (the same binder twice, a level that holds nothing before the last) are sortSetUp's
        // own: its message is the answer (nothing has been written when it throws).
        try {
          sortSetUp(f, levels, decks, settings, now);
        } catch (e) {
          if (e instanceof Error) throw new BadSetup(e.message);
          throw e;
        }
      };
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
    if (e instanceof BadSetup) return json({ error: e.message }, 400);
    return errorResponse(e);
  }
}
