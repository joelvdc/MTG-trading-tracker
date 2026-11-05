"use client";

import { useState } from "react";
import { CONDITIONS, FINISH_LABELS, finishesOf, LANGUAGES } from "@/lib/cards";
import type { CollectionEdit } from "@/lib/collection";
import type { CollBinder } from "@/lib/collview";
import { CURRENCIES, type Currency } from "@/lib/money";
import type { Finish } from "@/lib/pricing";
import { CardSearch, type Printing } from "./CardSearch";
import { Dialog } from "./Dialog";

type Add = Extract<CollectionEdit, { type: "add" }>;

/** Adds cards to the collection: search, pick a printing, then copies, finish, condition, language and binder. */
export function CollAddDialog({
  currency,
  perEuro,
  binders,
  binder: startBinder,
  busy,
  onAdd,
  onClose,
}: {
  currency: Currency;
  perEuro: number;
  binders: CollBinder[];
  binder: string | null;
  busy: boolean;
  onAdd: (edit: Add) => void;
  onClose: () => void;
}) {
  const [picked, setPicked] = useState<Printing | null>(null);
  const [quantity, setQuantity] = useState("1");
  const [finish, setFinish] = useState<Finish>("NONFOIL");
  const [condition, setCondition] = useState("NM");
  const [language, setLanguage] = useState("EN");
  const [binder, setBinder] = useState(startBinder ?? "");
  const [signed, setSigned] = useState(false);
  const [altered, setAltered] = useState(false);
  const [paid, setPaid] = useState("");
  const [paidCur, setPaidCur] = useState<string>(currency);

  const q = Number(quantity);
  const qOk = Number.isInteger(q) && q >= 1 && q <= 9999;
  const paidText = paid.trim().replace(",", ".");
  const paidNum = paidText === "" ? null : Number(paidText);
  const paidOk = paidNum === null || (Number.isFinite(paidNum) && paidNum >= 0);

  function pick(p: Printing) {
    setPicked(p);
    setFinish(finishesOf(p.card)[0]);
  }

  return (
    <Dialog title="Add to the collection" onClose={onClose}>
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
          <div className="form-grid">
            <label className="field">
              <span>Copies</span>
              <input type="number" min={1} max={9999} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
            </label>
            <label className="field">
              <span>Finish</span>
              <select value={finish} onChange={(e) => setFinish(e.target.value as Finish)}>
                {finishesOf(picked.card).map((f) => (
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
          </div>
          <div className="field">
            <span>Purchase price, one copy (optional)</span>
            <div className="inline">
              <input type="text" inputMode="decimal" aria-label="Purchase price" placeholder="Not known" value={paid} onChange={(e) => setPaid(e.target.value)} />
              <select aria-label="Purchase currency" value={paidCur} onChange={(e) => setPaidCur(e.target.value)}>
                {CURRENCIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="inline">
            <label className="check">
              <input type="checkbox" checked={signed} onChange={(e) => setSigned(e.target.checked)} />
              <span>Signed</span>
            </label>
            <label className="check">
              <input type="checkbox" checked={altered} onChange={(e) => setAltered(e.target.checked)} />
              <span>Altered</span>
            </label>
          </div>
          <div className="dialog-actions">
            <button className="btn" disabled={busy} onClick={() => setPicked(null)}>
              Back
            </button>
            <span className="spacer" />
            <button
              className="btn primary"
              disabled={busy || !qOk || !paidOk}
              onClick={() =>
                onAdd({
                  type: "add",
                  card: picked.card,
                  quantity: q,
                  finish,
                  condition,
                  language,
                  binder: binder || null,
                  signed,
                  altered,
                  ...(paidNum !== null ? { paid: { amount: paidNum, currency: paidCur } } : {}),
                })
              }
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
