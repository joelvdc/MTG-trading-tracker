"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, peek, remember } from "@/components/api";
import { Dialog } from "@/components/Dialog";
import { TopBar } from "@/components/Nav";
import type { SortView } from "@/app/api/sort/route";
import {
  DECKS_KEY,
  UNSORTED_KEY,
  type SortDeckCopies,
  type SortMove,
  type SortSettingsInput,
  type SortTogether,
} from "@/lib/collsort";
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

/** A careful number from a box: [letterbox] allows what's typed on the way (empty, a lone "-"). */
function numberIn(text: string): number | null {
  const t = text.trim().replace(",", ".");
  if (t === "") return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

/** A place's name: the level's binder, the Decks binder, or Unsorted. */
function placeName(view: SortView, place: string): string {
  if (place === UNSORTED_KEY) return "Unsorted";
  if (place === DECKS_KEY) return view.decks?.name ?? "Decks";
  const level = view.levels.find((l) => l.key === place);
  return level?.name ?? place;
}

/** "Best cards 187/200 · Bulk 1,204 · Decks 12 · Unsorted 3": a place without a limit has no "/". */
function countsLine(view: SortView): string {
  const n = (x: number) => x.toLocaleString("en-GB");
  const last = view.levels.length - 1;
  const places = view.levels
    .filter((l) => l.key !== DECKS_KEY)
    .map((l, i) => {
      const after = view.plan.after[l.key] ?? 0;
      // The last level with no limit shows just its number.
      return l.max > 0 && !(i === last && l.max === 0) ? `${l.name} ${n(after)}/${n(l.max)}` : `${l.name} ${n(after)}`;
    });
  if (view.decks) places.push(`${view.decks.name} ${n(view.plan.after[DECKS_KEY] ?? 0)}`);
  const unsorted = view.plan.after[UNSORTED_KEY] ?? 0;
  if (unsorted > 0) places.push(`Unsorted ${n(unsorted)}`);
  return places.join(" · ");
}

/** One move: its tick box, copies, name, finish, where it goes, why and its price. */
function MoveRow({
  m,
  view,
  on,
  money,
  readOnly,
  onToggle,
}: {
  m: SortMove;
  view: SortView;
  on: boolean;
  money: (eur: number) => string;
  readOnly: boolean;
  onToggle: (on: boolean) => void;
}) {
  const finish = finishOf(m.key);
  return (
    <li className={on ? undefined : "faded"}>
      <label className="check sort-move">
        <input type="checkbox" checked={on} disabled={readOnly} onChange={(e) => onToggle(e.target.checked)} />
        <span className="grow">
          <span>
            {m.copies > 1 ? `${m.copies}× ` : ""}
            <strong>{m.name}</strong>
          </span>
          <span className="muted small"> → {placeName(view, m.to)}</span>
          <div className="muted small">
            {finish && <span className={finish === "etched" ? "tag" : "tag foil"}>{finish === "etched" ? "ETCHED" : "FOIL"}</span>} {m.reason}
          </div>
        </span>
      </label>
      <span className="trade-price">{m.price != null ? money(m.price) : "–"}</span>
    </li>
  );
}

/**
 * Sort the collection (the app's Sort screen): sets up the levels, the Decks binder and the rules, then shows the
 * suggested moves grouped by where the cards are now. Tick what you moved and press Done; unticked moves aren't
 * suggested again.
 */
export default function SortPage() {
  const [view, setView] = useState<SortView | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // "Sort everything now": the plan without this week's limit.
  const [ignoreCap, setIgnoreCap] = useState(false);
  const [loadingPlan, setLoadingPlan] = useState(false);
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

  // What's on screen (after a change too) is what the next visit shows first (the plan as the weekly limit has it).
  useEffect(() => {
    if (view && !ignoreCap) remember("/api/sort", view);
  }, [view, ignoreCap]);

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

  /** Loads the plan again, with or without this week's limit. */
  async function loadPlan(ignore: boolean) {
    setLoadingPlan(true);
    setMessage(null);
    const r = await api<SortView>(ignore ? "/api/sort?ignoreCap=1" : "/api/sort");
    setLoadingPlan(false);
    if (r.status === 401 || r.data.loggedOut) return window.location.replace("/login");
    if (r.ok && Array.isArray(r.data.plan?.moves)) {
      setView(r.data);
      setIgnoreCap(ignore);
      setUnticked(new Set());
      return;
    }
    setMessage(r.data.error ?? "Could not load the sort.");
  }

  const notSetUp = !!view && view.levels.length === 0;

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
  const plural = (n: number, one: string, many: string) => (n === 1 ? one : many);

  // Groups of moves: the decks' first (in or out of Decks), then each level in order, then Unsorted.
  const levelKeys = view.levels.map((l) => l.key);
  const deckMoves = moves.filter((m) => m.to === DECKS_KEY || m.from === DECKS_KEY);
  const groups: { key: string; title: string; list: SortMove[] }[] = [];
  if (deckMoves.length > 0)
    groups.push({ key: "decks", title: view.decks ? `For your decks (${view.decks.name})` : "For your decks", list: deckMoves });
  for (const key of levelKeys) {
    const list = moves.filter((m) => m.from === key && m.to !== DECKS_KEY);
    if (list.length > 0) groups.push({ key, title: `From ${placeName(view, key)}`, list });
  }
  const fromUnsorted = moves.filter((m) => m.from === UNSORTED_KEY);
  if (fromUnsorted.length > 0) groups.push({ key: UNSORTED_KEY, title: "From Unsorted", list: fromUnsorted });
  // Cards the levels had no room for.
  // Cards under a level's minimum price go to Unsorted too, but they had a place: only the planner's own "no room" moves count.
  const noRoom = moves.filter((m) => m.to === UNSORTED_KEY && m.reason.includes("no room")).reduce((n, m) => n + m.copies, 0);

  const setGroup = (list: SortMove[], on: boolean) =>
    setUnticked((old) => (on ? new Set([...old].filter((k) => !list.some((m) => rowKey(m) === k))) : new Set([...old, ...list.map(rowKey)])));

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
            onSave={async (levels, decks, settings) => {
              await send({ action: "setup", levels, decks, settings }, "Settings saved; the suggestions follow them.");
            }}
          />
        ) : (
          <>
            <section className="stat-block">
              <h2>{settled ? "Where the cards go" : "First sort"}</h2>
              <p className="value-head">
                <strong>{countsLine(view)}</strong>
              </p>
              {!view.ownUserSet && (
                <div className="bar">
                  Set your Archidekt username in <Link href="/settings">Settings → Archidekt</Link> (on this website&apos;s Settings page or in the app) so the
                  sort knows which Archidekt decks are yours. Until then only your local decks hold cards.
                </div>
              )}
              {view.plan.deckShort > 0 && (
                <p className="muted small">
                  Your decks need {view.plan.deckShort} more {plural(view.plan.deckShort, "copy", "copies")} you don&apos;t own.
                </p>
              )}
              {settled ? (
                <p className="muted small">
                  {ignoreCap ? (
                    <>
                      Ignoring this week&apos;s limit.{" "}
                      <button className="link" disabled={busy || loadingPlan} onClick={() => void loadPlan(false)}>
                        Back to the weekly limit
                      </button>
                    </>
                  ) : (
                    <>
                      This week: {view.plan.weekUsed} of {view.settings.weekly} moves used
                      {view.plan.heldBack > 0 ? ` · ${view.plan.heldBack} more wait for next week` : ""}{" "}
                      <button
                        className="btn"
                        disabled={busy || loadingPlan}
                        title="Ignore this week's limit for this sort, e.g. after taking a deck apart"
                        onClick={() => void loadPlan(true)}
                      >
                        Sort everything now
                      </button>
                    </>
                  )}
                </p>
              ) : (
                <p className="muted small">
                  First sort: everything is placed at once. After this, cards only move when they clearly belong elsewhere, at most {view.settings.weekly} a week.
                </p>
              )}
            </section>

            {moves.length === 0 && (
              <p className="muted center">
                {settled ? "Nothing to move: the binders already hold the right cards for these rules." : "Everything is in place: each card is already in the binder it belongs in."}
              </p>
            )}

            {groups.map(({ key, title, list }) => {
              const copies = list.reduce((n, m) => n + m.copies, 0);
              return (
                <section className="stat-block" key={key}>
                  <div className="value-title">
                    <h2>
                      {title} ({copies} {plural(copies, "copy", "copies")})
                    </h2>
                    <div className="seg text" role="group" aria-label={`All of ${title}`}>
                      <button onClick={() => setGroup(list, false)} disabled={view.readOnly}>
                        Untick all
                      </button>
                      <button onClick={() => setGroup(list, true)} disabled={view.readOnly}>
                        Tick all
                      </button>
                    </div>
                  </div>
                  <ul className="stat-cards trade-list">
                    {list.map((m) => {
                      const k = rowKey(m);
                      return (
                        <MoveRow
                          key={k}
                          m={m}
                          view={view}
                          on={!untickedKeys.has(k)}
                          money={money}
                          readOnly={view.readOnly}
                          onToggle={(on) => setUnticked((old) => (on ? new Set([...old].filter((x) => x !== k)) : new Set([...old, k])))}
                        />
                      );
                    })}
                  </ul>
                </section>
              );
            })}

            {noRoom > 0 && (
              <p className="muted small">
                {noRoom} {plural(noRoom, "card has", "cards have")} no room: raise a level&apos;s limit or add a level in Settings, or put{" "}
                {plural(noRoom, "it", "them")} in the trade binder.
              </p>
            )}

            {moves.length > 0 && (
              <p className="muted small">The trade binder and your other binders are left alone.</p>
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

/** Where a place points: an existing binder ("b:<uid>"), a new one ("new") or, for the decks, "none". */
type BinderPick = string;
const BINDER = (uid: string) => `b:${uid}`;

/** One level as the form keeps it: its binder, how many copies it holds and the cheapest card it takes (as typed). */
interface LevelForm {
  pick: BinderPick;
  newName: string;
  holds: string;
  min: string;
}

/** A new setup's three levels: their names, how many copies and the cheapest card they take. */
const DEFAULT_LEVELS: { name: string; holds: string; min: string }[] = [
  { name: "Best cards", holds: "200", min: "0.50" },
  { name: "Good cards", holds: "1000", min: "0.50" },
  { name: "Bulk", holds: "0", min: "" },
];

/** What the form sends: the levels and Decks as the API wants them (new names made on the server). */
type LevelOut = { choice: { uid: string } | { newName: string }; max: number; minPrice: number | null };

/**
 * The sort's setup (or its saved settings): the levels (their binders, how many copies and the cheapest card they
 * take), the Decks binder, how the decks' copies are picked, how copies go together and how cards are weighed.
 */
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
  onSave: (levels: LevelOut[], decks: { uid: string } | { newName: string } | null, settings: SortSettingsInput) => void;
}) {
  const s = view.settings;
  const moneyToEur = (text: string): number | null => {
    const n = numberIn(text);
    return n === null ? null : Math.round((n / (view.perEuro || 1)) * 100) / 100;
  };
  const minText = (eur: number | null) => (eur === null ? "" : String(Math.round(eur * view.perEuro * 100) / 100));
  // An existing setup starts from its own levels and Decks binder; a new one from the three default levels.
  const saved = view.levels.length > 0;
  const [levels, setLevels] = useState<LevelForm[]>(() =>
    saved
      ? view.levels.map((l) => ({ pick: BINDER(l.uid), newName: "", holds: String(l.max), min: minText(l.minPrice) }))
      : DEFAULT_LEVELS.map((d) => ({ pick: "new", newName: d.name, holds: d.holds, min: d.min })),
  );
  const [decksPick, setDecksPick] = useState<BinderPick>(() => {
    if (s.decks && view.decks) return BINDER(s.decks);
    return "none";
  });
  const [decksNewName, setDecksNewName] = useState("Decks");
  const [deckCopies, setDeckCopies] = useState<SortDeckCopies>(s.deckCopies);
  const [together, setTogether] = useState<SortTogether>(s.together);
  const [ratio, setRatio] = useState(String(s.outlierRatio));
  const [gap, setGap] = useState(minText(s.outlierGap));
  const [mix, setMix] = useState(s.mix);
  const [weekly, setWeekly] = useState(String(s.weekly));
  const [problem, setProblem] = useState<string | null>(null);

  const setLevel = (i: number, patch: Partial<LevelForm>) => setLevels((old) => old.map((l, n) => (n === i ? { ...l, ...patch } : l)));
  const moveLevel = (i: number, by: number) =>
    setLevels((old) => {
      const next = [...old];
      const [l] = next.splice(i, 1);
      next.splice(i + by, 0, l);
      return next;
    });
  const addLevel = () => setLevels((old) => [...old, { pick: "new", newName: "", holds: "", min: "" }]);
  const removeLevel = (i: number) => setLevels((old) => old.filter((_, n) => n !== i));

  const holdsOf = (l: LevelForm) => numberIn(l.holds);
  const nWeekly = numberIn(weekly);
  const nRatio = numberIn(ratio);
  const nGap = moneyToEur(gap);

  // The checks the server makes too, so the problem shows before anything is sent.
  const problems: string[] = [];
  if (levels.length > 6) problems.push("There can be at most six levels.");
  if (levels.some((l) => l.pick === "new" && l.newName.trim() === "")) problems.push("Give each new binder a name.");
  const uidsUsed = levels.filter((l) => l.pick !== "new").map((l) => l.pick.slice(2).toLowerCase());
  if (decksPick !== "none" && decksPick !== "new") uidsUsed.push(decksPick.slice(2).toLowerCase());
  if (new Set(uidsUsed).size !== uidsUsed.length) problems.push("Two places can't use the same binder.");
  const newNames = [
    ...levels.filter((l) => l.pick === "new").map((l) => l.newName.trim().toLowerCase()),
    ...(decksPick === "new" ? [decksNewName.trim().toLowerCase()] : []),
  ];
  if (new Set(newNames).size !== newNames.length) problems.push("Two new binders can't have the same name.");
  if (decksPick === "new" && decksNewName.trim() === "") problems.push("Give the Decks binder a name.");
  levels.forEach((l, i) => {
    const n = holdsOf(l);
    if (n === null || !Number.isInteger(n) || n < 0 || n > 1000000) problems.push(`Level ${i + 1} needs how many copies it holds (0 or more).`);
    else if (n === 0 && i < levels.length - 1) problems.push("Only the last level may hold nothing (0 = no limit).");
  });
  if (nWeekly === null || !Number.isInteger(nWeekly) || nWeekly < 1 || nWeekly > 10000) problems.push("Most moves a week is a whole number, 1 or more.");
  if (nRatio === null || nRatio < 1 || nRatio > 100) problems.push("The much-more-valuable times must be 1 or more.");
  if (nGap === null || nGap < 0) problems.push("The much-more-valuable amount must be 0 or more.");
  const ready = problems.length === 0;

  const mixText = mix < 50 ? "Value counts more" : mix > 50 ? "Commander popularity counts more" : "Value and popularity, like the trade binder";

  return (
    <section className="stat-block">
      <h2>{onCancel ? "Sort settings" : "Set up the sort"}</h2>
      <p className="muted small">
        Splits your collection into levels by value and Commander popularity: the best cards in the first level, then the
        next, down to Unsorted. The copies your active decks use go to the Decks binder. The trade binder and your other
        binders are left alone.
      </p>

      <h3 className="mini-head">Levels, best first</h3>
      <div className="stack tight">
        {levels.map((l, i) => {
          const n = holdsOf(l);
          const last = i === levels.length - 1;
          return (
            <div key={i} className="stack tight">
              <div className="inline wrap">
                <label className="field">
                  <span>
                    Level {i + 1} binder
                  </span>
                  <select value={l.pick} onChange={(e) => setLevel(i, { pick: e.target.value })}>
                    <option value="new">New binder…</option>
                    {view.binders.map((b) => (
                      <option key={b.uid} value={BINDER(b.uid)}>
                        {b.name}
                      </option>
                    ))}
                  </select>
                </label>
                {l.pick === "new" && (
                  <label className="field">
                    <span>New binder&apos;s name</span>
                    <input type="text" maxLength={200} value={l.newName} onChange={(e) => setLevel(i, { newName: e.target.value })} />
                  </label>
                )}
                <label className="field">
                  <span>Holds</span>
                  <input type="number" min={0} max={1000000} step={1} inputMode="numeric" value={l.holds} onChange={(e) => setLevel(i, { holds: e.target.value })} />
                </label>
                <label className="field">
                  <span>Minimum price ({view.currency})</span>
                  <input type="text" inputMode="decimal" value={l.min} placeholder="None" onChange={(e) => setLevel(i, { min: e.target.value })} />
                </label>
                <div className="inline">
                  <button className="btn" disabled={i === 0} onClick={() => moveLevel(i, -1)} aria-label={`Move level ${i + 1} up`} title="Move up">
                    ↑
                  </button>
                  <button className="btn" disabled={last} onClick={() => moveLevel(i, 1)} aria-label={`Move level ${i + 1} down`} title="Move down">
                    ↓
                  </button>
                  {levels.length > 1 && (
                    <button className="btn" onClick={() => removeLevel(i)} aria-label={`Remove level ${i + 1}`} title="Remove">
                      −
                    </button>
                  )}
                </div>
              </div>
              {last && n === 0 && <p className="muted small">0 = no limit: this level takes every card the levels before it leave.</p>}
            </div>
          );
        })}
      </div>
      {levels.length < 6 && (
        <button className="btn" onClick={addLevel}>
          Add a level
        </button>
      )}

      <h3 className="mini-head">Decks</h3>
      <div className="inline wrap">
        <label className="field">
          <span>Decks binder</span>
          <select value={decksPick} onChange={(e) => setDecksPick(e.target.value)}>
            <option value="none">None</option>
            <option value="new">New binder…</option>
            {view.binders.map((b) => (
              <option key={b.uid} value={BINDER(b.uid)}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        {decksPick === "new" && (
          <label className="field">
            <span>New binder&apos;s name</span>
            <input type="text" maxLength={200} value={decksNewName} onChange={(e) => setDecksNewName(e.target.value)} />
          </label>
        )}
      </div>
      <p className="muted small">Holds the copies your active decks use: your own Archidekt decks and your local decks, not retired ones.</p>

      <h3 className="mini-head">Copies for decks</h3>
      <div className="inline wrap">
        <label className="check">
          <input type="radio" name="deckCopies" checked={deckCopies === "best"} onChange={() => setDeckCopies("best")} />
          <span>Best copy first</span>
        </label>
        <label className="check">
          <input type="radio" name="deckCopies" checked={deckCopies === "cheapest"} onChange={() => setDeckCopies("cheapest")} />
          <span>Cheapest first</span>
        </label>
      </div>

      <h3 className="mini-head">Keep copies together</h3>
      <div className="inline wrap">
        <label className="check">
          <input type="radio" name="together" checked={together === "name"} onChange={() => setTogether("name")} />
          <span>All printings of a card</span>
        </label>
        <label className="check">
          <input type="radio" name="together" checked={together === "printing"} onChange={() => setTogether("printing")} />
          <span>Only the same printing</span>
        </label>
        <label className="check">
          <input type="radio" name="together" checked={together === "off"} onChange={() => setTogether("off")} />
          <span>Off: every copy on its own</span>
        </label>
      </div>
      {together !== "off" && (
        <div className="inline wrap">
          <span className="muted small">Unless a copy is worth at least</span>
          <label className="field">
            <span>times more</span>
            <input type="number" min={1} max={100} step={0.5} value={ratio} onChange={(e) => setRatio(e.target.value)} aria-label="Much more valuable: times" />
          </label>
          <label className="field">
            <span>and more ({view.currency})</span>
            <input type="text" inputMode="decimal" value={gap} onChange={(e) => setGap(e.target.value)} aria-label="Much more valuable: amount" />
          </label>
        </div>
      )}

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
      <p className="muted small">
        You show prices in {view.currency} ({view.moneyExample} = 1 euro): type the minimum prices and the much-more-valuable amount in {view.currency} too.
      </p>

      {(problem ?? (problems.length > 0 ? problems[0] : null)) && <div className="bar error">{problem ?? problems[0]}</div>}
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
            // The server checks it all again; its message is shown if something still doesn't fit.
            const out: LevelOut[] = levels.map((l) => ({
              choice: l.pick === "new" ? { newName: l.newName.trim() } : { uid: l.pick.slice(2) },
              max: holdsOf(l) ?? 0,
              minPrice: moneyToEur(l.min),
            }));
            const decks = decksPick === "none" ? null : decksPick === "new" ? { newName: decksNewName.trim() } : { uid: decksPick.slice(2) };
            onSave(out, decks, {
              mix,
              weekly: nWeekly ?? 1,
              together,
              outlierRatio: Math.round((nRatio ?? 3) * 100) / 100,
              outlierGap: nGap ?? 0,
              deckCopies,
            });
          }}
        >
          Save and suggest
        </button>
      </div>
    </section>
  );
}
