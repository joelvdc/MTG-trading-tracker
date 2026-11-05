"use client";

import { format } from "@/lib/money";
import type { Currency } from "@/lib/money";
import type { WishRow } from "@/lib/view";
import { formatDate } from "./api";

/** "surgefoil" → "Surge foil", like the app's FoilTypes.name for treatments it doesn't list by name. */
export function foilName(type: string): string {
  const base = type.replace(/foil$/, "");
  return type === "serialized" ? "Serialized" : base.charAt(0).toUpperCase() + base.slice(1) + " foil";
}

export function cardTitle(row: WishRow): string {
  return row.card.flavorName ? `${row.card.flavorName} (${row.card.name})` : row.card.name;
}

export function setLine(row: WishRow): string {
  return `${row.card.setCode.toUpperCase()} #${row.card.collectorNumber}`;
}

export function WishRowItem({
  row,
  currency,
  perEuro,
  disabled,
  onOpen,
  onToggleOrdered,
}: {
  row: WishRow;
  currency: Currency;
  perEuro: number;
  disabled: boolean;
  onOpen: (row: WishRow) => void;
  onToggleOrdered: (row: WishRow, ordered: boolean) => void;
}) {
  const received = row.receivedAt != null;
  const money = (eur: number) => format(eur * perEuro, currency);
  const trend = row.trendPct;
  const classes = ["row", received ? "received" : "", row.alertHit ? "hit" : ""].filter(Boolean).join(" ");
  const open = () => !received && !disabled && onOpen(row);

  return (
    <li className={classes}>
      <div
        className={received ? "row-main" : "row-main clickable"}
        role={received ? undefined : "button"}
        tabIndex={received ? undefined : 0}
        onClick={open}
        onKeyDown={(e) => {
          if (e.target === e.currentTarget && (e.key === "Enter" || e.key === " ")) {
            e.preventDefault();
            open();
          }
        }}
      >
        {row.card.imageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img className="thumb" src={row.card.imageUrl} alt={row.card.name} loading="lazy" width={60} height={84} />
        ) : (
          <div className="thumb empty" />
        )}
        <div className="row-body">
          <div className="row-title">{cardTitle(row)}</div>
          <div className="muted small">{setLine(row)}</div>
          <div className="badges">
            {row.foil && <span className="badge foil">Foil</span>}
            {row.card.foilType && <span className="badge foil">{foilName(row.card.foilType)}</span>}
            {row.anyPrinting && <span className="badge any">Any printing</span>}
            {row.gotSince > 0 && <span className="badge got">Got {row.gotSince} since adding</span>}
          </div>
          {row.alertBelow != null && (
            <div className={row.alertHit ? "small gold" : "small muted"}>
              Alert ≤ {format(row.alertBelow, (row.alertCurrency as Currency | null) ?? "EUR")}
            </div>
          )}
          {row.notes && <div className="notes small">{row.notes}</div>}
          {received && (
            <div className="muted small">
              Received {formatDate(row.receivedAt!)}
              {row.receivedFrom ? ` · ${row.receivedFrom}` : ""}
            </div>
          )}
        </div>
        <div className="row-side">
          <div className="qty">×{row.quantity}</div>
          {row.unitEur != null ? (
            <div className="price">
              <div>{money(row.unitEur)}</div>
              {row.quantity > 1 && <div className="muted small">{money(row.unitEur * row.quantity)} total</div>}
            </div>
          ) : (
            <div className="muted small">No price</div>
          )}
          {trend != null && (
            <div className={"small " + (trend >= 0 ? "up" : "down")}>
              {trend >= 0 ? "▲" : "▼"} {Math.abs(trend).toFixed(1)}%
            </div>
          )}
        </div>
      </div>
      {received ? (
        <div className="muted small row-foot">Change received cards in the app</div>
      ) : (
        <label className="check row-foot">
          <input
            type="checkbox"
            checked={row.orderedAt != null}
            disabled={disabled}
            onChange={(e) => onToggleOrdered(row, e.target.checked)}
          />
          <span>Ordered</span>
        </label>
      )}
    </li>
  );
}
