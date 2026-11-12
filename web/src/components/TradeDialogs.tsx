"use client";

import { useEffect, useMemo, useState } from "react";
import { CONDITIONS, FINISH_LABELS, finishesOf, LANGUAGES } from "@/lib/cards";
import type { CollRow, CollectionView } from "@/lib/collview";
import { displayName, finishName } from "@/lib/colquery";
import { CURRENCIES, format } from "@/lib/money";
import type { Finish } from "@/lib/pricing";
import type { Side, TradeEdit } from "@/lib/tradeedit";
import type { TradeCard, TradeLine, TradesView } from "@/lib/tradeview";
import { api } from "./api";
import { CardSearch, type Printing } from "./CardSearch";
import { Dialog } from "./Dialog";

const VERDICTS: Record<TradeCard["verdict"], string> = {
  EMPTY: "No cards yet",
  FAIR: "Fair trade",
  FAVORS_YOU: "In your favour",
  FAVORS_THEM: "In their favour",
};

export function verdictText(t: TradeCard): string {
  return VERDICTS[t.verdict] + (t.verdict !== "EMPTY" && t.verdict !== "FAIR" ? ` (${Math.abs(t.pct).toFixed(1)}%)` : "");
}

export function verdictClass(t: TradeCard): string {
  return t.verdict === "FAIR" ? "verdict fair" : t.verdict === "FAVORS_YOU" ? "verdict up" : t.verdict === "FAVORS_THEM" ? "verdict down" : "verdict";
}

const num = (s: string) => Number(s.trim().replace(",", "."));

/**
 * A trade being made or changed (the app's trade screen): partner, notes, both sides' cards with copies, finish,
 * condition, language and an agreed price, the balance, and the prices refreshed. Every change is saved at once.
 */
export function TradeDialog({
  trade,
  view,
  busy,
  onEdit,
  onAdd,
  onClose,
}: {
  trade: TradeCard;
  view: TradesView;
  busy: boolean;
  onEdit: (e: TradeEdit) => Promise<unknown>;
  onAdd: (side: Side) => void;
  onClose: () => void;
}) {
  const [partner, setPartner] = useState(trade.partner);
  const [notes, setNotes] = useState(trade.notes);
  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const locked = busy || view.readOnly;
  const saveText = () => {
    if (partner.trim() !== trade.partner || notes.trim() !== trade.notes) onEdit({ type: "update", uid: trade.uid, partner, notes });
  };
  return (
    <Dialog title={trade.partner ? `Trade with ${trade.partner}` : "Trade"} onClose={() => (saveText(), onClose())} full>
      <div className="stack">
        <div className="form-grid">
          <label className="field">
            <span>Trading with</span>
            <input value={partner} maxLength={500} placeholder="Name" disabled={view.readOnly} onChange={(e) => setPartner(e.target.value)} onBlur={saveText} />
          </label>
          <label className="field">
            <span>Notes</span>
            <input value={notes} maxLength={500} placeholder="Optional" disabled={view.readOnly} onChange={(e) => setNotes(e.target.value)} onBlur={saveText} />
          </label>
        </div>
        <div className="trade-sides">
          {(["GET", "GIVE"] as Side[]).map((side) => (
            <div key={side} className="trade-side">
              <div className="trade-head">
                <strong>{side === "GET" ? "You get" : "You give"}</strong>
                <span>{money(side === "GET" ? trade.get : trade.give)}</span>
              </div>
              {trade.lines.filter((l) => l.side === side).length === 0 && <p className="muted small">Nothing yet</p>}
              {trade.lines
                .filter((l) => l.side === side)
                .map((l) => (
                  <LineEditor key={`${l.key}|${l.quantity}|${l.customEur}|${l.condition}|${l.language}|${l.finish}`} uid={trade.uid} line={l} view={view} locked={locked} onEdit={onEdit} />
                ))}
              <button className="btn" disabled={locked} onClick={() => onAdd(side)}>
                ＋ Add card
              </button>
            </div>
          ))}
        </div>
        <p>
          <span className={verdictClass(trade)}>{verdictText(trade)}</span>{" "}
          <span className="muted small">
            Prices are {view.priceTypeLabel.toLowerCase()} from when each card was added; up to {view.tolerancePct}% difference counts as fair.
          </span>
        </p>
        <div className="dialog-actions">
          <button className="btn" disabled={locked || trade.lines.length === 0} onClick={() => onEdit({ type: "refreshPrices", uid: trade.uid })}>
            Refresh prices
          </button>
          <span className="spacer" />
          <button className="btn primary" onClick={() => (saveText(), onClose())}>
            Done
          </button>
        </div>
      </div>
    </Dialog>
  );
}

function LineEditor({ uid, line: l, view, locked, onEdit }: { uid: string; line: TradeLine; view: TradesView; locked: boolean; onEdit: (e: TradeEdit) => Promise<unknown> }) {
  const [qty, setQty] = useState(String(l.quantity));
  const [price, setPrice] = useState(l.customTyped != null ? String(l.customTyped) : l.customEur != null ? (l.customEur * (view.currency === "EUR" ? 1 : view.perEuro)).toFixed(2) : "");
  const [cur, setCur] = useState<string>(l.customCurrency ?? (l.customEur != null ? "EUR" : view.currency));
  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const send = (e: Omit<Extract<TradeEdit, { type: "updateLine" }>, "type" | "uid" | "line">) => onEdit({ type: "updateLine", uid, line: l.key, ...e });
  const saveQty = () => {
    const q = num(qty);
    if (Number.isInteger(q) && q >= 1 && q <= 9999 && q !== l.quantity) send({ quantity: q });
    else setQty(String(l.quantity));
  };
  const savePrice = () => {
    const t = price.trim();
    if (t === "") {
      if (l.customEur != null) send({ custom: null });
      return;
    }
    const v = num(t);
    if (Number.isFinite(v) && v >= 0) send({ custom: { amount: v, currency: cur } });
  };
  return (
    <div className="trade-line">
      <div className="trade-line-name">
        <strong>{displayName(l.card)}</strong>
        <span className="muted small">
          {" "}
          {l.card.setCode.toUpperCase()} #{l.card.collectorNumber}
        </span>
        <span className="num nowrap trade-line-total">
          {l.unitEur != null ? money(l.unitEur * l.quantity) : "–"}
          {l.custom && <span className="muted small"> (agreed)</span>}
        </span>
      </div>
      <div className="trade-line-fields">
        <input aria-label="Copies" type="number" min={1} max={9999} className="qty" value={qty} disabled={locked} onChange={(e) => setQty(e.target.value)} onBlur={saveQty} />
        <select aria-label="Finish" value={l.finish} disabled={locked || finishesOf(l.card).length < 2} onChange={(e) => send({ finish: e.target.value as Finish })}>
          {finishesOf(l.card).map((f) => (
            <option key={f} value={f}>
              {f === l.finish ? finishName(l) : FINISH_LABELS[f]}
            </option>
          ))}
        </select>
        <select aria-label="Condition" value={l.condition} disabled={locked} onChange={(e) => send({ condition: e.target.value })}>
          {CONDITIONS.map(([k]) => (
            <option key={k} value={k}>
              {k}
            </option>
          ))}
        </select>
        <select aria-label="Language" value={l.language} disabled={locked} onChange={(e) => send({ language: e.target.value })}>
          {LANGUAGES.map(([k]) => (
            <option key={k} value={k}>
              {k}
            </option>
          ))}
        </select>
        <input
          aria-label="Agreed price for one copy"
          className="price"
          inputMode="decimal"
          placeholder="Agreed price"
          title="An agreed price for one copy, instead of the price guide (empty: the price guide)"
          value={price}
          disabled={locked}
          onChange={(e) => setPrice(e.target.value)}
          onBlur={savePrice}
        />
        <select aria-label="Agreed price currency" value={cur} disabled={locked} onChange={(e) => setCur(e.target.value)}>
          {CURRENCIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <button className="btn danger" aria-label={`Remove ${l.card.name}`} disabled={locked} onClick={() => onEdit({ type: "removeLine", uid, line: l.key })}>
          ✕
        </button>
      </div>
    </div>
  );
}

/**
 * Adds a card to one side. Given cards come from your collection first (its piles, with their finish, condition and
 * language); any card can be searched too.
 */
export function AddTradeCardDialog({
  side,
  view,
  busy,
  onAdd,
  onClose,
}: {
  side: Side;
  view: TradesView;
  busy: boolean;
  onAdd: (e: Omit<Extract<TradeEdit, { type: "addLine" }>, "type" | "uid" | "side">) => void;
  onClose: () => void;
}) {
  const [source, setSource] = useState<"mine" | "any">(side === "GIVE" ? "mine" : "any");
  const [picked, setPicked] = useState<{ card: Printing["card"]; finish: Finish; condition: string; language: string; have?: number } | null>(null);
  const [qty, setQty] = useState("1");
  const [finish, setFinish] = useState<Finish>("NONFOIL");
  const [condition, setCondition] = useState("NM");
  const [language, setLanguage] = useState("EN");
  const q = num(qty);
  const qOk = Number.isInteger(q) && q >= 1 && q <= 9999;

  function choose(p: { card: Printing["card"]; finish: Finish; condition: string; language: string; have?: number }) {
    setPicked(p);
    setFinish(p.finish);
    setCondition(p.condition);
    setLanguage(p.language);
    setQty("1");
  }

  return (
    <Dialog title={side === "GET" ? "Add a card you get" : "Add a card you give"} onClose={onClose}>
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
              {picked.have != null && <div className="muted small">You have {picked.have} in this pile.</div>}
            </div>
          </div>
          <div className="form-grid">
            <label className="field">
              <span>Copies</span>
              <input type="number" min={1} max={9999} value={qty} onChange={(e) => setQty(e.target.value)} />
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
          </div>
          <div className="dialog-actions">
            <button className="btn" disabled={busy} onClick={() => setPicked(null)}>
              Back
            </button>
            <span className="spacer" />
            <button className="btn primary" disabled={busy || !qOk} onClick={() => onAdd({ card: picked.card, finish, condition, language, quantity: q })}>
              Add
            </button>
          </div>
        </div>
      ) : (
        <div className="stack">
          {side === "GIVE" && (
            <div className="seg text" role="group" aria-label="Where from">
              <button className={source === "mine" ? "on" : undefined} aria-pressed={source === "mine"} onClick={() => setSource("mine")}>
                From my collection
              </button>
              <button className={source === "any" ? "on" : undefined} aria-pressed={source === "any"} onClick={() => setSource("any")}>
                Any card
              </button>
            </div>
          )}
          {source === "mine" ? (
            <MyPiles view={view} onPick={(r) => choose({ card: r.card, finish: r.finish, condition: r.condition, language: r.language, have: r.quantity })} />
          ) : (
            <CardSearch
              currency={view.currency}
              perEuro={view.perEuro}
              onPick={(p) => choose({ card: p.card, finish: finishesOf(p.card)[0], condition: "NM", language: "EN" })}
              onClose={onClose}
            />
          )}
        </div>
      )}
    </Dialog>
  );
}

/** The collection's piles to pick a given card from, searched by name or set. */
function MyPiles({ view, onPick }: { view: TradesView; onPick: (r: CollRow) => void }) {
  const [rows, setRows] = useState<CollRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [text, setText] = useState("");
  useEffect(() => {
    api<CollectionView>("/api/collection").then((r) => {
      if (r.ok && Array.isArray(r.data.rows)) setRows(r.data.rows);
      else setError(r.data.error ?? "The collection could not be loaded.");
    });
  }, []);
  const shown = useMemo(() => {
    const q = text.trim().toLowerCase();
    if (!rows || q.length < 2) return [];
    return rows
      .filter((r) => r.card.name.toLowerCase().includes(q) || r.card.setName.toLowerCase().includes(q) || r.card.setCode.toLowerCase() === q)
      .sort((a, b) => a.card.name.localeCompare(b.card.name) || (b.unitEur ?? 0) - (a.unitEur ?? 0))
      .slice(0, 60);
  }, [rows, text]);
  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  return (
    <div className="stack">
      <input type="search" autoFocus aria-label="Search your collection" placeholder="Search your collection (name or set)" value={text} onChange={(e) => setText(e.target.value)} />
      {error && <div className="bar error">{error}</div>}
      {!rows && !error && <p className="muted small">Loading your collection…</p>}
      {rows && text.trim().length >= 2 && shown.length === 0 && <p className="muted small">No piles match. Try &ldquo;Any card&rdquo;.</p>}
      <ul className="plain-list pick-list">
        {shown.map((r) => (
          <li key={r.uid}>
            <button className="linkish row-pick" onClick={() => onPick(r)}>
              <span className="grow">
                {r.quantity}× {displayName(r.card)}
                <span className="muted small">
                  {" "}
                  · {r.card.setCode.toUpperCase()} #{r.card.collectorNumber}
                  {r.finish !== "NONFOIL" ? ` · ${finishName(r)}` : ""} · {r.condition}
                  {r.language !== "EN" ? ` · ${r.language}` : ""}
                </span>
              </span>
              <span className="num nowrap">{r.unitEur != null ? money(r.unitEur) : "–"}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Completing a trade: where the received cards go, and what happens to the given ones. */
export function CompleteTradeDialog({
  trade,
  view,
  busy,
  onComplete,
  onClose,
}: {
  trade: TradeCard;
  view: TradesView;
  busy: boolean;
  onComplete: (binder: string | null) => void;
  onClose: () => void;
}) {
  const [binder, setBinder] = useState("");
  const get = trade.lines.filter((l) => l.side === "GET").reduce((n, l) => n + l.quantity, 0);
  const give = trade.lines.filter((l) => l.side === "GIVE").reduce((n, l) => n + l.quantity, 0);
  return (
    <Dialog title="Complete the trade" onClose={onClose}>
      <div className="stack">
        <p>
          This updates your collection: the {get} card{get === 1 ? "" : "s"} you get are added, and the {give} card{give === 1 ? "" : "s"} you give are taken out (from
          Unsorted first, then your binders; plain copies before signed or altered ones). You can undo it later.
        </p>
        <label className="field">
          <span>Put the cards you get in</span>
          <select value={binder} onChange={(e) => setBinder(e.target.value)}>
            <option value="">Unsorted</option>
            {view.binders.map((b) => (
              <option key={b.uid} value={b.uid}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn primary" disabled={busy || trade.lines.length === 0} onClick={() => onComplete(binder || null)}>
            Complete trade
          </button>
        </div>
      </div>
    </Dialog>
  );
}
