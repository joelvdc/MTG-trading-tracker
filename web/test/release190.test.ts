import { describe, expect, it } from "vitest";
import { needsLookup, toInfo, unknownInfo } from "@/lib/carddetails";
import { DEFAULT_COLUMNS, readColumns } from "@/lib/collcolumns";
import { comparator, deckUse, EMPTY_FILTER, faceKind, filterCount, matchesFilter, useOf, type Filter } from "@/lib/colquery";
import type { CollDeck, CollRow } from "@/lib/collview";
import type { CardRef } from "@/lib/syncfile";

// App 1.90: the Faces filter (MDFC…), the decks' use of each card, and the table's chosen columns.

const card = (name: string): CardRef => ({
  scryfallId: "44444444-4444-4444-4444-444444444444",
  name,
  setCode: "znr",
  setName: "Zendikar Rising",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: 1,
  fallbackEur: 1,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: false,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

function row(uid: string, name: string, layout: string | undefined, quantity = 1): CollRow {
  return {
    uid,
    card: card(name),
    finish: "NONFOIL",
    condition: "NM",
    language: "EN",
    quantity,
    addedAt: 0,
    binder: null,
    notes: null,
    purchasePrice: null,
    paidTyped: null,
    paidCurrency: null,
    signed: false,
    altered: false,
    unitEur: 1,
    cmEur: 1,
    approx: false,
    condNote: null,
    changeEur: null,
    changePct: null,
    srcEur: { cardmarket: 1, tcgplayer: null, cardkingdom: null, ckPays: null },
    info: { c: "B", i: "B", t: "Sorcery // Land", v: 3, r: 100, d: "2020-09-25", g: false, l: layout },
  };
}

const agadeem = row("a", "Agadeem's Awakening // Agadeem, the Undercrypt", "modal_dfc", 2);
const agadeem2 = row("a2", "Agadeem's Awakening // Agadeem, the Undercrypt", "modal_dfc", 1);
const delver = row("d", "Delver of Secrets // Insectile Aberration", "transform");
const fire = row("f", "Fire // Ice", "split");
const bolt = row("b", "Lightning Bolt", "normal");
const old = row("o", "Valki, God of Lies // Tibalt, Cosmic Impostor", undefined);
const rows = [agadeem, agadeem2, delver, fire, bolt, old];

const decks: CollDeck[] = [
  { name: "Zombies", owner: "me", cards: { "agadeem's awakening": 1, "lightning bolt": 1 } },
  { name: "Atraxa", owner: "me", cards: { "agadeem's awakening": 1 } },
  { name: "Burn", owner: "me", cards: { "lightning bolt": 4 } },
];

describe("the Faces filter", () => {
  it("reads Scryfall's layout as the app does", () => {
    expect(faceKind("modal_dfc")).toBe("MDFC");
    expect(faceKind("transform")).toBe("TRANSFORM");
    expect(faceKind("meld")).toBe("TRANSFORM");
    expect(faceKind("adventure")).toBe("SPLIT");
    expect(faceKind("normal")).toBeNull();
    expect(faceKind(undefined)).toBeNull();
  });

  it("shows only the kinds picked, together with In a deck", () => {
    const shown = (f: Partial<Filter>, names = new Set<string>()) => rows.filter((r) => matchesFilter({ ...EMPTY_FILTER, ...f }, r, names)).map((r) => r.uid);
    expect(shown({ faces: ["MDFC"] })).toEqual(["a", "a2"]);
    expect(shown({ faces: ["TRANSFORM", "SPLIT"] })).toEqual(["d", "f"]);
    expect(shown({ faces: ["MDFC"], decks: "IN" }, new Set(["lightning bolt"]))).toEqual([]);
    expect(shown({ faces: ["MDFC"], decks: "IN" }, new Set(["agadeem's awakening"]))).toEqual(["a", "a2"]);
    expect(filterCount({ ...EMPTY_FILTER, faces: ["MDFC"] })).toBe(1);
  });

  it("reads a filter saved before 1.90 (no faces)", () => {
    const saved = { ...EMPTY_FILTER } as Partial<Filter>;
    delete saved.faces;
    const f = saved as Filter;
    expect(filterCount(f)).toBe(0);
    expect(matchesFilter(f, bolt, new Set())).toBe(true);
  });

  it("keeps the layout with the card details, and looks up details saved without it", () => {
    expect(toInfo({ id: "x", layout: "modal_dfc" }, 1).l).toBe("modal_dfc");
    expect(toInfo({ id: "x" }, 1).l).toBe("");
    expect(unknownInfo(1).l).toBe("");
    expect(needsLookup(undefined)).toBe(true);
    const before190 = { ...toInfo({ id: "x" }, 1) };
    delete before190.l;
    expect(needsLookup(before190)).toBe(true);
    expect(needsLookup(toInfo({ id: "x", layout: "normal" }, 1))).toBe(false);
  });
});

describe("the decks' use of each card", () => {
  const use = deckUse(rows, decks);

  it("counts decks, copies in them and spare copies over every printing", () => {
    expect(useOf(use, agadeem)).toEqual({ decks: ["Atraxa", "Zombies"], copies: 2, owned: 3 });
    expect(useOf(use, bolt)).toEqual({ decks: ["Burn", "Zombies"], copies: 5, owned: 1 });
    expect(useOf(use, fire)).toEqual({ decks: [], copies: 0, owned: 1 });
  });

  it("sorts by decks and by spare copies", () => {
    const by = (field: "DECKS" | "SPARE", reversed = false) => [agadeem, delver, bolt].sort(comparator([{ field, reversed }], use)).map((r) => r.uid);
    // Two decks each for Agadeem and Bolt: the name breaks the tie.
    expect(by("DECKS")).toEqual(["a", "b", "d"]);
    // Spare: Agadeem 1, Delver 1, Bolt 1 − 5 = −4.
    expect(by("SPARE")).toEqual(["a", "d", "b"]);
    expect(by("SPARE", true)[0]).toBe("b");
  });
});

describe("the table's columns", () => {
  it("cleans a saved choice", () => {
    expect(readColumns(undefined)).toEqual(DEFAULT_COLUMNS);
    expect(readColumns(["decks", "nonsense", "decks", "price"])).toEqual(["decks", "price"]);
    expect(readColumns([])).toEqual([]);
    expect(readColumns(["nonsense"])).toEqual(DEFAULT_COLUMNS);
  });
});
