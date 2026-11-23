import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { archidektId, archidektUser, identityOf, parseDeck, parseDeckList, parseTime } from "@/lib/archidekt";
import { putLoadedDeck, updatePlan, type ReadDeck } from "@/lib/deckload";
import { syncDecks } from "@/lib/decks";
import { decodeSalt } from "@/lib/deckshared";
import { putRating } from "@/lib/deckrating";
import { categories } from "@/lib/deckedit";
import { rateDeck } from "@/lib/localratings";
import { parse, prefs, setPrefs, type SyncFile } from "@/lib/syncfile";

const NOW = 1_793_968_645_412;
const raw = () => JSON.parse(readFileSync(path.join(__dirname, "fixtures", "archidekt-deck.json"), "utf8"));
const appFile = (): SyncFile => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));

describe("Archidekt (the app's ArchidektApi and DeckLinks)", () => {
  it("finds deck ids and usernames in links", () => {
    expect(archidektId("Check out my deck: https://archidekt.com/decks/20263351/auntie_plague")).toBe(20263351);
    expect(archidektId("https://archidekt.com/api/decks/42/")).toBe(42);
    expect(archidektId(" 123 ")).toBe(123);
    expect(archidektId("joelvdc")).toBeNull();
    expect(archidektUser("https://archidekt.com/u/joelvdc")).toBe("joelvdc");
    expect(archidektUser("@joelvdc")).toBe("joelvdc");
    expect(archidektUser("https://archidekt.com/decks/1")).toBeNull();
    expect(archidektUser("two words")).toBeNull();
  });

  it("reads a deck: commander, maybeboard, finishes, categories and folder", () => {
    const d = parseDeck(raw());
    expect(d.name).toBe("Vohar Danse");
    expect(d.owner).toBe("joelvdc");
    expect(d.cards.filter((c) => c.commander).map((c) => c.name)).toEqual(["Vohar, Vodalian Desecrator"]);
    expect(d.cards).toHaveLength(7);
    expect(d.maybeboard).toHaveLength(2);
    expect(identityOf(d)).toBe("UB");
    expect(d.folderId).toBe(518583);
    expect(d.visibility).toBe("public");
    expect(d.updatedAt).toBe(parseTime(raw().updatedAt));
    expect(d.categories.find((c) => c.name === "Maybeboard")?.includedInDeck).toBe(false);
    const foil = parseDeck({ ...raw(), cards: [{ ...raw().cards[1], modifier: "Etched" }] }).cards[0];
    expect([foil.foil, foil.etched]).toEqual([true, true]);
  });

  it("reads a user's deck list, leaving private decks out", () => {
    const page = parseDeckList({
      next: "http://archidekt.com/api/decks/v3/?page=2",
      results: [
        { id: 1, name: "A", size: 100, deckFormat: 3, colors: { W: 2, U: 0, G: 5 }, parentFolderName: "Ready", updatedAt: "2026-10-01T18:20:24.479123Z" },
        { id: 2, name: "Secret", private: true },
      ],
    });
    expect(page.decks).toEqual([{ id: 1, name: "A", size: 100, artUrl: null, colors: "WG", commanderFormat: true, folder: "Ready", updatedAt: Date.parse("2026-10-01T18:20:24.479Z") }]);
    expect(page.next).toContain("page=2");
  });
});

describe("loading decks from Archidekt (DeckRepository.load)", () => {
  const read = (): ReadDeck => ({ deck: parseDeck(raw()), found: {} });

  it("replaces the deck's list, keeping the scores an older app kept", () => {
    const f = appFile();
    setPrefs(f, { powerSource: "EDH_POWER_LEVEL" }, NOW);
    const before = syncDecks(f).find((d) => d.deck.archidektId === 10710772)!.deck;
    const sd = putLoadedDeck(f, read(), NOW);
    expect(sd.deck.powerLevel).toBe(before.powerLevel);
    expect(sd.deck.importedAt).toBe(NOW);
    expect(sd.deck.updatedAt).toBeGreaterThan(before.updatedAt);
    expect(sd.deck.commanders).toBe("Vohar, Vodalian Desecrator");
    expect(sd.deck.cardCount).toBe(7);
    expect(sd.cards.filter((c) => c.maybeboard)).toHaveLength(2);
    expect(sd.cards.every((c) => c.lineUid && c.card.imageUrl?.startsWith("https://cards.scryfall.io/"))).toBe(true);
    expect(categories(sd.deck.categoryInfo).find((c) => c.name === "Maybeboard")?.included).toBe(false);
    expect(syncDecks(f).filter((d) => d.deck.archidektId === 10710772)).toHaveLength(1);
    // The settings the website doesn't know stay as they are.
    expect(prefs(f).powerSource).toBe("EDH_POWER_LEVEL");
  });

  it("keeps the website's rating when the deck is read again", () => {
    const f = appFile();
    putLoadedDeck(f, read(), NOW);
    const find = () => syncDecks(f).find((x) => x.deck.archidektId === 10710772)!.deck;
    const estimate = rateDeck([], new Map(), [], null);
    expect(putRating(f, 10710772, estimate, NOW + 1)).toBe(true);
    putLoadedDeck(f, read(), NOW + 5);
    const d = find();
    expect(decodeSalt(d.saltCard)!.source).toBe("app");
    expect(d.bracketBaseline).toBe(estimate.bracket);
    expect(d.bracketRealistic).toBeNull();
  });

  it("plans Update all decks: changed, not listed, or up to date", () => {
    const f = appFile();
    putLoadedDeck(f, read(), NOW);
    const at = parseTime(raw().updatedAt);
    expect(updatePlan(f, new Map([[10710772, at]]))).toEqual([]);
    expect(updatePlan(f, new Map([[10710772, at! + 1]]))[0].need).toBe("changed");
    expect(updatePlan(f, new Map())[0].need).toBe("check");
  });
});
