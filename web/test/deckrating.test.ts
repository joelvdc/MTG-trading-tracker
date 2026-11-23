import { describe, expect, it } from "vitest";
import { parse, type SyncFile } from "@/lib/syncfile";
import { putRating, RATED_SINCE, ratingStale } from "@/lib/deckrating";
import { decodeSalt, type Deck, type DeckCard, type SaltCard, type SyncDeck } from "@/lib/deckshared";
import type { StatsCard } from "@/lib/deckstats";
import { rateDeck, RATING_VERSION, ratingListKey } from "@/lib/localratings";

const NOW = 1_793_968_645_412;

function card(name: string, over: Partial<StatsCard> = {}): StatsCard {
  return {
    name,
    scryfallId: "00000000-0000-0000-0000-000000000000",
    quantity: 1,
    cmc: 0,
    types: "",
    commander: false,
    typeLine: null,
    manaCost: null,
    producedMana: null,
    oracleText: null,
    tokens: null,
    ...over,
  };
}

const stats = [card("Cmdr", { commander: true, cmc: 3, types: "Creature" }), card("Bear", { cmc: 2, types: "Creature" })];

function rating(): SaltCard {
  return rateDeck(stats, new Map(), [], null);
}

/** A sync file with one local deck and one from Archidekt, each with its cards. */
function syncFile(): SyncFile {
  const line = (deckId: number, name: string, commander = false): DeckCard => ({
    id: 0,
    deckId,
    card: {
      scryfallId: "00000000-0000-0000-0000-000000000000",
      name,
      setCode: "X",
      setName: "X",
      collectorNumber: "1",
      rarity: "rare",
      imageUrl: null,
      cardmarketId: null,
      fallbackEur: null,
      fallbackEurFoil: null,
      hasNonFoil: true,
      hasFoil: false,
      foilType: null,
      hasEtched: false,
      flavorName: null,
      cardmarketFoilId: null,
    },
    quantity: 1,
    category: commander ? "Commander" : "Creatures",
    types: "Creature",
    cmc: 2,
    commander,
    foil: false,
    etched: false,
    gameChanger: false,
    addedInApp: false,
    maybeboard: false,
    categories: commander ? "Commander" : "Creatures",
    archidektCardId: null,
    relationId: null,
    label: null,
    lineUid: `uid-${deckId}-${name}`,
  });
  const deck = (id: number): Deck => ({
    archidektId: id,
    name: id < 0 ? "My deck" : "Someone's deck",
    owner: id < 0 ? "" : "someone",
    commanders: "Cmdr",
    commanderScryfallId: null,
    artUrl: null,
    colorIdentity: "UB",
    cardCount: 2,
    importedAt: NOW,
    saltId: null,
    powerLevel: 6.5,
    bracketRealistic: 3,
    bracketBaseline: 2,
    saltPercent: 12.5,
    archetype: "Combo",
    scoredAt: null,
    scoreError: null,
    archidektUpdatedAt: null,
    updatedAt: NOW,
    edhPowerLevel: null,
    edhPowerAt: null,
    edhPowerError: null,
    saltCard: null,
    svPowerLevel: null,
    scrollVault: null,
    svAt: null,
    svError: null,
    folderId: null,
    folderName: null,
    visibility: null,
    categoryInfo: null,
    origin: null,
  });
  const both = (id: number): SyncDeck => ({ deck: deck(id), cards: [line(id, "Cmdr", true), line(id, "Bear")] });
  return parse(JSON.stringify({ format: 16, app: "1.119", writtenAt: NOW, writtenBy: "test", data: { decks: [both(-1), both(123)] } }));
}

describe("ratingStale", () => {
  const SALT = JSON.stringify({ gameChangers: ["Rhystic Study"], combos: [], twoCardCombos: [], earlyCombos: [], extraTurns: [], massLandDenial: [] });

  it("is never stale for a deck from Archidekt when its bracket is Commander Salt's", () => {
    expect(ratingStale(false, null, stats, false)).toBe(false);
    expect(ratingStale(false, JSON.stringify(rating()), stats, false)).toBe(false);
    expect(ratingStale(false, SALT, stats, false)).toBe(false);
  });

  it("is stale for a local deck without a rating", () => {
    expect(ratingStale(true, null, stats, false)).toBe(true);
    expect(ratingStale(true, "not json", stats, false)).toBe(true);
  });

  it("is stale for the website's rating of another list or another version", () => {
    const r = rating();
    expect(ratingStale(true, JSON.stringify(r), stats, false)).toBe(false);
    expect(ratingStale(true, JSON.stringify(r), stats, true)).toBe(false);
    expect(ratingStale(true, JSON.stringify(r), [...stats, card("More")], false)).toBe(true);
    expect(ratingStale(true, JSON.stringify({ ...r, listKey: "00000000", ratingVersion: RATING_VERSION }), stats, false)).toBe(true);
    expect(ratingStale(true, JSON.stringify({ ...r, listKey: ratingListKey(stats), ratingVersion: RATING_VERSION + 1 }), stats, false)).toBe(true);
  });

  it("is never stale for Commander Salt's data on a local deck (a deck made local from Archidekt)", () => {
    expect(ratingStale(true, SALT, stats, false)).toBe(false);
    expect(ratingStale(true, SALT, stats, true)).toBe(false);
  });

  it("asks for the estimate on an Archidekt deck with Commander Salt's data when the bracket comes from the app", () => {
    expect(ratingStale(false, SALT, stats, true)).toBe(true);
    expect(ratingStale(false, null, stats, true)).toBe(true);
    // The estimate is there already, for this list: nothing to do.
    expect(ratingStale(false, JSON.stringify(rating()), stats, true)).toBe(false);
    expect(ratingStale(false, JSON.stringify(rating()), [...stats, card("More")], true)).toBe(true);
  });

  it("makes the website's ratings from before RATED_SINCE again once", () => {
    const r = JSON.stringify(rating());
    expect(ratingStale(true, r, stats, false, RATED_SINCE - 1)).toBe(true);
    expect(ratingStale(true, r, stats, false, null)).toBe(true);
    expect(ratingStale(true, r, stats, false, RATED_SINCE)).toBe(false);
    // Commander Salt's data is never touched by it.
    expect(ratingStale(true, SALT, stats, false, 0)).toBe(false);
  });

  it("has no answer without the deck's cards", () => {
    expect(ratingStale(true, null, [], false)).toBe(false);
    expect(ratingStale(false, SALT, [], true)).toBe(false);
  });
});

describe("putRating", () => {
  it("puts the rating on a local deck, leaving Commander Salt's other scores alone", () => {
    const f = syncFile();
    const r = rating();
    expect(putRating(f, -1, r, NOW + 100)).toBe(true);
    const d = (f.data.decks as unknown as SyncDeck[]).find((x) => x.deck.archidektId === -1)!.deck;
    expect(d.saltCard).toBe(JSON.stringify(r));
    expect(d.bracketBaseline).toBe(r.bracket);
    expect(d.bracketRealistic).toBeNull();
    expect(d.scoredAt).toBe(NOW + 100);
    expect(d.scoreError).toBeNull();
    expect(d.updatedAt).toBe(NOW + 100);
    expect(d.powerLevel).toBe(6.5);
    expect(d.saltPercent).toBe(12.5);
    expect(d.archetype).toBe("Combo");
    const back = decodeSalt(d.saltCard)!;
    expect(back.source).toBe("app");
    expect(back.version).toBe(4);
  });

  it("puts it on a deck from Archidekt too", () => {
    const f = syncFile();
    const r = rating();
    expect(putRating(f, 123, r, NOW + 100)).toBe(true);
    const d = (f.data.decks as unknown as SyncDeck[]).find((x) => x.deck.archidektId === 123)!.deck;
    expect(d.saltCard).toBe(JSON.stringify(r));
    expect(d.bracketBaseline).toBe(r.bracket);
    expect(d.bracketRealistic).toBeNull();
    expect(d.powerLevel).toBe(6.5);
  });

  it("refuses a deck that isn't there", () => {
    const f = syncFile();
    const before = JSON.stringify(f.data.decks);
    expect(putRating(f, 999, rating(), NOW)).toBe(false);
    expect(JSON.stringify(f.data.decks)).toBe(before);
  });
});

