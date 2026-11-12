"use client";

import { Fragment } from "react";
import { columnDef, DEFAULT_COLUMNS, type ColumnDef, type ColumnId } from "@/lib/collcolumns";
import { format, type Currency } from "@/lib/money";
import type { CollRow } from "@/lib/collview";
import { displayName, faceKind, finishName, useOf, type DeckUse, type FaceKind, type SortField, type SortLevel } from "@/lib/colquery";
import { formatDate } from "./api";
import { useHoverCard } from "./HoverCard";
import { ColourPips } from "./Mana";

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
const UP_FIRST: SortField[] = ["NAME", "SET", "NUMBER", "MANA_VALUE", "COLOR", "TYPE", "EDHREC"];

/** Whether a sort shows as going up (▲) or down (▼). */
export function sortsUp(s: SortLevel): boolean {
  return UP_FIRST.includes(s.field) !== s.reversed;
}

/** Columns that the old fixed table hid on narrow screens; they still are while the usual columns are shown. */
const WIDE_ONLY = new Set<ColumnId>(["set", "binder", "total", "change", "added"]);

const FACE_LABEL: Record<FaceKind, string> = { MDFC: "MDFC", TRANSFORM: "Transforming", SPLIT: "Split" };
const RARITY_LABEL: Record<string, string> = { common: "Common", uncommon: "Uncommon", rare: "Rare", mythic: "Mythic", special: "Special", bonus: "Bonus" };

const dash = <span className="muted">–</span>;

/** The collection as a table, like the wishlist's: one row per pile, sortable column titles, a tick box to select. */
export function CollTable({
  rows,
  columns,
  use,
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
  hover,
}: {
  rows: CollRow[];
  /** The columns chosen (Name is always shown after the tick box). */
  columns: ColumnId[];
  /** The decks' use of each card (Decks, In decks, Spare). */
  use: Map<string, DeckUse>;
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
  /** Show the card's picture next to the mouse. */
  hover: boolean;
}) {
  const money = (eur: number) => format(eur * perEuro, currency);
  const signed = (eur: number) => (eur > 0 ? "+" : eur < 0 ? "−" : "") + money(Math.abs(eur));
  const { follow, hide, picture } = useHoverCard(hover);
  const allOn = rows.length > 0 && rows.every((r) => selected.has(r.uid));
  const usual = columns.join() === DEFAULT_COLUMNS.join();
  const cols = columns.filter((id) => id !== "binder" || showBinder).map(columnDef);
  const cellClass = (c: ColumnDef, extra = "") => [c.className, usual && WIDE_ONLY.has(c.id) ? "wide-only" : "", extra].filter(Boolean).join(" ") || undefined;
  const priceOr = (eur: number | null) => (eur != null ? money(eur) : dash);

  function cell(c: ColumnDef, r: CollRow) {
    const u = useOf(use, r);
    switch (c.id) {
      case "qty":
        return <td className={cellClass(c)}>{r.quantity}</td>;
      case "set":
        return (
          <td className={cellClass(c, "muted nowrap set-cell")} title={`${r.card.setName} (${r.card.setCode.toUpperCase()})`}>
            {r.card.setName}
          </td>
        );
      case "number":
        return <td className={cellClass(c, "muted")}>{r.card.collectorNumber}</td>;
      case "rarity":
        return <td className={cellClass(c, "nowrap")}>{RARITY_LABEL[r.card.rarity] ?? r.card.rarity}</td>;
      case "type":
        return <td className={cellClass(c, "small type-cell")}>{r.info?.t || dash}</td>;
      case "mv":
        return <td className={cellClass(c)}>{r.info ? r.info.v : dash}</td>;
      case "colors":
        return <td className={cellClass(c, "nowrap")}>{r.info ? <ColourPips identity={r.info.c} /> : dash}</td>;
      case "identity":
        return <td className={cellClass(c, "nowrap")}>{r.info ? <ColourPips identity={r.info.i} /> : dash}</td>;
      case "faces": {
        const k = faceKind(r.info?.l);
        return <td className={cellClass(c, "nowrap")}>{k ? FACE_LABEL[k] : dash}</td>;
      }
      case "edhrec":
        return <td className={cellClass(c)}>{r.info?.r != null ? r.info.r.toLocaleString("en") : dash}</td>;
      case "gc":
        return <td className={cellClass(c)}>{r.info?.g ? "✓" : ""}</td>;
      case "cond":
        return <td className={cellClass(c)}>{r.condition}</td>;
      case "lang":
        return <td className={cellClass(c)}>{r.language}</td>;
      case "finish":
        return <td className={cellClass(c, "nowrap")}>{finishName(r)}</td>;
      case "marks":
        return <td className={cellClass(c, "nowrap")}>{[r.signed ? "Signed" : null, r.altered ? "Altered" : null].filter(Boolean).join(", ")}</td>;
      case "binder":
        return <td className={cellClass(c, "binder-name nowrap")}>{binderName(r.binder)}</td>;
      case "added":
        return <td className={cellClass(c, "nowrap muted small")}>{formatDate(r.addedAt)}</td>;
      case "notes":
        return <td className={cellClass(c, "small muted notes-cell")}>{r.notes ?? ""}</td>;
      case "price":
        return (
          <td className={cellClass(c, "nowrap")}>
            {r.unitEur != null ? (
              <>
                <div>
                  {r.approx ? "≈" : ""}
                  {money(r.unitEur)}
                </div>
                {r.condNote && <div className="muted small">{r.condNote}</div>}
              </>
            ) : (
              dash
            )}
          </td>
        );
      case "total":
        return <td className={cellClass(c, "nowrap")}>{r.unitEur != null ? money(r.unitEur * r.quantity) : dash}</td>;
      case "change":
        return (
          <td className={cellClass(c, "nowrap")}>
            <Change pct={r.changePct} />
          </td>
        );
      case "changeMoney":
        return (
          <td className={cellClass(c, "nowrap")}>
            {r.changeEur == null ? dash : <span className={r.changeEur > 0 ? "up" : r.changeEur < 0 ? "down" : "muted"}>{signed(r.changeEur)}</span>}
          </td>
        );
      case "paid":
        return <td className={cellClass(c, "nowrap")}>{priceOr(r.purchasePrice)}</td>;
      case "profit": {
        if (r.purchasePrice == null || r.unitEur == null) return <td className={cellClass(c)}>{dash}</td>;
        const p = (r.unitEur - r.purchasePrice) * r.quantity;
        return (
          <td className={cellClass(c, "nowrap")}>
            <span className={p > 0 ? "up" : p < 0 ? "down" : "muted"}>{signed(p)}</span>
          </td>
        );
      }
      case "cardmarket":
        return <td className={cellClass(c, "nowrap")}>{priceOr(r.srcEur.cardmarket)}</td>;
      case "tcgplayer":
        return <td className={cellClass(c, "nowrap")}>{priceOr(r.srcEur.tcgplayer)}</td>;
      case "cardkingdom":
        return <td className={cellClass(c, "nowrap")}>{priceOr(r.srcEur.cardkingdom)}</td>;
      case "ckPays":
        return <td className={cellClass(c, "nowrap")}>{priceOr(r.srcEur.ckPays)}</td>;
      case "decks":
        return (
          <td className={cellClass(c)} title={u.decks.length ? u.decks.join("\n") : "In no deck"}>
            {u.decks.length || <span className="muted">0</span>}
          </td>
        );
      case "inDecks":
        return <td className={cellClass(c)}>{u.copies || <span className="muted">0</span>}</td>;
      case "spare": {
        const n = u.owned - u.copies;
        return (
          <td className={cellClass(c)} title={`${u.owned} owned, ${u.copies} in decks`}>
            <span className={n < 0 ? "down" : n === 0 ? "muted" : undefined}>{n}</span>
          </td>
        );
      }
    }
  }

  const heads: { id: string; label: string; hint: string; field?: SortField; cls?: string }[] = [
    { id: "name", label: "Name", hint: "", field: "NAME" },
    ...cols.map((c) => ({ id: c.id, label: c.label, hint: c.hint, field: c.field, cls: cellClass(c) })),
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
            {heads.map((c) => (
              <th
                key={c.id}
                className={c.cls}
                title={c.hint || undefined}
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
            <tr
              key={r.uid}
              className={["wish-row clickable", selected.has(r.uid) ? "picked" : ""].join(" ")}
              onClick={() => {
                hide();
                if (!disabled) onOpen(r);
              }}
              onMouseEnter={follow(r.card.imageUrl)}
              onMouseMove={follow(r.card.imageUrl)}
              onMouseLeave={hide}
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
                {!columns.includes("number") && <span className="muted small">#{r.card.collectorNumber}</span>}
                {!columns.includes("finish") && (r.finish !== "NONFOIL" || r.card.foilType) && <span className="tag foil">{finishName(r)}</span>}
                {!columns.includes("marks") && r.signed && <span className="tag">Signed</span>}
                {!columns.includes("marks") && r.altered && <span className="tag">Altered</span>}
                {!columns.includes("notes") && r.notes && <div className="muted small sub">{r.notes}</div>}
              </td>
              {cols.map((c) => (
                <Fragment key={c.id}>{cell(c, r)}</Fragment>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {picture}
    </div>
  );
}
