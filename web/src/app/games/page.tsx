"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { Dialog } from "@/components/Dialog";
import { GameDialog } from "@/components/GameDialog";
import { GameStatsPanel } from "@/components/GameStatsPanel";
import { ColourPips } from "@/components/Mana";
import { TopBar } from "@/components/Nav";
import { PlayersPanel } from "@/components/PlayersPanel";
import { deckKey, duration, gamesCsv, playerKey, winTypeLabel, type Game, type GamesView } from "@/lib/gameshared";
import type { GameEdit, GameEditResult, GameRecord } from "@/lib/games";

type Tab = "GAMES" | "STATS" | "PLAYERS";
const TABS: [Tab, string][] = [
  ["GAMES", "Games"],
  ["STATS", "Stats"],
  ["PLAYERS", "Players"],
];
const KEY = "mtg.games";
const PER_PAGE = [25, 50, 100];
type Answer = GamesView & { result?: GameEditResult };

function readPerPage(): number {
  try {
    const n = Number(JSON.parse(localStorage.getItem(KEY) ?? "{}").perPage);
    return PER_PAGE.includes(n) ? n : 25;
  } catch {
    return 25;
  }
}

const day = (ms: number) => new Date(ms).toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short", year: "numeric" });

/** Seats in finishing order (no place last, then by seat). */
const byPlace = (g: Game) => [...g.seats].sort((a, b) => (a.place ?? 99) - (b.place ?? 99) || a.seat - b.seat);

/** The Games section: the games your phones recorded, their stats and the players (the life tracker stays on the phone). */
export default function GamesPage() {
  const [view, setView] = useState<GamesView | null>(null);
  const [folder, setFolder] = useState("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [note, setNote] = useState<{ text: string; undo?: GameRecord } | null>(null);
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState<Tab>("GAMES");
  const [open, setOpen] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [deleting, setDeleting] = useState<Game | null>(null);
  const [dropping, setDropping] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [player, setPlayer] = useState("");
  const [deck, setDeck] = useState("");
  const [page, setPage] = useState(0);
  const [perPage, setPerPage] = useState(25);

  const goLogin = useCallback(() => window.location.replace("/login"), []);

  useEffect(() => {
    const q = new URLSearchParams(window.location.search);
    const t = q.get("view")?.toUpperCase();
    if (t === "STATS" || t === "PLAYERS") setTab(t);
    if (q.get("game")) setOpen(q.get("game"));
    setPerPage(readPerPage());
    (async () => {
      const [s, g] = await Promise.all([api<{ loggedIn: boolean; folder?: string }>("/api/session"), api<GamesView>("/api/games")]);
      if (!s.data.loggedIn || g.status === 401 || g.data.loggedOut) return goLogin();
      setFolder(s.data.folder ?? "");
      if (g.ok && Array.isArray(g.data.games)) setView(g.data);
      else setLoadError(g.data.error ?? "Could not load the games.");
    })();
  }, [goLogin]);

  function pickTab(t: Tab) {
    setTab(t);
    const u = new URL(window.location.href);
    if (t === "GAMES") u.searchParams.delete("view");
    else u.searchParams.set("view", t.toLowerCase());
    u.searchParams.delete("game");
    window.history.replaceState(null, "", u);
  }

  /** Sends one change; true when it worked (the games shown are then as saved). */
  async function edit(e: GameEdit): Promise<GameEditResult | null> {
    setBusy(true);
    setMessage(null);
    const r = await api<Answer>("/api/games", { edit: e });
    setBusy(false);
    if (r.status === 401 || r.data.loggedOut) {
      goLogin();
      return null;
    }
    if (r.ok && Array.isArray(r.data.games)) {
      setView(r.data);
      return r.data.result ?? {};
    }
    setMessage(r.data.error ?? "That change did not work.");
    return null;
  }

  const games = view?.games;
  const filtered = useMemo(() => {
    if (!games) return [];
    const words = search.trim().toLowerCase().split(/\s+/).filter(Boolean);
    return games.filter((g) => {
      if (player && !g.seats.some((s) => playerKey(s) === player)) return false;
      if (deck && !g.seats.some((s) => deckKey(s) === deck)) return false;
      if (!words.length) return true;
      const text = [g.note ?? "", winTypeLabel(g.winType) ?? "", ...g.seats.flatMap((s) => [s.name, s.deckName ?? "", s.commander ?? ""])].join(" ").toLowerCase();
      return words.every((w) => text.includes(w));
    });
  }, [games, search, player, deck]);

  /** The players and decks met in the games, for the filters. */
  const choices = useMemo(() => {
    const ps = new Map<string, string>();
    const ds = new Map<string, string>();
    for (const g of games ?? [])
      for (const s of g.seats) {
        if (!ps.has(playerKey(s))) ps.set(playerKey(s), s.name);
        const name = s.deckName || s.commander;
        if (name && !ds.has(deckKey(s))) ds.set(deckKey(s), name);
      }
    const sorted = (m: Map<string, string>) => [...m.entries()].sort((a, b) => a[1].toLowerCase().localeCompare(b[1].toLowerCase()));
    return { players: sorted(ps), decks: sorted(ds) };
  }, [games]);

  if (!view) {
    return (
      <>
        <TopBar page="games" folder={folder} />
        <main className="page">{loadError ? <div className="bar error">{loadError}</div> : <p className="muted center">Loading…</p>}</main>
      </>
    );
  }

  const openGame = open ? (view.games.find((g) => g.uid === open) ?? null) : null;
  const pages = Math.max(1, Math.ceil(filtered.length / perPage));
  const pageAt = Math.min(page, pages - 1);
  const pageRows = filtered.slice(pageAt * perPage, (pageAt + 1) * perPage);
  const locked = busy || view.readOnly;

  function setOpenGame(uid: string | null) {
    setOpen(uid);
    const u = new URL(window.location.href);
    if (uid) u.searchParams.set("game", uid);
    else u.searchParams.delete("game");
    window.history.replaceState(null, "", u);
  }

  function exportCsv() {
    const blob = new Blob([gamesCsv(view!.games)], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `mtg-games-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }

  return (
    <>
      <TopBar page="games" folder={folder} />
      <main className="page wide">
        <div className="chips" role="tablist" aria-label="Games">
          {TABS.map(([t, l]) => (
            <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? "chip on" : "chip"} onClick={() => pickTab(t)}>
              {l}
              {t === "GAMES" && <span className="count">{view.games.length}</span>}
              {t === "PLAYERS" && <span className="count">{view.players.length}</span>}
            </button>
          ))}
        </div>

        {message && (
          <div className="bar error" role="alert">
            {message}
            <button className="link" onClick={() => setMessage(null)}>
              Dismiss
            </button>
          </div>
        )}
        {note && (
          <div className="bar ok" role="status">
            {note.text}{" "}
            {note.undo && (
              <button
                className="link"
                disabled={locked}
                onClick={async () => {
                  if (await edit({ type: "restoreGame", game: note.undo! })) setNote({ text: "The game is back." });
                }}
              >
                Undo
              </button>
            )}
            <button className="link" onClick={() => setNote(null)}>
              OK
            </button>
          </div>
        )}
        {view.readOnly && <div className="bar">A newer app wrote your data: the games can be looked at here, but update the app before changing them.</div>}

        {tab === "GAMES" && (
          <>
            {view.paused.length > 0 && (
              <section className="stat-block paused-games">
                <h2>Paused games</h2>
                <ul className="plain-list">
                  {view.paused.map((p) => (
                    <li key={p.uid}>
                      <span className="grow">
                        {p.names.join(", ") || "A game"}
                        <span className="muted small"> · paused {day(p.pausedAt)}</span>
                      </span>
                      <span className="muted small">Continue it on a phone</span>
                      <button className="link" disabled={locked} onClick={() => setDropping(p.uid)}>
                        Drop it
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            <div className="controls">
              <input
                type="search"
                aria-label="Search the games"
                placeholder="Search player, deck, commander or note"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
              />
              <select
                aria-label="Only games with this player"
                value={player}
                onChange={(e) => {
                  setPlayer(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Every player</option>
                {choices.players.map(([k, n]) => (
                  <option key={k} value={k}>
                    {n}
                  </option>
                ))}
              </select>
              <select
                aria-label="Only games with this deck"
                value={deck}
                onChange={(e) => {
                  setDeck(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Every deck</option>
                {choices.decks.map(([k, n]) => (
                  <option key={k} value={k}>
                    {n}
                  </option>
                ))}
              </select>
              <span className="spacer" />
              <button className="btn" disabled={locked} onClick={() => setAdding(true)}>
                ＋ Add a past game
              </button>
              <button className="btn" disabled={view.games.length === 0} onClick={exportCsv} title="One line per player per game, like the app's export">
                Export (CSV)
              </button>
            </div>

            {view.games.length === 0 ? (
              <p className="muted center">No games yet. Games played with the app&apos;s life tracker show up here, or add a past game.</p>
            ) : filtered.length === 0 ? (
              <p className="muted center">No games match.</p>
            ) : (
              <ul className="game-list">
                {pageRows.map((g) => (
                  <li key={g.uid}>
                    <button className="game-line" onClick={() => setOpenGame(g.uid)}>
                      <span className="game-when">
                        <strong>{day(g.startedAt)}</strong>
                        <span className="muted small">
                          {[g.durationMs > 0 ? duration(g.durationMs) : null, g.rounds != null ? `${g.rounds} turns` : null, g.draw ? "draw" : winTypeLabel(g.winType), g.manual ? "added by hand" : null]
                            .filter(Boolean)
                            .join(" · ")}
                        </span>
                      </span>
                      <span className="game-seats">
                        {byPlace(g).map((s) => (
                          <span key={s.seat} className={s.place === 1 && !g.draw ? "game-seat won" : "game-seat"}>
                            <span className="game-place" aria-label={s.place === 1 && !g.draw ? "Won" : s.place != null ? `Place ${s.place}` : "No place"}>
                              {s.place === 1 && !g.draw ? "🏆" : (s.place ?? "–")}
                            </span>
                            <span className="game-who">
                              {s.name}
                              {(s.deckName || s.commander) && (
                                <span className="muted small">
                                  {" "}
                                  {s.colorIdentity ? <ColourPips identity={s.colorIdentity} size={11} /> : null} {s.deckName || s.commander}
                                </span>
                              )}
                            </span>
                          </span>
                        ))}
                      </span>
                      {g.note && <span className="game-note muted small">{g.note}</span>}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {filtered.length > 0 && (
              <div className="pager">
                <label className="inline small">
                  <span className="muted">Games per page</span>
                  <select
                    aria-label="Games per page"
                    value={perPage}
                    onChange={(e) => {
                      const n = Number(e.target.value);
                      setPerPage(n);
                      setPage(0);
                      try {
                        localStorage.setItem(KEY, JSON.stringify({ perPage: n }));
                      } catch {
                        // Not remembered; fine.
                      }
                    }}
                  >
                    {PER_PAGE.map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                </label>
                <span className="spacer" />
                <span className="muted small">
                  {pageAt * perPage + 1}–{Math.min((pageAt + 1) * perPage, filtered.length)} of {filtered.length}
                </span>
                <button className="btn small-btn" disabled={pageAt === 0} onClick={() => setPage(pageAt - 1)}>
                  Previous
                </button>
                <button className="btn small-btn" disabled={pageAt >= pages - 1} onClick={() => setPage(pageAt + 1)}>
                  Next
                </button>
              </div>
            )}
          </>
        )}

        {tab === "STATS" && <GameStatsPanel games={view.games} onGame={(g) => setOpenGame(g.uid)} />}
        {tab === "PLAYERS" && <PlayersPanel view={view} busy={busy} onEdit={async (e) => !!(await edit(e))} />}

        {(openGame || adding) && (
          <GameDialog
            key={openGame?.uid ?? "new"}
            game={adding ? null : openGame}
            view={view}
            busy={busy}
            onClose={() => {
              setAdding(false);
              setOpenGame(null);
            }}
            onSave={async (e) => {
              const res = await edit(e);
              if (!res) return false;
              setNote({ text: adding ? "The game is added." : "The game is saved." });
              setAdding(false);
              setOpenGame(null);
              return true;
            }}
            onDelete={(g) => setDeleting(g)}
          />
        )}
        {deleting && (
          <Dialog title="Delete this game?" onClose={() => setDeleting(null)}>
            <p>
              {day(deleting.startedAt)}: {byPlace(deleting).map((s) => s.name).join(", ")}. It&apos;s taken out of the stats on your phones too.
            </p>
            <div className="dialog-actions">
              <button
                className="btn danger"
                disabled={locked}
                onClick={async () => {
                  const res = await edit({ type: "deleteGame", uid: deleting.uid });
                  if (res) {
                    setDeleting(null);
                    setOpenGame(null);
                    setNote({ text: "The game is deleted.", undo: res.removed });
                  }
                }}
              >
                Delete
              </button>
              <button className="btn" onClick={() => setDeleting(null)}>
                Cancel
              </button>
            </div>
          </Dialog>
        )}
        {dropping && (
          <Dialog title="Drop this paused game?" onClose={() => setDropping(null)}>
            <p>It isn&apos;t saved, and it goes from your phones too.</p>
            <div className="dialog-actions">
              <button
                className="btn danger"
                disabled={locked}
                onClick={async () => {
                  if (await edit({ type: "dropPaused", uid: dropping })) setDropping(null);
                }}
              >
                Drop it
              </button>
              <button className="btn" onClick={() => setDropping(null)}>
                Cancel
              </button>
            </div>
          </Dialog>
        )}
      </main>
    </>
  );
}
