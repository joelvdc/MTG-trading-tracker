"use client";

import { useEffect, useMemo, useState } from "react";
import { costs, curve, scryfallImage, sources, tokens, type StatsCard } from "@/lib/deckstats";
import { calculate, type ManaOdds } from "@/lib/manaodds";
import { COLOUR_FILL, COLOUR_NAMES, ManaCost } from "./Mana";

const KEY = "mtg.deckStats";

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

/** The app's deck stats cards (DeckStatsViews.kt): mana curve, colours in the costs, mana production, commander odds, tokens. */
export function DeckStatsPanel({
  cards,
  identity,
  tokenInfo,
  hover,
}: {
  cards: StatsCard[];
  identity: string;
  tokenInfo: Record<string, { oi: string | null; pt: string | null }>;
  hover: { follow: (url: string | null) => (e: React.MouseEvent) => void; hide: () => void };
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
  const toks = useMemo(() => {
    const oi: Record<string, string | undefined> = {};
    const pt: Record<string, string | null> = {};
    for (const [id, t] of Object.entries(tokenInfo)) {
      if (t.oi) oi[id] = t.oi;
      pt[id] = t.pt;
    }
    return tokens(cards, oi, pt);
  }, [cards, tokenInfo]);

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
      <section className="stat-block">
        <h2>Mana curve</h2>
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
      </section>

      <section className="stat-block">
        <h2>Mana cost</h2>
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
      </section>

      <section className="stat-block">
        <h2>Mana production</h2>
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
      </section>

      {hasCommander && (
        <section className="stat-block odds-block">
          <h2>Casting the commander</h2>
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
                          <td key={t} style={{ background: `rgba(179, 157, 219, ${Math.max(0.08, p / 100)})`, color: p > 55 ? "#15131a" : undefined }}>
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
                          <td key={t} style={{ background: `rgba(179, 157, 219, ${Math.max(0.08, p / 100)})`, color: p > 55 ? "#15131a" : undefined }}>
                            {p}
                          </td>
                        ))}
                      </tr>
                    ))}
                    {odds.allColours && (
                      <tr>
                        <th>All colours</th>
                        {odds.allColours.map((p, t) => (
                          <td key={t} style={{ background: `rgba(179, 157, 219, ${Math.max(0.08, p / 100)})`, color: p > 55 ? "#15131a" : undefined }}>
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
        </section>
      )}

      {toks.length > 0 && (
        <section className="stat-block">
          <h2>Tokens</h2>
          <ul className="token-grid">
            {toks.map((t) => (
              <li key={t.id} onMouseMove={hover.follow(scryfallImage(t.id))} onMouseLeave={hover.hide}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={scryfallImage(t.id, "small")} alt="" loading="lazy" />
                <span className="small">
                  <strong>{t.label}</strong>
                  <span className="muted"> · made by {t.makers.join(", ")}</span>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
