"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useHoverCard } from "@/components/HoverCard";
import { Logo } from "@/components/Logo";
import { duration, ordinal, outReasonLabel, winTypeLabel } from "@/lib/gameshared";
import { format } from "@/lib/money";
import type { SharedDeck, SharedPile, SharedPrices, SharedView } from "@/lib/shares";

type Answer = { state: "ok"; view: SharedView } | { state: "off" | "gone" | "unknown" } | { state: "error"; error?: string };

const FINISH: Record<string, string> = { NONFOIL: "", FOIL: "Foil", ETCHED: "Etched" };
const STATE_TEXT: Record<string, string> = {
  off: "This link has been switched off by the person who shared it.",
  gone: "What this link showed no longer exists.",
  unknown: "This link doesn't exist. Check that it was copied whole.",
};

/** A shared deck, game, binder or collection (/s/<code>): read only, for anyone with the link. */
export default function SharedPage() {
  const { code } = useParams<{ code: string }>();
  const [answer, setAnswer] = useState<Answer | null>(null);

  useEffect(() => {
    fetch(`/api/share/${encodeURIComponent(code)}`, { cache: "no-store" })
      .then((r) => r.json() as Promise<Answer>)
      .then(setAnswer, () => setAnswer({ state: "error" }));
  }, [code]);

  useEffect(() => {
    if (answer?.state === "ok") document.title = `${answer.view.title} · shared from MTG Trader`;
  }, [answer]);

  return (
    <>
      <header className="topbar">
        <div className="topbar-in wide">
          <div className="brand">
            <div className="brand-name">
              <Logo size={28} />
              <strong>
                MTG <span className="brand-accent">Trader</span>
              </strong>
            </div>
            {answer?.state === "ok" && <div className="muted small">Shared by {answer.view.owner}</div>}
          </div>
        </div>
      </header>
      <main className="page wide">
        {!answer ? (
          <p className="muted center">Loading…</p>
        ) : answer.state === "ok" ? (
          <Shared view={answer.view} />
        ) : (
          <div className="bar">{answer.state === "error" ? "The page couldn't be loaded. Try again in a moment." : STATE_TEXT[answer.state]}</div>
        )}
      </main>
    </>
  );
}

function Shared({ view }: { view: SharedView }) {
  if (view.kind === "game") return <SharedGame view={view} />;
  if (view.kind === "deck") return <SharedDeckView deck={view.deck} prices={view.prices} />;
  return <SharedPiles title={view.kind === "collection" ? "Collection" : view.title} piles={view.piles} binders={view.binders} prices={view.prices} />;
}

const moneyOf = (p: SharedPrices) => (eur: number) => format(eur * p.perEuro, p.currency);

function SharedDeckView({ deck, prices }: { deck: SharedDeck; prices: SharedPrices }) {
  const money = moneyOf(prices);
  const hover = useHoverCard(true);
  const total = deck.sections.flatMap((s) => s.lines).reduce((n, l) => n + (l.unitEur ?? 0) * l.quantity, 0);
  const legal = deck.legality;
  return (
    <div className="stack">
      <div className="card">
        <h1 className="share-title">{deck.name}</h1>
        <p className="muted">{deck.commanders}</p>
        <p className="summary">
          {[`${deck.cards} cards`, deck.bracket, deck.power, `worth ${money(total)}`].filter(Boolean).join(" · ")}
        </p>
        <p className="muted small">{prices.line}</p>
        {legal && (
          <div className="share-legal">
            {legal.problems.length === 0 ? (
              <p>
                <strong>Commander legal ✓</strong>
              </p>
            ) : (
              <>
                <p>
                  <strong>Legality</strong>
                </p>
                {legal.problems.map((p) => (
                  <p key={p.kind + p.text} className="warn-text small">
                    {p.text}
                  </p>
                ))}
              </>
            )}
            {legal.unchecked.length > 0 && <p className="muted small">Couldn&apos;t check {legal.unchecked.length} card(s).</p>}
          </div>
        )}
      </div>
      <div className="table-wrap">
        <table className="wish-table">
          <thead>
            <tr>
              <th className="num">Copies</th>
              <th>Card</th>
              <th>Set</th>
              <th className="num">Price</th>
            </tr>
          </thead>
          {deck.sections.map((s) => (
            <tbody key={s.title}>
              <tr className="share-section">
                <th colSpan={4}>
                  {s.title} <span className="muted small">({s.lines.reduce((n, l) => n + l.quantity, 0)})</span>
                </th>
              </tr>
              {s.lines.map((l, i) => (
                <tr key={`${l.name}-${i}`} onMouseMove={hover.follow(l.image) as never} onMouseLeave={hover.hide}>
                  <td className="num">{l.quantity}</td>
                  <td>
                    {l.name}
                    {l.gameChanger && <span className="tag small"> Game changer</span>}
                    {FINISH[l.finish] && <span className="muted small"> · {FINISH[l.finish]}</span>}
                  </td>
                  <td className="muted">
                    {l.set.toUpperCase()} {l.number}
                  </td>
                  <td className="num">{l.unitEur != null ? money(l.unitEur) : "–"}</td>
                </tr>
              ))}
            </tbody>
          ))}
        </table>
      </div>
      {hover.picture}
    </div>
  );
}

type Sort = "VALUE" | "NAME" | "SET";
const PAGE = 100;

function SharedPiles({ title, piles, binders, prices }: { title: string; piles: SharedPile[]; binders: string[]; prices: SharedPrices }) {
  const money = moneyOf(prices);
  const hover = useHoverCard(true);
  const [search, setSearch] = useState("");
  const [binder, setBinder] = useState("");
  const [sort, setSort] = useState<Sort>("VALUE");
  const [shown, setShown] = useState(PAGE);
  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    const list = piles.filter(
      (p) => (!binder || p.binder === binder) && (!q || p.name.toLowerCase().includes(q) || p.set.toLowerCase() === q || p.setName.toLowerCase().includes(q)),
    );
    const byName = (a: SharedPile, b: SharedPile) => a.name.localeCompare(b.name);
    if (sort === "NAME") list.sort(byName);
    else if (sort === "SET") list.sort((a, b) => a.setName.localeCompare(b.setName) || a.number.localeCompare(b.number, undefined, { numeric: true }) || byName(a, b));
    else list.sort((a, b) => (b.unitEur ?? -1) - (a.unitEur ?? -1) || byName(a, b));
    return list;
  }, [piles, search, binder, sort]);
  const copies = rows.reduce((n, p) => n + p.quantity, 0);
  const value = rows.reduce((n, p) => n + (p.unitEur ?? 0) * p.quantity, 0);
  return (
    <div className="stack">
      <div>
        <h1 className="share-title">{title}</h1>
        <p className="summary">
          {copies} cards · {money(value)}
        </p>
        <p className="muted small">{prices.line}</p>
      </div>
      <div className="controls">
        <input
          type="search"
          aria-label="Search the cards"
          placeholder="Search name or set"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setShown(PAGE);
          }}
        />
        {binders.length > 1 && (
          <select aria-label="Binder" value={binder} onChange={(e) => (setBinder(e.target.value), setShown(PAGE))}>
            <option value="">All binders</option>
            {binders.map((b) => (
              <option key={b} value={b}>
                {b}
              </option>
            ))}
          </select>
        )}
        <select aria-label="Sort" value={sort} onChange={(e) => setSort(e.target.value as Sort)}>
          <option value="VALUE">Most valuable first</option>
          <option value="NAME">By name</option>
          <option value="SET">By set</option>
        </select>
      </div>
      <div className="table-wrap">
        <table className="wish-table">
          <thead>
            <tr>
              <th className="num">Copies</th>
              <th>Card</th>
              <th>Set</th>
              <th>Condition</th>
              {binders.length > 1 && <th>Binder</th>}
              <th className="num">Price</th>
            </tr>
          </thead>
          <tbody>
            {rows.slice(0, shown).map((p, i) => (
              <tr key={i} onMouseMove={hover.follow(p.image) as never} onMouseLeave={hover.hide}>
                <td className="num">{p.quantity}</td>
                <td>
                  {p.name}
                  <span className="muted small">
                    {[FINISH[p.finish], p.language !== "English" && p.language !== "EN" ? p.language : "", p.signed ? "Signed" : "", p.altered ? "Altered" : ""]
                      .filter(Boolean)
                      .map((x) => ` · ${x}`)
                      .join("")}
                  </span>
                </td>
                <td className="muted">
                  {p.set.toUpperCase()} {p.number}
                </td>
                <td>{p.condition}</td>
                {binders.length > 1 && <td className="muted">{p.binder}</td>}
                <td className="num">
                  {p.unitEur != null ? `${p.approx ? "≈" : ""}${money(p.unitEur)}` : "–"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rows.length > shown && (
        <button className="btn" onClick={() => setShown((n) => n + PAGE * 5)}>
          Show more ({rows.length - shown} left)
        </button>
      )}
      {hover.picture}
    </div>
  );
}

function SharedGame({ view }: { view: Extract<SharedView, { kind: "game" }> }) {
  const g = view.game;
  const seats = [...g.seats].sort((a, b) => (a.place ?? 99) - (b.place ?? 99) || a.seat - b.seat);
  const facts = [
    duration(g.durationMs),
    g.rounds != null ? `${g.rounds} turns` : null,
    g.draw ? "A draw" : winTypeLabel(g.winType) ? `Won by ${winTypeLabel(g.winType)!.toLowerCase()}` : null,
    `Starting life ${g.startingLife}`,
  ].filter(Boolean);
  return (
    <div className="stack">
      <div className="card">
        <h1 className="share-title">{view.title}</h1>
        <p className="summary">{facts.join(" · ")}</p>
      </div>
      <div className="table-wrap">
        <table className="wish-table">
          <thead>
            <tr>
              <th>Place</th>
              <th>Player</th>
              <th>Deck</th>
              <th>Bracket · power</th>
              <th>Out</th>
            </tr>
          </thead>
          <tbody>
            {seats.map((s) => (
              <tr key={s.seat}>
                <td>{s.place === 1 && !g.draw ? "🏆 1st" : s.place != null ? ordinal(s.place) : "–"}</td>
                <td>
                  {s.name}
                  {s.started && <span className="muted small"> · started</span>}
                </td>
                <td>{[s.deckName, s.commander && s.commander !== s.deckName ? s.commander : null].filter(Boolean).join(" · ") || "–"}</td>
                <td className="muted">{[s.bracket != null ? `Bracket ${s.bracket}` : null, s.power != null ? `power ${s.power.toFixed(1)}` : null].filter(Boolean).join(" · ") || "–"}</td>
                <td className="muted">{[s.outRound != null ? `turn ${s.outRound}` : null, outReasonLabel(s.outReason)].filter(Boolean).join(", ") || "–"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
