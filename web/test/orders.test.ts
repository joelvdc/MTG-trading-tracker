import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  addBought,
  applyOrderEdit,
  cardTraderCondition,
  cleanNumber,
  importOrderLines,
  matchOrder,
  orderLines,
  ordersFromFile,
  ordersOf,
  parseOrderRows,
  recordOrders,
  tokenNumber,
  worthOf,
  type Found,
  type Lookup,
} from "@/lib/orders";
import { pricingSetup } from "@/lib/pricing";
import { readSpreadsheet } from "@/lib/spreadsheet";
import { collection, deletions, parse, type CardRef, type SyncFile } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

const appFile = (): SyncFile => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const resource = (name: string) => new Uint8Array(readFileSync(path.join(__dirname, "..", "..", "app", "src", "test", "resources", "cardtrader", name)));
const NOW = 1_750_000_000_000;
let n = 0;
const uids = () => `9${String(++n).padStart(31, "0")}`;
const bytes = (s: string) => new TextEncoder().encode(s);

const shipment = [
  "idProduct;groupCount;price;idLanguage;condition;isFoil;isSigned;isAltered;isPlayset;isReverseHolo;isFirstEd;isFullArt;isUberRare;isWithDie",
  "315288;1;6.98;1;3;;;;;;;;;",
  "788181;2;2.98;1;2;1;;;;;;;;",
  "378864;1;48;4;2;;;;1;;;;;",
  "264381;1;0,50;7;2;;1;1;;;;;;",
  "555;1;1.00;42;2;;;;;;;;;",
].join("\n");

const monthly = [
  "Shipment nr.;Date of purchase;Article;Product ID;Localized Product Name;Expansion;Category;Amount;Article Value;Total;Currency;Comments",
  "1185249514;2024-12-02 16:38:27;Essence Flux;289154;Essence Flux;Shadows over Innistrad;Magic Single;1;2.38;2.38;EUR;289154",
  "1185249514;2024-12-02 16:38:27;Cavalier of Dawn;378651;暁の騎兵;Core 2020;Magic Single;3;2.55;7.65;EUR;",
  "1185249600;2024-12-05 09:00:00;80 KMC Hyper mat Sleeves (Black);4321;80 KMC Hyper mat Sleeves (Black);;Sleeves;3;5.99;17.97;EUR;",
  "1185249600;2024-12-05 09:00:00;Charizard GX;5555;Charizard GX;Hidden Fates;Pokémon Single;1;4;4;EUR;",
].join("\n");

let ids = 0;
const ref = (name: string, set: string, num: string, extra: Partial<CardRef> = {}): CardRef => ({
  scryfallId: `00000000-0000-0000-0000-${String(++ids).padStart(12, "0")}`,
  name,
  setCode: set,
  setName: set.toUpperCase(),
  collectorNumber: num,
  rarity: "common",
  imageUrl: null,
  cardmarketId: null,
  fallbackEur: null,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
  ...extra,
});
const found = (c: CardRef, typeLine = "Creature", layout = "normal"): Found => ({ card: c, typeLine, layout });

describe("order files (the app's OrderFiles and CardTraderOrder)", () => {
  it("reads a CardTrader order", () => {
    const file = parseOrderRows(readSpreadsheet(resource("order_sample.xls")), "cardtrader_order_20260623hxrahx.xls");
    expect(file.format).toBe("CARDTRADER");
    // The two Clifftop Lookout rows are one line of 2.
    expect(file.lines).toHaveLength(9);
    const by = (name: string) => file.lines.find((l) => l.name === name)!;
    expect(by("Clifftop Lookout")).toMatchObject({ quantity: 2, price: 0.24 });
    expect(by("Decorum Dissertation")).toMatchObject({ foil: true, condition: "EX" });
    expect(by("Island")).toMatchObject({ quantity: 4, language: "JA", signed: true });
    expect(by("Fist of Suns")).toMatchObject({ altered: true, language: "DE", condition: "PO" });
    expect(file.lines.every((l) => l.date === "2026-06-23" && l.order === "20260623hxrahx" && l.productId === null)).toBe(true);
    expect(cardTraderCondition("Moderately Played")).toBe("LP");
    expect(cleanNumber("044")).toBe("44");
    expect(tokenNumber("T 04/02")).toBe("4");
    expect(tokenNumber("T16/12")).toBe("16");
  });

  it("reads Cardmarket's shipment file", () => {
    const file = parseOrderRows(readSpreadsheet(bytes(shipment)), "ArticlesFromShipment1284353365.csv");
    expect(file.format).toBe("CARDMARKET_SHIPMENT");
    const [journey, marvin, cavalier, signed, odd] = file.lines;
    expect(journey).toMatchObject({ productId: 315288, condition: "EX", language: "EN", price: 6.98, foil: false, order: "1284353365", languageKnown: true });
    expect(marvin).toMatchObject({ foil: true, quantity: 2, condition: "NM" });
    expect(cavalier).toMatchObject({ playset: true, quantity: 4, price: 12, language: "ES" });
    expect(signed).toMatchObject({ signed: true, altered: true, language: "JA", price: 0.5 });
    expect(odd.languageKnown).toBe(false);
  });

  it("reads Cardmarket's monthly file and leaves out what isn't a Magic single", () => {
    const file = parseOrderRows(readSpreadsheet(bytes(monthly)));
    expect(file.format).toBe("CARDMARKET_MONTHLY");
    expect(file.lines).toHaveLength(2);
    expect(file.otherItems).toBe(2);
    expect(file.lines[0]).toMatchObject({ name: "Essence Flux", setName: "Shadows over Innistrad", productId: 289154, date: "2024-12-02", order: "1185249514", versionKnown: false });
    expect(file.lines[1]).toMatchObject({ quantity: 3, price: 2.55 });
  });

  it("refuses other files", () => {
    expect(() => parseOrderRows(readSpreadsheet(bytes("Name,Set code,Quantity\nBrainstorm,ICE,1\n")))).toThrow(EditError);
  });
});

describe("matching, importing and remembering orders", () => {
  it("finds CardTrader lines by set and number, then by name in the set", async () => {
    const file = parseOrderRows(readSpreadsheet(resource("order_sample.xls")), "cardtrader_order_20260623hxrahx.xls");
    const first = file.lines[0];
    const known = found(ref(first.name, first.setCode.toLowerCase(), cleanNumber(first.number)));
    const searched: string[] = [];
    const look: Lookup = {
      collection: async (ids) => (ids.some((i) => (i as { collector_number: string }).collector_number === known.card.collectorNumber) ? [known] : []),
      byCardmarketId: async () => null,
      fuzzy: async () => null,
      search: async (q) => {
        searched.push(q);
        return q.includes("Island") ? [found(ref("Island", "sos", "300"), "Basic Land — Island")] : [];
      },
    };
    const cands = await matchOrder(file, look);
    expect(cands[0]).toMatchObject({ card: known.card, guessed: false });
    const island = cands.find((c) => c.line.name === "Island")!;
    expect(island).toMatchObject({ basicLand: true, guessed: true });
    expect(searched.some((q) => q.startsWith(`!"Island" (e:${island.line.setCode.toLowerCase()}`))).toBe(true);
  });

  it("finds Cardmarket lines by product number and names them", async () => {
    const file = parseOrderRows(readSpreadsheet(bytes(shipment)), "ArticlesFromShipment1284353365.csv");
    const look: Lookup = {
      collection: async () => [],
      byCardmarketId: async (id) => (id === 315288 ? found(ref("Journey to Nowhere", "wwk", "13", { cardmarketId: 315288 })) : null),
      fuzzy: async () => null,
      search: async () => [],
    };
    const cands = await matchOrder(file, look);
    expect(cands[0].line).toMatchObject({ name: "Journey to Nowhere", setCode: "WWK", number: "13" });
    expect(cands[1].card).toBeNull();
  });

  it("adds the cards with their prices and remembers the order once", () => {
    const f = appFile();
    const file = parseOrderRows(readSpreadsheet(bytes(shipment)), "ArticlesFromShipment1284353365.csv");
    const journey = ref("Journey to Nowhere", "wwk", "13", { cardmarketId: 315288 });
    const added = importOrderLines(f, [{ card: journey, line: file.lines[0] }], null, NOW, null, uids);
    expect(added).toBe(1);
    const pile = collection(f).find((s) => s.item.card.scryfallId === journey.scryfallId)!;
    expect(pile.item).toMatchObject({ purchasePrice: 6.98, condition: "EX", quantity: 1 });
    // A second copy for 3.02 makes the pile's price the average.
    addBought(f, { card: journey, foil: false, condition: "EX", language: "EN", quantity: 1, binder: null, price: 3.02 }, NOW, null, uids);
    const again = collection(f).find((s) => s.item.card.scryfallId === journey.scryfallId)!;
    expect(again.item).toMatchObject({ quantity: 2, purchasePrice: 5 });

    const orders = ordersFromFile(file, "2026-11-06", (i) => (i === 0 ? journey : null), NOW);
    expect(orders).toHaveLength(1);
    expect(orders[0]).toMatchObject({ shop: "Cardmarket", number: "1284353365", title: "Cardmarket shipment 1284353365", date: "2026-11-06", cards: 1 + 2 + 4 + 1 + 1 });
    expect(orders[0].paid).toBeCloseTo(6.98 + 2 * 2.98 + 48 + 0.5 + 1, 2);
    expect(recordOrders(f, orders, NOW, uids)).toBe(1);
    expect(recordOrders(f, orders, NOW + 5, uids)).toBe(0);
    const saved = ordersOf(f);
    expect(saved).toHaveLength(1);
    const lines = orderLines(saved[0]);
    expect(lines[0]).toEqual({ name: "Journey to Nowhere", setCode: "wwk", number: "13", price: 6.98, condition: "EX", language: "EN", scryfallId: journey.scryfallId, cardmarketId: 315288 });
    // Unknown products keep a name the app understands.
    expect(lines[1].name).toBe("Cardmarket product 788181");

    applyOrderEdit(f, { type: "date", uid: saved[0].uid!, date: "2026-10-01" }, NOW + 10);
    expect(ordersOf(f)[0].date).toBe("2026-10-01");
    applyOrderEdit(f, { type: "delete", uid: saved[0].uid! }, NOW + 20);
    expect(ordersOf(f)).toHaveLength(0);
    expect(deletions(f).some((d) => d.uid === saved[0].uid)).toBe(true);
  });

  it("works out what an order is worth now", () => {
    const setup = pricingSetup({}, "trend", { createdAt: "", madeAt: 0, rates: { date: "", perEuro: {} }, products: { "315288": [10, null, null, null, null, null] } }, null, null);
    const w = worthOf([{ name: "Journey", quantity: 2, price: 6, cardmarketId: 315288 }, { name: "Unknown", quantity: 1, price: 3 }], setup);
    expect(w).toEqual({ value: 20, paid: 12, priced: 2, unpriced: 1 });
  });
});
