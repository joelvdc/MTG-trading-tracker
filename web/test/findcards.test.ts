import { readFileSync } from "node:fs";
import crypto from "node:crypto";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { applyCollectionEdit } from "@/lib/collection";
import { findBinders, findCards, findDecks, findFromDeck, findFromText, findInFile, findInputs, findStacks, ticksOf, type FindInput } from "@/lib/findcards";
import { binders, collection, parse, setPrefs, type CardRef, type SyncFile } from "@/lib/syncfile";
import type { SyncDeck } from "@/lib/deckshared";

// The same cases as the app's Release1104FindTest.kt.
const cases = JSON.parse(readFileSync(path.join(__dirname, "fixtures", "findcards-cases.json"), "utf8")) as {
  name: string;
  input: FindInput;
  expected: {
    groups: { binderKey: string; binderName: string; lines: (Record<string, unknown> & { lineKey: string })[] }[];
    onlyInDecks: { name: string; copies: number; decks: string[] }[];
    notOwned: { name: string; copies: number }[];
    total: { wanted: number; found: number; inDecks: number; notOwned: number };
  };
}[];

const lines = (g: { lines: readonly unknown[] }) => g.lines.map((l) => ({ ...(l as object) }));

describe("finding cards (FindCards)", () => {
  it("has enough cases", () => expect(cases.length).toBeGreaterThanOrEqual(8));

  for (const c of cases)
    it(c.name, () => {
      const got = findCards(c.input);
      expect(got.groups.map((g) => ({ binderKey: g.binderKey, binderName: g.binderName, lines: lines(g) }))).toEqual(c.expected.groups);
      expect(got.onlyInDecks).toEqual(c.expected.onlyInDecks);
      expect(got.notOwned).toEqual(c.expected.notOwned);
      expect(got.total).toEqual(c.expected.total);
      expect([...ticksOf(got)].sort()).toEqual(got.groups.flatMap((g) => g.lines.map((l) => l.lineKey)).sort());
    });
});

const card = (name: string): CardRef => ({
  // A Scryfall id made from the name (the collection only takes ids that look like Scryfall's).
  scryfallId: crypto
    .createHash("md5")
    .update(name)
    .digest("hex")
    .replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, "$1-$2-$3-$4-$5"),
  name,
  setCode: "abc",
  setName: "Set",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: null,
  fallbackEur: 1,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: false,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

const appFile = () => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const syncDecksOf = (f: SyncFile) => (f.data.decks as unknown as SyncDeck[] | undefined) ?? [];

/** A deck line of the example file's deck (it says how the app writes one), with the fields a test changes. */
const deckLine = (f: SyncFile, o: { card: CardRef; quantity: number; maybeboard?: boolean; commander?: boolean }) =>
  ({ ...syncDecksOf(f)[0].cards[0], ...o }) as unknown as SyncDeck["cards"][number];

/** The example file with a few more piles, and its binders made at this file's time stamps. */
function file(): SyncFile {
  const f = appFile();
  // The example's binder ("Main", holding a Sol Ring) is made again: one made long ago would have been worked around
  // by now, and these tests want it in the bar.
  const sol = collection(f)[0].item.card;
  const old = binders(f)[0];
  applyCollectionEdit(f, { type: "remove", uid: collection(f).find((s) => s.binder === old.uid)!.item.uid! }, 1000);
  applyCollectionEdit(f, { type: "deleteBinder", uid: old.uid!, deleteCards: false }, 1000);
  const trade = applyCollectionEdit(f, { type: "addBinder", name: "Trade binder" }, 2000).uid!;
  applyCollectionEdit(f, { type: "add", card: sol, quantity: 1, binder: trade }, 2000);
  // Two more Sol Rings in Unsorted, a Forest there, and a Cultivate in a binder of its own.
  applyCollectionEdit(f, { type: "add", card: sol, quantity: 2 }, 2000);
  applyCollectionEdit(f, { type: "add", card: card("Forest"), quantity: 4 }, 2000);
  const box = applyCollectionEdit(f, { type: "addBinder", name: "Green box" }, 2000).uid!;
  applyCollectionEdit(f, { type: "add", card: card("Cultivate"), quantity: 1, binder: box }, 2000);
  return f;
}

describe("finding cards in the sync file", () => {
  it("lists the binders in the bar's order, with the trade binder marked", () => {
    const f = file();
    const bar = findBinders(f);
    expect(bar[0]).toEqual({ key: "unsorted", name: "Unsorted", isDeckBinder: false, isTradeBinder: false });
    // The usual order: Unsorted, then the binders by name (the website's binder bar; the app puts the trade binder first).
    expect(bar.map((b) => b.name)).toEqual(["Unsorted", "Green box", "Trade binder"]);
    expect(bar.find((b) => b.name === "Trade binder")?.isTradeBinder).toBe(true);
    const spares = applyCollectionEdit(f, { type: "addBinder", name: "Spares" }, 3000).uid!;
    expect(findBinders(f).find((b) => b.key === `b:${spares}`)).toMatchObject({ isTradeBinder: false, isDeckBinder: false });
    // The saved order comes first.
    setPrefs(f, { binderOrder: `unsorted,b:${spares},b:${binders(f).find((b) => b.name === "Green box")!.uid}` }, 4000);
    expect(findBinders(f).map((b) => b.name).slice(0, 3)).toEqual(["Unsorted", "Spares", "Green box"]);
  });

  it("marks a binder named after a deck (any case) as a deck binder", () => {
    const f = file();
    const deckBinder = applyCollectionEdit(f, { type: "addBinder", name: "gruul times" }, 3000).uid!;
    const decks = syncDecksOf(f);
    decks.push({ deck: { ...decks[0].deck, name: "Gruul Times", archidektId: 42, retired: false }, cards: [] });
    expect(findBinders(f).find((b) => b.key === `b:${deckBinder}`)).toMatchObject({ isDeckBinder: true, isTradeBinder: false });
  });

  it("finds a pasted list binder by binder, leaving the basic lands out", () => {
    const f = file();
    const r = findInFile(f, "2 Sol Ring\n2 Cultivate\n4 Forest\n1 Mana Drain");
    // Unsorted first; the trade binder holds Cultivate (one more copy is missing).
    expect(r.groups.map((g) => [g.binderName, g.lines.map((l) => [l.name, l.copies])])).toEqual([
      ["Unsorted", [["Sol Ring", 2]]],
      ["Green box", [["Cultivate", 1]]],
    ]);
    expect(r.onlyInDecks).toEqual([]);
    expect(r.notOwned).toEqual([{ name: "Cultivate", copies: 1 }, { name: "Mana Drain", copies: 1 }]);
    // The 4 Forest stay out of the totals; the rest of the list is looked for.
    expect(r.total).toEqual({ wanted: 5, found: 3, inDecks: 0, notOwned: 2 });
    expect(findInFile(f, "4 Forest", null, false).total.found).toBe(4);
    // The trade binder's copies come after a plain binder's, and a deck binder's last.
    const box = applyCollectionEdit(f, { type: "addBinder", name: "Box" }, 3000).uid!;
    applyCollectionEdit(f, { type: "add", card: card("Cultivate"), quantity: 2, binder: box }, 3000);
    applyCollectionEdit(f, { type: "add", card: card("Cultivate"), quantity: 1, binder: binders(f).find((b) => b.name === "Trade binder")!.uid! }, 3000);
    const trade = binders(f).find((b) => b.name === "Trade binder")!.uid!;
    const sorted = findCards({
      wanted: [{ name: "Cultivate", qty: 3 }],
      stacks: findStacks(f),
      binders: [
        { key: "unsorted", name: "Unsorted", isDeckBinder: false, isTradeBinder: false },
        { key: `b:${box}`, name: "Gruul Times", isDeckBinder: true, isTradeBinder: false },
        { key: `b:${trade}`, name: "Trade binder", isDeckBinder: false, isTradeBinder: true },
      ],
      decks: [],
      fromDeck: null,
      skipBasics: true,
    });
    // So the deck's binder gets a copy before Green box (the plain binder in the bar's order); the groups are shown
    // in the bar's order, the deck's binder first.
    expect(sorted.groups.map((g) => [g.binderName, g.lines[0].copies])).toEqual([
      ["Gruul Times", 1],
      ["Trade binder", 1],
      ["Green box", 1],
    ]);
  });

  it("puts copies another deck uses under only in decks, and the deck itself doesn't count", () => {
    const f = file();
    // The example file's deck (Vohar Danse) needs one of the three Sol Rings.
    const decks = f.data.decks as unknown as SyncDeck[];
    // Two copies owned: both are spare.
    expect(findInFile(f, "2 Sol Ring").onlyInDecks).toEqual([]);
    // Four wanted: the two spare ones come first, then Vohar Danse's copy, then what isn't owned; every copy of
    // the four is accounted for.
    const four = findInFile(f, "4 Sol Ring");
    expect(four.total.found).toBe(2);
    expect(four.onlyInDecks).toEqual([{ name: "Sol Ring", copies: 1, decks: ["Vohar Danse"] }]);
    expect(four.notOwned).toEqual([{ name: "Sol Ring", copies: 1 }]);
    expect(four.total.found + four.total.inDecks + four.total.notOwned).toBe(4);
    // The same list from Vohar Danse itself: its own copy doesn't count against the list, so nothing is "in another
    // deck" (the copy stays in Unsorted either way; only the list from another deck sees it as the deck's).
    const own = findInFile(f, "4 Sol Ring", decks[0].deck.archidektId);
    expect(own.onlyInDecks).toEqual([]);
    expect(own.total.found + own.total.inDecks + own.total.notOwned).toBe(4);
  });

  it("reads a deck's own cards, commanders and main deck but not the maybeboard", () => {
    const f = file();
    const decks = syncDecksOf(f);
    // The deck's own Sol Ring line (a deck named Vohar Danse uses one), then a second copy, a maybeboard card and
    // its commander.
    const sol = decks[0].cards[0];
    decks[0] = {
      ...decks[0],
      cards: [sol, { ...sol, quantity: 1 }, deckLine(f, { card: card("Cultivate"), quantity: 1, maybeboard: true }), deckLine(f, { card: card("Vohar"), quantity: 1, commander: true })],
    };
    expect(findFromDeck(f, decks[0].deck.archidektId)?.map((w) => [w.name, w.qty])).toEqual([
      ["Sol Ring", 2],
      ["Vohar", 1],
    ]);
    expect(findFromDeck(f, 1)).toBeNull();
    // The others' use counts in decks; the deck's own doesn't come with the list.
    expect(findDecks(f).find((d) => d.name === "Vohar Danse")?.cards).toEqual({ "sol ring": 2, vohar: 1 });
    // And finding for the deck itself: two Sol Rings and the commander, nothing from the maybeboard.
    const r = findInFile(f, findFromDeck(f, decks[0].deck.archidektId)!, decks[0].deck.archidektId);
    expect(r.total.wanted).toBe(3);
    expect(r.groups.map((g) => [g.binderName, g.lines.map((l) => [l.name, l.copies])])).toEqual([
      ["Unsorted", [["Sol Ring", 2]]],
    ]);
    expect(r.notOwned).toEqual([{ name: "Vohar", copies: 1 }]);
  });

  it("parses a pasted list with the website's deck text parser", () => {
    expect(findFromText("Commander\n1 Vohar\n\nDeck\n2 Sol Ring\nSideboard\n1 Cultivate").map((w) => [w.name, w.qty])).toEqual([
      ["Vohar", 1],
      ["Sol Ring", 2],
    ]);
    const f = file();
    const input = findInputs(f, "1 Sol Ring");
    expect(input.wanted).toEqual([{ name: "Sol Ring", qty: 1, scryfallId: null }]);
    expect(input.stacks.some((s) => s.name === "Cultivate" && s.binderName === "Green box")).toBe(true);
    const tradeUid = binders(f).find((b) => b.name === "Trade binder")!.uid!;
    expect(findStacks(f).find((s) => s.name === "Sol Ring" && s.binderKey === `b:${tradeUid}`)).toBeDefined();
  });
});
