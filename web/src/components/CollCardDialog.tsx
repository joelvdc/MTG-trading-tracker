"use client";

import { useEffect, useState } from "react";
import { CONDITIONS, FINISH_LABELS, finishesOf, LANGUAGES } from "@/lib/cards";
import type { CollectionEdit, Paid } from "@/lib/collection";
import type { CardPrices, CollectionView, CollRow } from "@/lib/collview";
import { displayName, finishName, front } from "@/lib/colquery";
import { CURRENCIES, format } from "@/lib/money";
import { PRICE_TYPE_LABELS, PRICE_TYPES } from "@/lib/prices";
import { SOURCE_LABELS, type Finish } from "@/lib/pricing";
import { api } from "./api";
import { cardmarketUrlFor, cardtraderUrlFor } from "./cardLinks";
import type { Printing } from "./CardSearch";
import { Dialog } from "./Dialog";
import { rowTags } from "./CollTable";

type Update = Extract<CollectionEdit, { type: "update" }>;

/**
 * A stack's window (the app's collection card page): picture, prices at every source, the stack's details to
 * change, the decks the card is in and the other copies you own.
 */
export function CollCardDialog({
  row,
  view,
  busy,
  onSave,
  onRemove,
  onOpenOther,
  onClose,
}: {
  row: CollRow;
  view: CollectionView;
  busy: boolean;
  onSave: (edit: Update) => void;
  onRemove: (uid: string) => void;
  onOpenOther: (row: CollRow) => void;
  onClose: () => void;
}) {
  const [quantity, setQuantity] = useState(String(row.quantity));
  const [finish, setFinish] = useState<Finish>(row.finish);
  const [binder, setBinder] = useState<string>(row.binder ?? "");
  const [move, setMove] = useState(String(row.quantity));
  const [condition, setCondition] = useState(row.condition);
  const [language, setLanguage] = useState(row.language);
  const typedPaid = row.purchasePrice != null && row.paidTyped != null && row.paidCurrency && row.paidCurrency !== "EUR";
  const [paid, setPaid] = useState(row.purchasePrice == null ? "" : String(typedPaid ? row.paidTyped : row.purchasePrice));
  const [paidCur, setPaidCur] = useState<string>(typedPaid ? row.paidCurrency! : row.purchasePrice != null ? "EUR" : view.currency);
  const [notes, setNotes] = useState(row.notes ?? "");
  const [signed, setSigned] = useState(row.signed);
  const [altered, setAltered] = useState(row.altered);
  const [confirming, setConfirming] = useState(false);
  const [prices, setPrices] = useState<CardPrices | null>(null);
  const [picking, setPicking] = useState(false);
  const [prints, setPrints] = useState<Printing[] | null>(null);
  const [printError, setPrintError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    api<CardPrices>(`/api/collection/prices?uid=${encodeURIComponent(row.uid)}`).then((r) => {
      if (live && r.ok) setPrices(r.data);
    });
    return () => {
      live = false;
    };
  }, [row.uid]);

  const money = (eur: number | null | undefined) => (eur == null ? "–" : format(eur * view.perEuro, view.currency));
  const q = Number(quantity);
  const qOk = Number.isInteger(q) && q >= 1 && q <= 9999;
  const m = Number(move);
  const moving = (binder || null) !== row.binder;
  const mOk = !moving || (Number.isInteger(m) && m >= 1 && m <= q);
  const paidText = paid.trim().replace(",", ".");
  const paidNum = paidText === "" ? null : Number(paidText);
  const paidOk = paidNum === null || (Number.isFinite(paidNum) && paidNum >= 0);
  const finishes = finishesOf(row.card);
  if (!finishes.includes(row.finish)) finishes.push(row.finish);
  const readOnly = view.readOnly;

  const name = front(row);
  const inDecks = view.decks.filter((d) => d.cards[name]).map((d) => ({ name: d.name, copies: d.cards[name] }));
  const binderName = (uid: string | null) => (uid ? (view.binders.find((b) => b.uid === uid)?.name ?? "Unsorted") : "Unsorted");

  async function openPrintings() {
    setPicking(true);
    setPrints(null);
    setPrintError(null);
    const r = await api<{ results?: Printing[] }>(`/api/cards/prints?name=${encodeURIComponent(row.card.name)}`);
    if (r.ok) setPrints(r.data.results ?? []);
    else setPrintError(r.data.error ?? "Could not load the printings.");
  }

  function save(card?: Printing["card"]) {
    const e: Update = { type: "update", uid: row.uid };
    if (card && card.scryfallId !== row.card.scryfallId) e.card = card;
    if (q !== row.quantity) e.quantity = q;
    if (finish !== row.finish) e.finish = finish;
    if (condition !== row.condition) e.condition = condition;
    if (language !== row.language) e.language = language;
    if (signed !== row.signed) e.signed = signed;
    if (altered !== row.altered) e.altered = altered;
    const n = notes.trim() === "" ? null : notes.trim();
    if (n !== row.notes) e.notes = n;
    const before: Paid = row.purchasePrice == null ? null : { amount: typedPaid ? row.paidTyped! : row.purchasePrice, currency: typedPaid ? row.paidCurrency! : "EUR" };
    const now: Paid = paidNum === null ? null : { amount: paidNum, currency: paidCur };
    if (JSON.stringify(before) !== JSON.stringify(now)) e.paid = now;
    if (moving) {
      e.binder = binder || null;
      if (m < q) e.move = m;
    }
    if (Object.keys(e).length === 2) return onClose();
    onSave(e);
  }

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
                  <button key={p.card.scryfallId} className={current ? "print current" : "print"} disabled={busy} onClick={() => (current ? setPicking(false) : save(p.card))}>
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
                      {current ? "Current printing" : `${p.eur != null || p.card.hasNonFoil ? money(p.eur) : ""}${p.eurFoil != null ? ` / foil ${money(p.eurFoil)}` : ""}`}
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

  const others = view.rows.filter((r) => r.uid !== row.uid && front(r) === name);

  return (
    <Dialog title={displayName(row.card)} onClose={onClose}>
      <div className="dialog-top">
        {row.card.imageUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img className="big-img" src={row.card.imageUrl} alt={row.card.name} />
        )}
        <div className="dialog-info">
          <div className="muted">
            {row.card.setName} · {row.card.setCode.toUpperCase()} #{row.card.collectorNumber} · {row.card.rarity} · {finishName(row)}
          </div>
          <div className="price-line">
            <strong>
              {row.approx ? "≈" : ""}
              {money(row.unitEur)}
            </strong>
            {row.condNote && <span className="tag">{row.condNote}</span>}
            {row.quantity > 1 && row.unitEur != null && <span className="muted"> · {money(row.unitEur * row.quantity)} for {row.quantity}</span>}
          </div>
          <div className="shop-links">
            <button className="btn small-btn" disabled={busy || readOnly} onClick={openPrintings}>
              Change printing
            </button>
            <a className="btn small-btn" href={cardmarketUrlFor(row.card, row.finish !== "NONFOIL")} target="_blank" rel="noreferrer">
              Cardmarket ↗
            </a>
            <a className="btn small-btn" href={cardtraderUrlFor(row.card)} target="_blank" rel="noreferrer">
              CardTrader ↗
            </a>
          </div>
        </div>
      </div>

      <div className="stack">
        <div className="form-grid">
          <label className="field">
            <span>Copies</span>
            <input type="number" min={1} max={9999} value={quantity} disabled={readOnly} onChange={(e) => setQuantity(e.target.value)} />
          </label>
          <label className="field">
            <span>Finish</span>
            <select value={finish} disabled={readOnly || finishes.length < 2} onChange={(e) => setFinish(e.target.value as Finish)}>
              {finishes.map((f) => (
                <option key={f} value={f}>
                  {FINISH_LABELS[f]}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Condition</span>
            <select value={condition} disabled={readOnly} onChange={(e) => setCondition(e.target.value)}>
              {CONDITIONS.map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Language</span>
            <select value={language} disabled={readOnly} onChange={(e) => setLanguage(e.target.value)}>
              {LANGUAGES.map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Binder</span>
            <select value={binder} disabled={readOnly} onChange={(e) => setBinder(e.target.value)}>
              <option value="">Unsorted</option>
              {view.binders.map((b) => (
                <option key={b.uid} value={b.uid}>
                  {b.name}
                </option>
              ))}
            </select>
          </label>
          {moving && q > 1 && (
            <label className="field">
              <span>Copies to move</span>
              <input type="number" min={1} max={q} value={move} onChange={(e) => setMove(e.target.value)} />
            </label>
          )}
        </div>
        <div className="field">
          <span>Purchase price, one copy (empty = not known)</span>
          <div className="inline">
            <input type="text" inputMode="decimal" aria-label="Purchase price" placeholder="Not known" value={paid} disabled={readOnly} onChange={(e) => setPaid(e.target.value)} />
            <select aria-label="Purchase currency" value={paidCur} disabled={readOnly} onChange={(e) => setPaidCur(e.target.value)}>
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
            <input type="checkbox" checked={signed} disabled={readOnly} onChange={(e) => setSigned(e.target.checked)} />
            <span>Signed</span>
          </label>
          <label className="check">
            <input type="checkbox" checked={altered} disabled={readOnly} onChange={(e) => setAltered(e.target.checked)} />
            <span>Altered</span>
          </label>
        </div>
        <label className="field">
          <span>Notes</span>
          <textarea rows={2} maxLength={1000} value={notes} disabled={readOnly} onChange={(e) => setNotes(e.target.value)} />
        </label>

        <section>
          <h3 className="mini-head">Prices</h3>
          {prices ? (
            <table className="price-table">
              <tbody>
                {prices.sources.map((s) => (
                  <tr key={s.source} className={s.source === view.source ? "on" : undefined}>
                    <th>{SOURCE_LABELS[s.source]}</th>
                    <td className="num">{money(s.eur)}</td>
                    <td>
                      {s.url && (
                        <a href={s.url} target="_blank" rel="noreferrer">
                          Open ↗
                        </a>
                      )}
                    </td>
                  </tr>
                ))}
                {prices.cardKingdomPays != null && (
                  <tr>
                    <th>Card Kingdom pays</th>
                    <td className="num">{money(prices.cardKingdomPays)}</td>
                    <td />
                  </tr>
                )}
              </tbody>
            </table>
          ) : (
            <div className="muted small">Loading prices…</div>
          )}
          {prices?.cardmarket && (
            <>
              <h3 className="mini-head">Cardmarket prices{row.finish !== "NONFOIL" ? " (foil)" : ""}</h3>
              <table className="price-table">
                <tbody>
                  {PRICE_TYPES.map((t, i) => (
                    <tr key={t} className={t === view.priceType ? "on" : undefined}>
                      <th>{PRICE_TYPE_LABELS[t]}</th>
                      <td className="num">{money(prices.cardmarket![i])}</td>
                      <td />
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </section>

        <section>
          <h3 className="mini-head">In your decks</h3>
          {inDecks.length ? (
            <ul className="plain-list">
              {inDecks.map((d, i) => (
                <li key={i}>
                  {d.name} <span className="muted">×{d.copies}</span>
                </li>
              ))}
            </ul>
          ) : (
            <div className="muted small">In no deck.</div>
          )}
        </section>

        {others.length > 0 && (
          <section>
            <h3 className="mini-head">You also own {others.reduce((n, r) => n + r.quantity, 0)} more</h3>
            <ul className="plain-list">
              {others.map((o) => (
                <li key={o.uid}>
                  <button className="linkish" disabled={busy} onClick={() => onOpenOther(o)}>
                    {o.quantity}× {rowTags(o).join(" · ")}
                  </button>{" "}
                  <span className="muted small">{binderName(o.binder)}</span>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>

      <div className="dialog-actions">
        {confirming ? (
          <>
            <span>Delete these {row.quantity} cop{row.quantity === 1 ? "y" : "ies"}?</span>
            <button className="btn danger" disabled={busy} onClick={() => onRemove(row.uid)}>
              Yes, delete
            </button>
            <button className="btn" disabled={busy} onClick={() => setConfirming(false)}>
              No
            </button>
          </>
        ) : (
          <>
            <button className="btn danger-text" disabled={busy || readOnly} onClick={() => setConfirming(true)}>
              Delete
            </button>
            <span className="spacer" />
            <button className="btn" disabled={busy} onClick={onClose}>
              Cancel
            </button>
            <button className="btn primary" disabled={busy || readOnly || !qOk || !mOk || !paidOk} onClick={() => save()}>
              Save
            </button>
          </>
        )}
      </div>
    </Dialog>
  );
}
