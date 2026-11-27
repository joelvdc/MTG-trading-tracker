import { describe, expect, it } from "vitest";
import { cardMenuEntries, cardMenuItems, cardmarketUrlOfRow, cardtraderUrlOfRow, otherFinishOf, scryfallUrlFor, type MenuRow } from "@/components/CardMenu";
import { cardmarketUrlByName, cardtraderUrlByName, edhrecCardSlug, edhrecUrlFor } from "@/components/cardLinks";

// A deck card's right-click menu (website, 2026-11-22): its items, and where its links go.

/** A CardRef with the finishes and Cardmarket numbers a line's printing can have. */
function card(name: string, opts: { id?: number | null; foil?: boolean; nonfoil?: boolean; foilId?: number | null } = {}) {
  return {
    scryfallId: "sf-" + name,
    name,
    setCode: "c21",
    setName: "Commander 2021",
    collectorNumber: "1",
    rarity: "rare",
    imageUrl: null,
    cardmarketId: opts.id === undefined ? 42 : opts.id,
    fallbackEur: 1,
    fallbackEurFoil: 2,
    hasNonFoil: opts.nonfoil ?? true,
    hasFoil: opts.foil ?? true,
    foilType: null,
    hasEtched: false,
    flavorName: null,
    cardmarketFoilId: opts.foilId ?? null,
  };
}

const sol: MenuRow = { name: "Sol Ring", lineUid: "u1", card: card("Sol Ring"), finish: "NONFOIL", quantity: 1 };

/** The labels in order, to say which items a line gets. */
const labels = (row: MenuRow, editable: boolean, openable = true) => cardMenuEntries(row, editable, openable).map((e) => e.label);

describe("the links", () => {
  it("search Scryfall for exactly the card's name", () => {
    expect(scryfallUrlFor("Sol Ring")).toBe("https://scryfall.com/search?q=" + encodeURIComponent('!"Sol Ring"'));
    // A double-faced card keeps its whole name, quotes and all.
    expect(scryfallUrlFor('Fire // Ice')).toBe("https://scryfall.com/search?q=" + encodeURIComponent('!"Fire // Ice"'));
  });

  it("use the card's EDHREC slug", () => {
    expect(edhrecCardSlug("Sol Ring")).toBe("sol-ring");
    expect(edhrecCardSlug("K'rrik, Son of Yawgmoth")).toBe("krrik-son-of-yawgmoth");
    expect(edhrecCardSlug("Fire // Ice")).toBe("fire");
    expect(edhrecUrlFor("Sol Ring")).toBe("https://edhrec.com/cards/sol-ring");
  });

  it("open the printing's Cardmarket page, or search its name without one", () => {
    expect(cardmarketUrlOfRow(sol)).toBe("https://www.cardmarket.com/en/Magic/Products?idProduct=42");
    // A foil line prefers the printing's foil product.
    expect(cardmarketUrlOfRow({ ...sol, finish: "FOIL", card: card("Sol Ring", { foilId: 99 }) })).toBe("https://www.cardmarket.com/en/Magic/Products?idProduct=99");
    // No printing (a shared page's line) and a printing Cardmarket doesn't know both search the name.
    expect(cardmarketUrlOfRow({ name: "Sol Ring" })).toBe(cardmarketUrlByName("Sol Ring"));
    expect(cardmarketUrlOfRow({ ...sol, card: card("Sol Ring", { id: null }) })).toBe("https://www.cardmarket.com/en/Magic/Products/Search?searchString=Sol%20Ring");
  });

  it("search CardTrader for the card", () => {
    expect(cardtraderUrlOfRow(sol)).toBe("https://www.cardtrader.com/en/search?q=Sol%20Ring");
    expect(cardtraderUrlOfRow({ name: "Sol Ring" })).toBe(cardtraderUrlByName("Sol Ring"));
  });
});

describe("the finish a line can switch to", () => {
  it("is the other of foil and nonfoil where the printing comes in both", () => {
    expect(otherFinishOf(sol)).toBe("FOIL");
    expect(otherFinishOf({ ...sol, finish: "FOIL" })).toBe("NONFOIL");
  });

  it("is nothing where the printing has one finish only", () => {
    expect(otherFinishOf({ ...sol, card: card("Sol Ring", { foil: false }) })).toBeNull();
    expect(otherFinishOf({ ...sol, finish: "FOIL", card: card("Sol Ring", { nonfoil: false }) })).toBeNull();
    expect(otherFinishOf({ name: "Sol Ring" })).toBeNull();
  });
});

describe("the menu's items", () => {
  it("offer a card of a deck that can't be edited here its window, its name and its pages", () => {
    expect(labels(sol, false)).toEqual(["Open details", "Copy card name", "Scryfall", "EDHREC", "Cardmarket", "CardTrader"]);
    expect(cardMenuItems(sol, false)).toEqual(["open", "copy", "scryfall", "edhrec", "cardmarket", "cardtrader"]);
  });

  it("only copy and link on a page without a card window (a shared deck)", () => {
    expect(labels({ name: "Sol Ring", quantity: 1, finish: "NONFOIL" }, false, false)).toEqual([
      "Copy card name",
      "Scryfall",
      "EDHREC",
      "Cardmarket",
      "CardTrader",
    ]);
  });

  it("add the deck's own changes where the deck can be edited here", () => {
    expect(cardMenuItems(sol, true)).toEqual([
      "open",
      "copy",
      "scryfall",
      "edhrec",
      "cardmarket",
      "cardtrader",
      "more",
      "fewer",
      "printing",
      "finish",
      "commander",
      "maybe",
      "category",
      "select",
      "remove",
    ]);
    // The last four labels are the deck's own; the separator comes before "+1 copy".
    expect(labels(sol, true).slice(6)).toEqual([
      "+1 copy",
      "−1 copy",
      "Change printing",
      "Switch to foil",
      "Make commander",
      "Move to maybeboard",
      "Move to category ›",
      "Select",
      "Remove",
    ]);
  });

  it("make a commander a change back to the deck, and so for a maybeboard card", () => {
    expect(labels({ ...sol, commander: true }, true)).toContain("Back to the deck");
    expect(labels({ ...sol, maybeboard: true }, true)).toContain("Back to the deck");
    expect(labels({ ...sol, commander: true }, true)).not.toContain("Make commander");
    expect(labels({ ...sol, maybeboard: true }, true)).not.toContain("Move to maybeboard");
    // A normal line has both, once.
    expect(labels(sol, true)).toContain("Make commander");
    expect(labels(sol, true)).toContain("Move to maybeboard");
    expect(labels(sol, true)).not.toContain("Back to the deck");
  });

  it("say which way the finish goes, and leave it out when the printing has one finish", () => {
    expect(labels({ ...sol, finish: "FOIL" }, true)).toContain("Switch to nonfoil");
    expect(labels({ ...sol, card: card("Sol Ring", { foil: false }) }, true)).not.toContain("Switch to foil");
  });
});
