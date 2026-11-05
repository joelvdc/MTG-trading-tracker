"use client";

import { useState, type MouseEvent } from "react";
import { format, type Currency } from "@/lib/money";
import type { CollRow } from "@/lib/collview";
import { displayName, finishName, type SortField, type SortLevel } from "@/lib/colquery";
import { formatDate } from "./api";

/** One line under a card's name in the picture view: set, finish, marks, condition, language. */
export function rowTags(r: CollRow): string[] {
  return [
    `${r.card.setCode.toUpperCase()} #${r.card.collectorNumber}`,
    r.finish !== "NONFOIL" || r.card.foilType ? finishName(r) : null,
    r.signed ? "Signed" : null,
    r.altered ? "Altered" : null,
    r.condition,
    r.language !== "EN" ? r.language : null,
  ].filter((x): x is string => !!x);
}

export function Change({ pct }: { pct: number | null }) {
  if (pct == null) return <span className="muted">–</span>;
  if (pct === 0) return <span className="muted">0.0%</span>;
  return (
    <span className={pct > 0 ? "up" : "down"}>
      {pct > 0 ? "▲" : "▼"} {Math.max(Math.abs(pct), 0.1).toFixed(1)}%
    </span>
  );
}

/** Fields whose usual order goes up (A to Z, low to high); the others start with the biggest. */
const UP_FIRST: SortField[] = ["NAME", "SET", "NUMBER", "MANA_VALUE", "COLOR", "TYPE"];

/** Whether a sort shows as going up (▲) or down (▼). */
export function sortsUp(s: SortLevel): boolean {
  return UP_FIRST.includes(s.field) !== s.reversed;
}

type Col = { label: string; field?: SortField; className?: string };

/** The collection as a table, like the wishlist's: one row per pile, sortable column titles, a tick box to select. */
export function CollTable({
  rows,
  currency,
  perEuro,
  binderName,
  showBinder,
  selected,
  disabled,
  sort,
  onSort,
  onToggle,
  onToggleAll,
  onOpen,
}: {
  rows: CollRow[];
  currency: Currency;
  perEuro: number;
  binderName: (uid: string | null) => string;
  showBinder: boolean;
  selected: Set<string>;
  disabled: boolean;
  sort: SortLevel;
  onSort: (field: SortField) => void;
  onToggle: (uid: string) => void;
  onToggleAll: (on: boolean) => void;
  onOpen: (row: CollRow) => void;
}) {
  const money = (eur: number) => format(eur * perEuro, currency);
  const [hover, setHover] = useState<{ url: string; x: number; y: number } | null>(null);
  /** Shows the card's picture next to the mouse (only with a mouse; on touch screens a tap opens the card). */
  const follow = (r: CollRow) => (e: MouseEvent) => {
    if (!r.card.imageUrl || !window.matchMedia("(hover: hover)").matches) return;
    const w = 240;
    const h = (w * 680) / 488;
    const x = e.clientX + 24 + w > window.innerWidth ? e.clientX - 24 - w : e.clientX + 24;
    const y = Math.max(8, Math.min(e.clientY - h / 2, window.innerHeight - h - 8));
    setHover({ url: r.card.imageUrl, x, y });
  };
  const allOn = rows.length > 0 && rows.every((r) => selected.has(r.uid));
  const cols: Col[] = [
    { label: "Qty", field: "QUANTITY", className: "num" },
    { label: "Name", field: "NAME" },
    { label: "Set", field: "SET", className: "wide-only" },
    { label: "Cond.", className: "center" },
    { label: "Lang.", className: "center" },
    ...(showBinder ? [{ label: "Binder", className: "wide-only" }] : []),
    { label: "Price", field: "VALUE" as SortField, className: "num" },
    { label: "Total", className: "num wide-only" },
    { label: "Change", field: "CHANGE_PCT" as SortField, className: "num wide-only" },
    { label: "Added", field: "RECENT" as SortField, className: "num wide-only" },
  ];
  return (
    <div className="table-wrap">
      <table className="wish-table coll-table">
        <thead>
          <tr>
            <th className="center">
              <input
                type="checkbox"
                className="tick"
                aria-label="Select all shown"
                checked={allOn}
                disabled={disabled || rows.length === 0}
                onChange={(e) => onToggleAll(e.target.checked)}
              />
            </th>
            {cols.map((c) => (
              <th
                key={c.label}
                className={c.className}
                aria-sort={c.field && sort.field === c.field ? (sortsUp(sort) ? "ascending" : "descending") : undefined}
              >
                {c.field ? (
                  <button className="th-sort" onClick={() => onSort(c.field!)}>
                    {c.label}
                    {sort.field === c.field && <span aria-hidden="true">{sortsUp(sort) ? " ▲" : " ▼"}</span>}
                  </button>
                ) : (
                  <span className="th-plain">{c.label}</span>
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.uid} className={["wish-row clickable", selected.has(r.uid) ? "picked" : ""].join(" ")} onClick={() => {
                setHover(null);
                if (!disabled) onOpen(r);
              }}
              onMouseEnter={follow(r)}
              onMouseMove={follow(r)}
              onMouseLeave={() => setHover(null)}
            >
              <td className="center" onClick={(e) => e.stopPropagation()}>
                <input
                  type="checkbox"
                  className="tick"
                  aria-label={`Select ${r.card.name}`}
                  checked={selected.has(r.uid)}
                  disabled={disabled}
                  onChange={() => onToggle(r.uid)}
                />
              </td>
              <td className="num">{r.quantity}</td>
              <td className="card-cell">
                <button
                  className="row-title linkish"
                  disabled={disabled}
                  onClick={(e) => {
                    e.stopPropagation();
                    onOpen(r);
                  }}
                >
                  {displayName(r.card)}
                </button>{" "}
                <span className="muted small">#{r.card.collectorNumber}</span>
                {(r.finish !== "NONFOIL" || r.card.foilType) && <span className="tag foil">{finishName(r)}</span>}
                {r.signed && <span className="tag">Signed</span>}
                {r.altered && <span className="tag">Altered</span>}
                {r.notes && <div className="muted small sub">{r.notes}</div>}
              </td>
              <td className="wide-only muted nowrap set-cell" title={`${r.card.setName} (${r.card.setCode.toUpperCase()})`}>
                {r.card.setName}
              </td>
              <td className="center">{r.condition}</td>
              <td className="center">{r.language}</td>
              {showBinder && <td className="wide-only binder-name nowrap">{binderName(r.binder)}</td>}
              <td className="num nowrap">
                {r.unitEur != null ? (
                  <>
                    <div>
                      {r.approx ? "≈" : ""}
                      {money(r.unitEur)}
                    </div>
                    {r.condNote && <div className="muted small">{r.condNote}</div>}
                  </>
                ) : (
                  <span className="muted">–</span>
                )}
              </td>
              <td className="num nowrap wide-only">{r.unitEur != null ? money(r.unitEur * r.quantity) : <span className="muted">–</span>}</td>
              <td className="num nowrap wide-only">
                <Change pct={r.changePct} />
              </td>
              <td className="num nowrap wide-only muted small">{formatDate(r.addedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {hover && (
        // eslint-disable-next-line @next/next/no-img-element
        <img className="hover-card" src={hover.url} alt="" style={{ left: hover.x, top: hover.y }} />
      )}
    </div>
  );
}
