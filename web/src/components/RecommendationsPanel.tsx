"use client";

import { useEffect, useState } from "react";
import type { DeckView } from "@/lib/deckshared";
import { format } from "@/lib/money";
import { NO_REC_FILTER, recKeep, REC_SOURCES, recState, type RecCard, type RecResult, type RecSource } from "@/lib/recshared";
import { api } from "./api";
import { RefreshButton } from "./Icons";
import { RecCardDialog } from "./RecCardDialog";
import { addKey, cachedRecs, keepRecs, RecFilters, RecTile, sendRec, useRecFilter, useRecKeepDays, useRecLayout, useRecSource, withFreshMarks, type RecDeck, type RecTarget } from "./RecTiles";
import { ViewSwitch } from "./ViewSwitch";

/**
 * The app's recommendations (Recommendations.kt): EDHREC's lists for the commander or recommander.cards' picks for
 * this decklist, without the cards already in the deck. Cards you own wear a green corner; each card goes to the
 * wishlist, the deck's maybeboard (a deck from Archidekt's local one) or, for a local deck, the deck itself. The
 * results are kept in this browser, shared with the all-decks page (/decks/recommendations).
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
  const [source, setSource] = useRecSource();
  const [mode, setMode] = useRecLayout();
  const [filter, pickFilter] = useRecFilter("mtg.recFilter", NO_REC_FILTER);
  const [keepDays] = useRecKeepDays();
  const [open, setOpen] = useState<RecCard | null>(null);
  const [result, setResult] = useState<RecResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Cards that went into the deck (they leave the list), the maybeboard or the wishlist on this visit.
  const [added, setAdded] = useState<Set<string>>(new Set());
  const [maybe, setMaybe] = useState<Set<string>>(new Set());
  const [wished, setWished] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState<string | null>(null);
  const money = (eur: number | null) => (eur == null ? "–" : format(eur * view.perEuro, view.currency));
  const deck: RecDeck = { id: view.id, name: view.deck.name, local: view.local };

  useEffect(() => {
    let live = true;
    setResult(null);
    setError(null);
    // The kept recommendations, with what you own and want now.
    cachedRecs(view.id, source)
      .then(async (r) => (r ? ((await withFreshMarks(new Map([[0, r]]))).get(0) ?? r) : null))
      .then((r) => {
        if (live) setResult(r);
      });
    return () => {
      live = false;
    };
  }, [view.id, source]);

  async function load() {
    setBusy(true);
    setError(null);
    const r = await api<RecResult>(`/api/decks/${view.id}/recommendations?source=${source}`);
    setBusy(false);
    if (r.ok && Array.isArray(r.data.sections)) {
      setResult(r.data);
      setWished(new Set());
      setMaybe(new Set());
      await keepRecs(view.id, source, r.data);
    } else setError(r.data.error ?? "Could not get recommendations.");
  }

  async function send(c: RecCard, to: RecTarget) {
    setAdding(addKey(to, view.id, c.name));
    setError(null);
    const failed = await sendRec(c, to, view.id);
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

  const state = recState(result, view.recKey, keepDays * 86_400_000);
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
            {adding === addKey("maybe", view.id, c.name) ? "…" : inMaybe(c) ? "✓ Maybe" : "+ Maybe"}
          </button>
        )}
        {canDeck && (
          <button type="button" className="rec-act deck" disabled={adding !== null} onClick={() => send(c, "deck")} title="Add one copy to the deck">
            {adding === addKey("deck", view.id, c.name) ? "…" : "+ Deck"}
          </button>
        )}
      </span>
    );

  return (
    <div className="stack recs">
      <div className="controls">
        <div className="seg text" role="group" aria-label="Source">
          {(Object.keys(REC_SOURCES) as RecSource[]).map((s) => (
            <button key={s} className={source === s ? "on" : ""} onClick={() => setSource(s)} disabled={busy}>
              {REC_SOURCES[s]}
            </button>
          ))}
        </div>
        <ViewSwitch layout={mode} onLayout={setMode} hover={hoverOn} onHover={onHoverOn} />
        <RefreshButton
          onClick={load}
          busy={busy}
          label={result ? undefined : "Get recommendations"}
          title={
            busy
              ? "Getting recommendations…"
              : state === "fresh"
                ? "Up to date (the list hasn't changed since); refresh anyway"
                : result
                  ? "Refresh recommendations"
                  : "Get recommendations"
          }
        />
        {result ? (
          <span className="muted small rec-date">
            {new Date(result.fetchedAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}
            {state === "fresh" ? " · up to date" : state === "changed" ? " · the list changed since" : " · older than " + (keepDays === 1 ? "a day" : `${keepDays} days`)}
          </span>
        ) : (
          <span className="spacer" />
        )}
        {onClose && (
          <button className="btn small-btn" onClick={onClose}>
            Close
          </button>
        )}
      </div>
      {result && <RecFilters filter={filter} onFilter={pickFilter} />}
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
            {s.cards.map((c) => (
              <RecTile key={c.name} c={c} mode={mode} hoverOn={hoverOn} hover={hover} money={money} actions={actions(c)} onOpen={() => setOpen(c)} />
            ))}
          </ul>
        </details>
      ))}
      {result && (
        <p className="muted small">
          {result.source === "edhrec" ? "From EDHREC's page for this commander." : "From recommander.cards for this commander and decklist."} Cards already in the deck are left out;
          &quot;New&quot; means first printed in the last year; a green corner or ✓ means you own the card. <a href="/decks/recommendations">Recommendations for all decks ›</a>
        </p>
      )}
      {open && (
        <RecCardDialog
          rec={open}
          source={source}
          money={money}
          readOnly={view.readOnly}
          decks={[deck]}
          onWishlist={onWish(open)}
          inMaybe={() => inMaybe(open)}
          inDeck={() => added.has(open.name)}
          adding={adding}
          onSend={(to) => send(open, to)}
          onClose={() => setOpen(null)}
        />
      )}
    </div>
  );
}
