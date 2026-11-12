"use client";

import { Fragment, useEffect, useRef } from "react";
import { columnDef, DEFAULT_COLUMNS, type ColumnDef, type ColumnId } from "@/lib/collcolumns";
import { format, type Currency } from "@/lib/money";
import { range, same, type CollGroup } from "@/lib/collgroup";
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

/** A group's tick box: ticked when all its piles are, half-ticked when some are. */
function GroupTick({ on, some, disabled, label, onChange }: { on: boolean; some: boolean; disabled: boolean; label: string; onChange: () => void }) {
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.indeterminate = some && !on;
  }, [some, on]);
  return <input ref={ref} type="checkbox" className="tick" aria-label={label} checked={on} disabled={disabled} onChange={onChange} />;
}

/**
 * The collection as a table, like the wishlist's: one row per pile, sortable column titles, a tick box to select.
 * With [groups] (Group printings), one line per card name; a line of several piles opens up to show them.
 */
export function CollTable({
  rows,
  groups,
  expanded,
  onExpand,
  onToggleMany,
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
  /** Every pile shown on this page (also those inside closed groups). */
  rows: CollRow[];
  /** Group printings: the page's lines, by card name. */
  groups?: CollGroup[];
  /** The groups opened up, by key. */
  expanded?: Set<string>;
  onExpand?: (key: string) => void;
  /** Tick or untick several piles at once (a group's tick box). */
  onToggleMany?: (uids: string[], on: boolean) => void;
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

  const mixed = <span className="muted">Mixed</span>;
  const priceRange = (values: (number | null)[]) => {
    const r = range(values);
    if (!r) return dash;
    return r.min === r.max ? money(r.min) : `${money(r.min)} – ${money(r.max)}`;
  };
  const marksOf = (r: CollRow) => [r.signed ? "Signed" : null, r.altered ? "Altered" : null].filter(Boolean).join(", ");

  /** A group line's cell: one value when every pile agrees, otherwise a summary; card facts as for one pile. */
  function groupCell(c: ColumnDef, g: CollGroup) {
    const rs = g.rows;
    switch (c.id) {
      case "set": {
        const one = g.printings === 1;
        return (
          <td className={cellClass(c, "muted nowrap set-cell")} title={one ? `${rs[0].card.setName} (${rs[0].card.setCode.toUpperCase()})` : undefined}>
            {one ? rs[0].card.setName : `${g.printings} printings`}
          </td>
        );
      }
      case "number":
        return <td className={cellClass(c, "muted")}>{g.printings === 1 ? rs[0].card.collectorNumber : dash}</td>;
      case "rarity": {
        const r = same(rs, (x) => x.card.rarity);
        return <td className={cellClass(c, "nowrap")}>{r === undefined ? mixed : (RARITY_LABEL[r] ?? r)}</td>;
      }
      case "cond":
        return <td className={cellClass(c)}>{same(rs, (x) => x.condition) ?? mixed}</td>;
      case "lang":
        return <td className={cellClass(c)}>{same(rs, (x) => x.language) ?? mixed}</td>;
      case "finish":
        return <td className={cellClass(c, "nowrap")}>{same(rs, finishName) ?? mixed}</td>;
      case "marks":
        return <td className={cellClass(c, "nowrap")}>{same(rs, marksOf) ?? mixed}</td>;
      case "binder": {
        const b = same(rs, (x) => x.binder);
        const n = new Set(rs.map((x) => x.binder)).size;
        return <td className={cellClass(c, "binder-name nowrap")}>{b !== undefined ? binderName(b) : <span className="muted">{n} binders</span>}</td>;
      }
      case "notes":
        return <td className={cellClass(c, "small muted notes-cell")}>{same(rs, (x) => x.notes) ?? ""}</td>;
      case "price":
        return <td className={cellClass(c, "nowrap")}>{priceRange(rs.map((x) => x.unitEur))}</td>;
      case "total":
        return <td className={cellClass(c, "nowrap")}>{g.maxEur != null ? money(g.totalEur) : dash}</td>;
      case "paid":
        return <td className={cellClass(c, "nowrap")}>{priceRange(rs.map((x) => x.purchasePrice))}</td>;
      case "profit":
        return (
          <td className={cellClass(c, "nowrap")}>
            {g.profitEur == null ? dash : <span className={g.profitEur > 0 ? "up" : g.profitEur < 0 ? "down" : "muted"}>{signed(g.profitEur)}</span>}
          </td>
        );
      case "cardmarket":
      case "tcgplayer":
      case "cardkingdom":
      case "ckPays": {
        const src = c.id;
        return <td className={cellClass(c, "nowrap")}>{priceRange(rs.map((x) => x.srcEur[src]))}</td>;
      }
      default:
        // Qty, Added and Change come from the stand-in pile; the card's facts and its decks are the same for every pile.
        return cell(c, g.sum);
    }
  }

  function pileRow(r: CollRow, sub = false) {
    return (
      <tr
        key={r.uid}
        className={["wish-row clickable", sub ? "sub-pile" : "", selected.has(r.uid) ? "picked" : ""].filter(Boolean).join(" ")}
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
          {/* Inside an opened group the piles share the name, so say which printing each is when no column does. */}
          {sub && !columns.includes("set") && <span className="muted small">{r.card.setCode.toUpperCase()} </span>}
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
    );
  }

  /** One group: a single pile shows as usual; several show as one line, with the piles under it when opened. */
  function groupLines(g: CollGroup) {
    if (g.rows.length === 1) return pileRow(g.rows[0]);
    const open = !!expanded?.has(g.key);
    const uids = g.rows.map((r) => r.uid);
    const nOn = uids.filter((u) => selected.has(u)).length;
    const name = same(g.rows, (r) => displayName(r.card)) ?? g.rows[0].card.name;
    const toggleOpen = () => {
      hide();
      onExpand?.(g.key);
    };
    return (
      <Fragment key={g.sum.uid}>
        <tr
          className={["wish-row clickable group-row", open ? "open" : "", nOn === uids.length ? "picked" : ""].filter(Boolean).join(" ")}
          onClick={toggleOpen}
          onMouseEnter={follow(g.rows[0].card.imageUrl)}
          onMouseMove={follow(g.rows[0].card.imageUrl)}
          onMouseLeave={hide}
        >
          <td className="center" onClick={(e) => e.stopPropagation()}>
            <GroupTick
              on={nOn === uids.length}
              some={nOn > 0}
              disabled={disabled}
              label={`Select every ${g.rows[0].card.name}`}
              onChange={() => onToggleMany?.(uids, nOn < uids.length)}
            />
          </td>
          <td className="card-cell">
            <button
              className="row-title linkish"
              aria-expanded={open}
              onClick={(e) => {
                e.stopPropagation();
                toggleOpen();
              }}
            >
              <span className="fold" aria-hidden="true">
                {open ? "▾" : "▸"}
              </span>{" "}
              {name}
            </button>{" "}
            <span className="muted small">{g.rows.length} piles</span>
          </td>
          {cols.map((c) => (
            <Fragment key={c.id}>{groupCell(c, g)}</Fragment>
          ))}
        </tr>
        {open && g.rows.map((r) => pileRow(r, true))}
      </Fragment>
    );
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
        <tbody>{groups ? groups.map(groupLines) : rows.map((r) => pileRow(r))}</tbody>
      </table>
      {picture}
    </div>
  );
}
