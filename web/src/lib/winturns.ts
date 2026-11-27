// The decks' win turns worked out on the website, without opening the deck (owner, 2026-11-07: "calculates the
// win-turn for all decks automatically … then only recalculates if changes happen to the decklist"). The app's own
// code (web/winodds-kt) plays each deck 10,000 times, about 45 seconds a deck, so a run does what fits in its time and
// the next run (the daily job, or opening the Decks page) goes on. A result is kept with the key of the decklist it
// belongs to (its cards, colours and the card's combos, plus the app's WinOddsCalc.VERSION) and is worked out
// again only when that key changes. The deck page uses a kept result, or works it out in the browser and keeps it here.
import { accountStore, type AccountStore, type StoredWinTurn } from "./accounts";
import { ensureDeckDetails, statsCard, type DeckInfo } from "./deckdetails";
import type { SyncDeck } from "./deckshared";
import type { StatsCard } from "./deckstats";
import { ratingListKey } from "./localratings";
import { syncDecks } from "./decks";
import { decode, type SyncFile } from "./syncfile";
import { winInput, winKey, type WinResult } from "./winodds";
import { WIN_ODDS_VERSION } from "./winodds-js/version";
import { winOddsJson } from "./winodds-js/winodds-kt.mjs";

export { winKey };

/** A deck's main-deck lines as the stats and the win turn take them. */
export function deckStatsCards(sd: SyncDeck, info: Record<string, DeckInfo>): StatsCard[] {
  return sd.cards
    .filter((c) => !c.maybeboard)
    .map((c) => statsCard({ name: c.card.name, scryfallId: c.card.scryfallId, quantity: c.quantity, cmc: c.cmc, types: c.types, commander: c.commander }, info[c.card.scryfallId]));
}

/** What a deck's win turn is worked out from; null for a deck without a commander (it has none). */
export interface WinSource {
  deckId: number;
  key: string;
  input: string;
  identity: string;
  saltCard: string | null;
}

export function winSource(sd: SyncDeck, info: Record<string, DeckInfo>): WinSource | null {
  const cards = deckStatsCards(sd, info);
  if (!cards.some((c) => c.commander)) return null;
  const input = winInput(cards);
  const identity = sd.deck.colorIdentity ?? "";
  const saltCard = sd.deck.saltCard ?? null;
  return { deckId: sd.deck.archidektId, key: winKey(input, identity, saltCard), input, identity, saltCard };
}

/**
 * A deck's salt card text as a short fingerprint (2026-11-27): the app's LocalRating computes the same hash (FNV-1a,
 * 8 hex digits) of the deck's saltCard text, so a result sent from the phone is only kept while the salt is the same.
 */
export function saltHash(saltCard: string | null): string {
  const text = saltCard ?? "";
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h.toString(16).padStart(8, "0");
}

/**
 * A deck's list as the app's LocalRating.listKey sees it (2026-11-27): the same fingerprint the rating keeps, so the
 * website can tell whether a phone's win turn belongs to the decklist it has now.
 */
export function deckListKey(sd: SyncDeck): string {
  const cards = sd.cards
    .filter((c) => !c.maybeboard)
    .map((c) => ({ name: c.card.name, quantity: c.quantity, commander: c.commander }) as StatsCard);
  return ratingListKey(cards);
}

/** A kept result as the pages take it (null: the deck has none, e.g. too few cards). */
export function parseWin(w: StoredWinTurn | undefined): WinResult | null {
  if (!w) return null;
  try {
    return JSON.parse(w.result) as WinResult | null;
  } catch {
    return null;
  }
}

/** The kept win turns of an account's decks (whether or not their list changed since), by deck id. */
export async function keptWins(accountId: number): Promise<Map<number, StoredWinTurn>> {
  const store = accountStore();
  if (!store) return new Map();
  try {
    return await store.winTurns(accountId);
  } catch (e) {
    console.warn(`Win turns: ${e instanceof Error ? e.message : e}`);
    return new Map();
  }
}

// ---- The phones' own results (2026-11-27) -------------------------------------------------------------------------

/** How long the card details get before the answer goes without them (the run keeps what it has). */
const PHONE_DETAILS_MS = 20_000;
/** How many items of a phone's answer are looked at. */
const PHONE_MAX_ITEMS = 200;
/** The longest result text a phone may send; the website's own is well under 100 KB. */
const PHONE_MAX_RESULT = 200_000;

/** A deck's win turn as the phone works it out and takes it back. */
export interface PhoneWinTurn {
  deckId: number;
  /** The decklist the result belongs to (the app's LocalRating.listKey). */
  listKey: string;
  /** The app's WinOddsCalc.VERSION it was worked out with. */
  version: number;
  identity: string;
  /** The fingerprint of the deck's saltCard text (saltHash above). */
  saltHash: string;
  /** The app's JSON answer, the same shape as winOddsJson's. */
  result: string;
  at: number;
}

/**
 * The account's kept win turns as the phone takes them: only the decks whose kept result still belongs to their current
 * list, with the phone's own fingerprints so it can tell which of its decks the result is for.
 */
export async function phoneWinTurns(
  accountId: number,
  opts: { store?: AccountStore; details?: (ids: string[], deadline: number) => Promise<Record<string, DeckInfo>> } = {},
): Promise<PhoneWinTurn[]> {
  const store = opts.store ?? accountStore();
  if (!store) return [];
  const got = await store.getSync(accountId);
  if (!got) return [];
  const all = syncDecks(decode(got.bytes));
  const kept = await store.winTurns(accountId);
  const withCommander = all.filter((d) => kept.has(d.deck.archidektId) && d.cards.some((c) => c.commander && !c.maybeboard));
  if (withCommander.length === 0) return [];
  const ids = [...new Set(withCommander.flatMap((d) => d.cards.filter((c) => !c.maybeboard).map((c) => c.card.scryfallId)))];
  const info = await (opts.details ?? ensureDeckDetails)(ids, Date.now() + PHONE_DETAILS_MS);
  const out: PhoneWinTurn[] = [];
  for (const sd of withCommander) {
    const w = kept.get(sd.deck.archidektId)!;
    const source = winSource(sd, info);
    // A result kept for another list says nothing about this one.
    if (!source || w.key !== source.key) continue;
    out.push({
      deckId: sd.deck.archidektId,
      listKey: deckListKey(sd),
      version: WIN_ODDS_VERSION,
      identity: sd.deck.colorIdentity ?? "",
      saltHash: saltHash(sd.deck.saltCard ?? null),
      result: w.result,
      at: w.at,
    });
  }
  return out;
}

/** What a phone sent, if it's the right shape and size; null for anything else (skipped silently). */
function phoneItem(item: unknown): { deckId: number; listKey: string; version: number; identity: string; saltHash: string; result: string } | null {
  if (!item || typeof item !== "object") return null;
  const o = item as Record<string, unknown>;
  if (typeof o.deckId !== "number" || typeof o.version !== "number") return null;
  if (typeof o.listKey !== "string" || typeof o.identity !== "string" || typeof o.saltHash !== "string") return null;
  if (typeof o.result !== "string" || o.result.length > PHONE_MAX_RESULT) return null;
  // The same shape as the website's own answer, so the deck page reads it the same way.
  try {
    const parsed = JSON.parse(o.result) as unknown;
    if (!parsed || typeof parsed !== "object" || !("odds" in (parsed as Record<string, unknown>))) return null;
  } catch {
    return null;
  }
  return { deckId: o.deckId, version: o.version, listKey: o.listKey, identity: o.identity, saltHash: o.saltHash, result: o.result };
}

/**
 * Keeps the win turns the phone worked out, for the decks whose list it still matches; answers the deck ids kept. The
 * website's own result for a list wins over the phone's, so neither side works a deck out again and again.
 */
export async function keepPhoneWinTurns(
  accountId: number,
  items: unknown,
  opts: { store?: AccountStore; details?: (ids: string[], deadline: number) => Promise<Record<string, DeckInfo>>; clock?: () => number } = {},
): Promise<number[]> {
  const store = opts.store ?? accountStore();
  const clock = opts.clock ?? Date.now;
  if (!store || !Array.isArray(items)) return [];
  const got = await store.getSync(accountId);
  if (!got) return [];
  const all = syncDecks(decode(got.bytes));
  const kept = await store.winTurns(accountId);
  // The decks it's about, with the result: only where every fingerprint still matches the decklist the website has.
  const wanted = new Map<number, { sd: SyncDeck; result: string }>();
  for (const item of items.slice(0, PHONE_MAX_ITEMS)) {
    const it = phoneItem(item);
    if (!it) continue;
    const sd = all.find((d) => d.deck.archidektId === it.deckId);
    if (!sd) continue;
    if (it.version !== WIN_ODDS_VERSION) continue;
    if (it.identity !== (sd.deck.colorIdentity ?? "")) continue;
    if (it.saltHash !== saltHash(sd.deck.saltCard ?? null)) continue;
    if (it.listKey !== deckListKey(sd)) continue;
    wanted.set(it.deckId, { sd, result: it.result });
  }
  if (wanted.size === 0) return [];
  const ids = [...new Set([...wanted.values()].flatMap(({ sd }) => sd.cards.filter((c) => !c.maybeboard).map((c) => c.card.scryfallId)))];
  const info = await (opts.details ?? ensureDeckDetails)(ids, Date.now() + PHONE_DETAILS_MS);
  const done: number[] = [];
  for (const [deckId, { sd, result }] of wanted) {
    const source = winSource(sd, info);
    // No commander, or this list's result is here already: nothing to keep.
    if (!source || kept.get(deckId)?.key === source.key) continue;
    await store.putWinTurn(accountId, deckId, { key: source.key, result, at: clock() });
    done.push(deckId);
  }
  return done;
}

/** About how long one deck takes, to stop before the time is up rather than in the middle of a deck. */
export const DECK_MS = 75_000;
const lockName = (accountId: number) => `winturns:${accountId}`;
/** A run that hasn't finished after this long is taken to have stopped, and another may start. */
const LOCK_MS = 6 * 60_000;

export interface WinRun {
  /** Decks worked out in this run. */
  done: number;
  /** Decks whose list changed (or never had one) still waiting. */
  left: number;
  /** Decks with a commander. */
  decks: number;
  /** False when another run for the account was busy, so this one did nothing. */
  ran: boolean;
}

/**
 * Works out the win turn of the account's decks that have none for their current list, one after the other, until
 * [deadline]; [play] is the app's code (swapped in the tests). One run per account at a time.
 */
export async function runWinTurns(
  accountId: number,
  deadline: number,
  opts: { store?: AccountStore; play?: (s: WinSource) => string; details?: (ids: string[], deadline: number) => Promise<Record<string, DeckInfo>>; clock?: () => number } = {},
): Promise<WinRun> {
  const store = opts.store ?? accountStore();
  const clock = opts.clock ?? Date.now;
  if (!store) return { done: 0, left: 0, decks: 0, ran: false };
  if (!(await store.claimJob(lockName(accountId), clock(), LOCK_MS))) return { done: 0, left: 0, decks: 0, ran: false };
  let done = 0;
  let left = 0;
  let decks = 0;
  try {
    const got = await store.getSync(accountId);
    if (!got) return { done, left, decks, ran: true };
    const file: SyncFile = decode(got.bytes);
    const all = syncDecks(file);
    const kept = await store.winTurns(accountId);
    const gone = [...kept.keys()].filter((id) => !all.some((d) => d.deck.archidektId === id));
    if (gone.length) await store.dropWinTurns(accountId, gone);
    const withCommander = all.filter((d) => d.cards.some((c) => c.commander && !c.maybeboard));
    decks = withCommander.length;
    const ids = [...new Set(withCommander.flatMap((d) => d.cards.filter((c) => !c.maybeboard).map((c) => c.card.scryfallId)))];
    const info = await (opts.details ?? ensureDeckDetails)(ids, Math.min(deadline, clock() + 60_000));
    const sources = withCommander.flatMap((d) => winSource(d, info) ?? []).filter((s) => kept.get(s.deckId)?.key !== s.key);
    left = sources.length;
    const play = opts.play ?? ((s: WinSource) => winOddsJson(s.input, s.identity, s.saltCard, null, null));
    for (const s of sources) {
      if (clock() + DECK_MS > deadline) break;
      // The phone may have sent this deck's result while this run was busy: nothing left to work out for it.
      if ((await store.winTurns(accountId)).get(s.deckId)?.key === s.key) {
        left--;
        continue;
      }
      let result: string;
      try {
        result = play(s);
      } catch (e) {
        // A deck the app's code can't play is kept as having none, so it isn't tried again until its list changes.
        console.warn(`Win turn of deck ${s.deckId}: ${e instanceof Error ? e.message : e}`);
        result = "null";
      }
      await store.putWinTurn(accountId, s.deckId, { key: s.key, result, at: clock() });
      done++;
      left--;
    }
    return { done, left, decks, ran: true };
  } finally {
    await store.setJob(lockName(accountId), 0, { done, left, decks, at: clock() });
  }
}

/** Every account's decks (the daily job), until [deadline]: what was done and what is left. */
export async function runAllWinTurns(deadline: number, store: AccountStore | null = accountStore()): Promise<{ done: number; left: number; accounts: number }> {
  if (!store) return { done: 0, left: 0, accounts: 0 };
  let done = 0;
  let left = 0;
  const accounts = await store.list();
  for (const a of accounts) {
    if (Date.now() + DECK_MS > deadline) {
      left++;
      continue;
    }
    try {
      const r = await runWinTurns(a.id, deadline, { store });
      done += r.done;
      left += r.left;
    } catch (e) {
      console.warn(`Win turns for account ${a.id}: ${e instanceof Error ? e.message : e}`);
    }
  }
  return { done, left, accounts: accounts.length };
}
