// Texts, links and sorting shared by the wishlist's list view, picture view and card window.
import type { WishRow } from "@/lib/view";

/** "surgefoil" → "Surge foil", like the app's FoilTypes.name for treatments it doesn't list by name. */
export function foilName(type: string): string {
  if (type === "serialized") return "Serialized";
  if (type === "etched") return "Etched foil";
  const base = type.replace(/foil$/, "");
  return base.charAt(0).toUpperCase() + base.slice(1) + " foil";
}

export function cardTitle(row: WishRow): string {
  return row.card.flavorName ? `${row.card.flavorName} (${row.card.name})` : row.card.name;
}

export function setLine(row: WishRow): string {
  return `${row.card.setCode.toUpperCase()} #${row.card.collectorNumber}`;
}

/** "Normal", "Foil", "Surge foil", "Any printing", "Any printing · Foil". */
export function finishLabel(row: WishRow): string {
  const { foilType, hasNonFoil } = row.card;
  let finish: string | null;
  if (row.foil) finish = foilType ? foilName(foilType) : "Foil";
  else if (foilType && !hasNonFoil) finish = foilName(foilType);
  else finish = row.anyPrinting ? null : "Normal";
  return row.anyPrinting ? ["Any printing", finish].filter(Boolean).join(" · ") : finish!;
}

/**
 * The card on Cardmarket: this printing's page (as the app links it), asking for foil offers for a foil card;
 * a name search for "any printing" cards or when Cardmarket's product number is unknown.
 */
export function cardmarketUrl(row: WishRow): string {
  const id = row.foil ? (row.card.cardmarketFoilId ?? row.card.cardmarketId) : row.card.cardmarketId;
  if (row.anyPrinting || id == null) {
    return "https://www.cardmarket.com/en/Magic/Products/Search?searchString=" + encodeURIComponent(row.card.name);
  }
  return `https://www.cardmarket.com/en/Magic/Products?idProduct=${id}${row.foil ? "&isFoil=Y" : ""}`;
}

/** A CardTrader search for the card's name (an exact printing needs the owner's CardTrader key, kept on the phone). */
export function cardtraderUrl(row: WishRow): string {
  return "https://www.cardtrader.com/en/search?q=" + encodeURIComponent(row.card.name);
}

export type SortKey = "name" | "set" | "finish" | "qty" | "price" | "trend" | "alert" | "ordered";
export interface Sort {
  key: SortKey;
  desc: boolean;
}

export const SORT_LABELS: Record<SortKey, string> = {
  name: "Card",
  set: "Set",
  finish: "Finish",
  qty: "Qty",
  price: "Price",
  trend: "Trend",
  alert: "Alert",
  ordered: "Ordered",
};

/** A new column sort starts high-to-low for numbers, A-Z for text. */
export function firstDirection(key: SortKey): boolean {
  return key === "qty" || key === "price" || key === "trend" || key === "ordered";
}

const byName = (a: WishRow, b: WishRow) => a.card.name.localeCompare(b.card.name, "en", { sensitivity: "base" });

/** Compares two rows for [sort]; rows without a value (no price, no alert…) always go last. Ties go by name. */
export function compareRows(sort: Sort): (a: WishRow, b: WishRow) => number {
  const dir = sort.desc ? -1 : 1;
  const nums = (pick: (r: WishRow) => number | null) => (a: WishRow, b: WishRow) => {
    const x = pick(a);
    const y = pick(b);
    if (x == null || y == null) return x == null && y == null ? byName(a, b) : x == null ? 1 : -1;
    return (x - y) * dir || byName(a, b);
  };
  switch (sort.key) {
    case "set":
      return (a, b) =>
        (a.card.setCode.localeCompare(b.card.setCode) ||
          a.card.collectorNumber.localeCompare(b.card.collectorNumber, "en", { numeric: true })) * dir || byName(a, b);
    case "finish":
      return (a, b) => finishLabel(a).localeCompare(finishLabel(b)) * dir || byName(a, b);
    case "qty":
      return nums((r) => r.quantity);
    case "price":
      return nums((r) => r.unitEur);
    case "trend":
      return nums((r) => r.trendPct);
    case "alert":
      // Alerts are compared by how close the price is to them (reached ones first when going up).
      return nums((r) => (r.alertEur != null && r.alertEur > 0 && r.unitEur != null ? r.unitEur / r.alertEur : null));
    case "ordered":
      return nums((r) => r.orderedAt);
    default:
      return (a, b) => byName(a, b) * dir;
  }
}

export const VIEW_KEY = "mtgtrader.wishlist.view";
export const SORT_KEY = "mtgtrader.wishlist.sort2";
