import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { parseDeck } from "@/lib/archidekt";
import { putLoadedDeck } from "@/lib/deckload";
import { addToMaybeboard, NO_LOOKUPS, removeLines, setCategoryOrder } from "@/lib/deckedit";
import { categoryOrder, layoutSections, splitColumns } from "@/lib/decklayout";
import { syncDecks } from "@/lib/decks";
import { parse, WEB_FORMAT, type CardRef, type SyncFile } from "@/lib/syncfile";

// App 1.86: a deck from Archidekt's local maybeboard, a deck's own category order, and the deck page's columns.
const NOW = 1_794_300_000_000;
const ID = 10710772;
const appFile = (): SyncFile => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const deckOf = (f: SyncFile) => syncDecks(f).find((d) => d.deck.archidektId === ID)!;
const signet: CardRef = { ...deckOf(appFile()).cards[0].card, scryfallId: "55555555-5555-5555-5555-555555555555", name: "Arcane Signet" };

describe("a deck from Archidekt's local maybeboard (MtgRepository.addToMaybeboard)", () => {
  it("adds cards added in the app, counts a second copy, and only those can be removed", () => {
    expect(WEB_FORMAT).toBe(14);
    const f = appFile();
    const before = deckOf(f).deck.updatedAt;
    const uid = addToMaybeboard(f, ID, signet, false, false, { typeLine: "Artifact", cmc: 2 }, NOW, NO_LOOKUPS);
    expect(addToMaybeboard(f, ID, signet, false, false, null, NOW + 1, NO_LOOKUPS)).toBe(uid);
    const sd = deckOf(f);
    const line = sd.cards.find((c) => c.lineUid === uid)!;
    expect(line).toMatchObject({ addedInApp: true, maybeboard: true, category: "Maybeboard", quantity: 2, types: "Artifact", cmc: 2 });
    expect(sd.deck.updatedAt).toBeGreaterThan(before);
    expect(sd.deck.cardCount).toBe(100);
    const fromArchidekt = sd.cards.find((c) => !c.addedInApp)!.lineUid;
    expect(() => removeLines(f, ID, [fromArchidekt], NOW + 2, NO_LOOKUPS)).toThrow(/Archidekt/);
    removeLines(f, ID, [uid], NOW + 3, NO_LOOKUPS);
    expect(deckOf(f).cards.some((c) => c.lineUid === uid)).toBe(false);
  });

  it("keeps them when the deck is reloaded from Archidekt", () => {
    const f = appFile();
    addToMaybeboard(f, ID, signet, false, false, null, NOW, NO_LOOKUPS);
    const raw = JSON.parse(readFileSync(path.join(__dirname, "fixtures", "archidekt-deck.json"), "utf8"));
    const sd = putLoadedDeck(f, { deck: parseDeck(raw), found: {} }, NOW + 10);
    expect(sd.cards.filter((c) => c.addedInApp).map((c) => c.card.name)).toEqual(["Arcane Signet"]);
  });
});

describe("a deck's own category order (Deck.categoryOrder)", () => {
  it("is saved without blanks or repeats, and survives a reload", () => {
    const f = appFile();
    setCategoryOrder(f, ID, ["Ramp", " Draw ", "ramp", ""], NOW);
    expect(deckOf(f).deck.categoryOrder).toBe("Ramp\nDraw");
    expect(categoryOrder(deckOf(f).deck.categoryOrder)).toEqual(["ramp", "draw"]);
    const raw = JSON.parse(readFileSync(path.join(__dirname, "fixtures", "archidekt-deck.json"), "utf8"));
    expect(putLoadedDeck(f, { deck: parseDeck(raw), found: {} }, NOW + 5).deck.categoryOrder).toBe("Ramp\nDraw");
    setCategoryOrder(f, ID, [], NOW + 6);
    expect(deckOf(f).deck.categoryOrder).toBeNull();
  });

  it("orders the sections: commander first, the named ones, then the rest alphabetically", () => {
    const line = (name: string, category: string, commander = false) => ({ name, cmc: 1, commander, category, types: "Creature" });
    const rows = [line("Vohar", "Commander", true), line("Island", "Land"), line("Sol Ring", "Ramp"), line("Brainstorm", "Draw"), line("Murder", "Removal")];
    const titles = (order: string[]) => layoutSections(rows, "CATEGORY", "NAME", false, () => "", order).map((s) => s.title);
    expect(titles([])).toEqual(["Commander", "Draw", "Land", "Ramp", "Removal"]);
    expect(titles(["removal", "ramp"])).toEqual(["Commander", "Removal", "Ramp", "Draw", "Land"]);
  });
});

describe("the deck page's columns (splitColumns)", () => {
  it("fills them in order with about the same size each", () => {
    const items = [10, 2, 6, 6, 4, 8].map((weight, i) => ({ key: String(i), weight }));
    expect(splitColumns(items, 3).map((c) => c.map((x) => x.key))).toEqual([["0", "1"], ["2", "3"], ["4", "5"]]);
    expect(splitColumns(items, 1)).toHaveLength(1);
    expect(splitColumns([], 3)).toEqual([[], [], []]);
  });
});
