import { beforeEach, describe, expect, it, vi } from "vitest";

// The website's "Compare decks" route: another deck of the sync file, an Archidekt link or a pasted list, compared with
// this deck. The tests give it a logged-in account, a small sync file, fixed Scryfall answers and a fake Archidekt.

const ACCOUNT = { kind: "user", accountId: 1, username: "joel", admin: true, device: "test" };

// The sync file the tests answer with; each test sets it.
let syncFile: unknown = null;

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return { ...actual, currentAccount: async () => ACCOUNT };
});

vi.mock("@/lib/store", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/store")>();
  return { ...actual, loadSync: async () => ({ file: syncFile, etag: null, bytes: new Uint8Array() }) };
});

// Scryfall knows three cards of the pasted lists below (by name, as the lists give no set or number).
const SCRY = {
  atraxa: { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", name: "Atraxa, Praetors' Voice", typeLine: "Legendary Creature — Phyrexian Angel Horror" },
  sol: { id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", name: "Sol Ring", typeLine: "Artifact" },
  forest: { id: "cccccccc-cccc-cccc-cccc-cccccccccccc", name: "Forest", typeLine: "Basic Land — Forest" },
};
let scryFails = false;

vi.mock("@/lib/scryfall", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/scryfall")>();
  return {
    ...actual,
    lookupCollectionFull: async (identifiers: object[]) => {
      if (scryFails) throw new Error("Scryfall is down");
      const found = Object.values(SCRY).filter((c) => identifiers.some((i) => (i as { name?: string }).name === c.name));
      return found.map((c) => ({
        card: {
          scryfallId: c.id,
          name: c.name,
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
        },
        typeLine: c.typeLine,
        layout: "normal",
        cmc: 1,
        colorIdentity: [],
      }));
    },
  };
});

import { resetArchidekt } from "@/lib/archidekt";
import type { CompareCard } from "@/lib/deckcompare";
import type { SyncDeck } from "@/lib/deckshared";
import { GET, POST } from "@/app/api/decks/[id]/compare/route";

const ME = 100;
const OTHER = 200;

/** A card reference with what the sync file needs. */
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

/** A deck line; a plain main-deck card unless the options say otherwise. */
const line = (name: string, quantity = 1, types = "Instant", commander = false, maybeboard = false) => ({
  card: cardRef(name),
  quantity,
  types,
  commander,
  maybeboard,
});

/** A deck of the sync file with just what the route reads. */
const deck = (archidektId: number, name: string, cards: ReturnType<typeof line>[]): SyncDeck =>
  ({ deck: { archidektId, name, owner: "joelvdc" }, cards }) as unknown as SyncDeck;

/** A small sync file: three decks and a collection (one Sol Ring, two Forests). */
const fileWith = (decks: SyncDeck[], collection: { name: string; quantity: number }[] = []) => ({
  format: 15,
  app: "test",
  writtenAt: 0,
  writtenBy: "test",
  data: {
    decks,
    collection: collection.map((c) => ({ item: { card: cardRef(c.name), quantity: c.quantity }, binder: null })),
  },
});

// Two decks to compare: mine (ME) and "Zed's Party" (OTHER); a third, "alpha", sorts first by name.
const mineDeck = () =>
  deck(ME, "My Deck", [
    line("Atraxa, Praetors' Voice", 1, "Legendary Creature", true),
    line("Sol Ring", 1, "Artifact"),
    line("Cultivate", 1, "Sorcery"),
    line("Maybe In Mine", 1, "Instant", false, true),
  ]);
const zedDeck = () =>
  deck(OTHER, "Zed's Party", [
    line("Atraxa, Praetors' Voice", 1, "Legendary Creature", true),
    line("Sol Ring", 1, "Artifact"),
    line("Rampant Growth", 2, "Sorcery"),
    line("Maybe In Zed", 1, "Instant", false, true),
  ]);
const threeDecks = () => fileWith([mineDeck(), zedDeck(), deck(300, "alpha", [line("Island", 3, "Basic Land")])], [
  { name: "Sol Ring", quantity: 1 },
  { name: "Forest", quantity: 2 },
]);

// ---- A fake Archidekt (no test touches the network) ---------------------------------------------------------------

/** What the fake Archidekt answers per deck id: a deck, or a status when it can't be read. */
const archidekt: Record<string, unknown | number> = {};
let fetched: string[] = [];

/** An Archidekt deck as its API answers it: cards in the deck, a "Maybeboard" category left out of it. */
const archidektDeck = (id: number, name: string, owner: string, cards: { name: string; quantity: number; types: string[]; commander?: boolean; maybe?: boolean }[]) => ({
  id,
  name,
  owner: { username: owner },
  categories: [
    { id: 1, name: "Commander", includedInDeck: true, isPremier: true },
    { id: 2, name: "Ramp", includedInDeck: true, isPremier: false },
    { id: 3, name: "Maybeboard", includedInDeck: false, isPremier: false },
  ],
  cards: cards.map((c) => ({
    id: 1,
    label: "",
    categories: c.maybe ? ["Maybeboard"] : c.commander ? ["Commander"] : ["Ramp"],
    quantity: c.quantity,
    modifier: "Normal",
    card: {
      id: 1,
      uid: `uid-${c.name}`,
      collectorNumber: "1",
      edition: { editioncode: "tst", editionname: "Test Set" },
      oracleCard: { name: c.name, cmc: 2, types: c.types, colorIdentity: [], defaultCategory: null, gameChanger: false },
    },
  })),
});

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

const fakeFetch = async (input: RequestInfo | URL): Promise<Response> => {
  const url = String(input);
  fetched.push(url);
  if (url.includes("remote-switches.json")) return json({ archidektDecks: true });
  const m = /^https:\/\/archidekt\.com\/api\/decks\/(\d+)\/$/.exec(url);
  if (m) {
    const answer = archidekt[m[1]];
    if (answer === undefined) return json({ detail: "Not found." }, 404);
    if (typeof answer === "number") return json({ detail: "Not allowed." }, answer);
    return json(answer);
  }
  throw new Error(`Unexpected fetch: ${url}`);
};

beforeEach(() => {
  syncFile = threeDecks();
  scryFails = false;
  resetArchidekt();
  fetched = [];
  for (const k of Object.keys(archidekt)) delete archidekt[k];
  vi.stubGlobal("fetch", vi.fn(fakeFetch));
});

// ---- Calling the route --------------------------------------------------------------------------------------------

// The route's handlers take the route's params ({ params }), like the page passes them.
const callGet = (id: number) =>
  GET(new Request(`https://mtgtrader.vercel.app/api/decks/${id}/compare`), { params: Promise.resolve({ id: String(id) }) });

const callPost = (id: number, body: unknown, headers: Record<string, string> = { "x-requested-with": "mtgtrader" }) =>
  POST(
    new Request(`https://mtgtrader.vercel.app/api/decks/${id}/compare`, {
      method: "POST",
      headers: { "content-type": "application/json", ...headers },
      body: JSON.stringify(body),
    }),
    { params: Promise.resolve({ id: String(id) }) },
  );

/** The answer body. */
const body = async (r: Response) => (await r.json()) as Record<string, unknown>;

/** Cards as "copies name", for the short expectations. */
const short = (cards: CompareCard[]) => cards.map((c) => `${c.quantity} ${c.name}`);

// ---- The tests ----------------------------------------------------------------------------------------------------

describe("comparing a deck: the list of decks (GET)", () => {
  it("lists the other decks by name, without this one", async () => {
    const r = await callGet(ME);
    expect(r.status).toBe(200);
    expect(await body(r)).toEqual({
      decks: [
        { id: 300, name: "alpha" },
        { id: OTHER, name: "Zed's Party" },
      ],
    });
  });

  it("answers 404 for a deck that isn't in the sync file", async () => {
    const r = await callGet(999);
    expect(r.status).toBe(404);
    expect((await body(r)).error).toBe("That deck isn't in the sync file (any more).");
  });
});

describe("comparing a deck: the guards (POST)", () => {
  it("refuses a request that isn't from our own pages", async () => {
    const r = await callPost(ME, { source: "deck", deckId: OTHER }, {});
    expect(r.status).toBe(403);
    expect((await body(r)).error).toBe("Not allowed.");
  });

  it("answers 404 for a deck that isn't in the sync file", async () => {
    const r = await callPost(999, { source: "deck", deckId: OTHER });
    expect(r.status).toBe(404);
    expect((await body(r)).error).toBe("That deck isn't in the sync file (any more).");
  });

  it("refuses comparing with the deck itself", async () => {
    const r = await callPost(ME, { source: "deck", deckId: ME });
    expect(r.status).toBe(400);
    expect((await body(r)).error).toBe("Pick another deck to compare with.");
  });
});

describe("comparing a deck: another deck of the sync file", () => {
  it("says what to add, what to remove and what both have", async () => {
    // A third deck with a card Scryfall knows, to have something owned on the "add" side.
    syncFile = fileWith([mineDeck(), zedDeck()], [{ name: "Rampant Growth", quantity: 1 }]);
    const r = await callPost(ME, { source: "deck", deckId: OTHER });
    expect(r.status).toBe(200);
    const b = await body(r);
    // Rampant Growth: 2 in Zed's deck, 1 owned, neither deck the other's.
    expect(short(b.add as CompareCard[])).toEqual(["2 Rampant Growth"]);
    expect((b.add as CompareCard[])[0]).toMatchObject({ type: "Sorcery", owned: 1 });
    // Cultivate is only in my deck; the maybeboards count on neither side.
    expect(short(b.remove as CompareCard[])).toEqual(["1 Cultivate"]);
    expect(short(b.both as CompareCard[])).toEqual(["1 Atraxa, Praetors' Voice", "1 Sol Ring"]);
    expect(short(b.both as CompareCard[])).not.toContain("Maybe In Mine");
    expect(b.otherName).toBe("Zed's Party");
    expect(b.otherOwner).toBeNull();
    expect(b.notFound).toEqual([]);
    expect(b.unreadable).toEqual([]);
    expect(b.mineCount).toBe(3);
    expect(b.otherCount).toBe(4);
  });
});

describe("comparing a deck: an Archidekt link", () => {
  it("reads the deck from Archidekt, without its maybeboard", async () => {
    archidekt["555"] = archidektDeck(555, "Auntie's Plague", "someone", [
      { name: "Atraxa, Praetors' Voice", quantity: 1, types: ["Creature"], commander: true },
      { name: "Sol Ring", quantity: 1, types: ["Artifact"] },
      { name: "Rampant Growth", quantity: 3, types: ["Sorcery"] },
      { name: "Maybe From Archidekt", quantity: 1, types: ["Instant"], maybe: true },
    ]);
    const r = await callPost(ME, { source: "archidekt", link: "https://archidekt.com/decks/555/aunties_plague" });
    expect(r.status).toBe(200);
    expect(fetched).toContain("https://archidekt.com/api/decks/555/");
    const b = await body(r);
    expect(b.otherName).toBe("Auntie's Plague");
    expect(b.otherOwner).toBe("someone");
    // The maybeboard's card isn't compared: only the deck's own three cards.
    expect(short(b.add as CompareCard[])).toEqual(["3 Rampant Growth"]);
    expect(short(b.remove as CompareCard[])).toEqual(["1 Cultivate"]);
    expect(short(b.both as CompareCard[])).toEqual(["1 Atraxa, Praetors' Voice", "1 Sol Ring"]);
    expect(b.notFound).toEqual([]);
    expect(b.unreadable).toEqual([]);
  });

  it("refuses a link that isn't an Archidekt deck, without asking Archidekt anything", async () => {
    const r = await callPost(ME, { source: "archidekt", link: "https://moxfield.com/decks/abc" });
    expect(r.status).toBe(400);
    expect((await body(r)).error).toBe("That isn't a link to an Archidekt deck.");
    expect(fetched.filter((u) => u.includes("archidekt.com"))).toEqual([]);
  });

  it("explains a private deck", async () => {
    archidekt["666"] = 403;
    const r = await callPost(ME, { source: "archidekt", link: "666" });
    expect(r.status).toBe(400);
    expect((await body(r)).error).toBe("Archidekt deck 666 doesn't exist or isn't public.");
  });
});

describe("comparing a deck: a pasted list", () => {
  it("compares the cards Scryfall knows, with a commander, leaving out the maybeboard", async () => {
    const text = ["Commander", "1 Atraxa, Praetors' Voice", "", "Deck", "1 Sol Ring", "3 Forest", "1 Unknown Card", "", "Maybeboard", "1 Sol Ring"].join("\n");
    const r = await callPost(ME, { source: "text", text });
    expect(r.status).toBe(200);
    const b = await body(r);
    expect(b.otherName).toBe("Pasted list");
    expect(b.otherOwner).toBeNull();
    expect(b.notFound).toEqual(["Unknown Card"]);
    expect((b.commanders as { other: string[] }).other).toEqual(["Atraxa, Praetors' Voice"]);
    // The maybeboard's extra Sol Ring doesn't count: 1 Sol Ring, 3 Forests.
    expect(short(b.add as CompareCard[])).toEqual(["3 Forest"]);
    expect(short(b.both as CompareCard[])).toEqual(["1 Atraxa, Praetors' Voice", "1 Sol Ring"]);
    expect(short(b.remove as CompareCard[])).toEqual(["1 Cultivate"]);
    expect(b.otherCount).toBe(5);
  });

  it("says when Scryfall can't be reached", async () => {
    scryFails = true;
    const r = await callPost(ME, { source: "text", text: "1 Sol Ring" });
    expect(r.status).toBe(400);
    expect((await body(r)).error).toBe("Scryfall couldn't be reached.");
  });
});

describe("comparing a deck: anything else", () => {
  it("asks for a deck to compare with", async () => {
    const r = await callPost(ME, { source: "moxfield" });
    expect(r.status).toBe(400);
    expect((await body(r)).error).toBe("Pick a deck to compare with.");
  });
});
