import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { decksOf } from "@/lib/collview";
import { setRetired } from "@/lib/deckedit";
import { groupOf, sharedCards, syncDecks } from "@/lib/decks";
import { updatePlan } from "@/lib/deckload";
import { myDecks } from "@/lib/games";
import { parse } from "@/lib/syncfile";
import { planInputs } from "@/lib/tradebinder";

const NOW = Date.UTC(2026, 10, 8, 12);
const file = () => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const VOHAR = 10710772;

describe("retired decks (app 1.83)", () => {
  it("keeps the deck but leaves it out of the groups, updates, pickers and collection checks", () => {
    const f = file();
    expect(decksOf(f)).toHaveLength(1);
    const before = syncDecks(f)[0].deck.updatedAt ?? 0;
    setRetired(f, VOHAR, true, NOW);
    const d = syncDecks(f)[0].deck;
    expect(d.retired).toBe(true);
    expect(d.updatedAt).toBeGreaterThan(before);
    expect(groupOf(d, "joelvdc")).toBe("RETIRED");
    expect(decksOf(f)).toEqual([]);
    expect(decksOf(f, true)).toHaveLength(1);
    expect(planInputs(f).deckNeeds.size).toBe(0);
    expect(sharedCards(f)).toEqual([]);
    expect(updatePlan(f, new Map([[VOHAR, NOW]]))).toEqual([]);
    expect(myDecks(f)[0]).toMatchObject({ id: VOHAR, retired: true });
    // Bringing it back counts it again.
    setRetired(f, VOHAR, false, NOW + 1);
    expect(groupOf(syncDecks(f)[0].deck, "joelvdc")).not.toBe("RETIRED");
    expect(decksOf(f)).toHaveLength(1);
  });
});
