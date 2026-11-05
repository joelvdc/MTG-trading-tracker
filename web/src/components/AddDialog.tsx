"use client";

import { useState } from "react";
import type { Currency } from "@/lib/money";
import { CardSearch, type Printing } from "./CardSearch";
import { Dialog } from "./Dialog";

export type { Printing };

export interface AddRequest {
  card: Printing["card"];
  foil: boolean;
  quantity: number;
  anyPrinting: boolean;
}

export function AddDialog({
  currency,
  perEuro,
  busy,
  onAdd,
  onClose,
}: {
  currency: Currency;
  perEuro: number;
  busy: boolean;
  onAdd: (req: AddRequest) => void;
  onClose: () => void;
}) {
  const [picked, setPicked] = useState<Printing | null>(null);
  const [quantity, setQuantity] = useState("1");
  const [foil, setFoil] = useState(false);
  const [anyPrinting, setAnyPrinting] = useState(true);

  function pick(p: Printing) {
    setPicked(p);
    const canNon = p.card.hasNonFoil;
    setFoil(!canNon);
  }

  const q = Number(quantity);
  const qOk = Number.isInteger(q) && q >= 1 && q <= 999;
  const hasFoilVersion = picked ? picked.card.hasFoil || picked.card.hasEtched : false;

  return (
    <Dialog title="Add card" onClose={onClose}>
      {picked ? (
        <div className="stack">
          <div className="dialog-top">
            {picked.card.imageUrl && (
              // eslint-disable-next-line @next/next/no-img-element
              <img className="big-img" src={picked.card.imageUrl} alt={picked.card.name} />
            )}
            <div>
              <strong>{picked.card.name}</strong>
              <div className="muted">
                {picked.card.setName} · {picked.card.setCode.toUpperCase()} #{picked.card.collectorNumber}
              </div>
            </div>
          </div>
          <label className="field">
            <span>Quantity</span>
            <input type="number" min={1} max={999} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </label>
          {hasFoilVersion && (
            <label className="check">
              <input
                type="checkbox"
                checked={foil}
                disabled={!picked.card.hasNonFoil}
                onChange={(e) => setFoil(e.target.checked)}
              />
              <span>Foil</span>
            </label>
          )}
          <label className="check">
            <input type="checkbox" checked={anyPrinting} onChange={(e) => setAnyPrinting(e.target.checked)} />
            <span>Any printing</span>
          </label>
          <small className="muted">Any printing: any copy of this card counts. Off: only this printing.</small>
          <div className="dialog-actions">
            <button className="btn" disabled={busy} onClick={() => setPicked(null)}>
              Back
            </button>
            <span className="spacer" />
            <button
              className="btn primary"
              disabled={busy || !qOk}
              onClick={() => onAdd({ card: picked.card, foil, quantity: q, anyPrinting })}
            >
              Add
            </button>
          </div>
        </div>
      ) : (
        <CardSearch currency={currency} perEuro={perEuro} onPick={pick} onClose={onClose} />
      )}
    </Dialog>
  );
}
