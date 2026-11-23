import { beforeEach, describe, expect, it, vi } from "vitest";

// POST /api/decks/[id]/rate: rates a local deck and keeps the rating in the sync file. The tests give it a logged-in
// account (a website account or an older Nextcloud one), a small sync file, and fake card facts and combos.

const USER = { kind: "user", accountId: 1, username: "joel", admin: true, device: "test" };
const NEXTCLOUD = { server: "https://cloud.example.com", loginName: "me@example.com", userId: "joel", appPassword: "x", folder: "MTG Trader" };
let account: unknown = USER;

// The sync file the store answers with; the edit goes on it, as editSync would.
let file: unknown = null;

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return { ...actual, currentAccount: async () => account };
});

vi.mock("@/lib/store", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/store")>();
  return {
    ...actual,
    loadSync: async () => ({ file, etag: null, bytes: new Uint8Array() }),
    editSync: async (_a: unknown, change: (f: unknown, now: number) => unknown) => change(file, 2_000),
  };
});

vi.mock("@/lib/deckdetails", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/deckdetails")>();
  return { ...actual, ensureDeckDetails: async () => ({}) };
});

const combos = vi.fn(async (_commanders: string[], _main: { name: string; quantity: number }[]) => [] as unknown[]);
vi.mock("@/lib/spellbook", () => ({ findCombos: (...args: unknown[]) => combos(...(args as [string[], { name: string; quantity: number }[]])) }));
vi.mock("@/lib/cardfacts", () => ({ cardFactsByName: async (names: string[]) => new Map(names.map((n) => [n, { tags: [], gameChanger: false, salt: null }])) }));

import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { POST } from "@/app/api/decks/[id]/rate/route";

const cardRef = (name: string) => ({
  scryfallId: `id-${name}`,
  name,
  setCode: "tst",
  setName: "Test Set",
  collectorNumber: "1",
  rarity: "common",
  imageUrl: null,
  cardmarketId: null,
  fallbackEur: null,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: false,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

const line = (name: string, commander = false) => ({ card: cardRef(name), quantity: 1, types: "Creature", cmc: 2, commander, maybeboard: false });

/** A small file with one local deck (-1): a commander and one card. */
const localFile = () => ({
  format: 16,
  app: "test",
  writtenAt: 0,
  writtenBy: "test",
  data: {
    decks: [
      {
        deck: { archidektId: -1, name: "My deck", colorIdentity: "UB", updatedAt: 1_000, saltCard: null, powerLevel: 6.5, saltPercent: 12.5 },
        cards: [line("Cmdr", true), line("Bear")],
      },
    ],
  },
});

const rate = (id = -1) =>
  POST(new Request(`https://mtgtrader.vercel.app/api/decks/${id}/rate`, { method: "POST", headers: { "X-Requested-With": "mtgtrader" }, body: "{}" }), {
    params: Promise.resolve({ id: String(id) }),
  });

beforeEach(async () => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
  account = USER;
  file = localFile();
  combos.mockClear();
  await accountStore()!.replaceCardFacts([{ key: "bear", fact: { tags: [], gameChanger: false, salt: 1.5 } }], Date.now());
});

describe("POST /api/decks/[id]/rate", () => {
  it("needs a login and the website's own pages", async () => {
    account = null;
    expect((await rate()).status).toBe(401);
  });

  it("rates a local deck of a website account", async () => {
    const r = await rate();
    expect(r.status).toBe(200);
    expect(await r.json()).toEqual({ ok: true });
    const deck = (file as { data: { decks: { deck: { saltCard: string; bracketBaseline: number; bracketRealistic: number | null } }[] } }).data.decks[0].deck;
    const card = JSON.parse(deck.saltCard) as { source: string; version: number; bracket: number };
    expect(card.source).toBe("app");
    expect(card.version).toBe(4);
    expect(typeof card.bracket).toBe("number");
    expect(deck.bracketBaseline).toBe(card.bracket);
    expect(deck.bracketRealistic).toBeNull();
    expect(combos).toHaveBeenCalledWith(["Cmdr"], [{ name: "Bear", quantity: 1 }]);
  });

  it("rates a local deck of an older Nextcloud account too", async () => {
    account = NEXTCLOUD;
    const r = await rate();
    expect(r.status).toBe(200);
    expect(await r.json()).toEqual({ ok: true });
    const deck = (file as { data: { decks: { deck: { saltCard: string | null } }[] } }).data.decks[0].deck;
    expect(JSON.parse(deck.saltCard!).source).toBe("app");
  });

  it("doesn't rate a deck from Archidekt, or one that isn't there", async () => {
    expect((await rate(123)).status).toBe(404);
  });

  it("says so, and keeps nothing, while the card facts aren't there yet", async () => {
    resetMemoryStore();
    const r = await rate();
    expect(r.status).toBe(503);
    expect(((await r.json()) as { error: string }).error).toBe("Card facts aren't ready yet.");
    expect((file as { data: { decks: { deck: { saltCard: string | null } }[] } }).data.decks[0].deck.saltCard).toBeNull();
  });
});
