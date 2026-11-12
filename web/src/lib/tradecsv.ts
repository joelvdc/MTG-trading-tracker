// The trades as a CSV file, the same columns as the app's exportTradesCsv (MtgRepository): one row per card line.
import { csvRow } from "./csvexport";
import { finishName } from "./colquery";
import type { TradesView } from "./tradeview";

const two = (n: number) => String(n).padStart(2, "0");

/** "2026-11-13 21:05:09", in the browser's time zone like the phone's. */
export function tradeTime(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())} ${two(d.getHours())}:${two(d.getMinutes())}:${two(d.getSeconds())}`;
}

export function tradesCsv(view: TradesView): string {
  const head = [
    "Trade started",
    "Partner",
    "Applied to collection",
    "Side",
    "Name",
    "Set code",
    "Collector number",
    "Finish",
    "Condition",
    "Language",
    "Quantity",
    `Unit price EUR (${view.priceTypeShort})`,
    "Custom price",
    "Line total EUR",
    "Scryfall ID",
  ];
  const rows = [csvRow(head)];
  for (const t of view.trades) {
    for (const l of t.lines) {
      rows.push(
        csvRow([
          tradeTime(t.createdAt),
          t.partner,
          t.applied ? "yes" : "no",
          l.side === "GET" ? "received" : "given",
          l.card.name,
          l.card.setCode.toUpperCase(),
          l.card.collectorNumber,
          finishName(l).toLowerCase(),
          l.condition,
          l.language,
          String(l.quantity),
          l.unitEur,
          l.customEur,
          (l.unitEur ?? 0) * l.quantity,
          l.card.scryfallId,
        ]),
      );
    }
  }
  return rows.join("\n") + "\n";
}
