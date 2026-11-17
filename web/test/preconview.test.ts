import { describe, expect, it } from "vitest";
import { createDeck, NO_LOOKUPS, type NewLine } from "@/lib/deckedit";
import { makeCache } from "@/lib/deckimport";
import { deckView, syncDecks } from "@/lib/decks";
import type { CardRef, SyncFile } from "@/lib/syncfile";

// A precon's page (api/precons/view) builds the deck in a copy of the sync file and never saves it. No network here:
// createDeck, deckView and the cache only need data.

const card = (name: string, id: string, typeLine: string): CardRef => ({
  scryfallId: id,
  name,
  setCode: "mkc",
  setName: "MKC",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: 1,
  fallbackEur: null,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: 2,
});

const line = (name: string, id: string, typeLine: string, category: string, commander = false): NewLine => ({
  card: card(name, id, typeLine),
  quantity: 1,
  category,
  types: typeLine,
  cmc: 2,
  commander,
  foil: false,
  etched: false,
  gameChanger: false,
  addedInApp: false,
  maybeboard: false,
  categories: commander ? "Commander" : category,
  archidektCardId: null,
  label: null,
});

/** An empty sync file, like the app writes (test/nightly.test.ts). */
function file(): SyncFile {
  return { format: 15, app: "test", writtenAt: 0, writtenBy: "test", data: { prefs: { updatedAt: 0, values: {} } } } as unknown as SyncFile;
}

describe("a precon as a deck page (api/precons/view)", () => {
  it("builds the deck in a copy, in its own categories, and leaves the sync file alone", () => {
    const original = file();
    const clone = structuredClone(original);
    const id = createDeck(
      clone,
      "Deep Clue Sea",
      [line("Morska, Undersea Sleuth", "morska", "Creature", "Commander", true), line("Sol Ring", "ring", "Artifact", "Ramp"), line("Bennie Bracks, Zoologist", "bennie", "Creature", "Draw")],
      "Precon: Deep Clue Sea (2024)",
      "WU",
      1_750_000_000_000,
      NO_LOOKUPS,
    );
    // Nothing went into the file the page read.
    expect(original.data.decks).toBeUndefined();
    expect(syncDecks(original)).toHaveLength(0);
    // The copy has the deck, with the categories the precon's cards went into.
    expect(syncDecks(clone)).toHaveLength(1);
    const view = deckView(clone, id, null, null, null)!;
    expect(view.deck.name).toBe("Deep Clue Sea");
    expect(view.deck.colorIdentity).toBe("WU");
    expect(view.deck.cardCount).toBe(3);
    // The app's line order is by Scryfall id, so check each card's own category.
    const rows = new Map(view.rows.map((r) => [r.name, r]));
    expect([...rows.keys()].sort()).toEqual(["Bennie Bracks, Zoologist", "Morska, Undersea Sleuth", "Sol Ring"]);
    expect(rows.get("Morska, Undersea Sleuth")).toMatchObject({ category: "Commander", categories: ["Commander"], commander: true });
    expect(rows.get("Sol Ring")).toMatchObject({ category: "Ramp", categories: ["Ramp"], commander: false });
    expect(rows.get("Bennie Bracks, Zoologist")).toMatchObject({ category: "Draw", categories: ["Draw"], commander: false });
  });
});

describe("the precon lines' cache", () => {
  it("keeps an answer and asks again only when it is a day old", async () => {
    let now = 0;
    let loads = 0;
    const lines = makeCache(async (name: string) => {
      loads++;
      if (name === "broken") throw new Error("MTGJSON is down.");
      return `lines of ${name}`;
    }, () => now);
    expect(await lines("a")).toBe("lines of a");
    expect(await lines("a")).toBe("lines of a");
    expect(loads).toBe(1);
    // A failure isn't kept: the next call asks again.
    await expect(lines("broken")).rejects.toThrow("MTGJSON is down.");
    await expect(lines("broken")).rejects.toThrow("MTGJSON is down.");
    expect(loads).toBe(3);
    now = 24 * 3600_000;
    expect(await lines("a")).toBe("lines of a");
    expect(loads).toBe(3);
    now += 1;
    expect(await lines("a")).toBe("lines of a");
    expect(loads).toBe(4);
  });

  it("keeps at most 40 decks, dropping the oldest", async () => {
    let loads = 0;
    const lines = makeCache(async (name: string) => {
      loads++;
      return `lines of ${name}`;
    }, () => 0);
    for (let n = 0; n < 41; n++) await lines(`deck ${n}`);
    expect(loads).toBe(41);
    // The first one was dropped for the 41st, the others are still there.
    await lines("deck 0");
    expect(loads).toBe(42);
    await lines("deck 40");
    await lines("deck 2");
    expect(loads).toBe(42);
    // Asking for the dropped one again makes it the newest, which drops the next oldest.
    await lines("deck 1");
    expect(loads).toBe(43);
  });
});
