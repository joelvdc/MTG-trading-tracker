"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { TopBar } from "@/components/Nav";
import type { CollectionView } from "@/lib/collview";
import { computeStats, deckCardNames, displayName, type StatCard, type StatEntry } from "@/lib/colquery";
import { format } from "@/lib/money";

type Measure = "copies" | "value";

/** The collection's statistics, as the app's Stats screen shows them; each bar opens the matching cards. */
export default function StatsPage() {
  const [view, setView] = useState<CollectionView | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [binder, setBinder] = useState("");
  const [measure, setMeasure] = useState<Measure>("copies");

  useEffect(() => {
    setBinder(new URLSearchParams(window.location.search).get("binder") ?? "");
    (async () => {
      const [s, c] = await Promise.all([
        api<{ loggedIn: boolean; folder?: string }>("/api/session"),
        api<CollectionView & { noFolder?: boolean }>("/api/collection"),
      ]);
      if (!s.data.loggedIn || c.status === 401 || c.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || c.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (c.ok && Array.isArray(c.data.rows)) setView(c.data);
      else setError(c.data.error ?? "Could not load the collection.");
    })();
  }, []);

  const stats = useMemo(() => {
    if (!view) return null;
    const pick = binder && binder !== "~" && !view.binders.some((b) => b.uid === binder) ? "" : binder;
    const rows = pick === "" ? view.rows : view.rows.filter((r) => r.binder === (pick === "~" ? null : pick));
    const names = new Map<string | null, string>(view.binders.map((b) => [b.uid, b.name]));
    names.set(null, "Unsorted");
    return { pick, s: computeStats(rows, names, deckCardNames(view.decks)) };
  }, [view, binder]);

  if (!view || !stats) {
    return (
      <>
        <TopBar page="stats" folder={folder} />
        <main className="page wide">{error ? <div className="bar error">{error}</div> : <p className="muted center">Loading…</p>}</main>
      </>
    );
  }

  const { pick, s } = stats;
  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const amount = (e: StatEntry) => (measure === "copies" ? `${e.copies}` : money(e.value));
  const link = (filter: StatEntry["filter"], binderKey = pick) => {
    const p = new URLSearchParams();
    if (filter) p.set("filter", JSON.stringify(filter));
    p.set("binder", binderKey);
    return `/collection?${p.toString()}`;
  };

  function Bars({ title, entries, limit, sortBy }: { title: string; entries: StatEntry[]; limit?: number; sortBy?: boolean }) {
    let list = entries.filter((e) => e.copies > 0 || title === "Mana curve");
    if (sortBy) list = [...list].sort((a, b) => (measure === "copies" ? b.copies - a.copies : b.value - a.value));
    if (limit) list = list.slice(0, limit);
    if (list.length === 0) return null;
    const top = Math.max(...list.map((e) => (measure === "copies" ? e.copies : e.value)), 0.0001);
    return (
      <section className="stat-block">
        <h2>{title}</h2>
        <ul className="bars">
          {list.map((e) => {
            const n = measure === "copies" ? e.copies : e.value;
            const body = (
              <>
                <span className="bar-label">{e.label}</span>
                <span className="bar-track" aria-hidden="true">
                  <span className="bar-fill" style={{ width: n > 0 ? `max(6px, ${(n / top) * 100}%)` : 0 }} />
                </span>
                <span className="bar-num">{amount(e)}</span>
              </>
            );
            const tip = `${e.label}: ${e.copies} cards, ${money(e.value)}`;
            const target = title === "Binders" ? link(null, e.key === "" ? "~" : e.key) : e.filter ? link(e.filter) : null;
            return (
              <li key={e.key} title={tip}>
                {target ? (
                  <a className="bar-row" href={target}>
                    {body}
                  </a>
                ) : (
                  <div className="bar-row">{body}</div>
                )}
              </li>
            );
          })}
        </ul>
      </section>
    );
  }

  function Cards({ title, cards }: { title: string; cards: StatCard[] }) {
    if (cards.length === 0) return null;
    return (
      <section className="stat-block">
        <h2>{title}</h2>
        <ul className="stat-cards">
          {cards.map(({ row, note }) => (
            <li key={row.uid}>
              {row.card.imageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img className="mini-thumb" src={row.card.imageUrl} alt="" loading="lazy" />
              ) : (
                <span className="mini-thumb" />
              )}
              <span className="grow">
                {displayName(row.card)}
                <span className="muted small"> · {row.card.setCode.toUpperCase()}</span>
              </span>
              <span className="muted small">{note}</span>
              <span className="num">{row.unitEur != null ? money(row.unitEur) : "–"}</span>
            </li>
          ))}
        </ul>
      </section>
    );
  }

  const gain = s.paidWorth - s.paid;

  return (
    <>
      <TopBar page="stats" folder={folder} />
      <main className="page wide">
        <div className="controls">
          <a className="btn" href="/collection">
            ← Collection
          </a>
          <select aria-label="Binder" value={pick} onChange={(e) => setBinder(e.target.value)}>
            <option value="">Whole collection</option>
            <option value="~">Unsorted</option>
            {view.binders.map((b) => (
              <option key={b.uid} value={b.uid}>
                {b.name}
              </option>
            ))}
          </select>
          <div className="seg text" role="group" aria-label="Measure">
            <button className={measure === "copies" ? "on" : undefined} aria-pressed={measure === "copies"} onClick={() => setMeasure("copies")}>
              Cards
            </button>
            <button className={measure === "value" ? "on" : undefined} aria-pressed={measure === "value"} onClick={() => setMeasure("value")}>
              Value
            </button>
          </div>
        </div>

        <div className="stat-tiles">
          <div className="stat-tile">
            <div className="muted small">Cards</div>
            <div className="big">{s.copies}</div>
            <div className="muted small">
              {s.uniqueCards} different · {s.printings} printings · {s.foilCopies} foil
            </div>
          </div>
          <div className="stat-tile">
            <div className="muted small">Value ({view.priceTypeLabel}, {view.sourceLabel})</div>
            <div className="big">{money(s.value)}</div>
            <div className="muted small">{s.unpriced > 0 ? `${s.unpriced} cards without a price` : "Every card priced"}</div>
          </div>
          {s.paidCopies > 0 && (
            <div className="stat-tile">
              <div className="muted small">Paid and worth ({s.paidCopies} cards with a purchase price)</div>
              <div className="big">
                {money(s.paid)} → {money(s.paidWorth)}
              </div>
              <div className={gain >= 0 ? "up small" : "down small"}>
                {gain >= 0 ? "▲" : "▼"} {money(Math.abs(gain))}
                {s.paid > 0 ? ` (${((gain / s.paid) * 100).toFixed(1)}%)` : ""}
              </div>
            </div>
          )}
        </div>
        {s.withoutDetails > 0 && (
          <p className="muted small">
            {s.withoutDetails} cards have no colours or types yet; open the collection once to look them up.
          </p>
        )}

        <div className="stat-grid">
          <Bars title="Colors" entries={s.colors} />
          <Bars title="Color identity" entries={s.identities} sortBy />
          <Bars title="Rarity" entries={s.rarities} />
          <Bars title="Card types" entries={s.types} />
          <Bars title="Mana curve" entries={s.curve} />
          <Bars title="Finish" entries={s.finishes} />
          <Bars title="Condition" entries={s.conditions} />
          <Bars title="Language" entries={s.languages} sortBy />
          <Bars title="Decks" entries={[s.inDecks, s.notInDecks]} />
          {pick === "" && <Bars title="Binders" entries={s.binders} sortBy />}
          <Bars title="Sets (top 15)" entries={s.sets} sortBy limit={15} />
          <Bars title="Release year" entries={s.years} />
        </div>
        <div className="stat-grid">
          <Cards title="Most valuable" cards={s.mostValuable} />
          <Cards title="Rising (trend vs 30-day average)" cards={s.risers} />
          <Cards title="Falling (trend vs 30-day average)" cards={s.fallers} />
          <Cards title="Game Changers" cards={s.gameChangers} />
          <Cards title="Most played on EDHREC" cards={s.edhrecTop} />
          {s.oldest && <Cards title="Oldest card" cards={[s.oldest]} />}
        </div>
      </main>
    </>
  );
}
