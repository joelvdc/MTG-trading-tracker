import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";

// The website shares its win turns with the phones (app 1.123): /api/winturns hands the kept results to the phone and
// takes the ones the phone worked out, and a deck is never played twice for the same list.

// No Scryfall or Blob lookups here: the card details are empty, as a deck whose cards the website doesn't know yet.
vi.mock("@/lib/deckdetails", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/deckdetails")>();
  return { ...actual, ensureDeckDetails: async () => ({}) };
});

import { accountStore, memoryStore, resetMemoryStore, type AccountStore } from "@/lib/accounts";
import { addDevice } from "@/lib/users";
import { syncDecks } from "@/lib/decks";
import type { SyncDeck } from "@/lib/deckshared";
import type { StatsCard } from "@/lib/deckstats";
import { ratingListKey } from "@/lib/localratings";
import { encode, parse, type Json, type SyncFile } from "@/lib/syncfile";
import { DECK_MS, deckListKey, keepPhoneWinTurns, phoneWinTurns, runWinTurns, saltHash, winSource } from "@/lib/winturns";
import { WIN_ODDS_VERSION } from "@/lib/winodds-js/version";
import { GET, POST } from "@/app/api/winturns/route";

const NOW = 1_793_968_645_412;
const DECK = 10710772;

/** The app's sample file with its deck given a commander, and a second deck without one. */
function file(): SyncFile {
  const f = parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
  const [d] = syncDecks(f);
  const withCommander: SyncDeck = { ...d, cards: d.cards.map((c, i) => ({ ...c, commander: i === 0, maybeboard: false })) };
  const without: SyncDeck = { deck: { ...d.deck, archidektId: -5, name: "No commander" }, cards: d.cards.map((c) => ({ ...c, commander: false })) };
  f.data.decks = [without, withCommander] as unknown as Json;
  return f;
}

async function setup(f: SyncFile = file()): Promise<{ store: AccountStore; id: number }> {
  const store = memoryStore();
  const a = (await store.create("joel", "h", true, NOW))!;
  await store.putSync(a.id, encode(f), null, NOW);
  return { store, id: a.id };
}

const details = async () => ({});

/** The deck with a commander, as the website has it. */
const deck = () => syncDecks(file()).find((d) => d.deck.archidektId === DECK)!;

/** The phone's own answer, the same shape as the website's winOddsJson. */
const RESULT = JSON.stringify({ odds: { "7": 0.4 }, label: "wins by turn 7", fourInTen: 7 });

/** An item as the phone sends it, with the fingerprints of the decklist the website has. */
function item(sd: SyncDeck = deck(), over: Record<string, unknown> = {}) {
  return {
    deckId: sd.deck.archidektId,
    listKey: deckListKey(sd),
    version: WIN_ODDS_VERSION,
    identity: sd.deck.colorIdentity ?? "",
    saltHash: saltHash(sd.deck.saltCard ?? null),
    result: RESULT,
    ...over,
  };
}

describe("the phone's fingerprints", () => {
  it("hashes the salt card text the way the app does", () => {
    expect(saltHash(null)).toBe(saltHash(""));
    expect(saltHash(null)).toMatch(/^[0-9a-f]{8}$/);
    expect(saltHash('{"salt":2}')).not.toBe(saltHash(null));
  });

  it("names a decklist the way the phone's LocalRating does", () => {
    const sd = deck();
    const lines = sd.cards.filter((c) => !c.maybeboard).map((c) => ({ name: c.card.name, quantity: c.quantity, commander: c.commander }) as StatsCard);
    expect(deckListKey(sd)).toBe(ratingListKey(lines));
    // The maybeboard isn't part of the list.
    const withMaybe: SyncDeck = { ...sd, cards: [...sd.cards, { ...sd.cards[0], maybeboard: true, quantity: 5 }] };
    expect(deckListKey(withMaybe)).toBe(deckListKey(sd));
  });
});

describe("the website's win turns for the phone", () => {
  it("answers the kept results that still belong to the current list", async () => {
    const { store, id } = await setup();
    const sd = deck();
    await store.putWinTurn(id, DECK, { key: winSource(sd, {})!.key, result: RESULT, at: NOW });
    // A result kept for another list, and a deck without a commander: neither is the phone's.
    await store.putWinTurn(id, -5, { key: "old", result: RESULT, at: NOW });
    expect(await phoneWinTurns(id, { store, details })).toEqual([
      {
        deckId: DECK,
        listKey: deckListKey(sd),
        version: WIN_ODDS_VERSION,
        identity: "UB",
        saltHash: saltHash(null),
        result: RESULT,
        at: NOW,
      },
    ]);
  });

  it("leaves out a result its list has changed since", async () => {
    const { store, id } = await setup();
    await store.putWinTurn(id, DECK, { key: "old", result: RESULT, at: NOW });
    expect(await phoneWinTurns(id, { store, details })).toEqual([]);
  });

  it("has nothing for an account without a sync file", async () => {
    const store = memoryStore();
    const a = (await store.create("joel", "h", true, NOW))!;
    expect(await phoneWinTurns(a.id, { store, details })).toEqual([]);
  });
});

describe("the phone's win turns for the website", () => {
  it("keeps a result that matches the decklist the website has", async () => {
    const { store, id } = await setup();
    const sd = deck();
    expect(await keepPhoneWinTurns(id, [item(sd)], { store, details, clock: () => NOW })).toEqual([DECK]);
    expect((await store.winTurns(id)).get(DECK)).toEqual({ key: winSource(sd, {})!.key, result: RESULT, at: NOW });
  });

  it("skips anything that doesn't match the decklist, or that the website has already", async () => {
    const { store, id } = await setup();
    const sd = deck();
    const notInFile: SyncDeck = { ...sd, deck: { ...sd.deck, archidektId: -7 } };
    const noCommander: SyncDeck = { ...sd, deck: { ...sd.deck, archidektId: -5 } };
    const items = [
      item(sd, { listKey: "no-such-list" }),
      item(sd, { saltHash: "00000000" }),
      item(sd, { identity: "W" }),
      item(sd, { version: WIN_ODDS_VERSION + 1 }),
      item(sd, { result: JSON.stringify({ label: "no odds" }) }),
      item(sd, { result: "not json" }),
      item(notInFile),
      item(noCommander),
      { deckId: "10710772", listKey: deckListKey(sd), version: WIN_ODDS_VERSION, identity: "UB", saltHash: saltHash(null), result: RESULT },
      "not an item",
    ];
    expect(await keepPhoneWinTurns(id, items, { store, details, clock: () => NOW })).toEqual([]);
    // A body that isn't a list of results.
    expect(await keepPhoneWinTurns(id, { results: [] }, { store, details, clock: () => NOW })).toEqual([]);
    expect((await store.winTurns(id)).size).toBe(0);

    // The website's own result for this list stays.
    await store.putWinTurn(id, DECK, { key: winSource(sd, {})!.key, result: "null", at: NOW - 1 });
    expect(await keepPhoneWinTurns(id, [item(sd)], { store, details, clock: () => NOW })).toEqual([]);
    expect((await store.winTurns(id)).get(DECK)!.result).toBe("null");
  });
});

describe("a run of the website's own win turns", () => {
  it("doesn't play a deck whose result arrived while the run was busy", async () => {
    const { store, id } = await setup();
    const key = winSource(deck(), {})!.key;
    // The phone sends it while the website is fetching the card details (which happens before the playing).
    const arriving = async () => {
      await store.putWinTurn(id, DECK, { key, result: RESULT, at: NOW });
      return {};
    };
    const played: number[] = [];
    const r = await runWinTurns(id, NOW + 10 * DECK_MS, {
      store,
      details: arriving,
      play: (s) => (played.push(s.deckId), "null"),
      clock: () => NOW,
    });
    expect(played).toEqual([]);
    expect(r).toMatchObject({ done: 0, left: 0, decks: 1, ran: true });
    expect((await store.winTurns(id)).get(DECK)!.result).toBe(RESULT);
  });
});

describe("/api/winturns", () => {
  const URL = "https://mtgtrader.vercel.app/api/winturns";
  let token: string;
  let accountId: number;

  beforeEach(async () => {
    process.env.DATABASE_URL = "memory";
    resetMemoryStore();
    const store = accountStore()!;
    const account = (await store.create("joel", "x", true, NOW))!;
    accountId = account.id;
    token = (await addDevice(store, account, "Test phone", NOW)).token;
    await store.putSync(account.id, encode(file()), null, NOW);
    await store.putWinTurn(account.id, DECK, { key: winSource(deck(), {})!.key, result: RESULT, at: NOW });
  });

  const auth = (on = true): Record<string, string> => (on ? { authorization: `Bearer ${token}` } : {});
  const get = (on = true) => GET(new Request(URL, { headers: auth(on) }));
  const post = (body: unknown, on = true) =>
    POST(new Request(URL, { method: "POST", headers: { "content-type": "application/json", ...auth(on) }, body: JSON.stringify(body) }));

  it("wants a phone's login", async () => {
    expect((await get(false)).status).toBe(401);
    expect((await post({ results: [] }, false)).status).toBe(401);
  });

  it("answers the website's kept win turns and the app's version", async () => {
    const r = await get();
    expect(r.status).toBe(200);
    const body = (await r.json()) as { version: number; decks: Record<string, unknown>[] };
    expect(body.version).toBe(WIN_ODDS_VERSION);
    expect(body.decks).toEqual([
      { deckId: DECK, listKey: deckListKey(deck()), version: WIN_ODDS_VERSION, identity: "UB", saltHash: saltHash(null), result: RESULT, at: NOW },
    ]);
  });

  it("keeps the result the phone sent, and says no to a body that isn't a win turn", async () => {
    // The website's result is for another list, so the phone's takes its place.
    await accountStore()!.putWinTurn(accountId, DECK, { key: "old", result: "null", at: NOW });
    expect(await (await post({ results: [item()] })).json()).toEqual({ kept: [DECK] });
    expect((await accountStore()!.winTurns(accountId)).get(DECK)!.result).toBe(RESULT);
    const bad = await POST(new Request(URL, { method: "POST", headers: { "content-type": "application/json", ...auth() }, body: "not json" }));
    expect(bad.status).toBe(400);
  });
});
