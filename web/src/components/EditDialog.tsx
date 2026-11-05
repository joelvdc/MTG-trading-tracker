"use client";

import { useState } from "react";
import { CURRENCIES, type Currency } from "@/lib/money";
import type { WishRow } from "@/lib/view";
import type { WishEdit } from "@/lib/wishlist";
import { Dialog } from "./Dialog";
import { cardTitle, setLine } from "./WishRowItem";

type Update = Extract<WishEdit, { type: "update" }>;

export function EditDialog({
  row,
  viewCurrency,
  busy,
  onSave,
  onRemove,
  onClose,
}: {
  row: WishRow;
  viewCurrency: Currency;
  busy: boolean;
  onSave: (edit: Update) => void;
  onRemove: (uid: string) => void;
  onClose: () => void;
}) {
  const [quantity, setQuantity] = useState(String(row.quantity));
  const [anyPrinting, setAnyPrinting] = useState(row.anyPrinting);
  const [foil, setFoil] = useState(row.foil);
  const [notes, setNotes] = useState(row.notes ?? "");
  const [alert, setAlert] = useState(row.alertBelow != null ? String(row.alertBelow) : "");
  const [alertCur, setAlertCur] = useState<string>(row.alertCurrency ?? (row.alertBelow != null ? "EUR" : viewCurrency));
  const [ordered, setOrdered] = useState(row.orderedAt != null);
  const [confirming, setConfirming] = useState(false);

  const canFoil = row.card.hasNonFoil && (row.card.hasFoil || row.card.hasEtched);
  const q = Number(quantity);
  const qOk = Number.isInteger(q) && q >= 1 && q <= 999;
  const alertText = alert.trim().replace(",", ".");
  const alertNum = alertText === "" ? null : Number(alertText);
  const alertOk = alertNum === null || (Number.isFinite(alertNum) && alertNum > 0);

  function save() {
    const edit: Update = { type: "update", uid: row.uid };
    if (q !== row.quantity) edit.quantity = q;
    if (anyPrinting !== row.anyPrinting) edit.anyPrinting = anyPrinting;
    if (canFoil && foil !== row.foil) edit.foil = foil;
    const newNotes = notes.trim() === "" ? null : notes.trim();
    if (newNotes !== row.notes) edit.notes = newNotes;
    if (alertNum === null) {
      if (row.alertBelow != null) {
        edit.alertBelow = null;
        edit.alertCurrency = null;
      }
    } else if (alertNum !== row.alertBelow || alertCur !== (row.alertCurrency ?? "EUR")) {
      edit.alertBelow = alertNum;
      edit.alertCurrency = alertCur;
    }
    if (ordered !== (row.orderedAt != null)) edit.ordered = ordered;
    if (Object.keys(edit).length === 2) {
      onClose();
      return;
    }
    onSave(edit);
  }

  return (
    <Dialog title={cardTitle(row)} onClose={onClose}>
      <div className="dialog-top">
        {row.card.imageUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img className="big-img" src={row.card.imageUrl} alt={row.card.name} />
        )}
        <div className="muted">
          {row.card.setName} · {setLine(row)}
        </div>
      </div>
      <div className="stack">
        <label className="field">
          <span>Quantity</span>
          <input type="number" min={1} max={999} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
        </label>
        <label className="check">
          <input type="checkbox" checked={anyPrinting} onChange={(e) => setAnyPrinting(e.target.checked)} />
          <span>Any printing</span>
        </label>
        {canFoil && (
          <label className="check">
            <input type="checkbox" checked={foil} onChange={(e) => setFoil(e.target.checked)} />
            <span>Foil</span>
          </label>
        )}
        <label className="field">
          <span>Notes</span>
          <textarea rows={3} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
        <div className="field">
          <span>Price alert (empty = no alert)</span>
          <div className="inline">
            <input
              type="text"
              inputMode="decimal"
              placeholder="No alert"
              aria-label="Alert price"
              value={alert}
              onChange={(e) => setAlert(e.target.value)}
            />
            <select aria-label="Alert currency" value={alertCur} onChange={(e) => setAlertCur(e.target.value)}>
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
        </div>
        <label className="check">
          <input type="checkbox" checked={ordered} onChange={(e) => setOrdered(e.target.checked)} />
          <span>Ordered</span>
        </label>
      </div>
      <div className="dialog-actions">
        {confirming ? (
          <>
            <span>Remove this card?</span>
            <button className="btn danger" disabled={busy} onClick={() => onRemove(row.uid)}>
              Yes, remove
            </button>
            <button className="btn" disabled={busy} onClick={() => setConfirming(false)}>
              No
            </button>
          </>
        ) : (
          <>
            <button className="btn danger-text" disabled={busy} onClick={() => setConfirming(true)}>
              Remove from wishlist
            </button>
            <span className="spacer" />
            <button className="btn" disabled={busy} onClick={onClose}>
              Cancel
            </button>
            <button className="btn primary" disabled={busy || !qOk || !alertOk} onClick={save}>
              Save
            </button>
          </>
        )}
      </div>
    </Dialog>
  );
}
