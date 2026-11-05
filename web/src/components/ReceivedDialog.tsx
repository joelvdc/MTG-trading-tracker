"use client";

import { useState } from "react";
import { CONDITIONS, finishesOf, FINISH_LABELS, LANGUAGES, resolveFinish } from "@/lib/cards";
import type { ReceivedEdit } from "@/lib/received";
import type { WishRow } from "@/lib/view";
import { formatDate } from "./api";
import { Dialog } from "./Dialog";
import { cardTitle, finishLabel, setLine } from "./wishText";

const ADDED = "added to the collection";

/**
 * A received wishlist card: into the collection with the details picked (the app's swipe right in Received), back on
 * the wishlist, or removed.
 */
export function ReceivedDialog({
  row,
  binders,
  currency,
  busy,
  onEdit,
  onRemove,
  onClose,
}: {
  row: WishRow;
  binders: { uid: string; name: string }[];
  currency: string;
  busy: boolean;
  onEdit: (e: ReceivedEdit) => void;
  onRemove: (uid: string) => void;
  onClose: () => void;
}) {
  const added = row.receivedFrom === ADDED;
  const finishes = finishesOf(row.card);
  const [adding, setAdding] = useState(false);
  const [finish, setFinish] = useState(resolveFinish(row.card, row.foil ? "FOIL" : "NONFOIL"));
  const [condition, setCondition] = useState("NM");
  const [language, setLanguage] = useState("EN");
  const [quantity, setQuantity] = useState(String(row.quantity));
  const [binder, setBinder] = useState("");
  const [paid, setPaid] = useState("");
  const [paidCur, setPaidCur] = useState(currency);
  const [confirming, setConfirming] = useState(false);

  const q = Number(quantity);
  const qOk = Number.isInteger(q) && q >= 1 && q <= 9999;
  const paidText = paid.trim().replace(",", ".");
  const paidNum = paidText === "" ? null : Number(paidText);
  const paidOk = paidNum === null || (Number.isFinite(paidNum) && paidNum >= 0);

  return (
    <Dialog title={cardTitle(row)} onClose={onClose}>
      <div className="stack">
        <p className="muted">
          {row.card.setName} · {setLine(row)} · {finishLabel(row)} · ×{row.quantity}
          <br />
          Received {formatDate(row.receivedAt!)}
          {row.receivedFrom ? ` · ${row.receivedFrom}` : ""}
        </p>
        {adding ? (
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              if (!qOk || !paidOk) return;
              onEdit({
                type: "toCollection",
                uid: row.uid,
                card: row.card,
                finish,
                condition,
                language,
                quantity: q,
                binder: binder || null,
                paid: paidNum === null ? null : { amount: paidNum, currency: paidCur },
              });
            }}
          >
            <div className="form-grid">
              <label className="field">
                <span>Copies</span>
                <input type="number" min={1} max={9999} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
              </label>
              <label className="field">
                <span>Finish</span>
                <select value={finish} onChange={(e) => setFinish(e.target.value as typeof finish)}>
                  {finishes.map((f) => (
                    <option key={f} value={f}>
                      {FINISH_LABELS[f]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Condition</span>
                <select value={condition} onChange={(e) => setCondition(e.target.value)}>
                  {CONDITIONS.map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Language</span>
                <select value={language} onChange={(e) => setLanguage(e.target.value)}>
                  {LANGUAGES.map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Binder</span>
                <select value={binder} onChange={(e) => setBinder(e.target.value)}>
                  <option value="">Unsorted</option>
                  {binders.map((b) => (
                    <option key={b.uid} value={b.uid}>
                      {b.name}
                    </option>
                  ))}
                </select>
              </label>
              <div className="inline">
                <label className="field grow">
                  <span>Paid per copy</span>
                  <input type="text" inputMode="decimal" placeholder="Optional" value={paid} onChange={(e) => setPaid(e.target.value)} />
                </label>
                <label className="field">
                  <span>Currency</span>
                  <select value={paidCur} onChange={(e) => setPaidCur(e.target.value)}>
                    {["EUR", "DKK", "USD"].map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
            <p className="muted small">
              The copies join the same card already in that binder; its purchase price becomes the average of all copies. The card then
              leaves the wishlist.
            </p>
            <div className="dialog-actions">
              <span className="spacer" />
              <button type="button" className="btn" onClick={() => setAdding(false)}>
                Back
              </button>
              <button type="submit" className="btn primary" disabled={busy || !qOk || !paidOk}>
                Add to the collection
              </button>
            </div>
          </form>
        ) : (
          <div className="dialog-actions">
            {confirming ? (
              <>
                <span>Remove this card from the wishlist?</span>
                <button className="btn danger" disabled={busy} onClick={() => onRemove(row.uid)}>
                  Yes, remove
                </button>
                <button className="btn" onClick={() => setConfirming(false)}>
                  No
                </button>
              </>
            ) : (
              <>
                <button className="btn danger-text" disabled={busy} onClick={() => setConfirming(true)}>
                  Remove
                </button>
                <span className="spacer" />
                <button className="btn" disabled={busy} onClick={() => onEdit({ type: "unreceive", uids: [row.uid] })}>
                  Back on the wishlist
                </button>
                {!added && (
                  <button className="btn primary" disabled={busy} onClick={() => setAdding(true)}>
                    Add to the collection…
                  </button>
                )}
              </>
            )}
          </div>
        )}
        {added && !adding && <p className="muted small">Its copies were added to the collection when it was marked received.</p>}
      </div>
    </Dialog>
  );
}
