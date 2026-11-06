"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { useHoverCard, useHoverSetting } from "@/components/HoverCard";
import { TopBar } from "@/components/Nav";
import type { SharedCard } from "@/lib/decks";

/** Cards that are in more than one deck, with the copies those decks need against the copies you own (the app's SharedCardsScreen). */
export default function SharedCardsPage() {
  const [cards, setCards] = useState<SharedCard[] | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [onlyShort, setOnlyShort] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const [hoverOn, setHoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);

  useEffect(() => {
    (async () => {
      const [s, d] = await Promise.all([api<{ loggedIn: boolean; folder?: string }>("/api/session"), api<{ cards?: SharedCard[]; noFolder?: boolean }>("/api/decks/shared")]);
      if (!s.data.loggedIn || d.status === 401 || d.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || d.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (d.ok && Array.isArray(d.data.cards)) setCards(d.data.cards);
      else setError(d.data.error ?? "Could not load the decks.");
    })();
  }, []);

  const shown = useMemo(() => (cards ? (onlyShort ? cards.filter((c) => c.owned < c.need) : cards) : []), [cards, onlyShort]);

  return (
    <>
      <TopBar page="decks" folder={folder} />
      <main className="page">
        <a href="/decks" className="small">
          ← Decks
        </a>
        <h1 className="section-head">Cards in several decks</h1>
        {error && <div className="bar error">{error}</div>}
        {!cards && !error && <p className="muted center">Loading…</p>}
        {cards && (
          <>
            <p className="small">
              {cards.length} card{cards.length === 1 ? " is" : "s are"} in more than one deck (basic lands left out). Without enough copies you&apos;ll be moving them between decks.
            </p>
            <div className="controls">
              <button className={onlyShort ? "chip on" : "chip"} onClick={() => setOnlyShort((x) => !x)}>
                Only cards I don&apos;t have enough of
              </button>
              <span className="spacer" />
              <label className="check small">
                <input type="checkbox" checked={hoverOn} onChange={(e) => setHoverOn(e.target.checked)} />
                <span>Card on hover</span>
              </label>
            </div>
            {shown.length === 0 ? (
              <p className="muted center">{cards.length === 0 ? "No card is in more than one of your decks." : "You own enough copies of every shared card."}</p>
            ) : (
              <ul className="plain-list shared-list">
                {shown.map((c) => {
                  const k = c.name.toLowerCase();
                  const short = c.owned < c.need;
                  return (
                    <li key={k}>
                      <button className="shared-row" onClick={() => setOpen(open === k ? null : k)} onMouseMove={hover.follow(c.imageUrl)} onMouseLeave={hover.hide} aria-expanded={open === k}>
                        <span className="grow">
                          <strong>{c.name}</strong>
                          <span className="muted small shared-decks">
                            {" "}
                            · in {c.uses.length} decks: {c.uses.map((u) => u.deckName).join(", ")}
                          </span>
                        </span>
                        <span className={short ? "warn-text nowrap" : "ok-text nowrap"}>
                          own {c.owned} / {c.need}
                        </span>
                      </button>
                      {open === k && (
                        <ul className="plain-list shared-uses">
                          {c.uses.map((u) => (
                            <li key={u.deckId}>
                              <a href={`/decks/${u.deckId}`}>
                                {u.quantity}× in {u.deckName} ›
                              </a>
                            </li>
                          ))}
                        </ul>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </>
        )}
      </main>
      {hover.picture}
    </>
  );
}
