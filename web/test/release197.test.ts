import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";

// The website side of release 4 (app 1.97): deck notes, change history, roles, packages, and the routes that keep them.

// The routes ask for a login and Scryfall details; the tests give them a logged-in account and fixed card data.
vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return {
    ...actual,
    currentAccount: async () => ({ kind: "user", accountId: 1, username: "joel", admin: true, device: "test" }),
  };
});

vi.mock("@/lib/deckdetails", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/deckdetails")>();
  return { ...actual, ensureDeckDetails: async () => ({}) };
});

vi.mock("@/lib/scryfall", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/scryfall")>();
  return {
    ...actual,
    lookupCollectionFull: async (identifiers: object[]) => {
      const cards: Record<string, { name: string; typeLine: string; cmc: number }> = {
        "22222222-2222-2222-2222-222222222222": { name: "Arcane Signet", typeLine: "Artifact", cmc: 2 },
        "33333333-3333-3333-3333-333333333333": { name: "Cultivate", typeLine: "Sorcery", cmc: 3 },
      };
      return identifiers.flatMap((item) => {
        const id = (item as { id?: string }).id ?? "";
        const card = cards[id];
        if (!card) return [];
        return [
          {
            card: {
              scryfallId: id,
              name: card.name,
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
            typeLine: card.typeLine,
            layout: "normal",
            cmc: card.cmc,
            colorIdentity: [],
          },
        ];
      });
    },
  };
});

vi.mock("@/lib/deckimport", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/deckimport")>();
  const ref = {
    scryfallId: "11111111-1111-1111-1111-111111111111",
    name: "Sol Ring",
    setCode: "tst",
    setName: "Test Set",
    collectorNumber: "1",
    rarity: "uncommon",
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
  };
  return {
    ...actual,
    previewText: async () => ({
      result: { lines: [{ quantity: 2, name: "Sol Ring", setCode: null, number: null, foil: false, etched: false, tags: [], board: "MAIN" }], unreadable: [] },
      found: [{ card: ref, typeLine: "Artifact", layout: "normal", cmc: 1, colorIdentity: [] }],
      exact: 0,
      byName: 1,
      notFound: ["Bogus Card"],
      commanders: [],
    }),
  };
});

import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { parseDeck } from "@/lib/archidekt";
import { parseCardTags, encodeCardTags, normaliseTags, roleCounts, tagsOf } from "@/lib/deckroles";
import { diffLines, parseHistory, recordHistory, type HistoryLine } from "@/lib/deckhistory";
import { encodeCardNotes, nameKey, parseCardNotes, primerFromArchidekt } from "@/lib/decknotes";
import { encodePackages, MAX_PACKAGES, normalisePackage, packageUse, parsePackages, type DeckPackage } from "@/lib/deckpackages";
import { putLoadedDeck } from "@/lib/deckload";
import { deckView, syncDecks } from "@/lib/decks";
import { sharedView } from "@/lib/shares";
import { decode, encode, parse, prefs, setPrefs, type SyncFile } from "@/lib/syncfile";
import type { SyncDeck } from "@/lib/deckshared";
import { GET as deckPrefsGet, POST as deckPrefsPost } from "@/app/api/deckprefs/route";
import { POST as deckPost } from "@/app/api/decks/[id]/route";

const NOW = 1_794_300_000_000;
const ID = 10710772;
const fixture = path.join(__dirname, "fixtures");
const read = <T>(name: string): T => JSON.parse(readFileSync(path.join(fixture, name), "utf8")) as T;
const appFile = (): SyncFile => parse(readFileSync(path.join(fixture, "app-sync.json"), "utf8"));
const archidektRaw = () => read<Record<string, unknown>>("archidekt-deck.json");
const deckOf = (f: SyncFile, id = ID) => syncDecks(f).find((d) => d.deck.archidektId === id)!;

// ---- The shared cases (the Android app reads the same files) ------------------------------------------------------

describe("Archidekt descriptions as primers (DeckNotes.primerFromArchidekt)", () => {
  const cases = read<{ name: string; description: string | null; primer: string | null }[]>("decknotes-cases.json");
  for (const c of cases) {
    it(c.name, () => expect(primerFromArchidekt(c.description)).toBe(c.primer));
  }
});

describe("the shared change history cases (DeckHistory.record)", () => {
  const cases = read<{ name: string; start: string | null; steps: { now: number; source: string; before: HistoryLine[]; after: HistoryLine[] }[]; result: string | null }[]>(
    "deckhistory-cases.json",
  );
  for (const c of cases) {
    it(c.name, () => {
      let history = c.start;
      for (const step of c.steps) history = recordHistory(history, step.now, step.source, diffLines(step.before, step.after));
      expect(history).toBe(c.result);
    });
  }
});

describe("the shared role cases (DeckRoles)", () => {
  interface RoleCase {
    name: string;
    cards: {
      name: string;
      types: string;
      quantity: number;
      commander: boolean;
      maybeboard: boolean;
      categories: string[];
      ownTags: string[] | null;
      auto: string[];
    }[];
    tags: Record<string, string[]>;
    counts: { role: string; count: number; status: string }[];
  }
  const cases = read<RoleCase[]>("deckroles-cases.json");
  for (const c of cases) {
    it(c.name, () => {
      for (const card of c.cards) expect(tagsOf(card.name, card.categories, card.ownTags, card.auto)).toEqual(c.tags[card.name]);
      expect(roleCounts(c.cards)).toEqual(c.counts);
    });
  }
});

// ---- Deck notes ---------------------------------------------------------------------------------------------------

describe("card notes (Deck.cardNotes)", () => {
  it("keys by nameKey and normalises the notes", () => {
    expect(nameKey("  Sol Ring ")).toBe("sol ring");
    expect(nameKey("A // B")).toBe("a // b");
    expect(parseCardNotes(null)).toEqual({});
    expect(parseCardNotes("not json")).toEqual({});
    expect(parseCardNotes('{"Sol Ring":"  keep  ","Empty":"   ","Num":3,"Long":"' + "x".repeat(600) + '"}')).toEqual({
      "sol ring": "keep",
      long: "x".repeat(500),
    });
  });

  it("writes sorted keys without empty notes, and null when there are none", () => {
    expect(encodeCardNotes({ "Sol Ring": "  hi  ", "Arcane Signet": "   " })).toBe('{"sol ring":"hi"}');
    expect(encodeCardNotes({ B: "b", A: "a" })).toBe('{"a":"a","b":"b"}');
    expect(encodeCardNotes({})).toBeNull();
  });
});

// ---- Packages -----------------------------------------------------------------------------------------------------

describe("card packages (deckPackages)", () => {
  const pkg: DeckPackage = {
    id: "p1",
    name: " Mana rocks ",
    cards: [
      { name: "Sol Ring", scryfallId: "a", quantity: 1 },
      { name: "Arcane Signet", scryfallId: "b", quantity: 2 },
    ],
  };

  it("parses and writes the app's JSON, with the spec's limits", () => {
    expect(parsePackages(JSON.stringify([pkg]))).toEqual([{ ...pkg, name: "Mana rocks" }]);
    expect(parsePackages("not json")).toEqual([]);
    expect(parsePackages("{}")).toEqual([]);
    const long = normalisePackage({ id: "x", name: "n".repeat(80), cards: [] })!;
    expect(long.name).toHaveLength(60);
    const many = normalisePackage({ id: "x", name: "Many", cards: Array.from({ length: 201 }, (_, i) => ({ name: `Card ${i}`, scryfallId: String(i), quantity: 0 })) })!;
    expect(many.cards).toHaveLength(200);
    expect(many.cards[0].quantity).toBe(1);
    const lots = parsePackages(JSON.stringify(Array.from({ length: 101 }, (_, i) => ({ id: String(i), name: `P${i}`, cards: [] }))));
    expect(lots).toHaveLength(MAX_PACKAGES);
    expect(JSON.parse(encodePackages([pkg]))[0].name).toBe("Mana rocks");
  });

  it("says which decks hold all of it and which hold some of it", () => {
    const deck = (id: number, names: { name: string; maybeboard?: boolean }[]) =>
      ({ deck: { archidektId: id }, cards: names.map((n) => ({ card: { name: n.name }, maybeboard: n.maybeboard ?? false })) }) as unknown as SyncDeck;
    const decks = [
      deck(1, [{ name: "Sol Ring" }, { name: "Arcane Signet" }]),
      deck(2, [{ name: "sol ring" }]),
      deck(3, [{ name: "Sol Ring" }, { name: "Arcane Signet", maybeboard: true }]),
    ];
    expect(packageUse(pkg, decks)).toEqual({ full: [1], partly: [2, 3] });
    expect(packageUse({ id: "empty", name: "Empty", cards: [] }, decks)).toEqual({ full: [], partly: [] });
  });
});

describe("own tags and roles", () => {
  it("cleans tags the app's way", () => {
    expect(normaliseTags([" Ramp ", "ramp", "x".repeat(40), ""])).toEqual(["Ramp", "x".repeat(30)]);
    expect(normaliseTags(Array.from({ length: 12 }, (_, i) => `Tag ${i}`))).toHaveLength(10);
    expect(parseCardTags('{" Ramp Card ":["Ramp","ramp"],"Nope":3}')).toEqual({ "ramp card": ["Ramp"] });
    expect(parseCardTags("not json")).toEqual({});
    expect(encodeCardTags({ "B Card": ["Combo piece"], "a card": [] })).toBe('{"b card":["Combo piece"]}');
    expect(encodeCardTags({})).toBe("{}");
  });

  it("gives a card its own tags exactly, else the roles of its categories", () => {
    expect(tagsOf("Sol Ring", ["Ramp"], ["Combo piece"], ["Draw"])).toEqual(["Combo piece"]);
    expect(tagsOf("Sol Ring", ["Ramp", "Tutor"], null, ["Draw"])).toEqual(["Ramp", "Draw", "Tutor"]);
    expect(tagsOf("Sol Ring", ["Ramp"], [], ["Draw"])).toEqual([]);
  });

  it("counts lands by the word Land and skips commanders and the maybeboard", () => {
    const counts = roleCounts([
      { name: "Forest", types: "Basic Land — Forest", quantity: 3, commander: false, maybeboard: false, categories: [], ownTags: null, auto: [] },
      { name: "Landing", types: "Landing Party", quantity: 9, commander: false, maybeboard: false, categories: [], ownTags: null, auto: [] },
      { name: "Atraxa", types: "Legendary Creature", quantity: 9, commander: true, maybeboard: false, categories: ["Ramp"], ownTags: null, auto: [] },
      { name: "Maybe Rock", types: "Artifact", quantity: 9, commander: false, maybeboard: true, categories: ["Ramp"], ownTags: null, auto: [] },
    ]);
    expect(counts[0]).toEqual({ role: "Lands", count: 3, status: "low" });
    expect(counts[1]).toEqual({ role: "Ramp", count: 0, status: "low" });
  });
});

// ---- The sync format and Archidekt's description ------------------------------------------------------------------

describe("the sync file and Archidekt's description", () => {
  it("parses the description as a string only", () => {
    const raw = archidektRaw();
    raw.description = "Game plan";
    expect(parseDeck(raw).description).toBe("Game plan");
    raw.description = 3;
    expect(parseDeck(raw).description).toBeNull();
    delete raw.description;
    expect(parseDeck(raw).description).toBeNull();
  });

  it("keeps a deck's notes on a reload, fills an empty primer, and records what changed", () => {
    const f = appFile();
    const deck = deckOf(f).deck;
    deck.primer = "My primer";
    deck.cardNotes = '{"sol ring":"A note"}';
    deck.notesAt = 1234;
    const raw = archidektRaw();
    raw.description = "Archidekt's plan";
    const sd = putLoadedDeck(f, { deck: parseDeck(raw), found: {} }, NOW);
    expect(sd.deck.primer).toBe("My primer");
    expect(sd.deck.cardNotes).toBe('{"sol ring":"A note"}');
    expect(sd.deck.notesAt).toBe(1234);
    expect(parseHistory(sd.deck.history).at(0)?.sources).toEqual(["archidekt"]);

    // Never set: the description becomes the primer, dated now.
    const f2 = appFile();
    deckOf(f2).deck.primer = null;
    deckOf(f2).deck.notesAt = null;
    const sd2 = putLoadedDeck(f2, { deck: parseDeck(raw), found: {} }, NOW);
    expect(sd2.deck.primer).toBe("Archidekt's plan");
    expect(sd2.deck.notesAt).toBe(NOW);

    // Cleared by the user (""): the description never overwrites it.
    const f3 = appFile();
    deckOf(f3).deck.primer = "";
    deckOf(f3).deck.notesAt = 99;
    const sd3 = putLoadedDeck(f3, { deck: parseDeck(raw), found: {} }, NOW);
    expect(sd3.deck.primer).toBe("");
    expect(sd3.deck.notesAt).toBe(99);
  });

  it("records the old and new main lines, but nothing for a first import", () => {
    const f = appFile();
    deckOf(f).deck.history = null;
    const raw = archidektRaw();
    const sd = putLoadedDeck(f, { deck: parseDeck(raw), found: {} }, NOW);
    const entry = parseHistory(sd.deck.history)[0];
    expect(entry.sources).toEqual(["archidekt"]);
    expect(entry.removed).toEqual({ "Sol Ring": 1 });
    expect(Object.keys(entry.added)).toContain("Arcane Signet");

    const first = appFile();
    first.data.decks = [];
    const fresh = putLoadedDeck(first, { deck: parseDeck(raw), found: {} }, NOW);
    expect(fresh.deck.history ?? null).toBeNull();
  });
});

describe("the deck page's roles, notes and primer", () => {
  it("shows a row's own tags and note, and the deck's primer, history and role counts", () => {
    const f = appFile();
    const sd = deckOf(f);
    sd.deck.primer = "The plan";
    sd.deck.cardNotes = '{"sol ring":"Tutor target"}';
    sd.deck.history = JSON.stringify([{ day: "2025-11-16", sources: ["app"], added: { "Sol Ring": 1 }, removed: {} }]);
    const view = deckView(f, ID, null, null, null, { cardTags: { "sol ring": ["Combo piece"] } })!;
    const row = view.rows.find((r) => r.name === "Sol Ring")!;
    expect(row.roleTags).toEqual(["Combo piece"]);
    expect(row.note).toBe("Tutor target");
    expect(view.primer).toBe("The plan");
    expect(view.history).toEqual([{ day: "2025-11-16", sources: ["app"], added: { "Sol Ring": 1 }, removed: {} }]);
    expect(view.roles).toContainEqual({ role: "Combo piece", count: 1, status: "none" });

    // Without own tags the automatic categories decide; existing callers pass no extras at all.
    const auto = deckView(f, ID, null, null, null, { auto: { "Sol Ring": ["Ramp"] } })!;
    expect(auto.rows.find((r) => r.name === "Sol Ring")!.roleTags).toEqual(["Ramp"]);
    expect(auto.roles.find((r) => r.role === "Ramp")?.count).toBe(1);
    expect(deckView(f, ID, null, null, null)).not.toBeNull();
  });
});

// ---- The deck route: notes, history and packages ------------------------------------------------------------------

type Account = { kind: "user"; accountId: number; username: string; admin: boolean; device: string };
const ACCOUNT: Account = { kind: "user", accountId: 1, username: "joel", admin: true, device: "test" };
let store: NonNullable<ReturnType<typeof accountStore>>;

const install = async (f: SyncFile) => {
  await store.putSync(ACCOUNT.accountId, encode(f), null, NOW);
};
const savedFile = async () => decode((await store.getSync(ACCOUNT.accountId))!.bytes);
const savedDeck = async (id: number) => deckOf(await savedFile(), id);

const callDeck = (id: number, body: unknown) =>
  deckPost(
    new Request(`https://mtgtrader.vercel.app/api/decks/${id}`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-requested-with": "mtgtrader" },
      body: JSON.stringify(body),
    }),
    { params: Promise.resolve({ id: String(id) }) },
  );

const callPrefs = (body: unknown) =>
  deckPrefsPost(
    new Request("https://mtgtrader.vercel.app/api/deckprefs", {
      method: "POST",
      headers: { "content-type": "application/json", "x-requested-with": "mtgtrader" },
      body: JSON.stringify(body),
    }),
  );

beforeEach(async () => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
  store = accountStore()!;
  await store.create("joel", "x", true, NOW);
});

describe("deck notes through the deck route", () => {
  it("sets and clears a primer on any deck, also one from Archidekt", async () => {
    await install(appFile());
    const answer = await callDeck(ID, { action: "set-primer", primer: "  My plan  \n\n" });
    expect(answer.status).toBe(200);
    const body = (await answer.json()) as { before: SyncDeck };
    expect(body.before.deck.archidektId).toBe(ID);
    const deck = (await savedDeck(ID)).deck;
    expect(deck.primer).toBe("  My plan");
    expect(deck.notesAt).toBeGreaterThan(0);
    expect(deck.updatedAt).toBeGreaterThanOrEqual(NOW);

    expect((await callDeck(ID, { action: "set-primer", primer: "x".repeat(25_000) })).status).toBe(200);
    expect((await savedDeck(ID)).deck.primer).toHaveLength(20_000);
    expect((await callDeck(ID, { action: "set-primer", primer: "" })).status).toBe(200);
    expect((await savedDeck(ID)).deck.primer).toBe("");
  });

  it("sets and removes one card's note, sorted and trimmed", async () => {
    await install(appFile());
    expect((await callDeck(ID, { action: "set-card-note", name: " Sol Ring ", note: "  Tutor target  " })).status).toBe(200);
    expect((await savedDeck(ID)).deck.cardNotes).toBe('{"sol ring":"Tutor target"}');
    expect((await callDeck(ID, { action: "set-card-note", name: "Arcane Signet", note: "Ramp" })).status).toBe(200);
    expect((await savedDeck(ID)).deck.cardNotes).toBe('{"arcane signet":"Ramp","sol ring":"Tutor target"}');
    expect((await callDeck(ID, { action: "set-card-note", name: "Arcane Signet", note: "" })).status).toBe(200);
    expect((await savedDeck(ID)).deck.cardNotes).toBe('{"sol ring":"Tutor target"}');
    expect((await callDeck(ID, { action: "set-card-note", name: "Sol Ring", note: "   " })).status).toBe(200);
    expect((await savedDeck(ID)).deck.cardNotes).toBeNull();
  });
});

describe("deck history through the deck route", () => {
  it("records a website change once, for any card edit", async () => {
    const f = appFile();
    const sd = deckOf(f);
    sd.deck.archidektId = -7;
    sd.deck.history = null;
    await install(f);
    expect((await callDeck(-7, { action: "set-quantity", uid: sd.cards[0].lineUid, quantity: 3 })).status).toBe(200);
    const entry = parseHistory((await savedDeck(-7)).deck.history)[0];
    expect(entry.day).toBe(new Date(Date.now()).toISOString().slice(0, 10));
    expect(entry.sources).toEqual(["website"]);
    expect(entry.added).toEqual({ "Sol Ring": 2 });
    expect(entry.removed).toEqual({});
  });

  it("records restoring an undo too", async () => {
    const f = appFile();
    const sd = deckOf(f);
    sd.deck.archidektId = -8;
    sd.deck.history = null;
    await install(f);
    const first = (await (await callDeck(-8, { action: "set-quantity", uid: sd.cards[0].lineUid, quantity: 2 })).json()) as { before: SyncDeck };
    expect((await callDeck(-8, { action: "restore", snapshot: { ...first.before, cards: [] } })).status).toBe(200);
    const entry = parseHistory((await savedDeck(-8)).deck.history)[0];
    expect(entry.sources).toEqual(["website"]);
    expect(entry.removed).toEqual({ "Sol Ring": 2 });
  });
});

describe("packages through the deck route", () => {
  const rocks: DeckPackage = {
    id: "p1",
    name: "Mana rocks",
    cards: [
      { name: "Sol Ring", scryfallId: "11111111-1111-1111-1111-111111111111", quantity: 1 },
      { name: "Arcane Signet", scryfallId: "22222222-2222-2222-2222-222222222222", quantity: 2 },
    ],
  };

  const localWithPackages = async (id: number) => {
    const f = appFile();
    const sd = deckOf(f);
    sd.deck.archidektId = id;
    sd.deck.history = null;
    setPrefs(f, { deckPackages: encodePackages([rocks]) }, NOW);
    await install(f);
  };

  it("adds the package cards the deck doesn't have, and reports the ones it does", async () => {
    await localWithPackages(-9);
    const answer = await callDeck(-9, { action: "add-package", packageId: "p1" });
    const body = (await answer.json()) as { added: number; skipped: string[]; before: SyncDeck };
    expect(body.added).toBe(2);
    expect(body.skipped).toEqual(["Sol Ring"]);
    const deck = await savedDeck(-9);
    const signet = deck.cards.find((c) => c.card.name === "Arcane Signet")!;
    expect(signet).toMatchObject({ quantity: 2, maybeboard: false, commander: false, types: "Artifact", category: "Artifact" });
    expect(parseHistory(deck.deck.history)[0].added).toEqual({ "Arcane Signet": 2 });
  });

  it("removes the package's main non-commander lines in one undo", async () => {
    await localWithPackages(-10);
    expect((await callDeck(-10, { action: "remove-package", packageId: "p1" })).status).toBe(200);
    const deck = await savedDeck(-10);
    expect(deck.cards.some((c) => c.card.name === "Sol Ring")).toBe(false);
    const body = (await (await callDeck(-10, { action: "remove-package", packageId: "p1" })).json()) as { removed: number };
    expect(body.removed).toBe(0);
    expect(parseHistory(deck.deck.history)[0].removed).toEqual({ "Sol Ring": 1 });
  });

  it("is for local decks only", async () => {
    await install(appFile());
    const answer = await callDeck(ID, { action: "add-package", packageId: "p1" });
    expect(answer.status).toBe(400);
  });
});

describe("the tags and packages route", () => {
  it("reads the tags, packages and where each package is used", async () => {
    const f = appFile();
    setPrefs(f, { cardTags: JSON.stringify({ "sol ring": ["Ramp"] }), deckPackages: encodePackages([{ id: "p1", name: "Rocks", cards: [{ name: "Sol Ring", scryfallId: "a", quantity: 1 }] }]) }, NOW);
    await install(f);
    const body = (await (await deckPrefsGet()).json()) as { cardTags: Record<string, string[]>; packages: DeckPackage[]; use: Record<string, { full: number[]; partly: number[] }> };
    expect(body.cardTags).toEqual({ "sol ring": ["Ramp"] });
    expect(body.packages).toHaveLength(1);
    expect(body.use.p1).toEqual({ full: [ID], partly: [] });
  });

  it("sets one card's tags and bulk adds or removes one", async () => {
    await install(appFile());
    const first = (await (await callPrefs({ action: "set-tags", name: "Sol Ring", tags: [" Ramp ", "ramp", "Combo piece"] })).json()) as { cardTags: Record<string, string[]> };
    expect(first.cardTags).toEqual({ "sol ring": ["Ramp", "Combo piece"] });
    expect(prefs(await savedFile()).cardTags).toBe('{"sol ring":["Ramp","Combo piece"]}');

    await callPrefs({ action: "bulk-tags", names: ["Sol Ring", "Arcane Signet"], add: "Mana rock" });
    await callPrefs({ action: "bulk-tags", names: ["Sol Ring"], remove: "ramp" });
    const tags = parseCardTags(prefs(await savedFile()).cardTags);
    expect(tags).toEqual({ "arcane signet": ["Mana rock"], "sol ring": ["Combo piece", "Mana rock"] });
  });

  it("saves, renames and deletes a package", async () => {
    await install(appFile());
    const saved = (await (
      await callPrefs({
        action: "save-package",
        name: "  Mana rocks ",
        cards: [
          { name: "Sol Ring", scryfallId: "a", quantity: 1 },
          { name: "Arcane Signet", scryfallId: "b", quantity: 5000 },
        ],
      })
    ).json()) as { packages: DeckPackage[] };
    expect(saved.packages).toHaveLength(1);
    const pkg = saved.packages[0];
    expect(pkg.id).toMatch(/^[0-9a-f-]{36}$/);
    expect(pkg.name).toBe("Mana rocks");
    expect(pkg.cards[1].quantity).toBe(999);

    expect((await callPrefs({ action: "rename-package", id: pkg.id, name: "Rocks" })).status).toBe(200);
    expect(((await (await callPrefs({ action: "rename-package", id: pkg.id, name: "Rocks" })).json()) as { packages: DeckPackage[] }).packages[0].name).toBe("Rocks");
    const after = (await (await callPrefs({ action: "delete-package", id: pkg.id })).json()) as { packages: DeckPackage[] };
    expect(after.packages).toEqual([]);

    // Saving with an existing id replaces it in place.
    const made = (await (await callPrefs({ action: "save-package", name: "One", cards: [] })).json()) as { packages: DeckPackage[] };
    const id = made.packages[0].id;
    const replaced = (await (await callPrefs({ action: "save-package", id, name: "Two", cards: [] })).json()) as { packages: DeckPackage[] };
    expect(replaced.packages).toEqual([{ id, name: "Two", cards: [] }]);
  });

  it("makes a package from pasted text and reports the cards Scryfall didn't know", async () => {
    await install(appFile());
    const body = (await (await callPrefs({ action: "package-from-text", name: "From text", text: "2 Sol Ring" })).json()) as {
      package: DeckPackage;
      notFound: string[];
    };
    expect(body.package.cards).toEqual([{ name: "Sol Ring", scryfallId: "11111111-1111-1111-1111-111111111111", quantity: 2 }]);
    expect(body.notFound).toEqual(["Bogus Card"]);
  });
});

describe("a shared deck", () => {
  it("shows the primer and the card notes, and still never purchase prices or collection notes", () => {
    const f = appFile();
    const item = (f.data.collection as { item: Record<string, unknown> }[])[0].item;
    Object.assign(item, { purchasePrice: 12.5, notes: "bought from Anna" });
    const sd = deckOf(f);
    sd.deck.primer = "Game plan\n\n# Ramp\n- Sol Ring early";
    sd.deck.cardNotes = '{"sol ring":"Tutor target"}';

    const v = sharedView(f, { kind: "deck", target: String(ID) }, "joel", null, null, null)!;
    expect(v.kind).toBe("deck");
    if (v.kind !== "deck") return;
    expect(v.deck.primer).toBe("Game plan\n\n# Ramp\n- Sol Ring early");
    const line = v.deck.sections.flatMap((s) => s.lines).find((l) => l.name === "Sol Ring")!;
    expect(line.note).toBe("Tutor target");

    const text = JSON.stringify(v);
    expect(text).not.toContain("12.5");
    expect(text).not.toContain("bought from Anna");
    expect(text).not.toContain("purchase");
  });
});
