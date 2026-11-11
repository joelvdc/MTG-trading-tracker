// What the "Print" buttons put on paper: plain text lines and totals worked out from the rows the pages already hold.
// No pictures; the components in components/PrintLists.tsx draw them. Pure functions, so they can be tested.
import type { CollRow } from "./collview";
import { displayName } from "./colquery";
import type { DeckRow, Section } from "./deckshared";
import type { Finish } from "./pricing";

/** Turns an amount in euros into text in the page's currency. */
export type Money = (eur: number) => string;

const FINISHES: Record<Finish, string> = { NONFOIL: "Non-foil", FOIL: "Foil", ETCHED: "Etched" };

export interface CollPrintLine {
  uid: string;
  copies: number;
  name: string;
  set: string;
  number: string;
  finish: string;
  condition: string;
  language: string;
  /** One copy and all copies; "–" without a price. */
  price: string;
  total: string;
  /** Purchase price of one copy and of all copies; "–" when none is noted. */
  paid: string;
  paidTotal: string;
}

export interface CollPrintList {
  lines: CollPrintLine[];
  copies: number;
  /** In euros; rows without a price count 0. */
  valueEur: number;
  paidEur: number;
}

/** The collection's rows as print lines, with the totals (copies, value, and what was paid). */
export function collectionPrintList(rows: CollRow[], money: Money): CollPrintList {
  let copies = 0;
  let valueEur = 0;
  let paidEur = 0;
  const lines = rows.map((r) => {
    copies += r.quantity;
    valueEur += (r.unitEur ?? 0) * r.quantity;
    paidEur += (r.purchasePrice ?? 0) * r.quantity;
    return {
      uid: r.uid,
      copies: r.quantity,
      name: displayName(r.card),
      set: r.card.setCode.toUpperCase(),
      number: r.card.collectorNumber,
      finish: FINISHES[r.finish] ?? "Foil",
      condition: r.condition,
      language: r.language,
      price: r.unitEur != null ? (r.approx ? "≈" : "") + money(r.unitEur) : "–",
      total: r.unitEur != null ? money(r.unitEur * r.quantity) : "–",
      paid: r.purchasePrice != null ? money(r.purchasePrice) : "–",
      paidTotal: r.purchasePrice != null ? money(r.purchasePrice * r.quantity) : "–",
    };
  });
  return { lines, copies, valueEur, paidEur };
}

export interface DeckPrintLine {
  uid: string;
  copies: number;
  name: string;
  set: string;
  number: string;
  price: string;
  total: string;
}

export interface DeckPrintSection {
  title: string;
  copies: number;
  lines: DeckPrintLine[];
}

export interface DeckPrintList {
  sections: DeckPrintSection[];
  copies: number;
  valueEur: number;
}

/** The deck's sections (main deck only: the caller leaves the maybeboard out) as print lines, with the totals. */
export function deckPrintList(sections: Section<DeckRow>[], money: Money): DeckPrintList {
  let copies = 0;
  let valueEur = 0;
  const out = sections.map((s) => {
    let n = 0;
    const lines = s.rows.map((r) => {
      n += r.quantity;
      valueEur += (r.unitEur ?? 0) * r.quantity;
      return {
        uid: r.lineUid,
        copies: r.quantity,
        name: r.name,
        set: r.card.setCode.toUpperCase(),
        number: r.card.collectorNumber,
        price: r.unitEur != null ? money(r.unitEur) : "–",
        total: r.unitEur != null ? money(r.unitEur * r.quantity) : "–",
      };
    });
    copies += n;
    return { title: s.title, copies: n, lines };
  });
  return { sections: out, copies, valueEur };
}
