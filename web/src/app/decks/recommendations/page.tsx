"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/components/api";
import { useHoverCard, useHoverSetting } from "@/components/HoverCard";
import { TopBar } from "@/components/Nav";
import { RecCardDialog } from "@/components/RecCardDialog";
import { addKey, allCachedRecs, keepRecs, RecFilters, RecTile, sendRec, useRecFilter, useRecLayout, useRecSource, type RecDeck, type RecTarget } from "@/components/RecTiles";
import { ViewSwitch } from "@/components/ViewSwitch";
import type { DecksView } from "@/lib/deckshared";
import { format } from "@/lib/money";
import { NO_REC_FILTER, recKeep, recsByCard, REC_SOURCES, type RecAcross, type RecResult, type RecSource } from "@/lib/recshared";

/** Where a fetch-all run is: the deck being asked about, how many are done, and a pause asked for by a source. */
interface Job {
  current: string;
  done: number;
  total: number;
  /** While waiting because a source said it's busy: until when, and why. */
  waitUntil?: number;
  waitWhy?: string;
}

/** Between two decks, so the sources (Scryfall above all: 2 lookups a second) aren't asked too fast. */
const BETWEEN_MS = 1_500;
/** How often one deck is tried again after a source said it's busy. */
const BUSY_RETRIES = 2;

/**
 * Recommendations for all decks (website only): every deck's recommendations from EDHREC or
 * recommander.cards, each card once with the decks it fits, "By card" or only "For several decks". The decks are asked
 * one at a time from this page; the results are kept in this browser, shared with each deck's own window. Retired
 * decks are left out.
 */
export default function AllRecommendationsPage() {
  const [folder, setFolder] = useState("");
  const [view, setView] = useState<DecksView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [source, setSource] = useRecSource();
  const [mode, setMode] = useRecLayout();
  // Starts at "I own", as in the app: cards you already have that fit your decks.
  const [filter, pickFilter] = useRecFilter("mtg.recAllFilter", { ...NO_REC_FILTER, owned: "mine" });
  const [several, setSeveral] = useState(false);
  const [results, setResults] = useState<Map<number, RecResult> | null>(null);
  const [job, setJob] = useState<Job | null>(null);
  // The decks the last run got nothing for, with the reason.
  const [failures, setFailures] = useState<{ deck: RecDeck; why: string }[]>([]);
  const [, tick] = useState(0);
  const stop = useRef(false);
  const [open, setOpen] = useState<RecAcross | null>(null);
  const [adding, setAdding] = useState<string | null>(null);
  // What was sent on this visit: "deckId:name" for decks and maybeboards, names for the wishlist.
  const [inDeck, setInDeck] = useState<Set<string>>(new Set());
  const [inMaybe, setInMaybe] = useState<Set<string>>(new Set());
  const [wished, setWished] = useState<Set<string>>(new Set());
  const [hoverOn, setHoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);

  useEffect(() => {
    (async () => {
      const [s, d] = await Promise.all([api<{ loggedIn: boolean; folder?: string }>("/api/session"), api<DecksView & { noFolder?: boolean }>("/api/decks")]);
      if (!s.data.loggedIn || d.status === 401 || d.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || d.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (d.ok && Array.isArray(d.data.decks)) setView(d.data);
      else setError(d.data.error ?? "Could not load the decks.");
    })();
  }, []);

  useEffect(() => {
    let live = true;
    setResults(null);
    allCachedRecs(source).then((r) => {
      if (live) setResults(r);
    });
    return () => {
      live = false;
    };
  }, [source]);

  const decks: RecDeck[] = useMemo(() => (view?.decks ?? []).filter((d) => d.group !== "RETIRED").map((d) => ({ id: d.id, name: d.name, local: d.id < 0 })), [view]);
  const deckById = useMemo(() => new Map(decks.map((d) => [d.id, d])), [decks]);
  const have = useMemo(() => decks.filter((d) => results?.has(d.id)).map((d) => ({ deckId: d.id, result: results!.get(d.id)! })), [decks, results]);
  const shown = useMemo(() => recsByCard(have, (c) => recKeep(filter, c), several), [have, filter, several]);
  const oldest = have.length ? Math.min(...have.map((h) => h.result.fetchedAt)) : null;
  const money = (eur: number | null) => (eur == null || !view ? "–" : format(eur * view.perEuro, view.currency));
  const readOnly = view?.readOnly ?? true;

  /** Waits [ms] (showing a countdown), or less when Stop is pressed. */
  async function waitFor(ms: number, show: Job | null) {
    const until = Date.now() + ms;
    while (!stop.current && Date.now() < until) {
      if (show) setJob({ ...show, waitUntil: until });
      tick((n) => n + 1);
      await new Promise((r) => setTimeout(r, Math.min(1_000, until - Date.now())));
    }
  }

  /** Asks about [list] (every deck, or the ones that failed last time) one at a time. */
  async function fetchAll(list: RecDeck[] = decks) {
    stop.current = false;
    setError(null);
    setFailures([]);
    const failed: { deck: RecDeck; why: string }[] = [];
    let done = 0;
    for (const d of list) {
      if (stop.current) break;
      if (done > 0) await waitFor(BETWEEN_MS, null);
      for (let attempt = 0; !stop.current; attempt++) {
        const now: Job = { current: d.name, done, total: list.length };
        setJob(now);
        const r = await api<RecResult & { busy?: boolean; retryAfter?: number }>(`/api/decks/${d.id}/recommendations?source=${source}`);
        if (r.ok && Array.isArray(r.data.sections)) {
          await keepRecs(d.id, source, r.data);
          setResults((old) => new Map(old ?? []).set(d.id, r.data));
          break;
        }
        const why = r.status === 504 ? "the website took too long" : (r.data.error ?? `the website answered ${r.status || "nothing"}`);
        // A source asked to slow down: wait as long as it asked (plus a little) and try the same deck again.
        if (r.data.busy && attempt < BUSY_RETRIES) {
          await waitFor(((r.data.retryAfter ?? 30) + 3) * 1000, { ...now, waitWhy: why.split(";")[0] });
          continue;
        }
        failed.push({ deck: d, why });
        break;
      }
      done++;
    }
    setJob(null);
    setFailures(failed);
    if (failed.length) setError(`${failed.length} of ${done} decks got no recommendations this time:`);
  }

  async function send(x: RecAcross, to: RecTarget, deckId: number | null) {
    const name = x.card.name;
    setAdding(addKey(to, deckId, name));
    setError(null);
    setNotice(null);
    const failed = await sendRec(x.card, to, deckId);
    setAdding(null);
    if (failed) return setError(failed);
    const deck = deckId == null ? null : deckById.get(deckId);
    if (to === "wish") {
      setWished((s) => new Set(s).add(name));
      setNotice(`${name} added to the wishlist.`);
    } else if (to === "deck") {
      setInDeck((s) => new Set(s).add(`${deckId}:${name}`));
      setNotice(`${name} added to ${deck?.name ?? "the deck"}.`);
    } else {
      setInMaybe((s) => new Set(s).add(`${deckId}:${name}`));
      setNotice(deck?.local ? `${name} added to ${deck.name}'s maybeboard.` : `${name} added to ${deck?.name ?? "the deck"}'s local maybeboard (kept here and in the app; Archidekt doesn't see it).`);
    }
  }

  const onWish = (x: RecAcross) => !!x.card.wished || wished.has(x.card.name);
  const forText = (x: RecAcross) => "For " + x.deckIds.map((id) => deckById.get(id)?.name ?? "?").join(", ");

  return (
    <>
      <TopBar page="decks" folder={folder} />
      <main className="page">
        <a href="/decks" className="small">
          ← Decks
        </a>
        <h1 className="section-head">Recommendations for all decks</h1>
        {!view && !error && <p className="muted center">Loading…</p>}
        {view && (
          <div className="stack recs">
            <div className="controls">
              <div className="seg text" role="group" aria-label="Source">
                {(Object.keys(REC_SOURCES) as RecSource[]).map((s) => (
                  <button key={s} className={source === s ? "on" : ""} onClick={() => setSource(s)} disabled={job !== null}>
                    {REC_SOURCES[s]}
                  </button>
                ))}
              </div>
              <ViewSwitch layout={mode} onLayout={setMode} hover={hoverOn} onHover={setHoverOn} />
            </div>
            <div className="controls">
              <span className="small grow">
                {decks.length === 0
                  ? "No decks yet."
                  : `${have.length} of ${decks.length} decks have ${REC_SOURCES[source]} recommendations` +
                    (oldest ? ` (oldest ${new Date(oldest).toLocaleDateString("en-GB", { dateStyle: "medium" })})` : "")}
              </span>
              {job ? (
                <button className="btn small-btn" onClick={() => (stop.current = true)}>
                  Stop
                </button>
              ) : (
                <button className="btn small-btn primary" onClick={() => fetchAll()} disabled={decks.length === 0 || results === null}>
                  {have.length === 0 ? "Get them" : "Refresh all"}
                </button>
              )}
            </div>
            {job && (
              <div className="rec-job small">
                {job.waitUntil
                  ? `${job.current}: ${job.waitWhy ?? "the source is busy"}. Trying again in ${Math.max(0, Math.ceil((job.waitUntil - Date.now()) / 1000))} s (${job.done + 1} of ${job.total}) · keep this page open`
                  : `Getting recommendations: ${job.current} (${job.done + 1} of ${job.total}) · keep this page open`}
                <progress max={job.total} value={job.done} />
              </div>
            )}
            {error && (
              <div className="bar error">
                {error}
                {failures.length > 0 && (
                  <ul className="rec-failures">
                    {failures.map((f) => (
                      <li key={f.deck.id}>
                        {f.deck.name}: {f.why}
                      </li>
                    ))}
                  </ul>
                )}
                {failures.length > 0 && !job && (
                  <button className="btn small-btn" onClick={() => fetchAll(failures.map((f) => f.deck))}>
                    Try these {failures.length === 1 ? "" : `${failures.length} `}again
                  </button>
                )}
              </div>
            )}
            {notice && (
              <div className="bar ok" role="status">
                {notice}
              </div>
            )}
            <div className="chips" role="group" aria-label="View">
              <button type="button" className={several ? "chip" : "chip on"} aria-pressed={!several} onClick={() => setSeveral(false)}>
                By card
              </button>
              <button type="button" className={several ? "chip on" : "chip"} aria-pressed={several} onClick={() => setSeveral(true)}>
                For several decks
              </button>
            </div>
            <RecFilters filter={filter} onFilter={pickFilter} />
            {results === null ? (
              <p className="muted center">Loading…</p>
            ) : shown.length === 0 ? (
              <p className="muted center">
                {have.length === 0
                  ? `Press “Get them” to ask ${REC_SOURCES[source]} about every deck (a few seconds per deck).`
                  : filter.owned === "mine"
                    ? "None of the recommended cards are in your collection. Change the filter to see the others."
                    : "No recommendation matches these filters."}
              </p>
            ) : (
              <>
                <p className="small">
                  <strong>
                    {shown.length} card{shown.length === 1 ? "" : "s"}
                    {filter.owned === "mine" ? ` you own that fit${shown.length === 1 ? "s" : ""} your decks` : ""}
                  </strong>
                </p>
                <ul className={mode === "list" ? "rec-grid list" : "rec-grid images"}>
                  {shown.map((x) => (
                    <RecTile
                      key={x.card.name}
                      c={x.card}
                      mode={mode}
                      hoverOn={hoverOn}
                      hover={hover}
                      money={money}
                      note={forText(x)}
                      onOpen={() => setOpen(x)}
                      actions={
                        x.card.card && (
                          <span className="rec-actions">
                            <button
                              type="button"
                              className={onWish(x) ? "rec-act wish on" : "rec-act wish"}
                              disabled={adding !== null || onWish(x) || readOnly}
                              onClick={() => send(x, "wish", null)}
                              title={onWish(x) ? "On the wishlist" : "Add to the wishlist"}
                              aria-label={onWish(x) ? "On the wishlist" : "Add to the wishlist"}
                            >
                              {onWish(x) ? "★" : "☆"}
                            </button>
                          </span>
                        )
                      }
                    />
                  ))}
                </ul>
              </>
            )}
            <p className="muted small">
              Each deck&apos;s list leaves out the cards already in that deck; &quot;New&quot; means first printed in the last year; a green corner or ✓ means you own the card. Open a
              card to put it in one of its decks or a maybeboard. The recommendations are kept in this browser (each deck&apos;s own window uses them too); retired decks
              are left out.
            </p>
          </div>
        )}
      </main>
      {hover.picture}
      {open && (
        <RecCardDialog
          rec={open.card}
          source={source}
          money={money}
          readOnly={readOnly}
          named
          decks={open.deckIds.map((id) => deckById.get(id)).filter((d): d is RecDeck => !!d)}
          onWishlist={onWish(open)}
          inMaybe={(id) => inMaybe.has(`${id}:${open.card.name}`)}
          inDeck={(id) => inDeck.has(`${id}:${open.card.name}`)}
          adding={adding}
          onSend={(to, deckId) => send(open, to, deckId)}
          onClose={() => setOpen(null)}
        />
      )}
    </>
  );
}
