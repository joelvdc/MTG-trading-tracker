import { describe, expect, it } from "vitest";
import type { StatsCard } from "@/lib/deckstats";
import { decodeSalt } from "@/lib/deckshared";
import { featureLabel, rateDeck, RATING_VERSION, ratingListKey, saltIntensity, saltSumOf, type CardFact } from "@/lib/localratings";
import type { SpellbookCombo } from "@/lib/spellbook";

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

const spell = (name: string, cmc: number, typeLine: string, over: Partial<StatsCard> = {}) => card(name, { cmc, typeLine, ...over });

const facts = (m: Record<string, Partial<CardFact>>): Map<string, CardFact> => {
  const out = new Map<string, CardFact>();
  for (const [k, v] of Object.entries(m)) out.set(k, { tags: [], gameChanger: false, salt: null, ...v });
  return out;
};

const plains = (n: number) => card("Plains", { quantity: n, typeLine: "Basic Land — Plains", producedMana: "W" });

function combo(id: string, cards: string[], features: string[], over: Partial<SpellbookCombo> = {}): SpellbookCombo {
  return { id, cards, features, notablePrerequisites: "", easyPrerequisites: "", bracketTag: null, salt: null, ...over };
}

describe("the rating has no axes", () => {
  it("leaves them empty", () => {
    expect(rateDeck([card("Bear"), plains(1)], facts({}), [], null).axes).toEqual({});
  });
});

describe("the bracket", () => {
  const land = () => plains(1);
  const deck = (names: { name: string; cmc: number; typeLine: string }[], f: Map<string, CardFact>) => [...names.map((n) => spell(n.name, n.cmc, n.typeLine)), land()];

  it("2 with nothing", () => {
    const r = rateDeck(deck([{ name: "Bear", cmc: 2, typeLine: "Creature" }], facts({})), facts({}), [], null);
    expect(r.bracket).toBe(2);
    expect(r.bracketWhy).toEqual(["No Game Changers or two-card combos"]);
  });

  it("3 with 1 to 3 Game Changers, 4 with 4", () => {
    const gc = (n: number) => facts(Object.fromEntries(Array.from({ length: n }, (_, i) => [`GC${i}`, { gameChanger: true }])));
    const cards = deck(Array.from({ length: 4 }, (_, i) => ({ name: `GC${i}`, cmc: 2, typeLine: "Creature" })), facts({}));
    const r3 = rateDeck(cards, gc(1), [], null);
    expect(r3.bracket).toBe(3);
    expect(r3.bracketWhy).toEqual(["1 Game Changer: GC0"]);
    expect(rateDeck(cards, gc(3), [], null).bracketWhy).toEqual(["3 Game Changers: GC0, GC1, GC2"]);
    const r4 = rateDeck(cards, gc(4), [], null);
    expect(r4.bracket).toBe(4);
    expect(r4.bracketWhy).toEqual(["4 Game Changers: GC0, GC1, GC2, GC3"]);
  });

  it("a winning two-card combo of mana value 8 is bracket 4, a bigger one bracket 3", () => {
    const cards = [...deck([{ name: "A", cmc: 4, typeLine: "Creature" }, { name: "B", cmc: 4, typeLine: "Creature" }], facts({})), spell("C", 4, "Creature")];
    const early = rateDeck(cards, facts({}), [combo("1", ["A", "B"], ["Win the game"])], null);
    expect(early.bracket).toBe(4);
    expect(early.bracketWhy).toEqual(["A + B: wins, mana value 8"]);
    expect(early.twoCardCombos).toEqual([["A", "B"]]);
    expect(early.earlyCombos).toEqual([["A", "B"]]);
    expect(early.effects.combos).toBe(1);

    // A three-card combo doesn't count as a two-card one, whatever its mana value.
    const late = rateDeck(cards, facts({}), [combo("1", ["A", "B", "C"], ["Win the game"])], null);
    expect(late.bracket).toBe(2);
    expect(late.earlyCombos).toEqual([]);
    expect(late.effects).toEqual({});
    expect(late.combos[0].manaValue).toBe(12);
  });

  it("a two-card combo of mana value 9 doesn't win by itself: bracket 3, \"doesn't win\"", () => {
    const cards = deck([{ name: "A", cmc: 5, typeLine: "Creature" }, { name: "B", cmc: 4, typeLine: "Creature" }], facts({}));
    const r = rateDeck(cards, facts({}), [combo("1", ["A", "B"], ["Infinite mana"])], null);
    expect(r.bracket).toBe(3);
    expect(r.bracketWhy).toEqual(["A + B: wins, mana value 9"]);
    expect(r.earlyCombos).toEqual([]);
    expect(r.effects.combos).toBe(1);
  });

  it("a non-winning two-card combo is bracket 3 with \"doesn't win\"", () => {
    const cards = deck([{ name: "A", cmc: 2, typeLine: "Artifact" }, { name: "B", cmc: 2, typeLine: "Creature" }], facts({}));
    const r = rateDeck(cards, facts({}), [combo("u", ["A", "B"], ["Infinite untap"])], null);
    expect(r.bracket).toBe(3);
    expect(r.bracketWhy).toEqual(["A + B: doesn't win"]);
    expect(r.combos[0].wins).toBe(false);
    expect(r.combos[0].manaValue).toBe(4);
    expect(r.combos[0].results).toEqual(["infinite-untap"]);
    expect(r.earlyCombos).toEqual([]);
  });

  it("infinite mana, creature tokens and lifegain count as winning", () => {
    for (const feature of ["Infinite colorless mana", "Infinite creature tokens", "Infinite lifegain", "Infinite green mana", "Infinite life loss", "Infinite turns"]) {
      const r = rateDeck(deck([{ name: "A", cmc: 1, typeLine: "Artifact" }, { name: "B", cmc: 1, typeLine: "Artifact" }], facts({})), facts({}), [combo("w", ["A", "B"], [feature])], null);
      expect(r.bracket, feature).toBe(4);
      expect(r.combos[0].wins, feature).toBe(true);
    }
    const notWinning = rateDeck(deck([{ name: "A", cmc: 1, typeLine: "Artifact" }, { name: "B", cmc: 1, typeLine: "Artifact" }], facts({})), facts({}), [combo("x", ["A", "B"], ["Infinite untap"])], null);
    expect(notWinning.combos[0].wins).toBe(false);
  });

  it("a hyphenated word before the result still wins: infinite self-mill", () => {
    const cards = deck([{ name: "A", cmc: 1, typeLine: "Artifact" }, { name: "B", cmc: 1, typeLine: "Artifact" }], facts({}));
    const r = rateDeck(cards, facts({}), [combo("h", ["A", "B"], ["Infinite self-mill"])], null);
    expect(r.combos[0].wins).toBe(true);
    expect(r.bracket).toBe(4);
    expect(r.earlyCombos).toEqual([["A", "B"]]);
    expect(r.bracketWhy).toEqual(["A + B: wins, mana value 2"]);
  });

  it("two-card combos: the first three lines, then how many more", () => {
    const names = ["A", "B", "C", "D", "E"];
    const cards = deck(
      names.map((n) => ({ name: n, cmc: 2, typeLine: "Artifact" })),
      facts({}),
    );
    const combos = [combo("1", ["A", "B"], ["Infinite untap"]), combo("2", ["A", "C"], ["Infinite untap"]), combo("3", ["D", "E"], ["Infinite untap"]), combo("4", ["C", "D"], ["Infinite untap"]), combo("5", ["B", "E"], ["Infinite untap"])];
    const r = rateDeck(cards, facts({}), combos, null);
    expect(r.bracket).toBe(3);
    expect(r.bracketWhy).toEqual(["A + B: doesn't win", "A + C: doesn't win", "D + E: doesn't win", "and 2 more two-card combos"]);
  });

  it("mass land denial and 3 extra turns are bracket 4", () => {
    const cards = deck([{ name: "Armageddon", cmc: 4, typeLine: "Sorcery" }], facts({}));
    const r = rateDeck(cards, facts({ Armageddon: { tags: ["mass-land-denial"] } }), [], null);
    expect(r.bracket).toBe(4);
    expect(r.bracketWhy).toEqual(["Mass land denial: Armageddon"]);

    const turns = deck([{ name: "T1", cmc: 5, typeLine: "Sorcery" }, { name: "T2", cmc: 5, typeLine: "Sorcery" }, { name: "T3", cmc: 5, typeLine: "Sorcery" }], facts({}));
    const t = rateDeck(turns, facts({ T1: { tags: ["extra-turn"] }, T2: { tags: ["extra-turn"] }, T3: { tags: ["extra-turn"] } }), [], null);
    expect(t.bracket).toBe(4);
    expect(t.bracketWhy).toEqual(["3 extra-turn cards: T1, T2, T3"]);
    expect(t.extraTurns).toEqual(["T1", "T2", "T3"]);
  });

  it("a planeswalker tagged for mass land denial doesn't count for it", () => {
    const cards = deck([
      { name: "Liliana, Dreadhorde General", cmc: 6, typeLine: "Legendary Planeswalker — Liliana" },
      { name: "Armageddon", cmc: 4, typeLine: "Sorcery" },
    ], facts({}));
    const f = facts({ "Liliana, Dreadhorde General": { tags: ["mass-land-denial"] }, Armageddon: { tags: ["mass-land-denial"] } });
    const r = rateDeck(cards, f, [], null);
    expect(r.massLandDenial).toEqual(["Armageddon"]);
    expect(r.effects.mld).toBe(1);
    expect(r.bracketWhy).toEqual(["Mass land denial: Armageddon"]);

    const alone = rateDeck(deck([{ name: "Liliana, Dreadhorde General", cmc: 6, typeLine: "Legendary Planeswalker — Liliana" }], facts({})), f, [], null);
    expect(alone.massLandDenial).toEqual([]);
    expect(alone.effects.mld).toBeUndefined();
    expect(alone.bracket).toBe(2);
  });

  it("5 tutors are bracket 3, 4 are not", () => {
    const names = ["T1", "T2", "T3", "T4", "T5"];
    const cards = deck(
      names.map((n) => ({ name: n, cmc: 2, typeLine: "Sorcery" })),
      facts({}),
    );
    const five = facts(Object.fromEntries(names.map((n) => [n, { tags: ["tutor"] }])));
    const r = rateDeck(cards, five, [], null);
    expect(r.bracket).toBe(3);
    expect(r.bracketWhy).toEqual(["5 tutors"]);
    expect(r.effects.tutors).toBe(5);
    expect(r.tutors).toEqual(names);
    const four = rateDeck(cards, facts(Object.fromEntries(names.slice(1).map((n) => [n, { tags: ["tutor"] }]))), [], null);
    expect(four.bracket).toBe(2);
  });

  it("one early combo is listed once even when other rules also reach 4", () => {
    const cards = deck([{ name: "A", cmc: 2, typeLine: "Creature" }, { name: "B", cmc: 2, typeLine: "Creature" }], facts({}));
    const f = facts({ A: { gameChanger: true }, B: { tags: ["mass-land-denial"] } });
    const r = rateDeck(cards, f, [combo("e", ["A", "B"], ["Win the game"])], null);
    expect(r.bracket).toBe(4);
    expect(r.bracketWhy).toEqual(["A + B: wins, mana value 4", "1 Game Changer: A", "Mass land denial: B"]);
  });

  it("never below 2 or above 4", () => {
    expect(rateDeck([], facts({}), [], null).bracket).toBe(2);
  });
});

describe("effects, lists and combos", () => {
  it("names the cards each effect counts", () => {
    const cards = [
      spell("Sol Ring", 1, "Artifact"),
      spell("Mana Drain", 2, "Instant"),
      spell("Swords", 1, "Instant"),
      spell("Wrath", 4, "Sorcery"),
      spell("Tutor", 2, "Sorcery"),
      spell("Ramp", 2, "Sorcery"),
      spell("LandDenial", 3, "Sorcery"),
      spell("Turn", 5, "Sorcery"),
      plains(1),
    ];
    const f = facts({
      "Sol Ring": { tags: ["mana-rock", "ramp"] },
      "Mana Drain": { tags: ["counterspell"] },
      Swords: { tags: ["removal"] },
      Wrath: { tags: ["removal", "sweeper"] },
      Tutor: { tags: ["tutor"] },
      Ramp: { tags: ["tutor", "ramp"] },
      LandDenial: { tags: ["mass-land-denial"] },
      Turn: { tags: ["extra-turn"] },
    });
    const r = rateDeck(cards, f, [], null);
    expect(r.fastMana).toEqual(["Sol Ring"]);
    expect(r.tutors).toEqual(["Tutor"]);
    expect(r.effects).toEqual({ fastmana: 1, tutors: 1, counters: 1, spotRemoval: 1, boardWipes: 1, extraTurns: 1, mld: 1 });
    expect(r.massLandDenial).toEqual(["LandDenial"]);
  });

  it("makes the combo cards, results, prerequisites and mana values", () => {
    const cards = [card("Thopter Foundry"), card("Sword of the Meek"), card("Dockside Extortionist")];
    const c = combo("abc", ["Thopter Foundry", "Sword of the Meek"], ["Win the game", "Infinite creature tokens", "Infinite ETB", "Infinite death triggers", "Infinite damage"], {
      notablePrerequisites: "All permanents on the battlefield.\nNone.",
      easyPrerequisites: "",
    });
    const r = rateDeck(cards, facts({}), [c], null);
    expect(r.combos).toHaveLength(1);
    const s = r.combos[0];
    expect(s.cards).toEqual(["Thopter Foundry", "Sword of the Meek"]);
    // One label per feature, first match: creature tokens win-tokens only, ETB → infinite-etb, death → infinite-ltb.
    expect(s.results).toEqual(["win-game", "infinite-tokens", "infinite-etb", "infinite-ltb", "infinite-damage"]);
    expect(s.prerequisites).toEqual(["All permanents on the battlefield.", "None."]);
    expect(s.spellbookUri).toBe("https://commanderspellbook.com/combo/abc/");
    expect(s.needsBoard).toBe(false);
    expect(s.counts).toBe(true);
    expect(s.manaValue).toBe(0);
    expect(s.wins).toBe(true);
  });

  it("labels each feature once per combo", () => {
    const cards = [card("A"), card("B")];
    const r = rateDeck(cards, facts({}), [combo("d", ["A", "B"], ["Infinite creature tokens", "Infinite creature tokens", "Win the game"])], null);
    expect(r.combos[0].results).toEqual(["infinite-tokens", "win-game"]);
  });

  it("labels the features as Commander Salt's results read them", () => {
    expect(featureLabel("Win the game")).toBe("win-game");
    expect(featureLabel("Infinite lifeloss")).toBe("infinite-lifeloss");
    expect(featureLabel("Infinite colorless mana")).toBe("infinite-colorless-mana");
    expect(featureLabel("Infinite lifegain")).toBe("infinite-lifegain");
    expect(featureLabel("Infinite creature tokens")).toBe("infinite-tokens");
    expect(featureLabel("Infinite damage")).toBe("infinite-damage");
    expect(featureLabel("Infinite ETB")).toBe("infinite-etb");
    expect(featureLabel("Infinite enters the battlefield triggers")).toBe("infinite-etb");
    expect(featureLabel("Infinite death triggers")).toBe("infinite-ltb");
    expect(featureLabel("Infinite LTB")).toBe("infinite-ltb");
    // Nothing gets an "infinite-" it doesn't have.
    expect(featureLabel("Opponents locked")).toBe("opponents-locked");
  });

  it("labels features and looks up mana values by front-face name, case-insensitively", () => {
    const cards = [card("Saffi Eriksdotter", { cmc: 2 }), card("Loyal Retainers", { cmc: 3 }), card("Altar of Dementia", { cmc: 0 })];
    const r = rateDeck(cards, facts({}), [combo("l", ["saffi eriksdotter", "loyal retainers", "Altar of Dementia"], ["Infinite lifeloss", "Infinite colorless mana", "Infinite primordial lifeloss"])], null);
    const s = r.combos[0];
    // "Infinite lifeloss" is infinite-lifeloss, "Infinite primordial lifeloss" infinite-primordial-lifeloss — neither is
    // infinite life loss; infinite colorless mana wins.
    expect(s.results).toEqual(["infinite-lifeloss", "infinite-colorless-mana", "infinite-primordial-lifeloss"]);
    expect(s.manaValue).toBe(5);
    expect(s.wins).toBe(true);
    const plain = rateDeck(cards, facts({}), [combo("m", ["Saffi Eriksdotter", "Loyal Retainers", "Altar of Dementia"], ["Infinite unblockable attackers"])], null);
    expect(plain.combos[0].wins).toBe(false);
  });

  it("puts two-card combos first", () => {
    const cards = [card("A"), card("B"), card("C")];
    const r = rateDeck(cards, facts({}), [combo("1", ["A", "B", "C"], ["Infinite untap"]), combo("2", ["A", "B"], ["Infinite untap"])], null);
    expect(r.combos.map((c) => c.cards.length)).toEqual([2, 3]);
  });
});

describe("salt", () => {
  it("adds up every card's salt, lands and basic lands included, with the saltiest five", () => {
    const cards = [card("Bomb", { quantity: 2 }), card("Rhystic Study"), card("Cyclonic Rift"), card("Zedruu"), card("Alpha"), card("Beta"), card("Omega"), plains(4)];
    const f = facts({
      Bomb: { salt: 5.04 },
      "Rhystic Study": { salt: 2.5 },
      "Cyclonic Rift": { salt: 2.5 },
      Zedruu: { salt: 1.222 },
      Alpha: { salt: 1.01 },
      Beta: { salt: 0.4 },
      Omega: { salt: 0.9 },
      Plains: { salt: 10 },
    });
    const r = rateDeck(cards, f, [], null);
    // 2 × 5.04 + 2.5 + 2.5 + 1.222 + 1.01 + 0.4 + 0.9 + 4 × 10 = 58.612 → 58.6
    expect(r.salt).toBe(58.6);
    expect(r.saltIntensity).toBe("High");
    expect(r.saltDriver).toBe("Bomb");
    // The basic lands are counted in the total but never in the saltiest list.
    expect(r.saltiest).toEqual([
      { name: "Bomb", salt: 5.04 },
      { name: "Cyclonic Rift", salt: 2.5 },
      { name: "Rhystic Study", salt: 2.5 },
      { name: "Zedruu", salt: 1.222 },
      { name: "Alpha", salt: 1.01 },
    ]);
  });

  it("counts a commander and its land the same way, and leaves out cards without a score", () => {
    const cards = [card("Commander", { commander: true }), card("Overgrown Tomb", { typeLine: "Land — Swamp Forest" }), card("Bear"), plains(2)];
    const f = facts({ Commander: { salt: 12.34 }, "Overgrown Tomb": { salt: 4.5 }, Plains: { salt: 0 } });
    const r = saltSumOf(cards, f);
    expect(r.total).toBe(16.8);
    expect(r.level).toBe("Mild");
    expect(r.saltiest).toEqual([
      { name: "Commander", salt: 12.34 },
      { name: "Overgrown Tomb", salt: 4.5 },
    ]);
  });

  it("leaves a basic land out of the saltiest list by its name too, when the card has no Scryfall type line", () => {
    const cards = [card("Island", { quantity: 4, typeLine: "Land", types: "Land" }), card("Snow-Covered Forest", { typeLine: "Land", types: "Land" }), card("Zedruu")];
    const f = facts({ Island: { salt: 3 }, "Snow-Covered Forest": { salt: 2 }, Zedruu: { salt: 1 } });
    const r = saltSumOf(cards, f);
    expect(r.total).toBe(15);
    expect(r.saltiest).toEqual([{ name: "Zedruu", salt: 1 }]);
  });

  it("has no salt when no card has one", () => {
    const r = rateDeck([card("Bear"), plains(1)], facts({}), [], null);
    expect(r.salt).toBeNull();
    expect(r.saltIntensity).toBeNull();
    expect(r.saltDriver).toBeNull();
    expect(r.saltiest).toEqual([]);
    expect(saltSumOf([], facts({}))).toEqual({ total: null, level: null, saltiest: [] });
  });

  it("names the intensity by the thresholds (Commander Salt's labels)", () => {
    expect(saltIntensity(29.9)).toBe("Mild");
    expect(saltIntensity(30)).toBe("Moderate");
    expect(saltIntensity(44.9)).toBe("Moderate");
    expect(saltIntensity(45)).toBe("High");
    expect(saltIntensity(null)).toBeNull();
  });
});

describe("tutors", () => {
  it("counts a real tutor, but not a land tutor nor a fetch land", () => {
    const cards = [
      spell("Demonic Tutor", 2, "Sorcery"),
      // A tutor tagged as a land tutor too, the way Scryfall tags Rampant Growth.
      spell("Nature's Lore", 2, "Sorcery"),
      // A fetch land: a Land, tagged tutor.
      card("Evolving Wilds", { typeLine: "Land" }),
      spell("Cultivate", 3, "Sorcery"),
      plains(1),
    ];
    const f = facts({
      "Demonic Tutor": { tags: ["tutor"] },
      "Nature's Lore": { tags: ["tutor", "tutor-land"] },
      "Evolving Wilds": { tags: ["tutor", "tutor-land"] },
      Cultivate: { tags: ["tutor", "ramp"] },
    });
    const r = rateDeck(cards, f, [], null);
    expect(r.tutors).toEqual(["Demonic Tutor"]);
    expect(r.effects.tutors).toBe(1);
  });

  it("five real tutors are bracket 3", () => {
    const names = ["T1", "T2", "T3", "T4", "T5"];
    const cards = [...names.map((n) => spell(n, 2, "Sorcery")), card("Evolving Wilds", { typeLine: "Land" })];
    const f = facts({ ...Object.fromEntries(names.map((n) => [n, { tags: ["tutor"] }])), "Evolving Wilds": { tags: ["tutor", "tutor-land"] } });
    const r = rateDeck(cards, f, [], null);
    expect(r.bracket).toBe(3);
    expect(r.effects.tutors).toBe(5);
  });
});

describe("the rating", () => {
  it("says it's the app's, with its version", () => {
    const r = rateDeck([card("Bear"), plains(1)], facts({}), [], null);
    expect(r.source).toBe("app");
    expect(r.version).toBe(4);
    expect(r.ratingVersion).toBe(RATING_VERSION);
    expect(r.axes).toEqual({});
    expect(r.playStyle).toBeNull();
    expect(r.manaFixing).toBeNull();
    expect(r.onCurve).toBeNull();
    expect(r.manaQuality).toBeNull();
    expect(r.notes).toEqual([]);
    expect(r.wincons).toEqual([]);
  });

  it("keys the list by its cards, not their order", () => {
    const a = ratingListKey([card("Bear", { quantity: 2 }), card("Cmdr", { commander: true }), plains(3)]);
    const b = ratingListKey([plains(3), card("Cmdr", { commander: true }), card("Bear", { quantity: 2 })]);
    expect(a).toBe(b);
    expect(a).toMatch(/^[0-9a-f]{8}$/);
    expect(ratingListKey([card("Bear", { quantity: 3 }), card("Cmdr", { commander: true }), plains(3)])).not.toBe(a);
    expect(ratingListKey([card("Bear", { quantity: 2 }), plains(3)])).not.toBe(a);
  });
});

describe("decodeSalt", () => {
  it("reads the new fields", () => {
    const r = rateDeck([card("Bear"), plains(1)], facts({}), [combo("c", ["Bear", "Cmdr"], ["Infinite mana"], { notablePrerequisites: "X" })], null);
    const back = decodeSalt(JSON.stringify(r))!;
    expect(back.source).toBe("app");
    expect(back.version).toBe(4);
    expect(back.ratingVersion).toBe(RATING_VERSION);
    expect(back.bracket).toBe(r.bracket);
    expect(back.bracketWhy).toEqual(r.bracketWhy);
    expect(back.salt).toBe(r.salt);
    expect(back.saltiest).toEqual(r.saltiest);
    expect(back.listKey).toBe(r.listKey);
    expect(back.axes).toEqual({});
    expect(back.combos[0].manaValue).toBe(r.combos[0].manaValue);
    expect(back.combos[0].wins).toBe(true);
  });

  it("defaults them for Commander Salt's data", () => {
    const old = decodeSalt(JSON.stringify({ gameChangers: ["X"], combos: [{ cards: ["A", "B"], counts: true, results: [], needsBoard: false, spellbookUri: null, prerequisites: [] }] }))!;
    expect(old.source).toBeNull();
    expect(old.bracket).toBeNull();
    expect(old.bracketWhy).toEqual([]);
    expect(old.salt).toBeNull();
    expect(old.saltiest).toEqual([]);
    expect(old.listKey).toBeNull();
    expect(old.ratingVersion).toBeNull();
    expect(old.combos[0].manaValue).toBeNull();
    expect(old.combos[0].wins).toBeNull();
  });
});
