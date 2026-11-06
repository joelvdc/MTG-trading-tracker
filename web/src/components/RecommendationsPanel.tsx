"use client";

import { useEffect, useState } from "react";
import type { DeckView } from "@/lib/deckshared";
import { inclusionPct, REC_SOURCES, type RecCard, type RecResult, type RecSource } from "@/lib/recshared";
import { api } from "./api";
import { deckAction } from "./DeckEditDialogs";

const SOURCE_KEY = "mtg.recSource";

function store(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

/** What the source says about a card, in the app's words. */
function facts(c: RecCard): string {
  const out: string[] = [];
  const pct = inclusionPct(c);
  if (pct !== null) out.push(`in ${pct}% of decks`);
  if (c.synergy !== null) out.push(`synergy ${c.synergy >= 0 ? "+" : ""}${Math.round(c.synergy * 100)}%`);
  if (c.score !== null) out.push(`score ${c.score.toFixed(2)}`);
  return out.join(" · ");
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
        <button className="btn small-btn" onClick={load} disabled={busy}>
          {busy ? "Getting them…" : result ? "Refresh" : "Get recommendations"}
        </button>
        {result && <span className="muted small">{new Date(result.fetchedAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}</span>}
      </div>
      {!canAdd && view.local === false && <p className="muted small">To add cards from here, make the deck a local deck first (Edit ▾).</p>}
      {error && <div className="bar error">{error}</div>}
      {result?.note && <p className="warn-text small">{result.note}</p>}
      {result && result.sections.length === 0 && <p className="muted small">Nothing to recommend that isn&apos;t in the deck already.</p>}
      {result?.sections.map((s, i) => (
        <details key={s.key} open={i < 2}>
          <summary>
            <strong>{s.title}</strong> <span className="muted small">· {s.cards.filter((c) => !added.has(c.name)).length}</span>
          </summary>
          <ul className="rec-grid">
            {s.cards
              .filter((c) => !added.has(c.name))
              .map((c) => (
                <li key={c.name} onMouseMove={hover.follow(c.card?.imageUrl ?? null)} onMouseLeave={hover.hide}>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  {c.card?.imageUrl ? <img src={c.card.imageUrl.replace("/normal/", "/small/")} alt="" loading="lazy" /> : <span className="rec-noimg" />}
                  <span className="rec-text small">
                    <strong>{c.name}</strong>
                    {c.isNew && <span className="tag">New</span>}
                    <span className="muted">{facts(c)}</span>
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
    </div>
  );
}
