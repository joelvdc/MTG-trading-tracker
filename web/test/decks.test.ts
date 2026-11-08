import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { statsCard, toDeckInfo, type ScryDeckCard } from "@/lib/deckdetails";
import { copyDeck } from "@/lib/deckedit";
import { decksView, deckView, ruleZeroDecks, sharedCards, syncDecks } from "@/lib/decks";
import { decodeSalt, missingPlan, sections, tagsByCard } from "@/lib/deckshared";
import { costs, curve, fetches, sources, tokens, type StatsCard } from "@/lib/deckstats";
import { parse, type Json } from "@/lib/syncfile";

const fixture = () => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));

const sc = (name: string, typeLine: string, o: Partial<StatsCard> = {}): StatsCard => ({
  name,
  scryfallId: name,
  quantity: 1,
  cmc: 0,
  types: typeLine,
  commander: false,
  typeLine,
  manaCost: "",
  producedMana: "",
  oracleText: "",
  tokens: null,
  ...o,
});

describe("deck stats (the app's DeckStats)", () => {
  it("counts the curve without lands and the colours in the costs", () => {
    const cards = [
      sc("Bolt", "Instant", { cmc: 1, manaCost: "{R}", quantity: 2 }),
      sc("Big", "Creature — Giant", { cmc: 9, manaCost: "{7}{G}{G}" }),
      sc("Hybrid", "Sorcery", { cmc: 2, manaCost: "{B/G}{B/P}" }),
      sc("Forest", "Basic Land — Forest", { producedMana: "G" }),
    ];
    const c = curve(cards);
    expect(c.counts).toEqual([0, 2, 1, 0, 0, 0, 0, 1]);
    expect(c.spells).toBe(4);
    expect(costs(cards)).toEqual([
      { colour: "B", count: 2 },
      { colour: "R", count: 2 },
      { colour: "G", count: 3 },
    ]);
  });

  it("counts fetch lands for the lands they find and limits any-colour cards to the identity", () => {
    const tomb = sc("Overgrown Tomb", "Land — Swamp Forest", { producedMana: "BG" });
    const swamp = sc("Swamp", "Basic Land — Swamp", { producedMana: "B" });
    const mire = sc("Bloodstained Mire", "Land", { oracleText: "{T}, Pay 1 life, Sacrifice this land: Search your library for a Swamp or Mountain card, put it onto the battlefield, then shuffle." });
    const tower = sc("Command Tower", "Land", { producedMana: "WUBRG" });
    const signet = sc("Arcane Signet", "Artifact", { producedMana: "WUBRG", cmc: 2 });
    expect(Object.fromEntries(fetches(mire, [tomb, swamp, mire, tower]))).toEqual({ B: ["Overgrown Tomb", "Swamp"], G: ["Overgrown Tomb"] });
    const s = sources([tomb, swamp, mire, tower, signet], "BG", false);
    expect(s.map((x) => [x.colour, x.count])).toEqual([
      ["B", 5],
      ["G", 4],
    ]);
    expect(sources([tomb, swamp, mire, tower, signet], "BG", true).map((x) => x.count)).toEqual([4, 3]);
    expect(s[0].cards.find((l) => l.name === "Bloodstained Mire")?.finds).toEqual(["Overgrown Tomb", "Swamp"]);
  });

  it("lists each token once with the cards that make it", () => {
    const a = sc("A", "Creature", { tokens: [{ id: "t1", name: "Insect", typeLine: "Token Creature — Insect" }] });
    const b = sc("B", "Creature", { tokens: [{ id: "t2", name: "Insect", typeLine: "Token Creature — Insect" }, { id: "m", name: "The Monarch", typeLine: "Card" }] });
    expect(tokens([a, b], { t1: "o1", t2: "o1" }, { t1: "1/1" })).toEqual([
      { id: "t1", name: "Insect", label: "Insect 1/1", makers: ["A", "B"], key: "o1", defaultId: "t1", oracleId: "o1", picked: false },
      { id: "m", name: "The Monarch", label: "The Monarch", makers: ["B"], key: "The Monarch|Card", defaultId: "m", oracleId: null, picked: false },
    ]);
    // A printing picked for the deck (deckTokenPicks, by the token's key) is the one shown.
    expect(tokens([a, b], { t1: "o1", t2: "o1" }, {}, { o1: "t9" })[0]).toMatchObject({ id: "t9", defaultId: "t1", picked: true });
  });

  it("finds the cards in several decks and every scored deck's rule-zero cards", () => {
    const f = fixture();
    const src = syncDecks(f)[0];
    copyDeck(f, src.deck.archidektId, "Copy", 1_750_000_000_000);
    const shared = sharedCards(f);
    expect(shared.length).toBeGreaterThan(0);
    expect(shared[0].uses.map((u) => u.deckName).sort()).toEqual(["Copy", src.deck.name].sort());
    for (const s of shared) {
      expect(s.uses.length).toBeGreaterThan(1);
      expect(s.need).toBe(s.uses.reduce((n, u) => n + u.quantity, 0));
    }
    expect(ruleZeroDecks(f).decks.every((d) => d.salt !== null)).toBe(true);
  });
});

describe("Scryfall's deck details (the app's CardInfo.toInfo)", () => {
  it("reads double-faced cards, tokens and loyalty", () => {
    const c: ScryDeckCard = {
      id: "a",
      cmc: 3,
      color_identity: ["G", "B"],
      produced_mana: ["G", "C", "B"],
      card_faces: [
        { type_line: "Creature — Elf", mana_cost: "{1}{B}{G}", oracle_text: "Front text", power: "2", toughness: "3", oracle_id: "x" },
        { type_line: "Land", mana_cost: "", oracle_text: "Back text" },
      ],
      type_line: "Creature — Elf // Land",
      all_parts: [
        { id: "a", component: "combo_piece", name: "Self", type_line: "Creature" },
        { id: "tok", component: "token", name: "Elf Warrior", type_line: "Token Creature — Elf Warrior" },
        { id: "mon", component: "combo_piece", name: "The Monarch", type_line: "Card" },
      ],
    };
    const d = toDeckInfo(c, 1);
    expect(d).toMatchObject({ t: "Creature — Elf // Land", m: "{1}{B}{G} // ", p: "BGC", o: "Front text\n//\nBack text", pt: "2/3", l: null, oi: "x", i: "BG" });
    expect(d.k?.map((t) => t.id)).toEqual(["tok", "mon"]);
    expect(toDeckInfo({ id: "p", type_line: "Legendary Planeswalker — Jace", mana_cost: "{2}{U}{U}" }, 1).l).toBe("");
    const s = statsCard({ name: "X", scryfallId: "a", quantity: 1, cmc: 3, types: "Creature", commander: false }, d);
    expect(s.manaCost).toBe("{1}{B}{G} // ");
    expect(statsCard({ name: "X", scryfallId: "a", quantity: 1, cmc: 3, types: "Creature", commander: false }, undefined).producedMana).toBeNull();
  });
});

describe("decks pages", () => {
  it("groups by category or type with the commander first", () => {
    const r = (name: string, category: string, types: string, commander = false) => ({ name, category, types, commander });
    const rows = [r("Sol Ring", "Ramp", "Artifact"), r("Vohar", "Commander", "Legendary Creature", true), r("Arcane Signet", "ramp", "Artifact"), r("Island", "Land", "Basic Land")];
    expect(sections(rows, "CATEGORY").map((s) => [s.title, s.rows.map((x) => x.name)])).toEqual([
      ["Commander", ["Vohar"]],
      ["Land", ["Island"]],
      ["Ramp", ["Arcane Signet", "Sol Ring"]],
    ]);
    expect(sections(rows, "TYPE").map((s) => s.title)).toEqual(["Commander", "Lands", "Artifacts"]);
  });

  it("counts owned copies once across lines and skips basics", () => {
    const owned = new Map([["sol ring", 1]]);
    const plan = missingPlan(
      [
        { name: "Sol Ring", quantity: 1 },
        { name: "Sol Ring", quantity: 1 },
        { name: "Island", quantity: 10 },
      ],
      owned,
      true,
    );
    expect(plan.map(([l, n]) => [l.name, n])).toEqual([["Sol Ring", 1]]);
  });

  it("tags bracket cards like the app", () => {
    const s = decodeSalt(JSON.stringify({ gameChangers: ["Rhystic Study"], twoCardCombos: [["Thassa's Oracle", "Demonic Consultation"]], combos: [{ cards: ["A", "B", "C"] }], tutors: ["Demonic Tutor"] }))!;
    const t = tagsByCard(s);
    expect(t.get("rhystic study")).toEqual(["GAME_CHANGER"]);
    expect(t.get("thassa's oracle")).toEqual(["COMBO"]);
    expect(t.get("a")).toEqual(["OTHER_COMBO_3"]);
    expect(t.get("demonic tutor")).toEqual(["TUTOR"]);
  });

  it("shows the app's decks with value and missing cards", () => {
    const f = fixture();
    const v = decksView(f, null, null, null);
    expect(v.decks).toHaveLength(syncDecks(f).length);
    const first = v.decks[0];
    expect(first.name).toBe("Vohar Danse");
    expect(first.valueEur).toBeCloseTo(1.5);
    const d = deckView(f, first.id, null, null, null)!;
    expect(d.rows[0]).toMatchObject({ name: "Sol Ring", quantity: 1, category: "Ramp", unitEur: 1.5 });
    expect(deckView(f, 123, null, null, null)).toBeNull();
  });

  it("shows a deck in the printing you own", () => {
    const f = fixture();
    const sol = syncDecks(f)[0].cards[0].card;
    const mine = { ...sol, scryfallId: "22222222-2222-2222-2222-222222222222", setCode: "cmr" };
    const coll = ((f.data.collection as Json[]) ?? []) as unknown as { item: { card: unknown; quantity: number; foil: boolean; etched: boolean }; binder: string | null }[];
    coll.push({ item: { ...(coll[0]?.item ?? {}), card: mine, quantity: 1, foil: false, etched: false } as never, binder: null });
    f.data.collection = coll as unknown as Json;
    const row = deckView(f, syncDecks(f)[0].deck.archidektId, null, null, null)!.rows[0];
    if (coll.some((s) => (s.item.card as { name: string }).name === "Sol Ring")) {
      expect(row.missing).toBe(0);
      expect(row.owned).toBeGreaterThan(0);
    }
  });

  it("counts the copies of the printing shown and lists the piles for the collection tracker", () => {
    const f = fixture();
    const sd = syncDecks(f)[0];
    const line = sd.cards[0];
    const other = { ...line.card, scryfallId: "33333333-3333-3333-3333-333333333333", setCode: "cmr", setName: "Commander Legends" };
    const pile = (card: typeof line.card, quantity: number, foil: boolean, binder: string | null) =>
      ({ item: { card, quantity, foil, etched: false, condition: "NM" }, binder }) as never;
    // Show the deck's own printing, so the line's printing is the deck's.
    f.data.prefs = { values: { ...((f.data.prefs as { values?: Record<string, string> } | undefined)?.values ?? {}), deckPrintingsOff: String(sd.deck.archidektId) } } as unknown as Json;
    f.data.binders = [{ id: 1, name: "Trades", createdAt: 0, uid: "b1", updatedAt: 0 }] as unknown as Json;
    f.data.collection = [pile(line.card, 2, line.foil, "b1"), pile(line.card, 1, !line.foil, null), pile(other, 1, line.foil, null)] as unknown as Json;
    const row = deckView(f, sd.deck.archidektId, null, null, null)!.rows[0];
    // Only the pile with the same printing and finish counts as exact; the others are still listed.
    expect(row.exact).toBe(2);
    expect(row.owned).toBe(4);
    expect(row.held).toHaveLength(3);
    expect(row.held[0]).toMatchObject({ scryfallId: line.card.scryfallId, quantity: 2, condition: "NM", binder: "Trades" });
    expect(row.held[1].binder).toBe("Unsorted");
    // Only another printing: nothing is exact, but the card is held.
    f.data.collection = [pile(other, 1, line.foil, null)] as unknown as Json;
    const split = deckView(f, sd.deck.archidektId, null, null, null)!.rows[0];
    expect(split.exact).toBe(0);
    expect(split.held.map((h) => h.setCode)).toEqual(["cmr"]);
  });

  it("gives no piles for basic lands; the maybeboard gets its piles too", () => {
    const f = fixture();
    const sd = syncDecks(f)[0];
    const card = sd.cards[0].card;
    sd.cards.push(
      { ...sd.cards[0], id: 900, lineUid: "basic", card: { ...card, name: "Island" }, maybeboard: false },
      { ...sd.cards[0], id: 901, lineUid: "maybe", maybeboard: true },
    );
    f.data.collection = [
      { item: { card: { ...card, name: "Island" }, quantity: 5, foil: false, etched: false, condition: "NM" }, binder: null },
      { item: { card, quantity: 1, foil: false, etched: false, condition: "NM" }, binder: null },
    ] as unknown as Json;
    const rows = deckView(f, sd.deck.archidektId, null, null, null)!.rows;
    expect(rows.find((r) => r.lineUid === "basic")).toMatchObject({ held: [], exact: 0 });
    const maybe = rows.find((r) => r.lineUid === "maybe")!;
    expect(maybe.held).toHaveLength(1);
    expect(maybe.exact).toBe(1);
    // The maybeboard still gets no printing of your own and no printing choices.
    expect(maybe).toMatchObject({ original: null, choices: [] });
  });
});
