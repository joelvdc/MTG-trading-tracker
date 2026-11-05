"use client";

import { format, type Currency } from "@/lib/money";
import type { WishRow } from "@/lib/view";
import { formatDate } from "./api";
import { cardTitle, finishLabel, setLine, SORT_LABELS, type Sort, type SortKey } from "./wishText";

/** Columns that hide on narrow screens. */
const WIDE_ONLY: SortKey[] = ["set", "trend", "alert"];
const COLUMNS: SortKey[] = ["name", "set", "finish", "qty", "price", "trend", "alert", "ordered"];
const NUMERIC: SortKey[] = ["qty", "price", "trend", "alert"];

export interface Section {
  title: string;
  rows: WishRow[];
}

/** The wishlist as a table without pictures: one row per card, the sections as group rows. */
export function WishTable({
  sections,
  currency,
  perEuro,
  disabled,
  sort,
  onSort,
  onOpen,
  onToggleOrdered,
}: {
  sections: Section[];
  currency: Currency;
  perEuro: number;
  disabled: boolean;
  sort: Sort;
  onSort: (key: SortKey) => void;
  onOpen: (row: WishRow) => void;
  onToggleOrdered: (row: WishRow, ordered: boolean) => void;
}) {
  const money = (eur: number) => format(eur * perEuro, currency);
  const colClass = (key: SortKey) =>
    [WIDE_ONLY.includes(key) ? "wide-only" : "", NUMERIC.includes(key) ? "num" : "", key === "ordered" ? "center" : ""]
      .filter(Boolean)
      .join(" ") || undefined;

  return (
    <div className="table-wrap">
      <table className="wish-table">
        <thead>
          <tr>
            {COLUMNS.map((key) => (
              <th
                key={key}
                className={colClass(key)}
                aria-sort={sort.key === key ? (sort.desc ? "descending" : "ascending") : undefined}
              >
                <button className="th-sort" onClick={() => onSort(key)}>
                  {SORT_LABELS[key]}
                  {sort.key === key && <span aria-hidden="true">{sort.desc ? " ▼" : " ▲"}</span>}
                </button>
              </th>
            ))}
          </tr>
        </thead>
        {sections
          .filter((s) => s.rows.length > 0)
          .map((s) => (
            <tbody key={s.title}>
              <tr className="group">
                <th colSpan={COLUMNS.length} scope="colgroup">
                  {s.title} <span className="muted">({s.rows.reduce((n, r) => n + r.quantity, 0)})</span>
                </th>
              </tr>
              {s.rows.map((r) => {
                const received = r.receivedAt != null;
                const open = () => !received && !disabled && onOpen(r);
                const sub = [
                  r.gotSince > 0 ? `Got ${r.gotSince} since adding` : null,
                  received ? `Received ${formatDate(r.receivedAt!)}${r.receivedFrom ? ` · ${r.receivedFrom}` : ""}` : null,
                  r.notes,
                ].filter(Boolean);
                return (
                  <tr
                    key={r.uid}
                    className={["wish-row", received ? "received" : "clickable", r.alertHit ? "hit" : ""].filter(Boolean).join(" ")}
                    onClick={open}
                  >
                    <td className="card-cell">
                      {received ? (
                        <span className="row-title">{cardTitle(r)}</span>
                      ) : (
                        <button
                          className="row-title linkish"
                          disabled={disabled}
                          onClick={(e) => {
                            e.stopPropagation();
                            open();
                          }}
                        >
                          {cardTitle(r)}
                        </button>
                      )}
                      {sub.length > 0 && <div className="muted small sub">{sub.join(" · ")}</div>}
                    </td>
                    <td className="wide-only muted nowrap">{setLine(r)}</td>
                    <td className="nowrap finish-cell">
                      <span className={r.foil || r.card.foilType ? "foil-text" : r.anyPrinting ? "any-text" : "muted"}>
                        {finishLabel(r)}
                      </span>
                    </td>
                    <td className="num">{r.quantity}</td>
                    <td className="num nowrap">
                      {r.unitEur != null ? (
                        <>
                          <div className={r.alertHit ? "gold" : undefined}>{money(r.unitEur)}</div>
                          {r.quantity > 1 && <div className="muted small">{money(r.unitEur * r.quantity)} total</div>}
                        </>
                      ) : (
                        <span className="muted">–</span>
                      )}
                    </td>
                    <td className="wide-only num nowrap">
                      {r.trendPct != null ? (
                        <span className={r.trendPct >= 0 ? "up" : "down"}>
                          {r.trendPct >= 0 ? "▲" : "▼"} {Math.abs(r.trendPct).toFixed(1)}%
                        </span>
                      ) : (
                        <span className="muted">–</span>
                      )}
                    </td>
                    <td className={"wide-only num nowrap " + (r.alertHit ? "gold" : "muted")}>
                      {r.alertBelow != null ? `≤ ${format(r.alertBelow, (r.alertCurrency as Currency | null) ?? "EUR")}` : "–"}
                    </td>
                    <td className="center" onClick={(e) => e.stopPropagation()}>
                      {received ? (
                        <span className="muted small">Received</span>
                      ) : (
                        <input
                          type="checkbox"
                          className="tick"
                          aria-label={`Ordered: ${r.card.name}`}
                          checked={r.orderedAt != null}
                          disabled={disabled}
                          onChange={(e) => onToggleOrdered(r, e.target.checked)}
                        />
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          ))}
      </table>
    </div>
  );
}
