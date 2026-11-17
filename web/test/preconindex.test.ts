import { describe, expect, it } from "vitest";
import { artUrl, browseList, entryOf, identityOf, mergeIndex, setNames, type PreconEntry, type PreconIndex } from "@/lib/preconindex";
import type { PreconInfo } from "@/lib/precons";

// The precons page's tiles: an entry per deck, read from MTGJSON's deck files once and kept day by day.

const info = (fileName: string, name: string, releaseDate: string | null, code = "MKC"): PreconInfo => ({ code, fileName, name, releaseDate });

const card = (name: string, count: number, id: string | null) => ({
  name,
  count,
  isFoil: false,
  setCode: "MKC",
  number: "1",
  identifiers: id ? { scryfallId: id } : {},
  types: ["Creature"],
  supertypes: ["Legendary"],
});

const deck = {
  data: {
    name: "Deep Clue Sea",
    code: "MKC",
    releaseDate: "2024-02-09",
    type: "Commander Deck",
    commander: [
      card("Morska, Undersea Sleuth", 1, "morska"),
      card("Morska, Undersea Sleuth", 1, "morska-alt"),
      card("Kraum, Ludevic's Opus", 1, "kraum"),
      card("A Commander Scryfall Doesn't Know", 1, null),
    ],
    mainBoard: [
      card("Sol Ring", 1, "ring"),
      card("Island", 7, "island"),
      card("Magnifying Glass", 2, "glass"),
      card("Another Unknown Card", 3, null),
    ],
    sideBoard: [card("Extras", 1, "extras")],
  },
};

const entry = (fileName: string, name: string, releaseDate: string | null, cards = 0): PreconEntry => ({
  fileName,
  name,
  code: "MKC",
  setName: null,
  releaseDate,
  commanders: [],
  colorIdentity: "",
  cards,
});

describe("setNames", () => {
  it("maps set codes, upper case, to their names", () => {
    const names = setNames({
      data: [
        { code: "MKC", name: "Murders at Karlov Manor Commander", type: "commander" },
        { code: "frc", name: "Multiverse Reforged" },
        { code: "BAD" },
        { name: "No code" },
      ],
    });
    expect(names.get("MKC")).toBe("Murders at Karlov Manor Commander");
    expect(names.get("FRC")).toBe("Multiverse Reforged");
    expect(names.size).toBe(2);
  });

  it("takes anything that isn't a set list as no names", () => {
    expect(setNames(null).size).toBe(0);
    expect(setNames({}).size).toBe(0);
  });
});

describe("entryOf", () => {
  const e = entryOf(info("DeepClueSea_MKC", "Deep Clue Sea", "2024-02-09"), deck, "Murders at Karlov Manor Commander");

  it("takes the deck's commanders, each name once, in file order", () => {
    expect(e.commanders).toEqual([
      { name: "Morska, Undersea Sleuth", scryfallId: "morska" },
      { name: "Kraum, Ludevic's Opus", scryfallId: "kraum" },
    ]);
  });

  it("counts every card in the commander and main board, basics too", () => {
    // Both Morska printings, Kraum, Sol Ring, 7 Islands and 2 Magnifying Glasses; the sideboard and the cards Scryfall
    // has no id for don't count.
    expect(e.cards).toBe(13);
  });

  it("keeps the list's name, code, date and the set's name", () => {
    expect(e).toMatchObject({ fileName: "DeepClueSea_MKC", name: "Deep Clue Sea", code: "MKC", setName: "Murders at Karlov Manor Commander", releaseDate: "2024-02-09" });
    expect(entryOf(info("X", "X", null), deck, null).setName).toBeNull();
  });
});

describe("identityOf", () => {
  it("puts the letters in WUBRG order", () => {
    expect(identityOf(["G", "W", "U"])).toBe("WUG");
    expect(identityOf(["B", "R", "G", "W", "U"])).toBe("WUBRG");
  });

  it("is empty for no colours and ignores anything that isn't one", () => {
    expect(identityOf([])).toBe("");
    expect(identityOf(["X", "C", ""])).toBe("");
    expect(identityOf(["gw"])).toBe("WG");
  });
});

describe("mergeIndex", () => {
  const list = [info("A_MKC", "Deck A", "2024-02-09"), info("B_MKC", "Deck B", "2025-04-11"), info("C_MKC", "Deck C", "2023-08-04")];
  const old = [entry("A_MKC", "Deck A", "2024-02-09", 100), entry("B_MKC", "Deck B (old)", "2025-04-11", 50), entry("Z_MKC", "Gone", "2020-01-01", 10)];

  it("keeps, replaces, drops and sorts newest first", () => {
    const merged = mergeIndex(old, list, [entry("B_MKC", "Deck B", "2025-04-11", 120), entry("C_MKC", "Deck C", "2023-08-04", 80)]);
    expect(merged.map((e) => e.fileName)).toEqual(["B_MKC", "A_MKC", "C_MKC"]);
    expect(merged.map((e) => e.cards)).toEqual([120, 100, 80]);
    expect(merged.some((e) => e.fileName === "Z_MKC")).toBe(false);
  });

  it("leaves out a fresh entry the list doesn't have", () => {
    expect(mergeIndex([], list, [entry("Y_MKC", "Deck Y", "2026-01-01")])).toEqual([]);
  });
});

describe("browseList", () => {
  const list = [info("A_MKC", "Deck A", "2024-02-09"), info("D_FRC", "Deck D", "2026-10-02")];
  const index: PreconIndex = { madeAt: 0, listCount: 1, decks: [entry("A_MKC", "Deck A", "2024-02-09", 100)] };

  it("fills a deck the index doesn't have yet as a bare entry", () => {
    const decks = browseList(index, list);
    expect(decks.map((e) => e.fileName)).toEqual(["D_FRC", "A_MKC"]);
    expect(decks[0]).toMatchObject({ name: "Deck D", code: "MKC", setName: null, releaseDate: "2026-10-02", commanders: [], colorIdentity: "", cards: 0 });
  });

  it("works before there is an index at all", () => {
    expect(browseList(null, list).map((e) => e.fileName)).toEqual(["D_FRC", "A_MKC"]);
    expect(browseList(null, []).length).toBe(0);
  });
});

describe("artUrl", () => {
  it("uses the id's first two letters as folders on Scryfall's image server", () => {
    expect(artUrl("01234567-89ab-cdef-0123-456789abcdef")).toBe("https://cards.scryfall.io/art_crop/front/0/1/01234567-89ab-cdef-0123-456789abcdef.jpg");
  });
});
