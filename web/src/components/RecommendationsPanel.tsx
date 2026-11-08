"use client";

import { useEffect, useState } from "react";
import type { DeckView } from "@/lib/deckshared";
import { recFacts, REC_SOURCES, type RecCard, type RecResult, type RecSource } from "@/lib/recshared";
import { api } from "./api";
import { deckAction } from "./DeckEditDialogs";
import { RecCardDialog } from "./RecCardDialog";

const SOURCE_KEY = "mtg.recSource";
const VIEW_KEY = "mtg.recView";

function store(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

/**
 * The app's recommendations (Recommendations.kt): EDHREC's lists for the commander or recommander.cards' picks for
 * this decklist, without the cards already in the deck. A local deck takes a card with one click.
 */
export function RecommendationsPanel({
  view,
  hover,
  onAdded,
}: {
  view: DeckView;
  hover: { follow: (url: string | null) => (e: React.MouseEvent) => void; hide: () => void };
  onAdded: (notice: string) => void;
}) {
  const [source, setSource] = useState<RecSource>("edhrec");
  const [mode, setMode] = useState<"images" | "list">("images");
  const [open, setOpen] = useState<RecCard | null>(null);
  const [result, setResult] = useState<RecResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [added, setAdded] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState<string | null>(null);
  const cacheKey = `mtg.recs.${view.id}.${source}`;

  useEffect(() => {
    try {
      const s = localStorage.getItem(SOURCE_KEY);
      if (s === "edhrec" || s === "recommander") setSource(s);
      const m = localStorage.getItem(VIEW_KEY);
      if (m === "images" || m === "list") setMode(m);
    } catch {
      // Storage unavailable.
    }
  }, []);

  useEffect(() => {
    const s = store()?.getItem(cacheKey);
    setResult(s ? (JSON.parse(s) as RecResult) : null);
    setError(null);
  }, [cacheKey]);

  function pick(s: RecSource) {
    setSource(s);
    try {
      localStorage.setItem(SOURCE_KEY, s);
    } catch {
      // Not remembered.
    }
  }

  function pickMode(m: "images" | "list") {
    setMode(m);
    try {
      localStorage.setItem(VIEW_KEY, m);
    } catch {
      // Not remembered.
    }
  }

  async function load() {
    setBusy(true);
    setError(null);
    const r = await api<RecResult>(`/api/decks/${view.id}/recommendations?source=${source}`);
    setBusy(false);
    if (r.ok && Array.isArray(r.data.sections)) {
      setResult(r.data);
      try {
        store()?.setItem(cacheKey, JSON.stringify(r.data));
      } catch {
        // Too big to keep: fetched again next time.
      }
    } else setError(r.data.error ?? "Could not get recommendations.");
  }

  async function add(c: RecCard) {
    if (!c.card) return;
    setAdding(c.name);
    setError(null);
    const r = await deckAction(view.id, { action: "add-card", card: c.card, finish: c.card.hasNonFoil ? "NONFOIL" : c.card.hasFoil ? "FOIL" : "ETCHED", quantity: 1, category: null });
    setAdding(null);
    if (r.error) setError(r.error);
    else {
      setAdded((s) => new Set(s).add(c.name));
      if (open?.name === c.name) setOpen(null);
      onAdded(`${c.name} added to the deck.`);
    }
  }

  const canAdd = view.local && !view.readOnly;
  return (
    <div className="stack recs">
      <div className="controls">
        <div className="seg text" role="group" aria-label="Source">
          {(Object.keys(REC_SOURCES) as RecSource[]).map((s) => (
            <button key={s} className={source === s ? "on" : ""} onClick={() => pick(s)} disabled={busy}>
              {REC_SOURCES[s]}
            </button>
          ))}
        </div>
        <div className="seg text" role="group" aria-label="View as">
          <button className={mode === "images" ? "on" : ""} onClick={() => pickMode("images")}>
            Images
          </button>
          <button className={mode === "list" ? "on" : ""} onClick={() => pickMode("list")}>
            List
          </button>
        </div>
        <button className="btn small-btn" onClick={load} disabled={busy}>
          {busy ? "Getting them…" : result ? "Refresh" : "Get recommendations"}
        </button>
        {result && <span className="muted small">{new Date(result.fetchedAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}</span>}
      </div>
      {!canAdd && view.local === false && <p className="muted small">To add cards from here, make the deck a local deck first (Edit ▾).</p>}
      {error && <div className="bar error">{error}</div>}
      {result?.note && <p className="warn-text small">{result.note}</p>}
      {result && result.sections.length === 0 && <p className="muted small">Nothing to recommend that isn&apos;t in the deck already.</p>}
      {result?.sections.map((s) => (
        <details key={s.key} open>
          <summary>
            <strong>{s.title}</strong> <span className="muted small">· {s.cards.filter((c) => !added.has(c.name)).length}</span>
          </summary>
          <ul className={mode === "list" ? "rec-grid list" : "rec-grid"}>
            {s.cards
              .filter((c) => !added.has(c.name))
              .map((c) => (
                <li
                  key={c.name}
                  className={c.card ? "clickable" : undefined}
                  role={c.card ? "button" : undefined}
                  tabIndex={c.card ? 0 : undefined}
                  title={c.card ? "Open this card" : undefined}
                  onClick={(e) => {
                    if (!c.card || (e.target as HTMLElement).closest("button")) return;
                    hover.hide();
                    setOpen(c);
                  }}
                  onKeyDown={(e) => {
                    if (!c.card || e.target !== e.currentTarget) return;
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      hover.hide();
                      setOpen(c);
                    }
                  }}
                  onMouseMove={hover.follow(c.card?.imageUrl ?? null)}
                  onMouseLeave={hover.hide}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  {c.card?.imageUrl ? <img src={c.card.imageUrl.replace("/normal/", "/small/")} alt="" loading="lazy" /> : <span className="rec-noimg" />}
                  <span className="rec-text small">
                    <strong>{c.name}</strong>
                    {c.isNew && <span className="tag">New</span>}
                    <span className="muted">{recFacts(c)}</span>
                  </span>
                  {canAdd && c.card && (
                    <button className="btn small-btn" disabled={adding !== null} onClick={() => add(c)} title="Add one copy to the deck">
                      {adding === c.name ? "…" : "Add"}
                    </button>
                  )}
                </li>
              ))}
          </ul>
        </details>
      ))}
      {result && (
        <p className="muted small">
          {result.source === "edhrec" ? "From EDHREC's page for this commander." : "From recommander.cards for this commander and decklist."} Cards already in the deck are left out; &quot;New&quot; means
          first printed in the last year.
        </p>
      )}
      {open && (
        <RecCardDialog
          view={view}
          rec={open}
          source={source}
          canAdd={canAdd}
          adding={adding === open.name}
          onAdd={() => add(open)}
          onClose={() => setOpen(null)}
        />
      )}
    </div>
  );
}
