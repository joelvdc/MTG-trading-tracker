"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api } from "@/components/api";
import { TopBar } from "@/components/Nav";
import type { FindView } from "@/app/api/find/route";
import type { FindLine, FindResult } from "@/lib/findcards";
import type { FoundCard } from "@/lib/scryfall";

/** What the page keeps in the browser (this browser only, like the app's findList and findTicks on the phone). */
const STORE_KEY = "mtgtrader.find";
interface Saved {
  /** The pasted list, or "deck:<id>". */
  list: string;
  ticks: string[];
  skipBasics: boolean;
  /** Only these cards of the deck (from the deck page's Select mode). */
  names?: string[];
}

function load(): Saved | null {
  try {
    const raw = window.localStorage.getItem(STORE_KEY);
    if (!raw) return null;
    const s = JSON.parse(raw) as Partial<Saved>;
    return {
      list: typeof s.list === "string" ? s.list : "",
      ticks: Array.isArray(s.ticks) ? s.ticks.filter((t): t is string => typeof t === "string") : [],
      skipBasics: s.skipBasics !== false,
      names: Array.isArray(s.names) ? s.names.filter((n): n is string => typeof n === "string") : undefined,
    };
  } catch {
    return null;
  }
}

function save(s: Saved): void {
  try {
    window.localStorage.setItem(STORE_KEY, JSON.stringify(s));
  } catch {
    // A browser that doesn't keep it (or is full) just forgets the ticks.
  }
}

/** "Found 12 of 40 · 3 only in other decks · 2 not owned": the last two only when there are any. */
function summary(r: FindResult): string {
  return `Found ${r.total.found} of ${r.total.wanted}` + (r.total.inDecks > 0 ? ` · ${r.total.inDecks} only in other decks` : "") + (r.total.notOwned > 0 ? ` · ${r.total.notOwned} not owned` : "");
}

/** A deck to pick: the decks of the sync file, by name. */
interface DeckChoice {
  id: number;
  name: string;
}

/**
 * "Find these cards" (the app's Find these cards screen): the copies of a pasted list or a deck, binder by binder, with
 * what's only in other decks and what isn't owned. Ticked cards go to the bottom of their binder and are struck out.
 * Opened with ?deck=<id> (a deck's cards) and &names=<name1>|<name2>… (only those cards of it).
 */
export default function FindPage() {
  const [folder, setFolder] = useState("");
  const [decks, setDecks] = useState<DeckChoice[]>([]);
  const [text, setText] = useState("");
  /** The list, or "deck:<id>". */
  const [list, setList] = useState("");
  const [names, setNames] = useState<string[]>([]);
  const [skipBasics, setSkipBasics] = useState(true);
  const [result, setResult] = useState<FindView | null>(null);
  const [ticks, setTicks] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const started = useRef(false);
  // The ticks, the card names and the list the first lookup starts with.
  const ticksRef = useRef<Set<string>>(new Set());
  const namesRef = useRef<string[]>([]);
  const listRef = useRef("");

  // "It was opened with" wins over the pasted list or the deck picked here.
  function remember(s: Saved) {
    listRef.current = s.list;
    namesRef.current = s.names ?? [];
    save(s);
  }

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const q = new URLSearchParams(window.location.search);
    const deck = Number.parseInt(q.get("deck") ?? "", 10);
    const named = (q.get("names") ?? "")
      .split("|")
      .map((n) => n.trim())
      .filter(Boolean);
    const saved = load();
    const fromDeck = Number.isInteger(deck) ? `deck:${deck}` : "";
    const opening = fromDeck || saved?.list || "";
    setNames(fromDeck ? named : []);
    // The list it was last left with, unless it was opened from a deck.
    if (!fromDeck) setText(saved && !saved.list.startsWith("deck:") ? saved.list : "");
    setTicks(new Set(saved?.ticks ?? []));
    setSkipBasics(saved?.skipBasics ?? true);
    // The first lookup runs before the state above is set: it reads the same values from the refs.
    ticksRef.current = new Set(saved?.ticks ?? []);
    namesRef.current = named;
    listRef.current = opening;
    (async () => {
      const s = await api<{ loggedIn: boolean; folder?: string }>("/api/session");
      if (!s.data.loggedIn) return window.location.replace("/login");
      if (!s.data.folder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      const d = await api<{ decks?: { id: number; name: string }[] }>("/api/decks");
      const choices = (d.data.decks ?? []).map((x) => ({ id: Number(x.id), name: x.name })).filter((x) => Number.isInteger(x.id));
      setDecks(choices.sort((a, b) => a.name.localeCompare(b.name)));
      if (!opening) return;
      setList(opening);
      await find({ list: opening, skipBasics: saved?.skipBasics ?? true, names: fromDeck ? named : [], ticks: true });
    })();
    // The first load only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Looks the list up (never writes the sync file). */
  async function find(o: { list: string; skipBasics?: boolean; names?: string[]; ticks?: boolean }) {
    const deck = /^deck:(\d+)$/.exec(o.list);
    if (!o.list || (!deck && !o.list.trim())) {
      setResult(null);
      return;
    }
    const useNames = o.names ?? namesRef.current;
    setBusy(true);
    setError(null);
    setMessage(null);
    const r = await api<FindView>("/api/find", {
      ...(deck ? { deck: Number(deck[1]) } : { text: o.list }),
      ...(useNames.length > 0 ? { names: useNames } : {}),
      skipBasics: o.skipBasics ?? skipBasics,
    });
    setBusy(false);
    if (r.ok && Array.isArray(r.data.groups)) {
      setResult(r.data);
      if (o.ticks) {
        setTicks(new Set());
        ticksRef.current = new Set();
      }
      remember({ list: o.list, ticks: o.ticks ? [] : [...ticksRef.current], skipBasics: o.skipBasics ?? skipBasics, names: useNames });
    } else {
      setResult(null);
      setError(r.data.error ?? "Could not look the cards up.");
    }
  }

  function toggle(key: string, on: boolean) {
    const next = new Set(ticks);
    if (on) next.add(key);
    else next.delete(key);
    setTicks(next);
    ticksRef.current = next;
    remember({ list, ticks: [...next], skipBasics, names: namesRef.current });
  }

  /** The list as it was pasted, kept for the way back to this page. */
  function search() {
    const l = text;
    setList(l);
    setNames([]);
    setTicks(new Set());
    void find({ list: l, names: [], ticks: true });
  }

  function pickDeck(id: number) {
    const l = `deck:${id}`;
    setList(l);
    setText("");
    setNames([]);
    setTicks(new Set());
    void find({ list: l, names: [], ticks: true });
  }

  function clear() {
    setText("");
    setList("");
    setNames([]);
    setTicks(new Set());
    setResult(null);
    setError(null);
    setMessage(null);
    remember({ list: "", ticks: [], skipBasics });
  }

  /** Adds what isn't owned to the wishlist, looked up by name (any printing). */
  async function addMissing(missing: { name: string; copies: number }[]) {
    setBusy(true);
    setMessage(null);
    let added = 0;
    for (const m of missing) {
      const c = await api<{ results?: FoundCard[] }>(`/api/cards/search?q=${encodeURIComponent(m.name)}`);
      const card = (c.data.results ?? []).find((x) => x.card.name.toLowerCase() === m.name.toLowerCase())?.card ?? c.data.results?.[0]?.card;
      if (!c.ok || !card) continue;
      const copies = Math.min(999, m.copies);
      const n = await api<{ error?: string }>("/api/wishlist", { edit: { type: "add", card, foil: !card.hasNonFoil && !!card.hasFoil, quantity: copies, anyPrinting: true } });
      if (n.ok) added += copies;
    }
    setBusy(false);
    setMessage(added > 0 ? `Added ${added} card${added === 1 ? "" : "s"} to the wishlist.` : "Couldn't add the cards to the wishlist.");
  }

  const r = result;
  return (
    <>
      <TopBar page="collection" folder={folder} />
      <main className="page wide">
        <div className="controls">
          <Link className="btn" href="/collection">
            ← Collection
          </Link>
          <span className="grow" />
          <button className="btn" disabled={busy} onClick={clear}>
            Clear
          </button>
        </div>
        {error && <div className="bar error">{error}</div>}
        {message && <div className="bar">{message}</div>}

        {!r && (
          <section className="stat-block">
            <h2>Find these cards</h2>
            <p className="muted small">Paste a list, or pick a deck, to see which binder each card is in.</p>
            <textarea
              className="deck-text"
              aria-label="Paste a list"
              placeholder={"1 Sol Ring\n1 Arcane Signet …"}
              rows={8}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
            <div className="inline wrap find-form">
              <button className="btn primary" disabled={busy || !text.trim()} onClick={search}>
                {busy ? "Looking…" : "Find"}
              </button>
              {decks.length > 0 && (
                <label className="field grow">
                  <span>or pick a deck</span>
                  <select value="" disabled={busy} onChange={(e) => e.target.value && pickDeck(Number(e.target.value))}>
                    <option value="">Deck…</option>
                    {decks.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>
          </section>
        )}

        {r && (
          <>
            <div className="controls">
              <Link className="btn" href="/find" onClick={(e) => {
                e.preventDefault();
                clear();
              }}>
                Another list
              </Link>
              {r.fromDeck != null && (
                <Link className="btn" href={`/decks/${r.fromDeck}`}>
                  ← {r.deckName ?? "Deck"}
                </Link>
              )}
            </div>
            <section className="stat-block">
              {r.deckName && <strong className="find-deck">{r.deckName}</strong>}
              {names.length > 0 && (
                <p className="muted small find-names">
                  Selected cards only: {names.join(", ")}.{" "}
                  <button
                    className="link"
                    disabled={busy}
                    onClick={() => {
                      setNames([]);
                      remember({ list, ticks: [...ticks], skipBasics, names: [] });
                      void find({ list, names: [] });
                    }}
                  >
                    Look for the whole deck
                  </button>
                </p>
              )}
              <p>{summary(r)}</p>
              <label className="check">
                <input
                  type="checkbox"
                  checked={skipBasics}
                  disabled={busy}
                  onChange={(e) => {
                    setSkipBasics(e.target.checked);
                    remember({ list, ticks: [...ticks], skipBasics: e.target.checked, names: namesRef.current });
                    void find({ list, skipBasics: e.target.checked });
                  }}
                />{" "}
                Skip basic lands
              </label>
              <p className="muted small">Basic lands (and Wastes, Snow-Covered…) are left out of the list.</p>
            </section>

            {r.groups.length === 0 && r.onlyInDecks.length === 0 && r.notOwned.length === 0 && (
              <p className="muted center">Nothing to find: the list is empty, or every card in it is a basic land.</p>
            )}

            {r.groups.map((g) => {
              // The copies found already go to the bottom of their binder.
              const lines = [...g.lines].sort((a, b) => Number(ticks.has(a.lineKey)) - Number(ticks.has(b.lineKey)));
              const copies = g.lines.reduce((n, l) => n + l.copies, 0);
              return (
                <section className="stat-block" key={g.binderKey}>
                  <h2>
                    {g.binderName} · {copies} card{copies === 1 ? "" : "s"}
                  </h2>
                  <ul className="stat-cards find-list">
                    {lines.map((l) => (
                      <FindLineRow key={l.lineKey} line={l} checked={ticks.has(l.lineKey)} onCheck={(on) => toggle(l.lineKey, on)} />
                    ))}
                  </ul>
                </section>
              );
            })}

            {r.onlyInDecks.length > 0 && (
              <section className="stat-block">
                <h2>Only in other decks ({r.onlyInDecks.reduce((n, l) => n + l.copies, 0)})</h2>
                <ul className="stat-cards find-list">
                  {r.onlyInDecks.map((l) => (
                    <li key={`d:${l.name}`}>
                      <span className="grow">
                        {l.copies > 1 ? `${l.copies}× ` : ""}
                        {l.name}
                        <div className="muted small">used by {l.decks.join(", ")}</div>
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {r.notOwned.length > 0 && (
              <section className="stat-block">
                <div className="value-title">
                  <h2>Not owned ({r.notOwned.reduce((n, l) => n + l.copies, 0)})</h2>
                  <button className="btn small-btn" disabled={busy} onClick={() => void addMissing(r.notOwned)}>
                    Add all to wishlist
                  </button>
                </div>
                <ul className="stat-cards find-list">
                  {r.notOwned.map((l) => (
                    <li key={`m:${l.name}`}>
                      <span className="grow">
                        {l.copies > 1 ? `${l.copies}× ` : ""}
                        {l.name}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </>
        )}
      </main>
    </>
  );
}

/** A copy to pull out: ticked off (dimmed, struck through) once it's found. */
function FindLineRow({ line, checked, onCheck }: { line: FindLine; checked: boolean; onCheck: (on: boolean) => void }) {
  return (
    <li className={checked ? "found" : undefined}>
      <label className="check find-line">
        <input type="checkbox" checked={checked} onChange={(e) => onCheck(e.target.checked)} />
        <span className="grow">
          <span>
            {line.copies > 1 ? `${line.copies}× ` : ""}
            <strong>{line.name}</strong>
          </span>
          <span className="muted small">
            {" "}
            {line.set.toUpperCase()} #{line.number}
          </span>
          {(line.foil || line.etched) && <span className="tag foil">{line.etched ? "ETCHED" : "FOIL"}</span>}
          {line.wantedPrinting && <span className="gold small"> ★ wanted printing</span>}
        </span>
      </label>
    </li>
  );
}
