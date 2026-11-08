"use client";

import { duration, lent, ordinal, winTypeLabel, type DeckResult, type Game } from "@/lib/gameshared";

/**
 * A deck's results in the recorded games and its last games (the app's DeckGamesCard, on the deck page and in the
 * game stats). [isDeck] picks the deck's seat in a game.
 */
export function DeckGamesCard({ r, last, isDeck, onGame }: { r: DeckResult; last: Game[]; isDeck: (s: Game["seats"][number]) => boolean; onGame: (g: Game) => void }) {
  const rows: [string, string][] = [
    ["Average game", duration(r.avgMs)],
    ...(r.avgWinMs != null ? ([["Average win", duration(r.avgWinMs)]] as [string, string][]) : []),
    ...(r.avgRounds != null ? ([["Average turns", r.avgRounds.toFixed(1)]] as [string, string][]) : []),
    ...(r.usualWinRound != null ? ([["Usually wins on", `turn ${r.usualWinRound}`]] as [string, string][]) : []),
    ...(r.winTypes.length ? ([["Wins by", r.winTypes.map(([k, n]) => `${winTypeLabel(k)!.toLowerCase()} ${n}`).join(" · ")]] as [string, string][]) : []),
    ...(r.startedGames > 0 ? ([["Started first", `${r.startedGames} games, ${r.startedWins} won`]] as [string, string][]) : []),
    ...(r.lentGames > 0 ? ([["Lent", `${r.lentGames} games, ${r.lentWins} won`]] as [string, string][]) : []),
  ];
  const scores = [r.bracket != null ? `Bracket ${r.bracket}` : null, r.power != null ? `power ${r.power.toFixed(1)}` : null].filter(Boolean).join(" · ");
  const opp = [r.oppBracket != null ? `bracket ${r.oppBracket.toFixed(1)}` : null, r.oppPower != null ? `power ${r.oppPower.toFixed(1)}` : null].filter(Boolean).join(" · ");
  return (
    <div className="stack tight">
      <div className="game-card">
        <div className="game-big">
          <div>
            <div className="big">
              {r.wins}–{r.losses}
            </div>
            <div className="small muted">won–lost</div>
          </div>
          <div>
            <div className="big">{Math.round(r.winRate * 100)} %</div>
            <div className="small muted">win rate</div>
          </div>
          <div>
            <div className="big">{r.avgPlace != null ? r.avgPlace.toFixed(1) : "–"}</div>
            <div className="small muted">average place</div>
          </div>
        </div>
        <dl className="game-facts">
          {rows.map(([k, v]) => (
            <div key={k}>
              <dt>{k}</dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>
        {opp && (
          <p className="muted small">
            {scores ? `${scores}. ` : ""}The decks it played against: {opp} on average.
          </p>
        )}
      </div>
      <ul className="plain-list game-last">
        {last.slice(0, 5).map((g) => {
          const s = g.seats.find(isDeck);
          const won = s?.place === 1 && !g.draw;
          return (
            <li key={g.uid}>
              <button className="link left" onClick={() => onGame(g)}>
                <span aria-hidden="true">{won ? "🏆" : "–"}</span>{" "}
                {[
                  new Date(g.startedAt).toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" }),
                  won ? "won" : s?.place != null ? ordinal(s.place) : null,
                  duration(g.durationMs),
                  won && g.rounds != null ? `turn ${g.rounds}` : null,
                  s && lent(s) ? `played by ${s.name}` : null,
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
