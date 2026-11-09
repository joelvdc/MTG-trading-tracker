"use client";

import { useEffect, useState } from "react";
import type { DeckView } from "@/lib/deckshared";
import { format } from "@/lib/money";
import {
  NO_REC_FILTER,
  recFacts,
  recKeep,
  REC_OWNED,
  REC_SOURCES,
  REC_TYPES,
  type RecCard,
  type RecFilter,
  type RecOwned,
  type RecResult,
  type RecSource,
} from "@/lib/recshared";
import { api } from "./api";
import { deckAction } from "./DeckEditDialogs";
import { RefreshButton } from "./Icons";
import { ManaCost } from "./Mana";
import { RecCardDialog } from "./RecCardDialog";
import { ViewSwitch, type Layout } from "./ViewSwitch";

const SOURCE_KEY = "mtg.recSource";
const VIEW_KEY = "mtg.recView";
const FILTER_KEY = "mtg.recFilter";

function store(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

/** Where a recommended card can go: the deck itself (local decks), its maybeboard, or the wishlist. */
export type RecTarget = "deck" | "maybe" | "wish";

/**
 * The app's recommendations (Recommendations.kt): EDHREC's lists for the commander or recommander.cards' picks for
 * this decklist, without the cards already in the deck. Cards you own wear a green corner; each card goes to the
 * wishlist, the deck's maybeboard (a deck from Archidekt's local one) or, for a local deck, the deck itself.
 */
export function RecommendationsPanel({
  view,
  hover,
  hoverOn,
  onHoverOn,
  onAdded,
  onClose,
}: {
  view: DeckView;
  hover: { follow: (url: string | null) => (e: React.MouseEvent) => void; hide: () => void };
  /** The deck page's "Card on hover" switch (the page shows the hover picture). */
  hoverOn: boolean;
  onHoverOn: (on: boolean) => void;
  onAdded: (notice: string) => void;
  /** Shown in the window's toolbar (the deck page opens the recommendations as a full-screen window). */
  onClose?: () => void;
}) {
  const [source, setSource] = useState<RecSource>("edhrec");
  const [mode, setMode] = useState<Layout>("images");
  const [open, setOpen] = useState<RecCard | null>(null);
  const [result, setResult] = useState<RecResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Cards that went into the deck (they leave the list), the maybeboard or the wishlist on this visit.
  const [added, setAdded] = useState<Set<string>>(new Set());
  const [maybe, setMaybe] = useState<Set<string>>(new Set());
  const [wished, setWished] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState<string | null>(null);
  const [filter, setFilter] = useState<RecFilter>(NO_REC_FILTER);
  // v3: results cached before owned / wished existed are fetched again.
  const cacheKey = `mtg.recs.v3.${view.id}.${source}`;
  const money = (eur: number | null) => (eur == null ? "–" : format(eur * view.perEuro, view.currency));
  /** One price: the nonfoil one, or the foil one (marked ✦) when the card only comes in foil. */
  const priceOf = (c: RecCard) => {
    const card = c.card;
    if (!card) return null;
    if (card.fallbackEur != null || card.hasNonFoil) return { text: money(card.fallbackEur), foil: false };
    return { text: money(card.fallbackEurFoil), foil: card.fallbackEurFoil != null };
  };

  useEffect(() => {
    try {
      const s = localStorage.getItem(SOURCE_KEY);
      if (s === "edhrec" || s === "recommander") setSource(s);
      const m = localStorage.getItem(VIEW_KEY);
      if (m === "images" || m === "list") setMode(m);
      const f = localStorage.getItem(FILTER_KEY);
      if (f) setFilter({ ...NO_REC_FILTER, ...(JSON.parse(f) as Partial<RecFilter>) });
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

  function pickMode(m: Layout) {
    setMode(m);
    try {
      localStorage.setItem(VIEW_KEY, m);
    } catch {
      // Not remembered.
    }
  }

  function pickFilter(change: Partial<RecFilter>) {
    setFilter((old) => {
      const next = { ...old, ...change };
      try {
        localStorage.setItem(FILTER_KEY, JSON.stringify(next));
      } catch {
        // Not remembered.
      }
      return next;
    });
  }

  async function load() {
    setBusy(true);
    setError(null);
    const r = await api<RecResult>(`/api/decks/${view.id}/recommendations?source=${source}`);
    setBusy(false);
    if (r.ok && Array.isArray(r.data.sections)) {
      setResult(r.data);
      setWished(new Set());
      setMaybe(new Set());
      try {
        store()?.setItem(cacheKey, JSON.stringify(r.data));
      } catch {
        // Too big to keep: fetched again next time.
      }
    } else setError(r.data.error ?? "Could not get recommendations.");
  }

  async function send(c: RecCard, to: RecTarget) {
    const card = c.card;
    if (!card) return;
    setAdding(`${to}:${c.name}`);
    setError(null);
    const finish = card.hasNonFoil ? "NONFOIL" : card.hasFoil ? "FOIL" : "ETCHED";
    let failed: string | null;
    if (to === "wish") {
      const r = await api<{ error?: string }>("/api/wishlist", { edit: { type: "add", card, foil: !card.hasNonFoil && card.hasFoil, quantity: 1, anyPrinting: true } });
      failed = r.ok ? null : (r.data.error ?? "That did not work.");
    } else {
      failed = (await deckAction(view.id, to === "deck" ? { action: "add-card", card, finish, quantity: 1, category: null } : { action: "add-maybe", card, finish })).error;
    }
    setAdding(null);
    if (failed) return setError(failed);
    if (to === "deck") {
      setAdded((s) => new Set(s).add(c.name));
      if (open?.name === c.name) setOpen(null);
      onAdded(`${c.name} added to the deck.`);
    } else if (to === "maybe") {
      setMaybe((s) => new Set(s).add(c.name));
      onAdded(view.local ? `${c.name} added to the maybeboard.` : `${c.name} added to the deck's local maybeboard (kept here and in the app; Archidekt doesn't see it).`);
    } else {
      setWished((s) => new Set(s).add(c.name));
      onAdded(`${c.name} added to the wishlist.`);
    }
  }

  const canDeck = view.local && !view.readOnly;
  const canMaybe = !view.readOnly;
  const onWish = (c: RecCard) => !!c.wished || wished.has(c.name);
  const inMaybe = (c: RecCard) => maybe.has(c.name);

  // The sections as shown: without the cards added to the deck on this visit, filtered, empty ones left out.
  const shown = (result?.sections ?? [])
    .map((s) => ({ ...s, cards: s.cards.filter((c) => !added.has(c.name) && recKeep(filter, c)) }))
    .filter((s) => s.cards.length > 0);

  /** The buttons shown on a card when the mouse is over it (always on a touch screen). */
  const actions = (c: RecCard) =>
    c.card && (
      <span className="rec-actions">
        <button
          type="button"
          className={onWish(c) ? "rec-act wish on" : "rec-act wish"}
          disabled={adding !== null || onWish(c) || view.readOnly}
          onClick={() => send(c, "wish")}
          title={onWish(c) ? "On the wishlist" : "Add to the wishlist"}
          aria-label={onWish(c) ? "On the wishlist" : "Add to the wishlist"}
        >
          {onWish(c) ? "★" : "☆"}
        </button>
        {canMaybe && (
          <button
            type="button"
            className={inMaybe(c) ? "rec-act maybe on" : "rec-act maybe"}
            disabled={adding !== null || inMaybe(c)}
            onClick={() => send(c, "maybe")}
            title={view.local ? "Add to the maybeboard" : "Add to the deck's local maybeboard (Archidekt doesn't see it)"}
          >
            {adding === `maybe:${c.name}` ? "…" : inMaybe(c) ? "✓ Maybe" : "+ Maybe"}
          </button>
        )}
        {canDeck && (
          <button type="button" className="rec-act deck" disabled={adding !== null} onClick={() => send(c, "deck")} title="Add one copy to the deck">
            {adding === `deck:${c.name}` ? "…" : "+ Deck"}
          </button>
        )}
      </span>
    );

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
        <ViewSwitch layout={mode} onLayout={pickMode} hover={hoverOn} onHover={onHoverOn} />
        <RefreshButton
          onClick={load}
          busy={busy}
          label={result ? undefined : "Get recommendations"}
          title={busy ? "Getting recommendations…" : result ? "Refresh recommendations" : "Get recommendations"}
        />
        {result ? <span className="muted small rec-date">{new Date(result.fetchedAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}</span> : <span className="spacer" />}
        {onClose && (
          <button className="btn small-btn" onClick={onClose}>
            Close
          </button>
        )}
      </div>
      {result && (
        <div className="chips rec-filters" role="group" aria-label="Filters">
          <button type="button" className={filter.newOnly ? "chip on" : "chip"} aria-pressed={filter.newOnly} onClick={() => pickFilter({ newOnly: !filter.newOnly })}>
            New cards
          </button>
          {(Object.keys(REC_OWNED) as RecOwned[]).map((o) => (
            <button key={o} type="button" className={filter.owned === o ? "chip on" : "chip"} aria-pressed={filter.owned === o} onClick={() => pickFilter({ owned: o })}>
              {REC_OWNED[o]}
            </button>
          ))}
          <select value={filter.type ?? ""} onChange={(e) => pickFilter({ type: e.target.value || null })} aria-label="Card type">
            <option value="">All types</option>
            {REC_TYPES.map(([t, label]) => (
              <option key={t} value={t}>
                {label}
              </option>
            ))}
          </select>
        </div>
      )}
      {!view.local && !view.readOnly && (
        <p className="muted small">Decks from Archidekt are read only: + Maybe puts a card in the deck&apos;s local maybeboard, kept here and in the app (Archidekt doesn&apos;t see it).</p>
      )}
      {error && <div className="bar error">{error}</div>}
      {result?.note && <p className="warn-text small">{result.note}</p>}
      {result && result.sections.length === 0 && <p className="muted small">Nothing to recommend that isn&apos;t in the deck already.</p>}
      {result && result.sections.length > 0 && shown.length === 0 && <p className="muted small">No recommendation matches these filters.</p>}
      {shown.map((s) => (
        <details key={s.key} open>
          <summary>
            <strong>{s.title}</strong> <span className="muted small">· {s.cards.length}</span>
          </summary>
          <ul className={mode === "list" ? "rec-grid list" : "rec-grid images"}>
            {s.cards
              .map((c) => {
                const owned = c.owned ?? 0;
                const price = priceOf(c);
                return (
                  <li
                    key={c.name}
                    className={[c.card ? "clickable" : "", owned > 0 ? "owned" : ""].filter(Boolean).join(" ") || undefined}
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
                    onMouseMove={mode === "list" && hoverOn ? hover.follow(c.card?.imageUrl ?? null) : hover.hide}
                    onMouseLeave={hover.hide}
                  >
                    {mode === "images" && (
                      <span className="rec-pic">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        {c.card?.imageUrl ? <img src={c.card.imageUrl} alt="" loading="lazy" /> : <span className="rec-noimg">{c.name}</span>}
                        {owned > 0 && (
                          <span className="own-ribbon" title={`You own ${owned}`}>
                            <span>✓{owned > 1 ? owned : ""}</span>
                          </span>
                        )}
                        {actions(c)}
                      </span>
                    )}
                    <span className="rec-text small">
                      <span className="rec-name">
                        <strong title={c.name}>{c.name}</strong>
                        {c.isNew && <span className="tag">New</span>}
                        {mode === "list" && owned > 0 && (
                          <span className="own-tag" title={`You own ${owned}`}>
                            ✓ {owned}
                          </span>
                        )}
                      </span>
                      <span className="muted">{recFacts(c)}</span>
                    </span>
                    {(c.manaCost || price) && (
                      <span className="rec-side small">
                        {c.manaCost && (
                          <span className="rec-cost">
                            <ManaCost cost={c.manaCost} size={14} />
                          </span>
                        )}
                        {price && (
                          <span className="num nowrap rec-price" title={price.foil ? "Only printed in foil" : undefined}>
                            {price.text}
                            {price.foil && <span className="foil-text"> ✦</span>}
                          </span>
                        )}
                      </span>
                    )}
                    {mode === "list" && actions(c)}
                  </li>
                );
              })}
          </ul>
        </details>
      ))}
      {result && (
        <p className="muted small">
          {result.source === "edhrec" ? "From EDHREC's page for this commander." : "From recommander.cards for this commander and decklist."} Cards already in the deck are left out;
          &quot;New&quot; means first printed in the last year; a green corner or ✓ means you own the card.
        </p>
      )}
      {open && (
        <RecCardDialog
          view={view}
          rec={open}
          source={source}
          canDeck={canDeck}
          canMaybe={canMaybe}
          onWishlist={onWish(open)}
          inMaybe={inMaybe(open)}
          adding={adding}
          onSend={(to) => send(open, to)}
          onClose={() => setOpen(null)}
        />
      )}
    </div>
  );
}
