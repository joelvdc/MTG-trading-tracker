import { describe, expect, it } from "vitest";
import type { Candidate, OrderLine } from "@/lib/orders";
import { additions, byHand, checkPackage, countOf, lineStatus, orderedCount, problemLines, report, scanUse } from "@/lib/packagecheck";
import { scansOf, useScans, type ScannedCard } from "@/lib/scans";
import type { SyncFile } from "@/lib/syncfile";
import type { CardRef } from "@/lib/syncfile";

// The app's PackageCheckTest (Release132Test.kt), case for case: both must give the same answers.

const card = (name: string, set = "tst", number = "1"): CardRef => ({
  scryfallId: `${name}|${set}|${number}`,
  name,
  setCode: set,
  setName: `Set ${set}`,
  collectorNumber: number,
  rarity: "rare",
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
});

const line = (name: string, set: string, number: string, o: { qty?: number; price?: number; lang?: string; foil?: boolean; cond?: string } = {}): OrderLine => ({
  setCode: set.toUpperCase(),
  setName: `Set ${set}`,
  name,
  number,
  quantity: o.qty ?? 1,
  price: o.price ?? 1.0,
  condition: o.cond ?? "NM",
  language: o.lang ?? "EN",
  foil: o.foil ?? false,
  signed: false,
  altered: false,
  productId: null,
  versionKnown: true,
  languageKnown: true,
  playset: false,
  date: null,
  order: null,
});

const ordered = (index: number, l: OrderLine, guessed = false, found = true): Candidate => ({
  index,
  line: l,
  card: found ? card(l.name, l.setCode.toLowerCase(), l.number) : null,
  token: false,
  basicLand: false,
  guessed,
});

const scan = (id: number, name: string, set: string, number: string, o: { qty?: number; lang?: string; exact?: boolean } = {}): ScannedCard => ({
  id,
  card: card(name, set, number),
  foil: false,
  etched: false,
  condition: "NM",
  language: o.lang ?? "EN",
  quantity: o.qty ?? 1,
  exactPrinting: o.exact ?? true,
  scannedAt: 0,
  uid: `s${id}`,
  updatedAt: 0,
});

const order = [
  ordered(0, line("Sol Ring", "cmm", "400", { qty: 2 })),
  ordered(1, line("Counterspell", "dmr", "45", { price: 1.5 })),
  ordered(2, line("Thran Turbine", "usg", "311", { price: 1.74 })),
  ordered(3, line("Crop Rotation", "ulg", "98", { price: 3.0 })),
  ordered(4, line("Rhystic Study", "pcy", "45", { price: 30.0, cond: "EX", foil: true })),
  ordered(5, line("Brainstorm", "ice", "61", { qty: 3, price: 0.9 })),
];
const scans = [
  scan(1, "Sol Ring", "cmm", "400", { qty: 2 }),
  scan(2, "Thran Turbine", "usg", "311", { lang: "DE" }),
  scan(3, "Crop Rotation", "ddr", "7"),
  scan(4, "Rhystic Study", "pcy", "45"),
  scan(5, "Brainstorm", "ice", "61"),
  scan(6, "Brainstorm", "mmq", "61", { exact: false }),
  scan(7, "Lightning Bolt", "m10", "146", { qty: 2 }),
];

describe("Check an order (the app's PackageCheck)", () => {
  it("tells what arrived", () => {
    const r = checkPackage(order, scans);
    const at = (i: number) => r.lines.find((l) => l.cand.index === i)!;
    expect(lineStatus(at(0))).toBe("RECEIVED");
    expect(lineStatus(at(1))).toBe("MISSING");
    expect(lineStatus(at(2))).toBe("WRONG_LANGUAGE");
    expect(lineStatus(at(3))).toBe("WRONG_PRINTING");
    expect(lineStatus(at(4))).toBe("RECEIVED");
    expect(byHand(at(4))).toEqual(["foil", "EX"]);
    expect(at(5).parts.map((p) => [p.status, p.quantity])).toEqual([
      ["RECEIVED", 1],
      ["PRINTING_UNSURE", 1],
      ["MISSING", 1],
    ]);
    expect(orderedCount(r)).toBe(9);
    expect(countOf(r, "RECEIVED")).toBe(4);
    expect(countOf(r, "MISSING")).toBe(2);
    expect(r.extras.map((x) => [x.scan.id, x.quantity])).toEqual([[7, 2]]);
    expect(problemLines(r).map((l) => l.cand.index)).toEqual([1, 2, 3, 5]);
  });

  it("doesn't let another line take an exact scan", () => {
    const two = [ordered(0, line("Opt", "xln", "65")), ordered(1, line("Opt", "dom", "60"))];
    const r = checkPackage(two, [scan(1, "Opt", "dom", "60"), scan(2, "Opt", "xln", "65")]);
    expect(r.lines.every((l) => lineStatus(l) === "RECEIVED")).toBe(true);
    expect(r.extras).toEqual([]);
  });

  it("takes lines marked as received by hand", () => {
    const r = checkPackage(order, scans, new Set([1, 2]));
    expect(lineStatus(r.lines.find((l) => l.cand.index === 1)!)).toBe("BY_HAND");
    expect(lineStatus(r.lines.find((l) => l.cand.index === 2)!)).toBe("BY_HAND");
    expect(problemLines(r).map((l) => l.cand.index)).toEqual([3, 5]);
    expect(r.extras.map((x) => x.scan.id)).toEqual([7]);
  });

  it("doesn't call cards found by name only wrong", () => {
    const guessed = [ordered(0, line("Opt", "xln", "65"), true), ordered(1, line("Shock", "m21", "159"), false, false)];
    const r = checkPackage(guessed, [scan(1, "Opt", "dom", "60"), scan(2, "Shock", "m20", "160")]);
    expect(r.lines.every((l) => lineStatus(l) === "PRINTING_UNSURE")).toBe(true);
    expect(problemLines(r)).toEqual([]);
  });

  it("compares no language for Cardmarket's monthly file", () => {
    const monthly: OrderLine = { ...line("Cavalier of Dawn", "", ""), setCode: "", setName: "Core 2020", price: 2.55, productId: 378651, versionKnown: false };
    const cand: Candidate = { index: 0, line: monthly, card: card("Cavalier of Dawn", "m20", "10"), token: false, basicLand: false, guessed: false };
    const r = checkPackage([cand], [scan(1, "Cavalier of Dawn", "m20", "10", { lang: "JA" })]);
    expect(lineStatus(r.lines[0])).toBe("RECEIVED");
    const add = additions(r, false);
    expect(add).toHaveLength(1);
    expect(add[0].language).toBe("JA");
    expect(add[0].price).toBeCloseTo(2.55);
  });

  it("knows what goes into the collection", () => {
    const r = checkPackage(order, scans, new Set([1]));
    const adds = additions(r, true);
    const of = (name: string) => adds.filter((a) => a.card.name === name);
    expect(of("Rhystic Study")).toHaveLength(1);
    expect(of("Rhystic Study")[0]).toMatchObject({ finish: "FOIL", condition: "EX", price: 30 });
    expect(of("Counterspell")[0].card.setCode).toBe("dmr");
    expect(of("Thran Turbine")[0].language).toBe("DE");
    expect(of("Crop Rotation")[0].card.setCode).toBe("ddr");
    expect(of("Crop Rotation")[0].price).toBeCloseTo(3.0);
    expect(of("Brainstorm").map((a) => a.card.setCode)).toEqual(["ice", "ice"]);
    expect(of("Brainstorm").reduce((n, a) => n + a.quantity, 0)).toBe(2);
    expect(of("Lightning Bolt")[0].price).toBeNull();
    expect(of("Lightning Bolt")[0].quantity).toBe(2);
    expect(additions(r, false).some((a) => a.card.name === "Lightning Bolt")).toBe(false);
    expect(adds.reduce((n, a) => n + a.quantity, 0)).toBe(10);
    // Every scanned copy that was used leaves the scan list; the extras only when they're added.
    expect(Object.fromEntries(scanUse(r, true))).toEqual({ s1: 2, s2: 1, s3: 1, s4: 1, s5: 1, s6: 1, s7: 2 });
    expect(scanUse(r, false).has("s7")).toBe(false);
  });

  it("writes the report for the shop", () => {
    expect(report(checkPackage(order, scans), "CardTrader order 20260623hxrahx")).toBe(
      [
        "Problems with CardTrader order 20260623hxrahx",
        "",
        "Missing (2):",
        "- 1x Counterspell, Set dmr (DMR) #45, EN, NM, paid 1.50 EUR each",
        "- 1x Brainstorm, Set ice (ICE) #61, EN, NM, paid 0.90 EUR each",
        "",
        "Wrong language (1):",
        "- 1x Thran Turbine, Set usg (USG) #311, EN, NM, paid 1.74 EUR each; received in DE",
        "",
        "Wrong printing (1):",
        "- 1x Crop Rotation, Set ulg (ULG) #98, EN, NM, paid 3.00 EUR each; received Set ddr (DDR) #7",
      ].join("\n"),
    );
    expect(report(checkPackage(order.slice(0, 1), scans.slice(0, 1)), "the order")).toContain("every ordered card was received");
  });

  it("takes the used copies off the scan list", () => {
    const f = { format: 13, data: { scans: scans.map((s) => ({ ...s })) } } as unknown as SyncFile;
    useScans(f, new Map([["s1", 1], ["s2", 1]]), 1000);
    const left = scansOf(f);
    expect(left.find((s) => s.uid === "s1")?.quantity).toBe(1);
    expect(left.some((s) => s.uid === "s2")).toBe(false);
    expect(left).toHaveLength(scans.length - 1);
  });
});
