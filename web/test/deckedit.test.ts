import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  addCard,
  addCategory,
  AUTO,
  categories,
  COMMANDER,
  copyDeck,
  createDeck,
  deleteCategory,
  deleteDeck,
  detachDeck,
  MAYBEBOARD,
  moveLines,
  NO_LOOKUPS,
  removeLines,
  renameCategory,
  renameDeck,
  setCategoryHidden,
  setPick,
  setQuantity,
  sortLines,
  type Lookups,
  type NewLine,
} from "@/lib/deckedit";
import { parseDeckText, textCardCount, hasTags } from "@/lib/decktext";
import { syncDecks } from "@/lib/decks";
import type { DeckCard } from "@/lib/deckshared";
import { deletions, parse, prefs, type CardRef } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

const fixture = () => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));

describe("pasted decklists (the app's DeckText, Release146TextTest)", () => {
  it("reads Moxfield's export with tags and finishes", () => {
    const r = parseDeckText(
      ["1 Kenrith, the Returned King (ELD) 303", "", "1 Sol Ring (CMR) 263 #Ramp", "1 Swords to Plowshares (STA) 10 *F* #Removal #!Staple", "2 Island (UST) 213", "", "SIDEBOARD:", "1 Rhystic Study (PCY) 45"].join("\n"),
    );
    expect(r.unreadable).toEqual([]);
    expect(r.lines[0]).toMatchObject({ board: "COMMANDER", setCode: "eld", number: "303", name: "Kenrith, the Returned King" });
    const swords = r.lines.find((l) => l.name === "Swords to Plowshares")!;
    expect(swords.foil).toBe(true);
    expect(swords.tags).toEqual(["Removal", "Staple"]);
    expect(r.lines.find((l) => l.name === "Island")!.quantity).toBe(2);
    expect(r.lines[r.lines.length - 1].board).toBe("MAYBE");
    expect(hasTags(r)).toBe(true);
    expect(textCardCount(r)).toBe(5);
  });

  it("reads Archidekt's and Arena's lists", () => {
    const a = parseDeckText("1x Auntie Ool, Cursewretch (ecl) 201 [Commander{top}]\n1x Sol Ring (cmr) 263 [Ramp]\n1x Lightning Greaves (c21) 247 [Maybeboard{noDeck}{noPrice},Protection]");
    expect(a.lines[0].board).toBe("COMMANDER");
    expect(a.lines[1].tags).toEqual(["Ramp"]);
    expect(a.lines[2].board).toBe("MAYBE");
    const arena = parseDeckText("Commander\n1 Teval, the Balanced Scale (TDM) 230\n\nDeck\n1 Sol Ring\n35 Forest");
    expect(arena.lines[0].board).toBe("COMMANDER");
    expect(arena.lines[1].setCode).toBeNull();
    expect(arena.lines[2].quantity).toBe(35);
    expect(textCardCount(arena)).toBe(37);
    const plain = parseDeckText("1 Sol Ring\n1 Arcane Signet\nthis isn't a card line");
    expect(plain.lines.some((l) => l.board === "COMMANDER")).toBe(false);
    expect(plain.unreadable).toEqual(["this isn't a card line"]);
  });

  it("reads etched cards and set codes without a number", () => {
    const r = parseDeckText("1 Sol Ring (CMM) *E*\n3x Forest");
    expect(r.lines[0]).toMatchObject({ name: "Sol Ring", setCode: "cmm", number: null, foil: true, etched: true });
    expect(r.lines[1]).toMatchObject({ name: "Forest", quantity: 3 });
  });
});

const ref = (id: string, name: string): CardRef => ({
  scryfallId: id,
  name,
  setCode: "cmr",
  setName: "Commander Legends",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: null,
  fallbackEur: 1,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

const line = (id: string, name: string, category: string, o: Partial<NewLine> = {}): NewLine => ({
  card: ref(id, name),
  quantity: 1,
  category,
  types: "Artifact",
  cmc: 1,
  commander: category === COMMANDER,
  foil: false,
  etched: false,
  gameChanger: false,
  addedInApp: false,
  maybeboard: category === MAYBEBOARD,
  categories: category,
  archidektCardId: null,
  label: null,
  ...o,
});

const NOW = 1_800_000_000_000;
const look: Lookups = {
  identity: (ids) => (ids.includes("cmd") ? "BG" : ids.includes("cmd2") ? "R" : null),
  auto: new Map([["sol", { cardId: 77, category: "Ramp" }]]),
};

function withLocal() {
  const f = fixture();
  const id = createDeck(
    f,
    "Test deck",
    [line("cmd", "Auntie", COMMANDER, { types: "Legendary Creature" }), line("sol", "Sol Ring", "Mana", { categories: "Mana\nStaples" }), line("may", "Maybe card", MAYBEBOARD, { quantity: 2 })],
    "Made on the website",
    null,
    NOW,
    look,
  );
  const deck = () => syncDecks(f).find((d) => d.deck.archidektId === id)!;
  const uid = (name: string) => deck().cards.find((c) => c.card.name === name)!.lineUid;
  return { f, id, deck, uid };
}

describe("editing local decks (the app's DeckEditor)", () => {
  it("makes a local deck like createLocal", () => {
    const { id, deck } = withLocal();
    expect(id).toBe(-NOW);
    const d = deck().deck;
    expect(d).toMatchObject({ name: "Test deck", owner: "", commanders: "Auntie", commanderScryfallId: "cmd", colorIdentity: "BG", cardCount: 2, importedAt: NOW, updatedAt: NOW, archidektUpdatedAt: null });
    expect(deck().cards.every((c) => c.deckId === id && c.id === 0 && c.relationId === null && c.lineUid.length === 36)).toBe(true);
    expect(deck().cards.map((c) => c.card.scryfallId)).toEqual(["cmd", "may", "sol"]);
  });

  it("changes quantities and bumps the deck so its list wins the next sync", () => {
    const { f, id, deck, uid } = withLocal();
    setQuantity(f, id, uid("Sol Ring"), 3, NOW, look);
    expect(deck().deck.cardCount).toBe(4);
    expect(deck().deck.updatedAt).toBe(NOW + 1);
    setQuantity(f, id, uid("Sol Ring"), 0, NOW + 10, look);
    expect(deck().cards.some((c) => c.card.name === "Sol Ring")).toBe(false);
    expect(deck().deck.updatedAt).toBe(NOW + 10);
    removeLines(f, id, [uid("Maybe card")], NOW + 20, look);
    expect(deck().cards).toHaveLength(1);
  });

  it("moves cards keeping their other categories, to the maybeboard, to the commander and to auto categories", () => {
    const { f, id, deck, uid } = withLocal();
    const sol = uid("Sol Ring");
    moveLines(f, id, [sol], "Ramp stuff", NOW, look);
    const moved = deck().cards.find((c) => c.lineUid === sol)!;
    expect(moved.category).toBe("Ramp stuff");
    expect(moved.categories).toBe("Ramp stuff\nStaples");
    moveLines(f, id, [sol], MAYBEBOARD, NOW, look);
    expect(deck().cards.find((c) => c.lineUid === sol)!.maybeboard).toBe(true);
    expect(deck().deck.cardCount).toBe(1);
    moveLines(f, id, [sol], AUTO, NOW, look);
    expect(deck().cards.find((c) => c.lineUid === sol)).toMatchObject({ category: "Ramp", maybeboard: false });
    moveLines(f, id, [uid("Maybe card")], AUTO, NOW, look);
    expect(deck().cards.find((c) => c.card.name === "Maybe card")).toMatchObject({ category: "Artifact", maybeboard: false });
    moveLines(f, id, [sol], COMMANDER, NOW, look);
    expect(deck().deck.commanders).toBe("Auntie & Sol Ring");
    // Unknown identity keeps the old one, as the app does when Scryfall can't be reached.
    expect(deck().deck.colorIdentity).toBe("BG");
  });

  it("renames, deletes and adds categories like the app", () => {
    const { f, id, deck, uid } = withLocal();
    setCategoryHidden(f, id, "Mana", true, NOW);
    renameCategory(f, id, "Mana", "Fast mana", NOW, look);
    expect(deck().cards.find((c) => c.lineUid === uid("Sol Ring"))!.category).toBe("Fast mana");
    expect(JSON.parse(prefs(f).deckHidden)[String(id)]).toEqual({ "fast mana": "Fast mana" });
    expect(categories(deck().deck.categoryInfo)).toEqual([{ name: "Fast mana" }]);
    addCategory(f, id, "Draw", NOW);
    addCategory(f, id, "draw", NOW);
    addCategory(f, id, "Commander", NOW);
    expect(categories(deck().deck.categoryInfo).map((c) => c.name)).toEqual(["Fast mana", "Draw"]);
    deleteCategory(f, id, "Fast mana", null, NOW, look);
    expect(deck().cards.find((c) => c.card.name === "Sol Ring")!.category).toBe("Ramp");
    expect(JSON.parse(prefs(f).deckHidden)[String(id)]).toBeUndefined();
    expect(categories(deck().deck.categoryInfo).map((c) => c.name)).toEqual(["Draw"]);
  });

  it("puts a card in a category left out of the deck in the maybeboard", () => {
    const { f, id, deck } = withLocal();
    deck().deck.categoryInfo = JSON.stringify([{ name: "Sideboard", included: false }]);
    addCard(f, id, ref("new", "New card"), false, false, 1, "Sideboard", { typeLine: "Instant", cmc: 2 }, NOW, look);
    expect(deck().cards.find((c) => c.card.name === "New card")).toMatchObject({ maybeboard: true, types: "Instant", cmc: 2, category: "Sideboard" });
    expect(deck().deck.cardCount).toBe(2);
  });

  it("adds cards next to the same printing, else into the auto or type category", () => {
    const { f, id, deck } = withLocal();
    addCard(f, id, ref("sol", "Sol Ring"), false, false, 2, null, null, NOW, look);
    expect(deck().cards.filter((c) => c.card.name === "Sol Ring").map((c) => c.quantity)).toEqual([3]);
    addCard(f, id, ref("sol", "Sol Ring"), true, false, 1, null, { typeLine: "Artifact", cmc: 1 }, NOW, look);
    expect(deck().cards.find((c) => c.card.name === "Sol Ring" && c.foil)).toMatchObject({ category: "Ramp", archidektCardId: 77, categories: "" });
    addCard(f, id, ref("bolt", "Lightning Bolt"), false, false, 1, null, { typeLine: "Instant", cmc: 1 }, NOW, look);
    expect(deck().cards.find((c) => c.card.name === "Lightning Bolt")).toMatchObject({ category: "Instant", types: "Instant" });
    expect(deck().deck.cardCount).toBe(6);
  });

  it("refuses to edit decks from Archidekt", () => {
    const f = fixture();
    const archidekt = syncDecks(f)[0];
    expect(() => setQuantity(f, archidekt.deck.archidektId, archidekt.cards[0].lineUid, 2, NOW, NO_LOOKUPS)).toThrow(EditError);
  });

  it("deletes a deck from Archidekt like the app (here and on the phones, not on Archidekt)", () => {
    const f = fixture();
    const id = syncDecks(f)[0].deck.archidektId;
    deleteDeck(f, id, NOW);
    expect(syncDecks(f).some((x) => x.deck.archidektId === id)).toBe(false);
    expect(deletions(f).some((x) => x.uid === `deck:${id}` && x.deletedAt === NOW)).toBe(true);
  });

  it("detaches a deck from Archidekt like the app (new id, lines keep their uid, old deck deleted)", () => {
    const f = fixture();
    const src = syncDecks(f)[0];
    const oldId = src.deck.archidektId;
    const uids = src.cards.map((c) => c.lineUid);
    setCategoryHidden(f, oldId, "Ramp", true, NOW);
    const id = detachDeck(f, oldId, NOW);
    expect(id).toBe(-NOW);
    const d = syncDecks(f).find((x) => x.deck.archidektId === id)!;
    expect(d.deck.origin).toBe(`Detached from Archidekt (${src.deck.owner})`);
    expect(d.deck.archidektUpdatedAt).toBeNull();
    expect(d.cards.map((c) => c.lineUid).sort()).toEqual([...uids].sort());
    expect(syncDecks(f).some((x) => x.deck.archidektId === oldId)).toBe(false);
    expect(deletions(f).some((x) => x.uid === `deck:${oldId}` && x.deletedAt === NOW)).toBe(true);
    expect(Object.keys(JSON.parse(prefs(f).deckHidden))).toEqual([String(id)]);
  });

  it("copies, renames and deletes decks", () => {
    const { f, id, deck } = withLocal();
    const copy = copyDeck(f, id, "Copy", NOW + 5);
    const c = syncDecks(f).find((x) => x.deck.archidektId === copy)!;
    expect(c.deck).toMatchObject({ name: "Copy", origin: "Copy of Test deck", colorIdentity: "BG", cardCount: 2 });
    expect(c.cards.map((x) => x.lineUid)).not.toContain(deck().cards[0].lineUid);
    renameDeck(f, id, "  Renamed ", NOW + 6);
    expect(deck().deck.name).toBe("Renamed");
    deleteDeck(f, id, NOW + 7);
    expect(syncDecks(f).some((x) => x.deck.archidektId === id)).toBe(false);
    expect(deletions(f).some((x) => x.uid === `deck:${id}`)).toBe(true);
    expect(syncDecks(f).map((x) => x.deck.archidektId)).toEqual([...syncDecks(f).map((x) => x.deck.archidektId)].sort((a, b) => a - b));
  });

  it("remembers the printing picked for a card", () => {
    const { f, id } = withLocal();
    setPick(f, id, "Sol Ring", "abc|FOIL", NOW);
    expect(JSON.parse(prefs(f).deckPicks)[String(id)]).toEqual({ "sol ring": "abc|FOIL" });
    setPick(f, id, "Sol Ring", null, NOW);
    expect(JSON.parse(prefs(f).deckPicks)[String(id)]).toBeUndefined();
  });

  it("sorts lines like SyncDeck.sorted", () => {
    const l = (sid: string, o: Partial<DeckCard>) => ({ ...line(sid, sid, "X"), id: 0, deckId: 1, relationId: null, lineUid: sid, ...o }) as DeckCard;
    const sorted = sortLines([l("b", {}), l("a", { foil: true }), l("a", {}), l("a", { quantity: 0 })]);
    expect(sorted.map((c) => [c.card.scryfallId, c.foil, c.quantity])).toEqual([
      ["a", false, 0],
      ["a", false, 1],
      ["a", true, 1],
      ["b", false, 1],
    ]);
  });
});
