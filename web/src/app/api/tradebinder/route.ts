import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { collectionView } from "@/lib/collview";
import { format } from "@/lib/money";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { binders, prefs, setPrefs, type SyncFile } from "@/lib/syncfile";
import {
  applyTrade,
  forgetDeclined,
  plan,
  planInputs,
  rulesOf,
  rulesPrefs,
  skipsOf,
  tradeBinderOf,
  type TradeDecision,
  type TradeRules,
} from "@/lib/tradebinder";

export const dynamic = "force-dynamic";

type Loaded = Awaited<ReturnType<typeof loadAll>>;
const loadAll = () => Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);

/** What the trade binder page shows: the suggestions, the rules and the binder. */
function tradeView(file: SyncFile, [prices, details, ck]: Loaded) {
  const view = collectionView(file, prices, details, ck);
  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const p = prefs(file);
  const binder = tradeBinderOf(file);
  const skipped = skipsOf(p);
  const rules = rulesOf(p);
  const changes = plan({ rows: view.rows, ...planInputs(file), binder, rules, skipped, cardmarket: view.source === "cardmarket", money });
  const inBinder = binder ? view.rows.filter((r) => r.binder === binder) : [];
  return {
    changes,
    rules,
    binder: binder ? { uid: binder, name: binders(file).find((b) => b.uid === binder)?.name ?? "Trade binder" } : null,
    binderCards: inBinder.reduce((n, r) => n + r.quantity, 0),
    binderValue: inBinder.reduce((n, r) => n + (r.unitEur ?? 0) * r.quantity, 0),
    declined: [...skipped].filter((k) => !k.startsWith("prefer|")).length,
    currency: view.currency,
    perEuro: view.perEuro,
    priceTypeLabel: view.priceTypeLabel,
    sourceLabel: view.sourceLabel,
    readOnly: view.readOnly,
  };
}

export type TradeBinderView = ReturnType<typeof tradeView>;

export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, loaded] = await Promise.all([loadSync(a), loadAll()]);
    return json(tradeView(file, loaded));
  } catch (e) {
    return errorResponse(e);
  }
}

const decisions = (v: unknown): TradeDecision[] =>
  Array.isArray(v)
    ? v.flatMap((d) =>
        d && typeof d.key === "string" && typeof d.add === "boolean" && Number.isInteger(d.copies) && d.copies > 0 && d.copies <= 9999
          ? [{ key: d.key, add: d.add, copies: d.copies }]
          : [],
      )
    : [];

function cleanRules(r: Partial<TradeRules> | undefined): TradeRules | null {
  if (!r || typeof r !== "object") return null;
  const max = Number(r.maxCards);
  const min = Number(r.minValue);
  if (!Number.isInteger(max) || max < 0 || max > 10000 || !Number.isFinite(min) || min < 0 || min > 100000) return null;
  return { maxCards: max, minValue: Math.round(min * 100) / 100, keepOne: !!r.keepOne, keepBestForDecks: !!r.keepBestForDecks, keepBestAlways: !!r.keepBestAlways };
}

/**
 * { action: "apply", accepted, declined } moves the accepted copies and remembers the declined suggestions;
 * { action: "rules", rules } saves the rules; { action: "forget" } suggests the declined cards again.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { action?: string; accepted?: unknown; declined?: unknown; rules?: Partial<TradeRules> };
    const loaded = await loadAll();
    const rates = loaded[0]?.rates ?? null;
    let change: (file: SyncFile, now: number) => void;
    if (body?.action === "apply") {
      const accepted = decisions(body.accepted);
      const declined = decisions(body.declined);
      if (accepted.length + declined.length === 0) return json({ error: "Nothing chosen." }, 400);
      change = (f, now) => void applyTrade(f, accepted, declined, now, rates);
    } else if (body?.action === "rules") {
      const rules = cleanRules(body.rules);
      if (!rules) return json({ error: "Those rules can't be used." }, 400);
      change = (f, now) => setPrefs(f, rulesPrefs(rules), now);
    } else if (body?.action === "forget") {
      change = (f, now) => forgetDeclined(f, now);
    } else return json({ error: "Unknown action." }, 400);
    const file = await editSync(a, (f, now) => {
      change(f, now);
      return f;
    });
    return json(tradeView(file, loaded));
  } catch (e) {
    return errorResponse(e);
  }
}
