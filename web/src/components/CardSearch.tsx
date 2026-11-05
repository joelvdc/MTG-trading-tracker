"use client";

import { useEffect, useState } from "react";
import { format, type Currency } from "@/lib/money";
import type { FoundCard } from "@/lib/scryfall";
import { api } from "./api";

export type Printing = FoundCard & { eur: number | null; eurFoil: number | null };

/** Search a card by name on Scryfall, then pick one of its printings (with pictures and prices). */
export function CardSearch({
  currency,
  perEuro,
  onPick,
  onClose,
}: {
  currency: Currency;
  perEuro: number;
  onPick: (p: Printing) => void;
  onClose: () => void;
}) {
  const [text, setText] = useState("");
  const [names, setNames] = useState<FoundCard[]>([]);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState<string | null>(null);
  const [prints, setPrints] = useState<Printing[] | null>(null);

  useEffect(() => {
    const q = text.trim();
    if (q.length < 2) {
      setNames([]);
      setSearching(false);
      return;
    }
    let live = true;
    setSearching(true);
    const t = setTimeout(async () => {
      const r = await api<{ results?: FoundCard[] }>(`/api/cards/search?q=${encodeURIComponent(q)}`);
      if (!live) return;
      setSearching(false);
      if (r.ok) {
        setError(null);
        setNames(r.data.results ?? []);
      } else {
        setError(r.data.error ?? "Search failed.");
      }
    }, 300);
    return () => {
      live = false;
      clearTimeout(t);
    };
  }, [text]);

  async function choose(n: string) {
    setName(n);
    setPrints(null);
    setError(null);
    const r = await api<{ results?: Printing[] }>(`/api/cards/prints?name=${encodeURIComponent(n)}`);
    if (r.ok) setPrints(r.data.results ?? []);
    else {
      setError(r.data.error ?? "Could not load the printings.");
      setName(null);
    }
  }

  const money = (eur: number | null) => (eur == null ? "–" : format(eur * perEuro, currency));

  return (
    <div className="stack">
      <input
        autoFocus
        type="search"
        aria-label="Search cards"
        placeholder="Card name (at least 2 letters)"
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          setName(null);
          setPrints(null);
        }}
      />
      {error && <div className="bar error">{error}</div>}
      {name && (
        <div className="stack">
          <div className="inline">
            <strong>{name}</strong>
            <button
              className="btn small-btn"
              onClick={() => {
                setName(null);
                setPrints(null);
              }}
            >
              Other cards
            </button>
          </div>
          {prints === null ? (
            <div className="muted">Loading printings…</div>
          ) : prints.length === 0 ? (
            <div className="muted">No printings found.</div>
          ) : (
            <div className="prints">
              {prints.map((p) => (
                <button key={p.card.scryfallId} className="print" onClick={() => onPick(p)}>
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
                    {p.eur != null || p.card.hasNonFoil ? money(p.eur) : ""}
                    {p.eurFoil != null ? ` / foil ${money(p.eurFoil)}` : ""}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
      {!name && (
        <>
          {searching && <div className="muted">Searching…</div>}
          {!searching && text.trim().length >= 2 && names.length === 0 && !error && <div className="muted">No cards found.</div>}
          <ul className="names">
            {names.map((n) => (
              <li key={n.card.scryfallId}>
                <button className="name-btn" onClick={() => choose(n.card.name)}>
                  <span>{n.card.name}</span>
                  <span className="muted small">{n.typeLine}</span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
      <div className="dialog-actions">
        <span className="spacer" />
        <button className="btn" onClick={onClose}>
          Cancel
        </button>
      </div>
    </div>
  );
}
