"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, peek, remember } from "@/components/api";
import { Dialog } from "@/components/Dialog";
import { TopBar } from "@/components/Nav";
import type { SortView } from "@/app/api/sort/route";
import { PLACE_LABELS, SORT_PLACES, SORT_LEVELS, UNSORTED_KEY, type SortMove, type SortPlace, type SortRules } from "@/lib/collsort";
import { format } from "@/lib/money";

/** A move's own key: a card's kind and the two places ("<kind>><from>><to>"). */
const rowKey = (m: Pick<SortMove, "key" | "from" | "to">) => `${m.key}>${m.from}>${m.to}`;
const served = (m: SortMove) => ({ key: m.key, from: m.from, to: m.to, copies: m.copies });

/** A card kind's key is "<scryfallId>|<foil>|<etched>|…" (the trade binder's CardKind.key). */
function finishOf(key: string): "etched" | "foil" | null {
  const p = key.split("|");
  if (p[2] === "true") return "etched";
  return p[1] === "true" ? "foil" : null;
}

/** "Collection binder 187/200 · Not bulk 640/1,000 · Bulk 1,204": a place without a limit has no "/". */
function countsLine(view: SortView): string {
  const places = SORT_PLACES.map((place) => {
    const n = view.plan.after[place] ?? 0;
    const max = place === "top" ? view.rules.maxTop : place === "mid" ? view.rules.maxMid : view.rules.maxBulk;
    const name = view.places[place]?.name ?? PLACE_LABELS[place];
    return max > 0 ? `${name} ${n.toLocaleString("en-GB")}/${max.toLocaleString("en-GB")}` : `${name} ${n.toLocaleString("en-GB")}`;
  });
  const unsorted = view.plan.after[UNSORTED_KEY] ?? 0;
  if (unsorted > 0) places.push(`Unsorted ${unsorted.toLocaleString("en-GB")}`);
  return places.join(" · ");
}

/** What a place is called: its binder's name, or its usual name when it isn't set up. */
const placeName = (view: SortView, place: string) =>
  place === UNSORTED_KEY ? "Unsorted" : (view.places[place as SortPlace]?.name ?? PLACE_LABELS[place as SortPlace] ?? place);

/**
 * Sort the collection (the app's Sort screen): sets up the three binders and the rules, then shows the suggested
 * moves grouped by where the cards are now. Tick what you moved and press Done; unticked moves aren't suggested again.
 */
export default function SortPage() {
  const [view, setView] = useState<SortView | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // The moves ticked off: by default every one the plan suggests.
  const [unticked, setUnticked] = useState<Set<string>>(new Set());
  const [settingUp, setSettingUp] = useState(false);
  const [startOver, setStartOver] = useState(false);

  useEffect(() => {
    // Seen before while the website was open: show that at once; the fresh answer replaces it below.
    const ks = peek<{ loggedIn: boolean; folder?: string }>("/api/session");
    const kv = peek<SortView>("/api/sort");
    if (ks?.data.folder && kv && Array.isArray(kv.data.plan?.moves)) {
      setFolder((cur) => cur || ks.data.folder!);
      setView((cur) => cur ?? kv.data);
    }
    (async () => {
      const [s, v] = await Promise.all([api<{ loggedIn: boolean; folder?: string }>("/api/session"), api<SortView & { noFolder?: boolean }>("/api/sort")]);
      if (!s.data.loggedIn || v.status === 401 || v.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || v.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (v.ok && Array.isArray(v.data.plan?.moves)) setView(v.data);
      else setError(v.data.error ?? "Could not load the sort.");
    })();
  }, []);

  // What's on screen (after a change too) is what the next visit shows first.
  useEffect(() => {
    if (view) remember("/api/sort", view);
  }, [view]);

  const moves = useMemo(() => view?.plan.moves ?? [], [view]);
  const untickedKeys = useMemo(() => new Set(moves.filter((m) => unticked.has(rowKey(m))).map(rowKey)), [moves, unticked]);
  const kept = moves.filter((m) => !untickedKeys.has(rowKey(m)));

  /** Sends a change; the answer is the sort as it is now. */
  async function send(body: unknown, done: string) {
    setBusy(true);
    setMessage(null);
    const r = await api<SortView>("/api/sort", body);
    setBusy(false);
    if (r.ok && Array.isArray(r.data.plan?.moves)) {
      setView(r.data);
      setUnticked(new Set());
      setSettingUp(false);
      setMessage(done);
      return true;
    }
    setMessage(r.data.error ?? "That didn't work.");
    return false;
  }

  const notSetUp = !!view && (!view.places.top || !view.places.mid || !view.places.bulk);

  if (!view) {
    return (
      <>
        <TopBar page="collection" folder={folder} />
        <main className="page wide">{error ? <div className="bar error">{error}</div> : <p className="muted center">Loading…</p>}</main>
      </>
    );
  }

  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const settled = view.settled;
  const groups = SORT_LEVELS.filter((from) => from !== "other")
    .map((from) => ({ from, list: moves.filter((m) => m.from === from) }))
    .filter((g) => g.list.length > 0);

  const plural = (n: number, one: string, many: string) => (n === 1 ? one : many);
  const done = () => {
    const cards = kept.reduce((n, m) => n + m.copies, 0);
    return send(
      { action: "apply", accepted: kept.map(served), declined: moves.filter((m) => untickedKeys.has(rowKey(m))).map(served) },
      kept.length === 0
        ? `Nothing moved; ${moves.length} ${plural(moves.length, "move", "moves")} won't be suggested again.`
        : `Moved ${cards} ${plural(cards, "card", "cards")}` +
            (untickedKeys.size > 0 ? `, declined ${untickedKeys.size} ${plural(untickedKeys.size, "move", "moves")}` : "") +
            ", done. The phones get the changes with their next sync.",
    );
  };

  return (
    <>
      <TopBar page="collection" folder={folder} />
      <main className="page wide">
        <div className="controls">
          <Link className="btn" href="/collection">
            ← Collection
          </Link>
          <button className="btn" disabled={busy} onClick={() => setSettingUp(true)}>
            Settings
          </button>
          {!notSetUp && view.declined > 0 && (
            <button className="btn" disabled={busy || view.readOnly} onClick={() => void send({ action: "unskip" }, "The declined moves are suggested again.")}>
              Suggest declined moves again ({view.declined})
            </button>
          )}
          {!notSetUp && (
            <button className="btn danger-text" disabled={busy || view.readOnly || !settled} onClick={() => setStartOver(true)} title={settled ? undefined : "Nothing has been sorted yet"}>
              Start over
            </button>
          )}
        </div>
        {message && <div className="bar">{message}</div>}

        {settingUp || notSetUp ? (
          <SetUp
            view={view}
            money={money}
            busy={busy}
            onCancel={settingUp && !notSetUp ? () => setSettingUp(false) : undefined}
            onSave={async (places, rules) => {
              if (await send({ action: "setup", places, rules }, "Settings saved; the suggestions follow them.")) return;
            }}
          />
        ) : (
          <>
            <section className="stat-block">
              <h2>{settled ? "Where the cards go" : "First sort"}</h2>
              <p className="value-head">
                <strong>{countsLine(view)}</strong>
              </p>
              {settled ? (
                <p className="muted small">
                  This week: {view.plan.weekUsed} of {view.rules.weekly} moves used
                  {view.plan.heldBack > 0 ? ` · ${view.plan.heldBack} more wait for next week` : ""}
                </p>
              ) : (
                <p className="muted small">
                  First sort: everything is placed at once. After this, cards only move when they clearly belong elsewhere, at most {view.rules.weekly} a week.
                </p>
              )}
            </section>

            {moves.length === 0 && (
              <p className="muted center">
                {settled ? "Nothing to move: the binders already hold the right cards for these rules." : "Everything is in place: each card is already in the binder it belongs in."}
              </p>
            )}

            {groups.map(({ from, list }) => {
              const copies = list.reduce((n, m) => n + m.copies, 0);
              return (
                <section className="stat-block" key={from}>
                  <div className="value-title">
                    <h2>
                      From {placeName(view, from)} ({copies} {plural(copies, "copy", "copies")})
                    </h2>
                    <div className="seg text" role="group" aria-label={`All of ${placeName(view, from)}`}>
                      <button onClick={() => setUnticked((old) => new Set([...old, ...list.map(rowKey)]))} disabled={view.readOnly}>
                        Untick all
                      </button>
                      <button onClick={() => setUnticked((old) => new Set([...old].filter((k) => !list.some((m) => rowKey(m) === k))))} disabled={view.readOnly}>
                        Tick all
                      </button>
                    </div>
                  </div>
                  <ul className="stat-cards trade-list">
                    {list.map((m) => {
                      const key = rowKey(m);
                      const on = !untickedKeys.has(key);
                      const finish = finishOf(m.key);
                      return (
                        <li key={key} className={on ? undefined : "faded"}>
                          <label className="check sort-move">
                            <input type="checkbox" checked={on} disabled={view.readOnly} onChange={(e) => setUnticked((old) => (e.target.checked ? new Set([...old].filter((k) => k !== key)) : new Set([...old, key])))} />
                            <span className="grow">
                              <span>
                                {m.copies > 1 ? `${m.copies}× ` : ""}
                                <strong>{m.name}</strong>
                              </span>
                              <span className="muted small">
                                {" "}
                                → {placeName(view, m.to)}
                              </span>
                              <div className="muted small">
                                {finish && <span className={finish === "etched" ? "tag" : "tag foil"}>{finish === "etched" ? "ETCHED" : "FOIL"}</span>} {m.reason}
                              </div>
                            </span>
                          </label>
                          <span className="trade-price">{m.price != null ? money(m.price) : "–"}</span>
                        </li>
                      );
                    })}
                  </ul>
                </section>
              );
            })}

            {moves.length > 0 && (
              <p className="muted small">Cards your decks use, the trade binder and your other binders are left alone.</p>
            )}
          </>
        )}
      </main>

      {!settingUp && !notSetUp && moves.length > 0 && (
        <div className="bulk-bar sort-done" role="region" aria-label="Done">
          <span className="muted small grow">Tick what you moved. Unticked moves won&apos;t be suggested again.</span>
          <button className="btn primary" disabled={busy || view.readOnly} onClick={done}>
            {busy ? "Saving…" : kept.length === moves.length ? "Done" : `Done (${kept.length} of ${moves.length} moves)`}
          </button>
        </div>
      )}

      {startOver && (
        <Dialog title="Start over?" onClose={() => setStartOver(false)}>
          <p>Sort everything again from scratch? The weekly limit and the 8-week rest start over.</p>
          <div className="dialog-actions">
            <button className="btn" onClick={() => setStartOver(false)}>
              Cancel
            </button>
            <button
              className="btn primary"
              disabled={busy}
              onClick={async () => {
                if (await send({ action: "startover" }, "Starting over: the next sort is a first sort.")) setStartOver(false);
              }}
            >
              Start over
            </button>
          </div>
        </Dialog>
      )}
    </>
  );
}

/** The sort's setup (or its saved settings): the three binders, the limits and how cards are weighed. */
function SetUp({
  view,
  money,
  busy,
  onCancel,
  onSave,
}: {
  view: SortView;
  money: (eur: number) => string;
  busy: boolean;
  onCancel?: () => void;
  onSave: (places: Record<SortPlace, { uid?: string; newName?: string }>, rules: SortRules) => void;
}) {
  const r = view.rules;
  // Each place keeps the binder it has, or a new one with the place's usual name.
  const [pick, setPick] = useState<Record<SortPlace, string>>(() => ({
    top: view.places.top ? `b:${view.places.top.uid}` : "",
    mid: view.places.mid ? `b:${view.places.mid.uid}` : "",
    bulk: view.places.bulk ? `b:${view.places.bulk.uid}` : "",
  }));
  const [newName, setNewName] = useState<Record<SortPlace, string>>(() => ({
    top: view.places.top ? "" : PLACE_LABELS.top,
    mid: view.places.mid ? "" : PLACE_LABELS.mid,
    bulk: view.places.bulk ? "" : PLACE_LABELS.bulk,
  }));
  const [maxTop, setMaxTop] = useState(String(r.maxTop));
  const [maxMid, setMaxMid] = useState(String(r.maxMid));
  const [maxBulk, setMaxBulk] = useState(String(r.maxBulk));
  const [floor, setFloor] = useState(String(r.floor));
  const [mix, setMix] = useState(r.mix);
  const [weekly, setWeekly] = useState(String(r.weekly));
  const [problem, setProblem] = useState<string | null>(null);

  const nTop = Number.parseInt(maxTop, 10);
  const nMid = Number.parseInt(maxMid, 10);
  const nBulk = Number.parseInt(maxBulk, 10);
  const nWeekly = Number.parseInt(weekly, 10);
  const floorN = Number.parseFloat(floor.replace(",", "."));
  const valid = [nTop, nMid, nBulk].every((n) => Number.isInteger(n) && n >= 0) && Number.isInteger(nWeekly) && nWeekly >= 1 && Number.isFinite(floorN) && floorN >= 0;
  const chosen = (place: SortPlace) => (pick[place] ? { uid: pick[place].slice(2) } : { newName: newName[place].trim() });
  const namesUsed = SORT_PLACES.map((p) => (pick[p] ? "" : newName[p].trim().toLowerCase()));
  const ready = valid && SORT_PLACES.every((p) => pick[p] || newName[p].trim().length > 0) && new Set(namesUsed.filter(Boolean)).size === namesUsed.filter(Boolean).length;

  const mixText = mix < 50 ? "Value counts more" : mix > 50 ? "Commander popularity counts more" : "Value and popularity, like the trade binder";

  return (
    <section className="stat-block">
      <h2>{onCancel ? "Sort settings" : "Set up the sort"}</h2>
      <p className="muted small">
        Splits your collection into a Collection binder, Not bulk and Bulk by value and Commander popularity. Cards your decks use, the trade binder and your other binders are left alone.
      </p>
      <div className="stack tight">
        {SORT_PLACES.map((place) => (
          <div key={place} className="inline wrap">
            <label className="field">
              <span>{PLACE_LABELS[place]}</span>
              <select
                value={pick[place]}
                onChange={(e) => {
                  const v = e.target.value;
                  setPick((old) => ({ ...old, [place]: v }));
                }}
              >
                <option value="">New binder…</option>
                {view.binders.map((b) => (
                  <option key={b.uid} value={`b:${b.uid}`}>
                    {b.name}
                  </option>
                ))}
              </select>
            </label>
            {!pick[place] && (
              <label className="field">
                <span>New binder&apos;s name</span>
                <input type="text" maxLength={200} value={newName[place]} onChange={(e) => setNewName((old) => ({ ...old, [place]: e.target.value }))} />
              </label>
            )}
          </div>
        ))}
      </div>
      <h3 className="mini-head">How many copies each binder holds (0 = no limit)</h3>
      <div className="form-grid">
        <label className="field">
          <span>Collection binder copies</span>
          <input type="number" min={0} max={100000} value={maxTop} onChange={(e) => setMaxTop(e.target.value)} />
        </label>
        <label className="field">
          <span>Not bulk copies</span>
          <input type="number" min={0} max={100000} value={maxMid} onChange={(e) => setMaxMid(e.target.value)} />
        </label>
        <label className="field">
          <span>Bulk copies (0 = no limit)</span>
          <input type="number" min={0} max={1000000} value={maxBulk} onChange={(e) => setMaxBulk(e.target.value)} />
        </label>
        <label className="field">
          <span>Always bulk under (in euros{money(1).startsWith("€") ? "" : `; ${money(1)} per euro`})</span>
          <input type="number" min={0} step={0.5} value={floor} onChange={(e) => setFloor(e.target.value)} />
        </label>
      </div>
      <h3 className="mini-head">How cards are weighed</h3>
      <label className="field">
        <span>Value ←→ Commander popularity</span>
        <input type="range" min={0} max={100} step={10} value={mix} onChange={(e) => setMix(Number(e.target.value))} aria-valuetext={`${mix}: ${mixText}`} />
      </label>
      <p className="muted small">{mixText}</p>
      <label className="field">
        <span>Most moves a week</span>
        <input type="number" min={1} max={10000} value={weekly} onChange={(e) => setWeekly(e.target.value)} />
      </label>
      {problem && <div className="bar error">{problem}</div>}
      <div className="dialog-actions">
        {onCancel && (
          <button className="btn" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
        )}
        <button
          className="btn primary"
          disabled={!ready || busy}
          onClick={() => {
            setProblem(null);
            const places = Object.fromEntries(SORT_PLACES.map((p) => [p, chosen(p)])) as Record<SortPlace, { uid?: string; newName?: string }>;
            const same = SORT_PLACES.filter((p) => places[p].uid).map((p) => places[p].uid!.toLowerCase());
            if (new Set(same).size !== same.length) return setProblem("Two places can't use the same binder.");
            onSave(places, { maxTop: nTop, maxMid: nMid, maxBulk: nBulk, floor: Math.round(floorN * 100) / 100, mix, weekly: nWeekly });
          }}
        >
          Save and suggest
        </button>
      </div>
    </section>
  );
}
