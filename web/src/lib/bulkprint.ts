// Picking one printing for each selected deck line (the deck page's "Printing" bulk action): pure code, also used in
// the browser. The printings come from /api/cards/prints (see ChangePrintingDialog), newest first is not assumed.

export type PrintChoice = "CHEAPEST" | "NEWEST" | "OLDEST" | "OWNED";

/** A printing as the printings lookup answers it. */
export interface BulkPrinting {
  card: { scryfallId: string; hasNonFoil?: boolean; hasFoil?: boolean; hasEtched?: boolean };
  /** Scryfall's release date, as an ISO date ("2021-06-18"); compared as text. */
  released: string;
  /** Cardmarket's trend price for the non-foil and the foil printing, or null when unknown. */
  eur: number | null;
  eurFoil: number | null;
}

/** Whether a printing comes in the line's finish; a missing flag counts as available. */
function hasFinish(p: BulkPrinting, foil: boolean): boolean {
  return foil ? (p.card.hasFoil ?? true) || (p.card.hasEtched ?? true) : (p.card.hasNonFoil ?? true);
}

/**
 * The printing to switch a line to, or null when nothing fits, or when the best one is the line's printing already.
 * Only printings in the line's finish count. CHEAPEST takes the lowest price for that finish (ones without a price are
 * skipped); NEWEST and OLDEST go by the release date; OWNED takes the newest printing you own. Ties keep the first
 * printing the list gives.
 */
export function pickPrinting(prints: readonly BulkPrinting[], choice: PrintChoice, foil: boolean, owned: Set<string>, currentId: string): string | null {
  const fits = prints.filter((p) => hasFinish(p, foil));
  let pick: BulkPrinting | null = null;
  if (choice === "CHEAPEST") {
    let best: number | null = null;
    for (const p of fits) {
      const price = foil ? p.eurFoil : p.eur;
      if (price === null) continue;
      if (best === null || price < best) {
        best = price;
        pick = p;
      }
    }
  } else if (choice === "OWNED") {
    for (const p of fits) if (owned.has(p.card.scryfallId) && (!pick || p.released > pick.released)) pick = p;
  } else {
    for (const p of fits) {
      if (!pick || (choice === "NEWEST" ? p.released > pick.released : p.released < pick.released)) pick = p;
    }
  }
  if (!pick || pick.card.scryfallId === currentId) return null;
  return pick.card.scryfallId;
}
