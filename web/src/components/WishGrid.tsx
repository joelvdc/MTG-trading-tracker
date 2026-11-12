"use client";

import { format, type Currency } from "@/lib/money";
import type { WishRow } from "@/lib/view";
import { formatDate } from "./api";
import { cardTitle, finishLabel } from "./wishText";
import type { Section } from "./WishTable";

/** The wishlist as card pictures, per section. */
export function WishGrid({
  sections,
  currency,
  perEuro,
  disabled,
  onOpen,
  onToggleOrdered,
}: {
  sections: Section[];
  currency: Currency;
  perEuro: number;
  disabled: boolean;
  onOpen: (row: WishRow) => void;
  /** The Ordered tick box under each picture, as in the table and the app (since 1.90). */
  onToggleOrdered: (row: WishRow, ordered: boolean) => void;
}) {
  const money = (eur: number) => format(eur * perEuro, currency);
  return (
    <>
      {sections
        .filter((s) => s.rows.length > 0)
        .map((s) => (
          <section key={s.title}>
            <h2 className="section-head">
              {s.title} <span className="muted">({s.rows.reduce((n, r) => n + r.quantity, 0)})</span>
            </h2>
            <ul className="wish-grid">
              {s.rows.map((r) => {
                const received = r.receivedAt != null;
                const ordered = !received && r.orderedAt != null;
                const classes = ["tile", r.alertHit ? "hit" : "", ordered || received ? "dim" : ""].filter(Boolean).join(" ");
                const body = (
                  <>
                    <div className="tile-img">
                      {r.card.imageUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={r.card.imageUrl} alt={cardTitle(r)} loading="lazy" />
                      ) : (
                        <span className="tile-noimg">{cardTitle(r)}</span>
                      )}
                      {r.quantity > 1 && <span className="tile-qty">×{r.quantity}</span>}
                      {ordered && <span className="tile-flag ordered">Ordered</span>}
                      {received && <span className="tile-flag">Received</span>}
                    </div>
                    <div className="tile-name" title={cardTitle(r)}>
                      {cardTitle(r)}
                    </div>
                    <div className={r.alertHit ? "gold" : undefined}>
                      {r.unitEur != null ? money(r.unitEur) : <span className="muted">No price</span>}
                    </div>
                    <div className="muted small">
                      {[`×${r.quantity}`, finishLabel(r), r.alertHit ? "alert reached" : null].filter(Boolean).join(" · ")}
                    </div>
                    {received && <div className="muted small">{formatDate(r.receivedAt!)}</div>}
                  </>
                );
                return (
                  <li key={r.uid} className={classes}>
                    <button className="tile-in" disabled={disabled} onClick={() => onOpen(r)}>
                      {body}
                    </button>
                    {!received && (
                      <label className="tile-tick small">
                        <input
                          type="checkbox"
                          className="tick"
                          aria-label={`Ordered: ${r.card.name}`}
                          checked={r.orderedAt != null}
                          disabled={disabled}
                          onChange={(e) => onToggleOrdered(r, e.target.checked)}
                        />
                        Ordered
                      </label>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
    </>
  );
}
