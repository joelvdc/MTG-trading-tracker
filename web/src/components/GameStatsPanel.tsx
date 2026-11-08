"use client";

import { useMemo, useState } from "react";
import { DeckGamesCard } from "@/components/DeckGamesCard";
import { Dialog } from "@/components/Dialog";
import { ColourPips } from "@/components/Mana";
import {
  deckKey,
  deckResults,
  duration,
  firstPlayer,
  headToHead,
  inPeriod,
  overview,
  perMonth,
  PERIODS,
  playerResults,
  sortDecks,
  winsByType,
  type DeckOrder,
  type DeckResult,
  type Game,
  type Period,
} from "@/lib/gameshared";

const KEY = "mtg.gameStats";

function readSaved(): { period?: Period; order?: DeckOrder; onlyMine?: boolean } {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? "{}") ?? {};
  } catch {
    return {};
  }
}

function save(s: object) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ ...readSaved(), ...s }));
  } catch {
    // Not remembered; fine.
  }
}

const pct = (x: number | null) => (x == null ? "–" : `${Math.round(x * 100)} %`);
const shortDate = (ms: number) => new Date(ms).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "2-digit" });

/** The decks table's columns; those with an order sort the table (the app's sort orders). */
const DECK_COLUMNS: { label: string; order?: DeckOrder; title?: string }[] = [
  { label: "Deck" },
  { label: "Games", order: "GAMES" },
  { label: "Won–lost", order: "WINS" },
  { label: "Win rate", order: "WIN_RATE" },
  { label: "Avg place", order: "PLACE" },
  { label: "Avg game", order: "LENGTH" },
  { label: "Wins on", title: "The turn it usually wins on (games with turns counted)" },
  { label: "Last played", order: "RECENT" },
];

/**
 * The game stats: the app's decks, players and "You against…" tables, plus the website's extras (tiles, games per
 * month, how games were won, the starting player's results), for all time or a recent period.
 */
export function GameStatsPanel({ games, onGame }: { games: Game[]; onGame: (g: Game) => void }) {
  const [period, setPeriod] = useState<Period>(() => readSaved().period ?? "ALL");
  const [order, setOrder] = useState<DeckOrder>(() => readSaved().order ?? "GAMES");
  const [onlyMine, setOnlyMine] = useState<boolean>(() => readSaved().onlyMine ?? false);
  const [deck, setDeck] = useState<DeckResult | null>(null);

  const shown = useMemo(() => inPeriod(games, period, Date.now()), [games, period]);
  const sum = useMemo(() => overview(shown), [shown]);
  const months = useMemo(() => perMonth(shown, period === "QUARTER" ? 4 : period === "YEAR" ? 13 : 24), [shown, period]);
  const decks = useMemo(() => sortDecks(deckResults(shown), order).filter((d) => !onlyMine || d.mine), [shown, order, onlyMine]);
  const people = useMemo(() => playerResults(shown), [shown]);
  const h2h = useMemo(() => headToHead(shown), [shown]);
  const ways = useMemo(() => winsByType(shown), [shown]);
  const first = useMemo(() => firstPlayer(shown), [shown]);

  if (games.length === 0) return <p className="muted center">No games yet. Games played with the app&apos;s life tracker, or added here, show up in the stats.</p>;
  const top = Math.max(...months.map((m) => m.games), 1);
  const topWay = Math.max(...ways.map((w) => w.games), 1);

  return (
    <>
      <div className="controls">
        <div className="seg text" role="group" aria-label="Period">
          {PERIODS.map(([p, l]) => (
            <button
              key={p}
              className={period === p ? "on" : ""}
              aria-pressed={period === p}
              onClick={() => {
                setPeriod(p);
                save({ period: p });
              }}
            >
              {l}
            </button>
          ))}
        </div>
      </div>

      {shown.length === 0 ? (
        <p className="muted center">No games in this period.</p>
      ) : (
        <>
          <div className="stat-tiles game-tiles">
            <div className="stat-tile">
              <div className="big">{sum.games}</div>
              <div className="muted small">games played</div>
            </div>
            <div className="stat-tile">
              <div className="big">{pct(sum.myWinRate)}</div>
              <div className="muted small">you won</div>
            </div>
            <div className="stat-tile">
              <div className="big">{sum.avgMs != null ? duration(sum.avgMs) : "–"}</div>
              <div className="muted small">average game</div>
            </div>
            <div className="stat-tile">
              <div className="big">{sum.avgRounds != null ? sum.avgRounds.toFixed(1) : "–"}</div>
              <div className="muted small">average turns</div>
            </div>
          </div>

          <div className="stat-grid">
            <section className="stat-block">
              <h2>Games per month</h2>
              <div className="curve month-curve" role="img" aria-label={months.map((m) => `${m.label}: ${m.games}`).join(", ")}>
                {months.map((m) => (
                  <div key={m.month} className="curve-col" title={`${m.label}: ${m.games} game${m.games === 1 ? "" : "s"}`}>
                    <span className="curve-num">{m.games || ""}</span>
                    <span className="curve-bar" style={{ height: `${(m.games / top) * 100}%` }} />
                    <span className="curve-label">{m.label}</span>
                  </div>
                ))}
              </div>
            </section>

            <section className="stat-block">
              <h2>How games were won</h2>
              {ways.length === 0 ? (
                <p className="muted small">No winners recorded.</p>
              ) : (
                <ul className="bars">
                  {ways.map((w) => (
                    <li key={w.label} className="bar-row" title={`${w.label}: ${w.games} game${w.games === 1 ? "" : "s"}`}>
                      <span className="bar-label">{w.label}</span>
                      <span className="bar-track" aria-hidden="true">
                        <span className={w.key ? "bar-fill" : "bar-fill untold"} style={{ width: `${(w.games / topWay) * 100}%` }} />
                      </span>
                      <span className="bar-num">{w.games}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="stat-block">
              <h2>Starting first</h2>
              {first.games === 0 ? (
                <p className="muted small">No games with a known starting player and winner yet.</p>
              ) : (
                <>
                  <div className="big-line">
                    <strong>{pct(first.wins / first.games)}</strong> <span className="muted">of games won by the player who started</span>
                  </div>
                  <p className="muted small">
                    {first.wins} of {first.games} games. With these tables an equal chance would be about {pct(first.fairShare)}.
                  </p>
                </>
              )}
            </section>
          </div>

          <h2 className="section-head">Decks</h2>
          <div className="controls">
            <button
              className={onlyMine ? "chip on" : "chip"}
              aria-pressed={onlyMine}
              onClick={() => {
                setOnlyMine(!onlyMine);
                save({ onlyMine: !onlyMine });
              }}
            >
              My decks
            </button>
            <span className="muted small">Click a deck for its results and last games.</span>
          </div>
          <div className="table-wrap">
            <table className="wish-table game-table">
              <thead>
                <tr>
                  {DECK_COLUMNS.map((c) => (
                    <th key={c.label} className={c.label === "Deck" ? undefined : "num"} title={c.title} aria-sort={c.order && order === c.order ? "descending" : undefined}>
                      {c.order ? (
                        <button
                          className="th-sort"
                          onClick={() => {
                            setOrder(c.order!);
                            save({ order: c.order });
                          }}
                        >
                          {c.label}
                          {order === c.order ? " ▾" : ""}
                        </button>
                      ) : (
                        <span className="th-plain">{c.label}</span>
                      )}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {decks.map((d) => (
                  <tr key={d.key}>
                    <td>
                      <button className="link left" onClick={() => setDeck(d)}>
                        {d.colorIdentity && <ColourPips identity={d.colorIdentity} size={12} />} {d.name}
                        {d.mine && <span className="tag">mine</span>}
                        {d.lentGames > 0 && <span className="tag">{d.lentGames} lent</span>}
                      </button>
                      {d.commander && d.commander !== d.name && <div className="muted small">{d.commander}</div>}
                    </td>
                    <td className="num">{d.games}</td>
                    <td className="num nowrap">
                      {d.wins}–{d.losses}
                    </td>
                    <td className="num">{pct(d.winRate)}</td>
                    <td className="num">{d.avgPlace != null ? d.avgPlace.toFixed(1) : "–"}</td>
                    <td className="num nowrap">{duration(d.avgMs)}</td>
                    <td className="num">{d.usualWinRound ?? "–"}</td>
                    <td className="num nowrap">{shortDate(d.lastPlayed)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="stat-grid game-people">
            <section className="stat-block">
              <h2>Players</h2>
              <table className="game-mini">
                <thead>
                  <tr>
                    <th>Player</th>
                    <th className="num">Games</th>
                    <th className="num">Wins</th>
                    <th className="num">Win rate</th>
                    <th className="num">Avg place</th>
                    <th className="num" title="Games they started, and won of those">
                      Started
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {people.map((p) => (
                    <tr key={p.key}>
                      <td>
                        {p.name}
                        {p.me && <span className="muted"> (you)</span>}
                      </td>
                      <td className="num">{p.games}</td>
                      <td className="num">{p.wins}</td>
                      <td className="num">{pct(p.winRate)}</td>
                      <td className="num">{p.avgPlace != null ? p.avgPlace.toFixed(1) : "–"}</td>
                      <td className="num nowrap">{p.startedGames ? `${p.startedWins} of ${p.startedGames}` : "–"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
            {h2h.length > 0 && (
              <section className="stat-block">
                <h2>You against…</h2>
                <ul className="plain-list">
                  {h2h.map((h) => (
                    <li key={h.key}>
                      <span className="grow">{h.name}</span>
                      <span className="muted small">{h.games} games</span>
                      <strong className="num h2h">
                        {h.ahead}–{h.behind}
                      </strong>
                    </li>
                  ))}
                </ul>
                <p className="muted small">Who finished ahead, in the games you both played.</p>
              </section>
            )}
          </div>
        </>
      )}

      {deck && (
        <Dialog title={deck.name} onClose={() => setDeck(null)}>
          <DeckGamesCard
            r={deck}
            last={shown.filter((g) => g.seats.some((s) => deckKey(s) === deck.key)).sort((a, b) => b.startedAt - a.startedAt)}
            isDeck={(s) => deckKey(s) === deck.key}
            onGame={(g) => {
              setDeck(null);
              onGame(g);
            }}
          />
          <div className="dialog-actions">
            {deck.deckId != null && (
              <a className="btn" href={`/decks/${deck.deckId}`}>
                Open the deck
              </a>
            )}
            <button className="btn" onClick={() => setDeck(null)}>
              Close
            </button>
          </div>
        </Dialog>
      )}
    </>
  );
}
