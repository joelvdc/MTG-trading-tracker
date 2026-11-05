"use client";

import { useState } from "react";
import { CURRENCIES, type Currency, format } from "@/lib/money";
import type { WishRow } from "@/lib/view";
import type { WishEdit } from "@/lib/wishlist";
import { api } from "./api";
import { Dialog } from "./Dialog";
import type { Printing } from "./AddDialog";
import { cardmarketUrl, cardTitle, cardtraderUrl, finishLabel, setLine } from "./wishText";

type Update = Extract<WishEdit, { type: "update" }>;

export function EditDialog({
  row,
  viewCurrency,
  perEuro,
  busy,
  onSave,
  onRemove,
  onReceive,
  onClose,
}: {
  row: WishRow;
  viewCurrency: Currency;
  perEuro: number;
  busy: boolean;
  onSave: (edit: Update) => void;
  onRemove: (uid: string) => void;
  /** "Mark received" (the app's tick in the wishlist). */
  onReceive: () => void;
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
  // The printing list, while choosing another printing: null while it loads.
  const [picking, setPicking] = useState(false);
  const [prints, setPrints] = useState<Printing[] | null>(null);
  const [printError, setPrintError] = useState<string | null>(null);

  const canFoil = row.card.hasNonFoil && (row.card.hasFoil || row.card.hasEtched);
  const q = Number(quantity);
  const qOk = Number.isInteger(q) && q >= 1 && q <= 999;
  const alertText = alert.trim().replace(",", ".");
  const alertNum = alertText === "" ? null : Number(alertText);
  const alertOk = alertNum === null || (Number.isFinite(alertNum) && alertNum > 0);

  async function openPrintings() {
    setPicking(true);
    setPrints(null);
    setPrintError(null);
    const r = await api<{ results?: Printing[] }>(`/api/cards/prints?name=${encodeURIComponent(row.card.name)}`);
    if (r.ok) setPrints(r.data.results ?? []);
    else setPrintError(r.data.error ?? "Could not load the printings.");
  }

  /** Saves the window's changes; with [card], the chosen printing too (straight away, from the printing list). */
  function save(card?: Printing["card"]) {
    const edit: Update = { type: "update", uid: row.uid };
    if (card && card.scryfallId !== row.card.scryfallId) edit.card = card;
    if (q !== row.quantity) edit.quantity = q;
    if (anyPrinting !== row.anyPrinting) edit.anyPrinting = anyPrinting;
    if ((canFoil || edit.card) && foil !== row.foil) edit.foil = foil;
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

  const money = (eur: number | null) => (eur == null ? "–" : format(eur * perEuro, viewCurrency));

  if (picking) {
    return (
      <Dialog title={`Which printing? ${row.card.name}`} onClose={onClose}>
        <div className="stack">
          {printError && <div className="bar error">{printError}</div>}
          {prints === null && !printError ? (
            <div className="muted">Loading printings…</div>
          ) : prints?.length === 0 ? (
            <div className="muted">No printings found.</div>
          ) : (
            <div className="prints">
              {prints?.map((p) => {
                const current = p.card.scryfallId === row.card.scryfallId;
                return (
                  <button
                    key={p.card.scryfallId}
                    className={current ? "print current" : "print"}
                    disabled={busy}
                    aria-current={current || undefined}
                    onClick={() => (current ? setPicking(false) : save(p.card))}
                  >
                    {p.card.imageUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={p.card.imageUrl} alt={`${p.card.name} (${p.card.setCode})`} loading="lazy" />
                    ) : (
                      <div className="thumb empty" />
                    )}
                    <span className="small">
                      {p.card.setCode.toUpperCase()} #{p.card.collectorNumber} · {p.released.slice(0, 4)}
                    </span>
                    <span className="small muted">
                      {current
                        ? "Current printing"
                        : `${p.eur != null || p.card.hasNonFoil ? money(p.eur) : ""}${p.eurFoil != null ? ` / foil ${money(p.eurFoil)}` : ""}`}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
          <div className="dialog-actions">
            <button className="btn" disabled={busy} onClick={() => setPicking(false)}>
              Back
            </button>
          </div>
        </div>
      </Dialog>
    );
  }

  return (
    <Dialog title={cardTitle(row)} onClose={onClose}>
      <div className="dialog-top">
        {row.card.imageUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img className="big-img" src={row.card.imageUrl} alt={row.card.name} />
        )}
        <div className="dialog-info">
          <div className="muted">
            {row.card.setName} · {setLine(row)} · {finishLabel(row)}
          </div>
          <div className="shop-links">
            <button className="btn small-btn" disabled={busy} onClick={openPrintings}>
              Change printing
            </button>
            <a className="btn small-btn" href={cardmarketUrl(row)} target="_blank" rel="noreferrer">
              Cardmarket ↗
            </a>
            <a className="btn small-btn" href={cardtraderUrl(row)} target="_blank" rel="noreferrer">
              CardTrader ↗
            </a>
          </div>
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
            <button className="btn" disabled={busy} onClick={onReceive}>
              Mark received
            </button>
            <button className="btn" disabled={busy} onClick={onClose}>
              Cancel
            </button>
            <button className="btn primary" disabled={busy || !qOk || !alertOk} onClick={() => save()}>
              Save
            </button>
          </>
        )}
      </div>
    </Dialog>
  );
}
