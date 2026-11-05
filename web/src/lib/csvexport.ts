// The collection as a CSV file, in the app's two formats (MtgRepository.exportCollectionCsv and ArchidektExport.kt).
import type { CollBinder, CollRow } from "./collview";
import type { Finish } from "./pricing";

function escape(s: string): string {
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** The app's Csv.row: numbers (money) with two decimals, empty for null; pass whole numbers as text. */
export function csvRow(fields: (string | number | null)[]): string {
  return fields.map((f) => (f == null ? "" : typeof f === "number" ? f.toFixed(2) : escape(f))).join(",");
}

export function manaBoxFinish(f: Finish): string {
  return f === "NONFOIL" ? "normal" : f === "FOIL" ? "foil" : "etched";
}

export function manaBoxCondition(code: string): string {
  const m: Record<string, string> = { MT: "mint", NM: "near_mint", EX: "excellent", GD: "good", LP: "light_played", PL: "played", PO: "poor" };
  return m[code] ?? "near_mint";
}

const PRICE_SHORT: Record<string, string> = { trend: "Trend", avg: "Average", avg30: "30-day avg", avg7: "7-day avg", avg1: "1-day avg", low: "Low" };

/** The app's full file: ManaBox's column names plus the app's own (binders, prices, notes, marks). */
export function fullCsv(rows: CollRow[], binders: CollBinder[], priceType: string): string {
  const names = new Map(binders.map((b) => [b.uid, b.name]));
  const lines = [
    csvRow([
      "Binder Name", "Binder Type", "Name", "Set code", "Set name", "Collector number", "Foil", "Rarity", "Quantity", "Scryfall ID",
      "Condition", "Language", `Price EUR (${PRICE_SHORT[priceType] ?? "Trend"})`, "Purchase price", "Purchase price currency", "Notes",
      "Signed", "Altered", "Purchase price as typed", "Typed currency",
    ]),
  ];
  for (const r of rows) {
    const binder = r.binder ? (names.get(r.binder) ?? null) : null;
    const typed = r.purchasePrice != null && r.paidTyped != null && !!r.paidCurrency && r.paidCurrency !== "EUR";
    lines.push(
      csvRow([
        binder ?? "", binder != null ? "binder" : "", r.card.name, r.card.setCode.toUpperCase(), r.card.setName, r.card.collectorNumber,
        manaBoxFinish(r.finish), r.card.rarity, String(r.quantity), r.card.scryfallId, manaBoxCondition(r.condition), r.language.toLowerCase(),
        r.cmEur, r.purchasePrice ?? "", r.purchasePrice != null ? "EUR" : "", r.notes ?? "", String(r.signed), String(r.altered),
        typed ? r.paidTyped : "", typed ? r.paidCurrency : "",
      ]),
    );
  }
  return lines.join("\n") + "\n";
}

/** For Archidekt's importer, preset "ManaBox – Single folder": 18 columns, binders merged. */
export const ARCHIDEKT_HEADER = [
  "Name", "Set code", "Set name", "Collector number", "Foil", "Rarity", "Quantity", "ManaBox ID", "Scryfall ID",
  "Purchase price", "Misprint", "Altered", "Signed", "Condition", "Language", "Purchase price currency", "Binder Name", "Binder Type",
];

export function archidektCsv(rows: CollRow[]): string {
  const groups = new Map<string, { r: CollRow; q: number }>();
  for (const r of rows) {
    const k = [r.card.scryfallId, r.finish, r.condition, r.language, r.signed, r.altered].join("|");
    const g = groups.get(k);
    if (g) g.q += r.quantity;
    else groups.set(k, { r, q: r.quantity });
  }
  const list = [...groups.values()].sort(
    (a, b) =>
      a.r.card.name.toLowerCase().localeCompare(b.r.card.name.toLowerCase()) ||
      (a.r.card.setCode < b.r.card.setCode ? -1 : a.r.card.setCode > b.r.card.setCode ? 1 : 0) ||
      (a.r.card.collectorNumber < b.r.card.collectorNumber ? -1 : a.r.card.collectorNumber > b.r.card.collectorNumber ? 1 : 0),
  );
  const out = [ARCHIDEKT_HEADER.map(escape).join(",")];
  for (const { r, q } of list) {
    out.push(
      [
        escape(r.card.name), escape(r.card.setCode.toUpperCase()), escape(r.card.setName), escape(r.card.collectorNumber), manaBoxFinish(r.finish),
        escape(r.card.rarity), String(q), "", r.card.scryfallId, "", "false", String(r.altered), String(r.signed), manaBoxCondition(r.condition),
        r.language.toLowerCase(), "", "", "",
      ].join(","),
    );
  }
  return out.join("\n") + "\n";
}
