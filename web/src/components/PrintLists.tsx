"use client";

import { useState } from "react";
import { createPortal } from "react-dom";
import { Dialog } from "@/components/Dialog";
import { formatDate } from "./api";
import { collectionPrintList, deckPrintList, type Money } from "@/lib/printlist";
import type { CollRow } from "@/lib/collview";
import type { DeckRow, Section } from "@/lib/deckshared";

/** Prints the page: only the print-only block shows (see "Printing a list" in globals.css), then the browser's print window opens. */
function printNow() {
  document.body.classList.add("printing-list");
  window.addEventListener("afterprint", () => document.body.classList.remove("printing-list"), { once: true });
  window.print();
}

/** The print-only block goes straight under the page's body, so the print rules can hide everything else. */
function PrintOnly({ children }: { children: React.ReactNode }) {
  return createPortal(<div className="print-only">{children}</div>, document.body);
}

/** The small window before printing the collection: one tick box, then Print. */
export function PrintCollectionDialog({
  rows,
  title,
  filterText,
  pricesNote,
  money,
  onClose,
}: {
  rows: CollRow[];
  title: string;
  /** The search and filters in words ("" when none). */
  filterText: string;
  pricesNote: string;
  money: Money;
  onClose: () => void;
}) {
  const [withPaid, setWithPaid] = useState(false);
  const list = collectionPrintList(rows, money);
  return (
    <Dialog title="Print the collection" onClose={onClose}>
      <div className="stack">
        <p className="small">
          Prints the {rows.length} piles shown now ({list.copies} cards), all pages, as a list without pictures. Choose “Save as PDF” in the
          print window to keep it as a file.
        </p>
        <label className="check small">
          <input type="checkbox" checked={withPaid} onChange={(e) => setWithPaid(e.target.checked)} />
          <span>Include purchase prices</span>
        </label>
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn primary" onClick={printNow}>
            Print
          </button>
        </div>
      </div>
      <PrintOnly>
        <h1>MTG Trader: {title}</h1>
        <p>
          Printed {formatDate(Date.now())}
          {filterText ? ` · ${filterText}` : ""}
        </p>
        <p>{pricesNote}</p>
        <table>
          <thead>
            <tr>
              <th className="num">Copies</th>
              <th>Name</th>
              <th>Set</th>
              <th>No.</th>
              <th>Finish</th>
              <th>Condition</th>
              <th>Language</th>
              <th className="num">Price</th>
              <th className="num">Total</th>
              {withPaid && <th className="num">Paid</th>}
              {withPaid && <th className="num">Paid total</th>}
            </tr>
          </thead>
          <tbody>
            {list.lines.map((l) => (
              <tr key={l.uid}>
                <td className="num">{l.copies}</td>
                <td>{l.name}</td>
                <td>{l.set}</td>
                <td>{l.number}</td>
                <td>{l.finish}</td>
                <td>{l.condition}</td>
                <td>{l.language}</td>
                <td className="num">{l.price}</td>
                <td className="num">{l.total}</td>
                {withPaid && <td className="num">{l.paid}</td>}
                {withPaid && <td className="num">{l.paidTotal}</td>}
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td className="num">{list.copies}</td>
              <td colSpan={7}>Total</td>
              <td className="num">{money(list.valueEur)}</td>
              {withPaid && <td />}
              {withPaid && <td className="num">{money(list.paidEur)}</td>}
            </tr>
          </tfoot>
        </table>
      </PrintOnly>
    </Dialog>
  );
}

/** The small window before printing a deck (it has no choices), with its print-only list. */
export function PrintDeckDialog({
  name,
  commanders,
  details,
  sections,
  priceLine,
  money,
  onClose,
}: {
  name: string;
  commanders: string;
  /** Under the name: the card count, bracket, power… */
  details: string[];
  /** The main deck's sections, commander first (the maybeboard is left out). */
  sections: Section<DeckRow>[];
  priceLine: string;
  money: Money;
  onClose: () => void;
}) {
  const list = deckPrintList(sections, money);
  return (
    <Dialog title="Print the deck" onClose={onClose}>
      <div className="stack">
        <p className="small">
          Prints the main deck ({list.copies} cards, without the maybeboard) as a list without pictures. Choose “Save as PDF” in the print window
          to keep it as a file.
        </p>
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn primary" onClick={printNow}>
            Print
          </button>
        </div>
      </div>
      <PrintOnly>
        <h1>{name}</h1>
        {commanders && <p>{commanders}</p>}
        <p>{[`Printed ${formatDate(Date.now())}`, ...details].join(" · ")}</p>
        <p>{priceLine}</p>
        <table>
          <thead>
            <tr>
              <th className="num">Copies</th>
              <th>Name</th>
              <th>Set</th>
              <th>No.</th>
              <th className="num">Price</th>
              <th className="num">Total</th>
            </tr>
          </thead>
          {list.sections.map((s) => (
            <tbody key={s.title}>
              <tr className="print-section">
                <th colSpan={6}>
                  {s.title} ({s.copies})
                </th>
              </tr>
              {s.lines.map((l) => (
                <tr key={l.uid}>
                  <td className="num">{l.copies}</td>
                  <td>{l.name}</td>
                  <td>{l.set}</td>
                  <td>{l.number}</td>
                  <td className="num">{l.price}</td>
                  <td className="num">{l.total}</td>
                </tr>
              ))}
            </tbody>
          ))}
          <tfoot>
            <tr>
              <td className="num">{list.copies}</td>
              <td colSpan={4}>Total</td>
              <td className="num">{money(list.valueEur)}</td>
            </tr>
          </tfoot>
        </table>
      </PrintOnly>
    </Dialog>
  );
}
