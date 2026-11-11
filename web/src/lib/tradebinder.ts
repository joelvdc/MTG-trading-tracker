// The trade binder (the app's TradeBinder.kt, since 1.18): which spare copies are worth the most to trade, by value
// and by how much Commander players want them, with a nudge for rising prices. The website shows the suggestions,
// applies the accepted ones and remembers the declined ones. Its rules and the declined suggestions are synced
// settings (since app 1.80): tradeMax, tradeKeepOne, tradeMinValue, tradeKeepBestDecks, tradeKeepBestAll, tradeSkips.
import { applyCollectionEdit } from "./collection";
import { decksOf, type CollRow } from "./collview";
import type { PriceFile } from "./prices";
import { binders, collection, prefs, setPrefs, wishlist, type SyncFile } from "./syncfile";

export const TRADE_BINDER_NAME = "Trade binder";
/** The synced setting naming the trade binder by uid (SyncPrefs.TRADE_BINDER, since 1.79). */
export const TRADE_BINDER_PREF = "tradeBinder";
/** The declined suggestions (the app's trade_skips table), newline-joined. */
export const TRADE_SKIPS_PREF = "tradeSkips";

export interface TradeRules {
  maxCards: number;
  keepOne: boolean;
  minValue: number;
  keepBestForDecks: boolean;
  keepBestAlways: boolean;
}

export const DEFAULT_RULES: TradeRules = { maxCards: 100, keepOne: true, minValue: 1, keepBestForDecks: true, keepBestAlways: false };

const bool = (v: string | undefined, d: boolean) => (v === "true" ? true : v === "false" ? false : d);

/** The rules from the synced settings (the app's defaults where a phone hasn't sent them). */
export function rulesOf(p: Record<string, string>): TradeRules {
  const max = Number.parseInt(p.tradeMax ?? "", 10);
  const min = Number.parseFloat(p.tradeMinValue ?? "");
  return {
    maxCards: Number.isFinite(max) && max >= 0 ? max : DEFAULT_RULES.maxCards,
    keepOne: bool(p.tradeKeepOne, DEFAULT_RULES.keepOne),
    minValue: Number.isFinite(min) && min >= 0 ? min : DEFAULT_RULES.minValue,
    keepBestForDecks: bool(p.tradeKeepBestDecks, DEFAULT_RULES.keepBestForDecks),
    keepBestAlways: bool(p.tradeKeepBestAll, DEFAULT_RULES.keepBestAlways),
  };
}

/** The settings for [r], as the app writes them. */
export function rulesPrefs(r: TradeRules): Record<string, string> {
  return {
    tradeMax: String(r.maxCards),
    tradeKeepOne: String(r.keepOne),
    tradeMinValue: String(r.minValue),
    tradeKeepBestDecks: String(r.keepBestForDecks),
    tradeKeepBestAll: String(r.keepBestAlways),
  };
}

export const skipsOf = (p: Record<string, string>): Set<string> => new Set((p[TRADE_SKIPS_PREF] ?? "").split("\n").filter(Boolean));

/** One copy-kind of a card: printing, finish, condition, language and marks (CardKind). */
export interface CardKind {
  scryfallId: string;
  foil: boolean;
  etched: boolean;
  condition: string;
  language: string;
  signed: boolean;
  altered: boolean;
}

/** Plain copies keep the app's key from before 1.27, so skipped suggestions stay skipped. */
export const kindKey = (k: CardKind) =>
  `${k.scryfallId}|${k.foil}|${k.etched}|${k.condition}|${k.language}` + (k.signed ? "|signed" : "") + (k.altered ? "|altered" : "");

export const kindOf = (r: Pick<CollRow, "card" | "finish" | "condition" | "language" | "signed" | "altered">): CardKind => ({
  scryfallId: r.card.scryfallId,
  foil: r.finish !== "NONFOIL",
  etched: r.finish === "ETCHED",
  condition: r.condition,
  language: r.language,
  signed: r.signed,
  altered: r.altered,
});

/** A suggested change: [copies] of a card in or out, and why. */
export interface TradeChange {
  add: boolean;
  key: string;
  kind: CardKind;
  card: CollRow["card"];
  copies: number;
  unitPrice: number | null;
  reason: string;
  score: number;
  /** The skip key remembering a decline: "add|<kind>" or "remove|<kind>". */
  skipKey: string;
}

export function popularity(rank: number | null): number {
  if (rank == null) return 0;
  if (rank <= 100) return 1;
  if (rank <= 500) return 0.6;
  if (rank <= 2000) return 0.35;
  if (rank <= 8000) return 0.15;
  return 0;
}

export const score = (price: number, rank: number | null, trendPct: number | null) =>
  price * (1 + popularity(rank)) * (1 + Math.min(0.25, Math.max(-0.2, (trendPct ?? 0) / 100)));

export function why(price: number | null, rank: number | null, trendPct: number | null, money: (eur: number) => string): string {
  return [
    price != null ? money(price) : null,
    rank != null && rank <= 8000 ? `top ${[100, 500, 1000, 2000, 5000, 8000].find((t) => rank <= t)!.toLocaleString("en-US")} on EDHREC` : null,
    trendPct != null && trendPct >= 5 ? `▲ ${Math.round(trendPct)}%` : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

const BASICS = new Set(["Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes"].flatMap((b) => [b, `Snow-Covered ${b}`]).map((b) => b.toLowerCase()));
export const nameKey = (name: string) => name.split(" // ")[0].trim().toLowerCase();

export interface PlanInput {
  rows: CollRow[];
  /** Copies per card name (nameKey) over all decks, without maybeboards. */
  deckNeeds: Map<string, number>;
  /** Card names (nameKey) still wanted on the wishlist. */
  wishlist: Set<string>;
  /** The trade binder's uid, or null while there is none. */
  binder: string | null;
  rules: TradeRules;
  skipped: Set<string>;
  /** Trends only count with Cardmarket as the price source, as in the app. */
  cardmarket: boolean;
  money: (eur: number) => string;
}

/** TradeBinderPlanner.plan: the changes against what the binder holds now, additions first, by score. */
export function plan(input: PlanInput): TradeChange[] {
  const { rows, deckNeeds, wishlist: wish, binder, rules, skipped, money } = input;
  const preferred = new Map<string, string>();
  for (const k of skipped) {
    if (!k.startsWith("prefer|")) continue;
    const rest = k.slice("prefer|".length);
    const i = rest.indexOf("|");
    preferred.set(rest.slice(0, i), rest.slice(i + 1));
  }
  interface Kind {
    kind: CardKind;
    key: string;
    card: CollRow["card"];
    total: number;
    inBinder: number;
    price: number | null;
    rank: number | null;
    trend: number | null;
    type: string;
  }
  const byKind = new Map<string, CollRow[]>();
  for (const r of rows) {
    const k = kindKey(kindOf(r));
    byKind.set(k, [...(byKind.get(k) ?? []), r]);
  }
  const kinds: Kind[] = [...byKind].map(([key, list]) => {
    const r = list[0];
    return {
      kind: kindOf(r),
      key,
      card: r.card,
      total: list.reduce((n, x) => n + x.quantity, 0),
      inBinder: list.filter((x) => binder !== null && x.binder === binder).reduce((n, x) => n + x.quantity, 0),
      price: r.unitEur,
      rank: r.info?.r ?? null,
      trend: input.cardmarket ? r.changePct : null,
      type: r.info?.t ?? "",
    };
  });
  const desired = new Map<string, number>();
  const reasonOut = new Map<string, string>();
  const candidates: { k: Kind; n: number; s: number }[] = [];
  const groups = new Map<string, Kind[]>();
  for (const k of kinds) groups.set(nameKey(k.card.name), [...(groups.get(nameKey(k.card.name)) ?? []), k]);
  for (const [name, group] of groups) {
    const owned = group.reduce((n, k) => n + k.total, 0);
    const needed = deckNeeds.get(name) ?? 0;
    const keep = Math.max(needed, rules.keepOne ? 1 : 0);
    let spare = owned - keep;
    const blocked = BASICS.has(name)
      ? "basic land"
      : wish.has(name)
        ? "on your wishlist"
        : group[0].type.includes("Token") || group[0].type.includes("Emblem")
          ? "token"
          : null;
    const keepBest = rules.keepBestAlways || (rules.keepBestForDecks && needed > 0);
    const pick = preferred.get(name);
    // Kotlin's sortedWith is stable, as is Array.prototype.sort.
    const order = [...group].sort((a, b) => {
      const p = Number(b.key === pick) - Number(a.key === pick);
      if (p !== 0) return p;
      if (keepBest) return (a.price ?? Number.MAX_VALUE) - (b.price ?? Number.MAX_VALUE);
      const inB = Number(b.inBinder > 0) - Number(a.inBinder > 0);
      return inB !== 0 ? inB : (b.price ?? 0) - (a.price ?? 0);
    });
    let offeredOther = false;
    for (const k of order) {
      const price = k.price;
      let reason: string | null = null;
      if (blocked !== null) reason = blocked;
      else if (k.kind.signed || k.kind.altered) reason = [k.kind.signed ? "signed" : null, k.kind.altered ? "altered" : null].filter(Boolean).join(" and ");
      else if (spare <= 0 && offeredOther && pick !== undefined && k.key !== pick) reason = "you picked another copy to trade";
      else if (spare <= 0 && offeredOther && keepBest) reason = "a cheaper copy goes in instead; the best stays home";
      else if (spare <= 0) reason = needed > 0 ? `your decks use ${needed === 1 ? "it" : needed}` : "the copy you keep";
      else if (price == null || price < rules.minValue) reason = `under ${money(rules.minValue)} now`;
      if (reason !== null) {
        if (k.inBinder > 0) reasonOut.set(k.key, reason);
        continue;
      }
      const n = Math.min(spare, k.total);
      spare -= n;
      offeredOther = true;
      candidates.push({ k, n, s: score(price!, k.rank, k.trend) });
    }
  }
  // Declined removals stay in; declined additions stay out.
  const pinned = kinds.filter((k) => k.inBinder > 0 && skipped.has(`remove|${k.key}`));
  const markedIn = kinds.filter((k) => (k.kind.signed || k.kind.altered) && k.inBinder > 0).reduce((n, k) => n + k.inBinder, 0);
  let room = rules.maxCards - pinned.reduce((n, k) => n + k.inBinder, 0) - markedIn;
  for (const k of pinned) desired.set(k.key, k.inBinder);
  const ranked = candidates.filter((c) => !skipped.has(`add|${c.k.key}`) || c.k.inBinder > 0).sort((a, b) => b.s - a.s);
  for (const { k, n } of ranked) {
    if (desired.has(k.key)) continue;
    const take = Math.max(0, Math.min(n, room));
    if (take <= 0) {
      if (k.inBinder > 0 && !reasonOut.has(k.key)) reasonOut.set(k.key, "made room for more valuable cards");
      continue;
    }
    desired.set(k.key, take);
    room -= take;
    if (take < k.inBinder && !reasonOut.has(k.key)) reasonOut.set(k.key, "made room for more valuable cards");
  }
  const changes: TradeChange[] = [];
  for (const k of kinds) {
    if (k.kind.signed || k.kind.altered) continue;
    const diff = (desired.get(k.key) ?? 0) - k.inBinder;
    if (diff === 0) continue;
    const s = k.price != null ? score(k.price, k.rank, k.trend) : 0;
    const add = diff > 0;
    const skipKey = `${add ? "add" : "remove"}|${k.key}`;
    if (skipped.has(skipKey)) continue;
    changes.push({
      add,
      key: k.key,
      kind: k.kind,
      card: k.card,
      copies: Math.abs(diff),
      unitPrice: k.price,
      reason: add ? why(k.price, k.rank, k.trend, money) : (reasonOut.get(k.key) ?? "made room for more valuable cards"),
      score: s,
      skipKey,
    });
  }
  return changes.sort((a, b) => Number(b.add) - Number(a.add) || b.score - a.score);
}

/** The trade binder's uid: the remembered one if it still exists, else one called "Trade binder". */
export function tradeBinderOf(file: SyncFile): string | null {
  const all = binders(file).filter((b) => b.uid);
  const remembered = prefs(file)[TRADE_BINDER_PREF];
  if (remembered && all.some((b) => b.uid === remembered)) return remembered;
  return all.find((b) => b.name === TRADE_BINDER_NAME)?.uid ?? null;
}

/** The plan's inputs from the sync file: deck needs and the wishlist, as the app's TradeBinder.suggest. */
export function planInputs(file: SyncFile): { deckNeeds: Map<string, number>; wishlist: Set<string> } {
  const deckNeeds = new Map<string, number>();
  for (const d of decksOf(file)) for (const [name, n] of Object.entries(d.cards)) deckNeeds.set(nameKey(name), (deckNeeds.get(nameKey(name)) ?? 0) + n);
  const wanted = new Set(wishlist(file).filter((w) => w.receivedAt == null).map((w) => nameKey(w.card.name)));
  return { deckNeeds, wishlist: wanted };
}

/** A change the page sends back: the kind's key, in or out, and how many copies. */
export interface TradeDecision {
  key: string;
  add: boolean;
  copies: number;
}

/**
 * TradeBinder.apply: moves the [accepted] copies (making the binder if needed), copies taken out go to Unsorted; the
 * [declined] are remembered so they aren't suggested again. Answers the trade binder's uid.
 */
export function applyTrade(file: SyncFile, accepted: TradeDecision[], declined: TradeDecision[], now: number, rates: PriceFile["rates"] | null): string {
  let binder = tradeBinderOf(file);
  if (binder === null) binder = applyCollectionEdit(file, { type: "addBinder", name: TRADE_BINDER_NAME }, now, rates).uid!;
  setPrefs(file, { [TRADE_BINDER_PREF]: binder }, now);
  // A binder named after a deck holds that deck's cards: spare copies come from Unsorted and other binders first.
  // Binders named after a deck, retired ones too (TradeBinder.apply).
  const deckNames = new Set(decksOf(file, true).map((d) => d.name.toLowerCase()));
  const deckBinders = new Set(binders(file).filter((b) => b.uid && deckNames.has(b.name.toLowerCase())).map((b) => b.uid!));
  const rank = (b: string | null) => (b === null ? 0 : deckBinders.has(b) ? 2 : 1);
  const known = new Set(binders(file).map((b) => b.uid));
  for (const c of accepted) {
    let left = Math.max(0, Math.floor(c.copies));
    while (left > 0) {
      const stacks = collection(file)
        .filter((s) => s.item.uid)
        .map((s) => ({ uid: s.item.uid!, quantity: s.item.quantity, binder: s.binder && known.has(s.binder) ? s.binder : null, key: kindKey(kindOf({ card: s.item.card, finish: s.item.etched ? "ETCHED" : s.item.foil ? "FOIL" : "NONFOIL", condition: s.item.condition, language: s.item.language, signed: !!s.item.signed, altered: !!s.item.altered })) }))
        .filter((s) => s.key === c.key && (c.add ? s.binder !== binder : s.binder === binder))
        .sort((a, b) => (c.add ? rank(a.binder) - rank(b.binder) || b.quantity - a.quantity : 0));
      const s = stacks[0];
      if (!s) break;
      const n = Math.min(left, s.quantity);
      applyCollectionEdit(file, { type: "update", uid: s.uid, binder: c.add ? binder : null, move: n }, now, rates);
      left -= n;
    }
  }
  if (declined.length > 0) {
    const skips = skipsOf(prefs(file));
    for (const d of declined) skips.add(`${d.add ? "add" : "remove"}|${d.key}`);
    setPrefs(file, { [TRADE_SKIPS_PREF]: [...skips].join("\n") }, now);
  }
  return binder;
}

/** Another copy-kind of a suggested card that Swap can offer instead: where its copies are and what one is worth. */
export interface OtherCopy {
  key: string;
  kind: CardKind;
  card: CollRow["card"];
  copies: number;
  unitPrice: number | null;
  /** The binders holding it ("Unsorted" for none), in the order first seen. */
  binders: string[];
}

/** The app's TradeBinderScreen.others: your other copy-kinds of [change]'s card (not signed or altered), cheapest last. */
export function otherCopies(rows: CollRow[], change: Pick<TradeChange, "key" | "card">, binderName: (uid: string | null) => string): OtherCopy[] {
  const name = nameKey(change.card.name);
  const byKey = new Map<string, OtherCopy>();
  for (const r of rows) {
    if (nameKey(r.card.name) !== name || r.signed || r.altered) continue;
    const key = kindKey(kindOf(r));
    if (key === change.key) continue;
    const o = byKey.get(key) ?? { key, kind: kindOf(r), card: r.card, copies: 0, unitPrice: r.unitEur, binders: [] };
    o.copies += r.quantity;
    const b = binderName(r.binder);
    if (!o.binders.includes(b)) o.binders.push(b);
    byKey.set(key, o);
  }
  return [...byKey.values()].sort((a, b) => (b.unitPrice ?? -1) - (a.unitPrice ?? -1));
}

/**
 * TradeBinder.prefer (Swap): offer copy-kind [key] of card [name] from now on, instead of [insteadOf], which is
 * declined so it isn't suggested again.
 */
export function preferCopy(file: SyncFile, name: string, key: string, insteadOf: string, now: number): void {
  const prefix = `prefer|${nameKey(name)}|`;
  const skips = new Set([...skipsOf(prefs(file))].filter((k) => !k.startsWith(prefix)));
  skips.add(prefix + key);
  skips.add(`add|${insteadOf}`);
  setPrefs(file, { [TRADE_SKIPS_PREF]: [...skips].join("\n") }, now);
}

/** Suggests the declined cards again (keeps the copies picked with Swap). */
export function forgetDeclined(file: SyncFile, now: number): void {
  const keep = [...skipsOf(prefs(file))].filter((k) => k.startsWith("prefer|"));
  setPrefs(file, { [TRADE_SKIPS_PREF]: keep.join("\n") }, now);
}
