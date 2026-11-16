import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { deckToText, type ExportFormat } from "@/lib/deckexport";
import type { DeckCard } from "@/lib/deckshared";
import { parseDeckText } from "@/lib/decktext";
import type { CardRef } from "@/lib/syncfile";

const ref = (name: string, setCode: string, collectorNumber: string): CardRef => ({
  scryfallId: `${setCode}-${collectorNumber}-${name}`,
  name,
  setCode,
  setName: "A set",
  collectorNumber,
  rarity: "rare",
  imageUrl: null,
  cardmarketId: null,
  fallbackEur: 1,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

const card = (name: string, setCode: string, collectorNumber: string, o: Partial<DeckCard> = {}): DeckCard => ({
  id: 0,
  deckId: -1,
  card: ref(name, setCode, collectorNumber),
  quantity: 1,
  category: "Mana",
  types: "Artifact",
  cmc: 1,
  commander: false,
  foil: false,
  etched: false,
  gameChanger: false,
  addedInApp: false,
  maybeboard: false,
  categories: "Mana",
  archidektCardId: null,
  relationId: null,
  label: null,
  lineUid: `${setCode}-${collectorNumber}`,
  ...o,
});

/** A deck with a commander, a foil and an etched card, a card with two categories and a maybeboard card. */
const deck = (): DeckCard[] => [
  card("Kenrith, the Returned King", "ELD", "303", { commander: true, category: "Commander", categories: "Commander", types: "Legendary Creature" }),
  card("Sol Ring", "CMR", "263", { quantity: 2, categories: "Mana\nCard Draw" }),
  card("Swords to Plowshares", "STA", "10", { foil: true, category: "Removal", categories: "Removal" }),
  card("Arcane Signet", "C21", "1", { foil: true, etched: true, category: "Mana", categories: "Mana" }),
  card("Rhystic Study", "PCY", "45", { maybeboard: true, category: "Maybeboard", categories: "Maybeboard" }),
];

describe("copying a deck as text", () => {
  it("writes Moxfield's export with tags and finishes", () => {
    expect(deckToText(deck(), "moxfield", { categories: true, maybeboard: true })).toBe(
      [
        "Commander",
        "1 Kenrith, the Returned King (ELD) 303 #Commander",
        "",
        "Deck",
        "1 Arcane Signet (C21) 1 *E* #Mana",
        "2 Sol Ring (CMR) 263 #Mana #Card_Draw",
        "1 Swords to Plowshares (STA) 10 *F* #Removal",
        "",
        "Maybeboard",
        "1 Rhystic Study (PCY) 45 #Maybeboard",
      ].join("\n"),
    );
    expect(deckToText(deck(), "moxfield", { categories: false, maybeboard: false })).toBe(
      ["Commander", "1 Kenrith, the Returned King (ELD) 303", "", "Deck", "1 Arcane Signet (C21) 1 *E*", "2 Sol Ring (CMR) 263", "1 Swords to Plowshares (STA) 10 *F*"].join("\n"),
    );
  });

  it("writes Archidekt's export with categories in brackets", () => {
    expect(deckToText(deck(), "archidekt", { categories: true, maybeboard: true })).toBe(
      [
        "Commander",
        "1x Kenrith, the Returned King (eld) 303 [Commander]",
        "",
        "Deck",
        "1x Arcane Signet (c21) 1 *E* [Mana]",
        "2x Sol Ring (cmr) 263 [Mana,Card Draw]",
        "1x Swords to Plowshares (sta) 10 *F* [Removal]",
        "",
        "Maybeboard",
        "1x Rhystic Study (pcy) 45 [Maybeboard]",
      ].join("\n"),
    );
    expect(deckToText(deck(), "archidekt", { categories: false, maybeboard: false })).toBe(
      ["Commander", "1x Kenrith, the Returned King (eld) 303", "", "Deck", "1x Arcane Signet (c21) 1 *E*", "2x Sol Ring (cmr) 263", "1x Swords to Plowshares (sta) 10 *F*"].join("\n"),
    );
  });

  it("writes Arena's list, with a Sideboard instead of a maybeboard", () => {
    expect(deckToText(deck(), "arena", { categories: true, maybeboard: true })).toBe(
      [
        "Commander",
        "1 Kenrith, the Returned King (ELD) 303",
        "",
        "Deck",
        "1 Arcane Signet (C21) 1",
        "2 Sol Ring (CMR) 263",
        "1 Swords to Plowshares (STA) 10",
        "",
        "Sideboard",
        "1 Rhystic Study (PCY) 45",
      ].join("\n"),
    );
  });

  it("writes a plain list without printings or finishes", () => {
    expect(deckToText(deck(), "plain", { categories: true, maybeboard: true })).toBe(
      ["Commander", "1 Kenrith, the Returned King", "", "Deck", "1 Arcane Signet", "2 Sol Ring", "1 Swords to Plowshares", "", "Maybeboard", "1 Rhystic Study"].join("\n"),
    );
  });

  it("leaves out empty sections and trailing blank lines", () => {
    expect(deckToText([], "moxfield", { categories: true, maybeboard: true })).toBe("");
    const onlyDeck = [card("Sol Ring", "CMR", "263")];
    expect(deckToText(onlyDeck, "moxfield", { categories: true, maybeboard: true })).toBe(["Deck", "1 Sol Ring (CMR) 263 #Mana"].join("\n"));
    // A maybeboard alone has no "Deck" heading either.
    const onlyMaybe = [card("Rhystic Study", "PCY", "45", { maybeboard: true, category: "Maybeboard", categories: "Maybeboard" })];
    expect(deckToText(onlyMaybe, "arena", { categories: false, maybeboard: true })).toBe(["Sideboard", "1 Rhystic Study (PCY) 45"].join("\n"));
  });

  it("keeps two lines of the same card apart", () => {
    const cards = [card("Sol Ring", "CMR", "263", { quantity: 2 }), card("Sol Ring", "CMR", "263", { foil: true, quantity: 1 })];
    expect(deckToText(cards, "moxfield", { categories: false, maybeboard: false })).toBe(["Deck", "2 Sol Ring (CMR) 263", "1 Sol Ring (CMR) 263 *F*"].join("\n"));
  });
});

describe("reading an export back (round trip)", () => {
  /** Each parsed line as "quantity|name|set|number|foil|etched|board|tags", sorted, with the lines it couldn't read. */
  const back = (text: string) => {
    const r = parseDeckText(text);
    return {
      unreadable: r.unreadable,
      lines: r.lines.map((l) => `${l.quantity}|${l.name}|${l.setCode}|${l.number}|${l.foil}|${l.etched}|${l.board}|${l.tags.join(",")}`).sort(),
    };
  };

  it("gives Moxfield's export back (its tags write spaces as underscores)", () => {
    const r = back(deckToText(deck(), "moxfield", { categories: true, maybeboard: true }));
    expect(r.unreadable).toEqual([]);
    expect(r.lines).toEqual(
      [
        "1|Arcane Signet|c21|1|true|true|MAIN|Mana",
        "1|Kenrith, the Returned King|eld|303|false|false|COMMANDER|Commander",
        "1|Rhystic Study|pcy|45|false|false|MAYBE|Maybeboard",
        "2|Sol Ring|cmr|263|false|false|MAIN|Mana,Card Draw",
        "1|Swords to Plowshares|sta|10|true|false|MAIN|Removal",
      ].sort(),
    );
  });

  it("gives Archidekt's export back", () => {
    const r = back(deckToText(deck(), "archidekt", { categories: true, maybeboard: true }));
    expect(r.unreadable).toEqual([]);
    expect(r.lines).toEqual(
      [
        "1|Arcane Signet|c21|1|true|true|MAIN|Mana",
        "1|Kenrith, the Returned King|eld|303|false|false|COMMANDER|Commander",
        "1|Rhystic Study|pcy|45|false|false|MAYBE|Maybeboard",
        "2|Sol Ring|cmr|263|false|false|MAIN|Mana,Card Draw",
        "1|Swords to Plowshares|sta|10|true|false|MAIN|Removal",
      ].sort(),
    );
  });

  it("gives Arena's export back (it holds no finishes, so a matte deck)", () => {
    const matte = deck().map((c) => ({ ...c, foil: false, etched: false }));
    const r = back(deckToText(matte, "arena", { categories: true, maybeboard: true }));
    expect(r.unreadable).toEqual([]);
    expect(r.lines).toEqual(
      [
        "1|Arcane Signet|c21|1|false|false|MAIN|",
        "1|Kenrith, the Returned King|eld|303|false|false|COMMANDER|",
        "1|Rhystic Study|pcy|45|false|false|MAYBE|",
        "2|Sol Ring|cmr|263|false|false|MAIN|",
        "1|Swords to Plowshares|sta|10|false|false|MAIN|",
      ].sort(),
    );
  });
});

/** The lines and expected texts shared with the Android app (DeckTextExportTest.kt). */
interface SharedLine {
  quantity: number;
  name: string;
  setCode: string;
  number: string;
  foil: boolean;
  etched: boolean;
  categories: string[];
  commander: boolean;
  maybeboard: boolean;
}

interface SharedCase {
  format: ExportFormat;
  categories: boolean;
  maybeboard: boolean;
  text: string;
}

const shared = JSON.parse(readFileSync(path.join(__dirname, "fixtures", "deckexport-cases.json"), "utf8")) as { lines: SharedLine[]; cases: SharedCase[] };

describe("shared cases", () => {
  const cards = shared.lines.map((l) =>
    card(l.name, l.setCode, l.number, {
      quantity: l.quantity,
      category: l.categories[0] ?? "",
      categories: l.categories.join("\n"),
      commander: l.commander,
      foil: l.foil,
      etched: l.etched,
      maybeboard: l.maybeboard,
    }),
  );

  for (const c of shared.cases) {
    it(`writes ${c.format} with categories ${c.categories ? "on" : "off"} and the maybeboard ${c.maybeboard ? "on" : "off"}`, () => {
      expect(deckToText(cards, c.format, { categories: c.categories, maybeboard: c.maybeboard })).toBe(c.text);
    });
  }
});
