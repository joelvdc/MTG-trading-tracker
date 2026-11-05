"use client";

import { format, type Currency } from "@/lib/money";
import type { CollRow } from "@/lib/collview";
import { displayName, finishName } from "@/lib/colquery";

/** The collection as card pictures, like the wishlist's: price, set and condition under each card. */
export function CollGrid({
  rows,
  currency,
  perEuro,
  disabled,
  onOpen,
}: {
  rows: CollRow[];
  currency: Currency;
  perEuro: number;
  disabled: boolean;
  onOpen: (row: CollRow) => void;
}) {
  const money = (eur: number) => format(eur * perEuro, currency);
  return (
    <ul className="wish-grid coll-grid">
      {rows.map((r) => {
        const foil = r.finish !== "NONFOIL" || !!r.card.foilType;
        return (
          <li key={r.uid} className="tile">
            <button className="tile-in" disabled={disabled} onClick={() => onOpen(r)} title={displayName(r.card)}>
              <div className={foil ? "tile-img foil-glow" : "tile-img"}>
                {r.card.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={r.card.imageUrl} alt={displayName(r.card)} loading="lazy" />
                ) : (
                  <span className="tile-noimg">{displayName(r.card)}</span>
                )}
                {r.quantity > 1 && <span className="tile-qty">×{r.quantity}</span>}
                {(r.signed || r.altered) && <span className="tile-flag">{r.signed ? "Signed" : "Altered"}</span>}
              </div>
              <div className="tile-price">
                {r.unitEur != null ? (
                  <>
                    {r.approx ? "≈" : ""}
                    {money(r.unitEur)}
                  </>
                ) : (
                  <span className="muted">No price</span>
                )}
              </div>
              <div className="tile-tags">
                <span className="muted">{r.card.setCode.toUpperCase()}</span>
                <span>{r.condition}</span>
                {r.language !== "EN" && <span>{r.language}</span>}
                {foil && <span className="tag foil">{finishName(r)}</span>}
              </div>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
