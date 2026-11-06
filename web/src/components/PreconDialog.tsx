"use client";

import { useEffect, useState } from "react";
import { api } from "@/components/api";
import type { PreconInfo } from "@/lib/precons";
import { Dialog } from "./Dialog";

interface DeckAnswer {
  info: PreconInfo;
  count: number;
  worth: number;
  cards: { name: string; quantity: number; foil: boolean; worth: number | null }[];
}

const NEW = "__new__";

function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * Add a preconstructed Commander deck (the app's Precon screen): pick the deck, then the binder, the price paid and
 * the day. Its cards (no basic lands) go into the binder, each with its share of the price, and the deck is
 * remembered as an order.
 */
export function PreconDialog({ onDone, onClose }: { onDone: (summary: string) => void; onClose: () => void }) {
  const [query, setQuery] = useState("");
  const [list, setList] = useState<{ decks: PreconInfo[]; more: number } | null>(null);
  const [deck, setDeck] = useState<DeckAnswer | null>(null);
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("EUR");
  const [date, setDate] = useState(today());
  const [binders, setBinders] = useState<{ uid: string; name: string }[]>([]);
  const [binder, setBinder] = useState(NEW);
  const [newName, setNewName] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(async () => {
      const r = await api<{ decks?: PreconInfo[]; more?: number }>(`/api/precons?q=${encodeURIComponent(query)}`);
      if (r.ok && r.data.decks) setList({ decks: r.data.decks, more: r.data.more ?? 0 });
      else setError(r.data.error ?? "Could not get MTGJSON's list of decks.");
    }, 250);
    return () => clearTimeout(t);
  }, [query]);

  useEffect(() => {
    api<{ binders?: { uid: string; name: string }[]; currency?: string }>("/api/collection").then((r) => {
      if (r.ok && r.data.binders) setBinders(r.data.binders);
      if (r.ok && r.data.currency) setCurrency(r.data.currency);
    });
  }, []);

  async function pick(d: PreconInfo) {
    setBusy("Getting the deck's cards…");
    setError(null);
    const r = await api<DeckAnswer>(`/api/precons?deck=${encodeURIComponent(d.fileName)}`);
    setBusy(null);
    if (!r.ok || !r.data.cards) return setError(r.data.error ?? "Could not get the deck.");
    setDeck(r.data);
    setNewName(d.name);
    if (d.releaseDate && /^\d{4}-\d{2}-\d{2}$/.test(d.releaseDate) && d.releaseDate > today()) setDate(today());
  }

  const paid = Number(amount.replace(",", "."));
  const paidOk = amount.trim() !== "" && Number.isFinite(paid) && paid >= 0;

  async function add() {
    if (!deck || !paidOk) return;
    setBusy("Adding the cards…");
    setError(null);
    const r = await api<{ added?: number; deck?: string; top?: string[] }>("/api/precons", {
      deck: deck.info.fileName,
      paid: { amount: paid, currency },
      date,
      binder: binder === NEW || binder === "" ? null : binder,
      newBinder: binder === NEW ? newName.trim() : null,
    });
    setBusy(null);
    if (!r.ok || r.data.added === undefined) return setError(r.data.error ?? "Adding the deck did not work.");
    const where = binder === NEW ? newName.trim() : binder === "" ? "Unsorted" : (binders.find((b) => b.uid === binder)?.name ?? "the binder");
    onDone(`Added ${r.data.added} cards of ${r.data.deck} to ${where}; most of the price went to ${(r.data.top ?? []).join(", ")}. The deck is in the order history.`);
  }

  return (
    <Dialog title={deck ? deck.info.name : "Add a preconstructed deck"} onClose={() => !busy && onClose()}>
      <div className="stack">
        {!deck && (
          <>
            <p className="muted small">
              Commander precons from MTGJSON&apos;s deck lists. The deck&apos;s cards (without the basic lands) go into a binder, each
              with its share of what you paid, split by what the cards are worth today.
            </p>
            <label className="field">
              <span>Deck name or set code</span>
              <input type="text" autoFocus value={query} onChange={(e) => setQuery(e.target.value)} placeholder="e.g. clue, MKC" />
            </label>
            <div className="order-review">
              {!list && <p className="muted">Loading the decks…</p>}
              {list && list.decks.length === 0 && <p className="muted">No deck with that name.</p>}
              <ul className="plain-list">
                {list?.decks.map((d) => (
                  <li key={d.fileName}>
                    <button className="link grow left" disabled={!!busy} onClick={() => pick(d)}>
                      {d.name}
                    </button>
                    <span className="muted small nowrap">
                      {d.code} · {d.releaseDate?.slice(0, 4) ?? ""}
                    </span>
                  </li>
                ))}
              </ul>
              {list && list.more > 0 && <p className="muted small">…and {list.more} more: type more of the name.</p>}
            </div>
          </>
        )}
        {deck && (
          <>
            <p className="small">
              {deck.count} cards without the basic lands, worth €{deck.worth.toFixed(2)} today (Scryfall&apos;s euro prices).
            </p>
            <div className="form-grid">
              <label className="field">
                <span>What it cost</span>
                <input type="text" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" disabled={!!busy} />
              </label>
              <label className="field">
                <span>Currency</span>
                <select value={currency} onChange={(e) => setCurrency(e.target.value)} disabled={!!busy}>
                  <option value="EUR">€</option>
                  <option value="DKK">kr.</option>
                  <option value="USD">$</option>
                </select>
              </label>
              <label className="field">
                <span>Bought on</span>
                <input type="date" value={date} onChange={(e) => setDate(e.target.value)} disabled={!!busy} />
              </label>
            </div>
            <label className="field">
              <span>Add the cards to</span>
              <select value={binder} onChange={(e) => setBinder(e.target.value)} disabled={!!busy}>
                <option value={NEW}>A new binder…</option>
                <option value="">Unsorted</option>
                {binders.map((b) => (
                  <option key={b.uid} value={b.uid}>
                    {b.name}
                  </option>
                ))}
              </select>
            </label>
            {binder === NEW && (
              <label className="field">
                <span>New binder&apos;s name</span>
                <input type="text" maxLength={60} value={newName} onChange={(e) => setNewName(e.target.value)} disabled={!!busy} />
              </label>
            )}
          </>
        )}
        {busy && <div className="bar">{busy}</div>}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          {deck && (
            <button className="btn" disabled={!!busy} onClick={() => setDeck(null)}>
              Another deck
            </button>
          )}
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={!!busy}>
            Cancel
          </button>
          {deck && (
            <button className="btn primary" disabled={!!busy || !paidOk || (binder === NEW && !newName.trim())} onClick={add}>
              Add {deck.count} cards
            </button>
          )}
        </div>
      </div>
    </Dialog>
  );
}
