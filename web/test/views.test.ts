import { describe, expect, it } from "vitest";
import { cardmarketUrl, cardtraderUrl, compareRows, finishLabel, foilName } from "@/components/wishText";
import type { CardRef } from "@/lib/syncfile";
import type { WishRow } from "@/lib/view";

const card: CardRef = {
  scryfallId: "44444444-4444-4444-4444-444444444444",
  name: "Lightning Bolt",
  setCode: "2xm",
  setName: "Double Masters",
  collectorNumber: "117",
  rarity: "uncommon",
  imageUrl: null,
  cardmarketId: 470000,
  fallbackEur: null,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
};

function row(over: Omit<Partial<WishRow>, "card"> & { card?: Partial<CardRef> } = {}): WishRow {
  const { card: c, ...rest } = over;
  return {
    uid: "u",
    card: { ...card, ...c },
    foil: false,
    quantity: 1,
    anyPrinting: false,
    notes: null,
    addedAt: 0,
    orderedAt: null,
    receivedAt: null,
    receivedFrom: null,
    alertBelow: null,
    alertCurrency: null,
    unitEur: null,
    trendPct: null,
    gotSince: 0,
    alertHit: false,
    alertEur: null,
    ...rest,
  };
}

describe("wishlist texts", () => {
  it("names the finish like the app", () => {
    expect(finishLabel(row())).toBe("Normal");
    expect(finishLabel(row({ foil: true }))).toBe("Foil");
    expect(finishLabel(row({ foil: true, card: { foilType: "surgefoil" } }))).toBe("Surge foil");
    expect(finishLabel(row({ card: { foilType: "serialized", hasNonFoil: false } }))).toBe("Serialized");
    expect(finishLabel(row({ anyPrinting: true }))).toBe("Any printing");
    expect(finishLabel(row({ anyPrinting: true, foil: true }))).toBe("Any printing · Foil");
    expect(foilName("etched")).toBe("Etched foil");
  });

  it("links the printing on Cardmarket, or searches the name", () => {
    expect(cardmarketUrl(row())).toBe("https://www.cardmarket.com/en/Magic/Products?idProduct=470000");
    expect(cardmarketUrl(row({ foil: true }))).toBe("https://www.cardmarket.com/en/Magic/Products?idProduct=470000");
    expect(cardmarketUrl(row({ foil: true, card: { cardmarketFoilId: 470001 } }))).toBe(
      "https://www.cardmarket.com/en/Magic/Products?idProduct=470001",
    );
    expect(cardmarketUrl(row({ anyPrinting: true }))).toBe(
      "https://www.cardmarket.com/en/Magic/Products/Search?searchString=Lightning%20Bolt",
    );
    expect(cardmarketUrl(row({ card: { cardmarketId: null } }))).toContain("searchString=Lightning%20Bolt");
  });

  it("searches CardTrader by name", () => {
    expect(cardtraderUrl(row({ card: { name: "Atraxa, Praetors' Voice" } }))).toBe(
      "https://www.cardtrader.com/en/search?q=Atraxa%2C%20Praetors'%20Voice",
    );
  });
});

describe("sorting", () => {
  const a = row({ uid: "a", card: { name: "Alpha" }, quantity: 2, unitEur: 5, trendPct: -1 });
  const b = row({ uid: "b", card: { name: "Bravo" }, quantity: 1, unitEur: null, trendPct: 4 });
  const c = row({ uid: "c", card: { name: "charlie" }, quantity: 3, unitEur: 1, trendPct: null, alertEur: 2 });
  const names = (rows: WishRow[]) => rows.map((r) => r.uid).join("");

  it("sorts by name either way, ignoring case", () => {
    expect(names([c, b, a].sort(compareRows({ key: "name", desc: false })))).toBe("abc");
    expect(names([a, b, c].sort(compareRows({ key: "name", desc: true })))).toBe("cba");
  });

  it("keeps cards without a value last in both directions", () => {
    expect(names([b, c, a].sort(compareRows({ key: "price", desc: true })))).toBe("acb");
    expect(names([b, c, a].sort(compareRows({ key: "price", desc: false })))).toBe("cab");
    expect(names([c, b, a].sort(compareRows({ key: "trend", desc: true })))).toBe("bac");
  });

  it("sorts alerts by how close the price is", () => {
    const d = row({ uid: "d", card: { name: "Delta" }, unitEur: 10, alertEur: 5 });
    expect(names([d, b, c].sort(compareRows({ key: "alert", desc: false })))).toBe("cdb");
  });
});
