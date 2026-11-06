// The decks' win turns worked out on the website, without opening the deck (owner, 2026-11-07: "calculates the
// win-turn for all decks automatically … then only recalculates if changes happen to the decklist"). The app's own
// code (web/winodds-kt) plays each deck 10,000 times, about 45 seconds a deck, so a run does what fits in its time and
// the next run (the daily job, or opening the Decks page) goes on. A result is kept with the key of the decklist it
// belongs to (its cards, colours and Commander Salt's combos, plus the app's WinOddsCalc.VERSION) and is worked out
// again only when that key changes. The deck page uses a kept result, or works it out in the browser and keeps it here.
import { accountStore, type AccountStore, type StoredWinTurn } from "./accounts";
import { ensureDeckDetails, statsCard, type DeckInfo } from "./deckdetails";
import type { SyncDeck } from "./deckshared";
import type { StatsCard } from "./deckstats";
import { syncDecks } from "./decks";
import { decode, type SyncFile } from "./syncfile";
import { fingerprint, winInput, type WinResult } from "./winodds";
import { winOddsJson } from "./winodds-js/winodds-kt.mjs";
import { WIN_ODDS_VERSION } from "./winodds-js/version";

/** The key of what a win turn is worked out from: the same in the browser (components/useWinOdds.ts) and here. */
export function winKey(input: string, identity: string, saltCard: string | null): string {
  return `${WIN_ODDS_VERSION}|${fingerprint(`${input}|${identity}|${saltCard ?? ""}`)}`;
}

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
