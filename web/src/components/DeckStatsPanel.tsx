"use client";

import { Fragment, useEffect, useMemo, useState } from "react";
import { costs, curve, sources, type StatsCard } from "@/lib/deckstats";
import { calculate, type ManaOdds } from "@/lib/manaodds";
import { WIN_SLOW_NOTE, winAbout, winDamageNote, winWindow, type WinResult } from "@/lib/winodds";
import { COLOUR_FILL, COLOUR_NAMES, ManaCost } from "./Mana";
import type { WinState } from "./useWinOdds";

const KEY = "mtg.deckStats";

/** One stats section (only the whole Stats block folds, on the deck page). */
function Fold({ title, className, children }: { title: string; className: string; children: React.ReactNode }) {
  return (
    <section className={className}>
      <h2>{title}</h2>
      {children}
    </section>
  );
}

function useStored(key: string, initial: boolean): [boolean, (v: boolean) => void] {
  const [v, setV] = useState(initial);
  useEffect(() => {
    try {
      const s = localStorage.getItem(`${KEY}.${key}`);
      if (s !== null) setV(s === "1");
    } catch {
      // Storage unavailable.
    }
  }, [key]);
  return [
    v,
    (x: boolean) => {
      setV(x);
      try {
        localStorage.setItem(`${KEY}.${key}`, x ? "1" : "0");
      } catch {
        // Not remembered.
      }
    },
  ];
}

/** The app's deck stats cards (DeckStatsViews.kt): mana curve, colours in the costs, mana production, commander odds. */
export function DeckStatsPanel({
  cards,
  identity,
  win,
}: {
  cards: StatsCard[];
  identity: string;
  win: WinState;
}) {
  const [landsOnly, setLandsOnly] = useStored("landsOnly", false);
  const [colourless, setColourless] = useStored("colourless", true);
  const [odds, setOdds] = useState<ManaOdds | null | undefined>(undefined);
  const [about, setAbout] = useState(false);
  const hasCommander = cards.some((c) => c.commander);
  const waiting = cards.filter((c) => c.producedMana === null).length;

  const mc = useMemo(() => curve(cards), [cards]);
  const cc = useMemo(() => costs(cards), [cards]);
  const src = useMemo(() => sources(cards, hasCommander ? identity : null, landsOnly, colourless), [cards, identity, hasCommander, landsOnly, colourless]);

  // The commander odds: 10,000 shuffles, worked out after the page has drawn.
  useEffect(() => {
    if (!hasCommander) return;
    setOdds(undefined);
    const t = setTimeout(() => setOdds(calculate(cards, identity)), 30);
    return () => clearTimeout(t);
  }, [cards, identity, hasCommander]);

  const top = Math.max(...mc.counts, 1);
  const costTotal = cc.reduce((s, c) => s + c.count, 0);
  const srcTop = Math.max(...src.map((s) => s.count), 1);

  return (
    <div className="deck-stats">
      <Fold title="Mana curve" className="stat-block">
        {mc.spells === 0 ? (
          <p className="muted small">No spells yet (lands are left out).</p>
        ) : (
          <>
            <div className="curve" role="img" aria-label={`Mana curve: ${mc.counts.map((n, i) => `${i === 7 ? "7+" : i}: ${n}`).join(", ")}`}>
              {mc.counts.map((n, i) => (
                <div key={i} className="curve-col" title={`${n} card${n === 1 ? "" : "s"} of mana value ${i === 7 ? "7 or more" : i}`}>
                  <span className="curve-num">{n || ""}</span>
                  <span className="curve-bar" style={{ height: `${(n / top) * 100}%` }} />
                  <span className="curve-label">{i === 7 ? "7+" : i}</span>
                </div>
              ))}
            </div>
            <p className="muted small">
              {mc.spells} spells · average mana value {mc.average.toFixed(2)}
            </p>
          </>
        )}
      </Fold>

      <Fold title="Mana cost" className="stat-block">
        {costTotal === 0 ? (
          <p className="muted small">No coloured mana symbols in the costs.</p>
        ) : (
          <>
            <div className="split-bar" role="img" aria-label={cc.map((c) => `${COLOUR_NAMES[c.colour]} ${c.count}`).join(", ")}>
              {cc.map((c) => (
                <span key={c.colour} style={{ flexGrow: c.count, background: COLOUR_FILL[c.colour] }} title={`${COLOUR_NAMES[c.colour]}: ${c.count} symbols`} />
              ))}
            </div>
            <ul className="legend">
              {cc.map((c) => (
                <li key={c.colour}>
                  <ManaCost cost={`{${c.colour}}`} size={14} /> {c.count} ({Math.round((c.count / costTotal) * 100)}%)
                </li>
              ))}
            </ul>
          </>
        )}
      </Fold>

      <Fold title="Mana production" className="stat-block">
        <div className="inline small">
          <label className="check">
            <input type="checkbox" checked={landsOnly} onChange={(e) => setLandsOnly(e.target.checked)} />
            <span>Lands only</span>
          </label>
          <label className="check">
            <input type="checkbox" checked={colourless} onChange={(e) => setColourless(e.target.checked)} />
            <span>Colourless</span>
          </label>
        </div>
        {src.length === 0 ? (
          <p className="muted small">{landsOnly ? "No lands that make mana yet." : "No cards that make mana yet."}</p>
        ) : (
          <ul className="bars sources">
            {src.map((s) => (
              <li key={s.colour}>
                <details>
                  <summary className="bar-row">
                    <span className="bar-label">
                      <ManaCost cost={`{${s.colour}}`} size={14} /> {COLOUR_NAMES[s.colour]}
                    </span>
                    <span className="bar-track" aria-hidden="true">
                      <span className="bar-fill" style={{ width: `${(s.count / srcTop) * 100}%`, background: COLOUR_FILL[s.colour] }} />
                    </span>
                    <span className="bar-num">{s.count}</span>
                  </summary>
                  <ul className="plain-list source-cards">
                    {s.cards.map((l) => (
                      <li key={l.name} className="small">
                        <span className="grow">
                          {l.quantity > 1 ? `${l.quantity}× ` : ""}
                          {l.name}
                          {l.finds.length > 0 && <span className="muted"> · finds {l.finds.join(", ")}</span>}
                        </span>
                      </li>
                    ))}
                  </ul>
                </details>
              </li>
            ))}
          </ul>
        )}
        <p className="muted small">Each card counts once for every colour it can make. Fetch lands count for the lands they can find in this deck; &quot;any colour&quot; cards only for the commander&apos;s colours.</p>
      </Fold>

      {hasCommander && (
        <Fold title="Casting the commander" className="stat-block odds-block">
          {odds === undefined ? (
            <p className="muted small">Shuffling the deck…</p>
          ) : odds === null ? (
            <p className="muted small">Too few cards to shuffle.</p>
          ) : (
            <>
              {odds.commanders.map((c) => {
                const i = c.byTurn.findIndex((p) => p >= 50);
                return (
                  <p key={c.name} className="small">
                    {c.name} ({c.manaValue} mana): {i < 0 ? `not by turn ${c.byTurn.length} in most games` : `turn ${i + 1} in ${c.byTurn[i]}% of games`}
                  </p>
                );
              })}
              <div className="table-wrap">
                <table className="odds-table">
                  <thead>
                    <tr>
                      <th />
                      {odds.commanders[0]?.byTurn.map((_, t) => (
                        <th key={t}>T{t + 1}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {odds.commanders.map((c) => (
                      <tr key={c.name}>
                        <th>{odds.commanders.length > 1 ? c.name : "Commander"}</th>
                        {c.byTurn.map((p, t) => (
                          <td key={t} style={heat(p)}>
                            {p}
                          </td>
                        ))}
                      </tr>
                    ))}
                    {odds.colours.map(([colour, values]) => (
                      <tr key={colour}>
                        <th>
                          <ManaCost cost={`{${colour}}`} size={16} />
                        </th>
                        {values.map((p, t) => (
                          <td key={t} style={heat(p)}>
                            {p}
                          </td>
                        ))}
                      </tr>
                    ))}
                    {odds.allColours && (
                      <tr>
                        <th>All colours</th>
                        {odds.allColours.map((p, t) => (
                          <td key={t} style={heat(p)}>
                            {p}
                          </td>
                        ))}
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <p className="muted small">
                % of games, from {odds.games.toLocaleString("en-GB")} shuffles of this deck (playing alone, a best case), the same model as the app.{" "}
                <button className="link" onClick={() => setAbout((a) => !a)}>
                  {about ? "Less" : "How?"}
                </button>
              </p>
              {about && (
                <p className="muted small">
                  It plays the deck alone: it draws (also on turn 1), keeps a hand like Commander players do (a free mulligan, then London mulligans), plays a land
                  each turn and casts mana makers when it can. Counted: {odds.lands} lands, {odds.rocks} mana rocks, {odds.creatures} mana creatures (from the turn
                  after) and {odds.ramp} land ramp spells, all costing up to 4. Lands that enter tapped come in tapped (&quot;unless …&quot; lands check their
                  condition), and a tapped land is played first when an untapped one&apos;s mana wouldn&apos;t be used that turn. Not counted: opponents, removal,
                  Treasure, rituals and cost reducers. The method follows{" "}
                  <a
                    href="https://www.tcgplayer.com/content/article/How-Many-Sources-Do-You-Need-to-Consistently-Cast-Your-Spells-A-2022-Update/dc23a7d2-0a16-4c0b-ad36-586fcca03ad8/"
                    target="_blank"
                    rel="noreferrer"
                  >
                    Frank Karsten&apos;s articles
                  </a>
                  , with this deck&apos;s own cards instead of his tables.
                </p>
              )}
            </>
          )}
          {waiting > 0 && <p className="muted small">{waiting} card(s) are still waiting for their details from Scryfall, so they count as plain spells for now.</p>}
        </Fold>
      )}

      {hasCommander && <WinningBlock win={win} missing={cards.filter((c) => c.typeLine === null).length} />}
    </div>
  );
}

/** The first win (green) and 4 in 10 games (the accent, gold in Dark). */
const FIRST_WIN = "var(--green-text)";
const FOUR_IN_TEN = "var(--accent)";

/** A heatmap cell: the theme's second accent, stronger with a higher share. */
function heat(p: number): { background: string; color?: string } {
  return { background: `color-mix(in srgb, var(--accent2) ${Math.round(Math.max(8, p))}%, transparent)`, color: p > 55 ? "var(--on-accent2)" : undefined };
}

/**
 * When the deck can win (the app's WinningCard): the first turn it wins (green) and the turn of 4 in 10 games
 * (gold), 9 in 10 games, the share of games won by each turn (and by a combo), where the damage came from and what
 * was counted. Worked out by the app's own code in this browser.
 */
function WinningBlock({ win, missing }: { win: WinState; missing: number }) {
  const [about, setAbout] = useState(false);
  return (
    <Fold title="Winning the game" className="stat-block odds-block">
      {win.status === "working" ? (
        <div role="progressbar" aria-valuemin={0} aria-valuemax={win.games} aria-valuenow={win.started} aria-label="Games played">
          <div className="progress">
            <span style={{ width: `${(win.started / win.games) * 100}%` }} />
          </div>
          <p className="muted small">
            Playing the deck: {win.started.toLocaleString("en-GB")} of {win.games.toLocaleString("en-GB")} games… (in this browser; kept here afterwards)
          </p>
        </div>
      ) : win.status === "off" ? (
        <p className="muted small">Playing the deck 10,000 times… (half a minute or so, in this browser; kept here afterwards)</p>
      ) : win.status === "failed" ? (
        <p className="warn-text small">Couldn&apos;t work the win turn out: {win.error}</p>
      ) : win.win === null ? (
        <p className="muted small">Too few cards to play.</p>
      ) : (
        <WinningTable w={win.win} about={about} setAbout={setAbout} />
      )}
      {missing > 0 && win.status === "done" && <p className="muted small">{missing} card(s) are still waiting for their details from Scryfall, so they don&apos;t count yet.</p>}
    </Fold>
  );
}

function WinningTable({ w, about, setAbout }: { w: WinResult; about: boolean; setAbout: (f: (a: boolean) => boolean) => void }) {
  const marks = new Map<number, string>();
  if (w.firstWin !== null) marks.set(w.firstWin, FIRST_WIN);
  if (w.fourInTen !== null) marks.set(w.fourInTen, FOUR_IN_TEN);
  const turn = (t: number) => {
    const c = marks.get(t);
    return c ? <strong style={{ color: c }}>turn {t}</strong> : <>turn {t}</>;
  };
  const turns = winWindow(w);
  const cell = (p: number, mark?: string) => (
    <td style={{ ...heat(p), outline: mark ? `2px solid ${mark}` : undefined }}>{p}</td>
  );
  return (
    <>
      <p className="small">
        {w.firstWin !== null && <>Can win from {turn(w.firstWin)}. </>}
        {w.fourInTen !== null ? <>Wins by {turn(w.fourInTen)}</> : <>{w.odds.byTurn[w.odds.byTurn.length - 1]}% of games won by turn {w.odds.byTurn.length}</>}
        {w.nineInTen !== null && <>; 9 in 10 by turn {w.nineInTen}</>}.
      </p>
      <div className="table-wrap">
        <table className="odds-table">
          <thead>
            <tr>
              <th />
              {turns.map((t) => (
                <th key={t} style={marks.has(t) ? { color: marks.get(t), fontWeight: 700 } : undefined}>
                  T{t}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              <th>Win</th>
              {turns.map((t) => (
                <Fragment key={t}>{cell(w.odds.byTurn[t - 1], marks.get(t))}</Fragment>
              ))}
            </tr>
            {w.comboShare > 0 && (
              <tr>
                <th>by combo</th>
                {turns.map((t) => (
                  <Fragment key={t}>{cell(w.odds.byCombo[t - 1])}</Fragment>
                ))}
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {winDamageNote(w) && <p className="muted small">{winDamageNote(w)}</p>}
      {w.fourInTen === null && <p className="muted small">{WIN_SLOW_NOTE}</p>}
      <p className="muted small">
        % of games won by each turn, from {w.odds.games.toLocaleString("en-GB")} shuffles of this deck (playing alone against three players at 40 life, a best case),
        worked out by the app&apos;s own code.{" "}
        <button className="link" onClick={() => setAbout((a) => !a)}>
          {about ? "Less" : "How?"}
        </button>
      </p>
      {about && <p className="muted small">{winAbout(w.odds.counted)}</p>}
    </>
  );
}
