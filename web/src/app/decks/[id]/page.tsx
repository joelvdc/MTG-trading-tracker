"use client";

import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { DeckStatsPanel } from "@/components/DeckStatsPanel";
import { Dialog } from "@/components/Dialog";
import { useHoverCard, useHoverSetting } from "@/components/HoverCard";
import { ColourPips, ManaCost } from "@/components/Mana";
import { TopBar } from "@/components/Nav";
import { BracketCard, formatPower, PowerCard } from "@/components/RuleZeroCards";
import type { StatsCard } from "@/lib/deckstats";
import { bracketName, caseless, POWER_SOURCES, sections, TAG_LABELS, type DeckRow, type DeckView, type GroupBy } from "@/lib/deckshared";
import { format } from "@/lib/money";

type Answer = DeckView & { stats: StatsCard[]; tokenInfo: Record<string, { oi: string | null; pt: string | null }>; detailsError: string | null };

const BASICS = new Set(["Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes"].flatMap((b) => [b, `Snow-Covered ${b}`]).map((n) => n.toLowerCase()));

/** One deck (the app's deck page): its cards by category or type, what you own, the stats and the rule-zero cards. */
export default function DeckPage() {
  const params = useParams<{ id: string }>();
  const [view, setView] = useState<Answer | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [groupBy, setGroupBy] = useState<GroupBy>("CATEGORY");
  const [onlyBracket, setOnlyBracket] = useState(false);
  const [maybe, setMaybe] = useState(false);
  const [showStats, setShowStats] = useState(true);
  const [missingOpen, setMissingOpen] = useState(false);
  const [rz, setRz] = useState<"BRACKET" | "POWER" | null>(null);
  const [hoverOn, setHoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);

  useEffect(() => {
    (async () => {
      const [s, d] = await Promise.all([api<{ loggedIn: boolean; folder?: string }>("/api/session"), api<Answer & { noFolder?: boolean }>(`/api/decks/${encodeURIComponent(params.id)}`)]);
      if (!s.data.loggedIn || d.status === 401 || d.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || d.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (d.ok && Array.isArray(d.data.rows)) setView(d.data);
      else setError(d.data.error ?? "Could not load the deck.");
    })();
  }, [params.id]);

  const shaped = useMemo(() => {
    if (!view) return null;
    const main = view.rows.filter((r) => !r.maybeboard);
    const shown = onlyBracket ? main.filter((r) => r.tags.length > 0) : main;
    const all = sections(shown, groupBy);
    const hidden = new Set(view.hidden);
    const visible = groupBy === "CATEGORY" ? all.filter((s) => !hidden.has(s.title.toLowerCase())) : all;
    const hiddenRows = all
      .filter((s) => !visible.includes(s))
      .flatMap((s) => s.rows)
      .sort((a, b) => caseless(a.category, b.category) || (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
    const maybeSections = sections(
      view.rows.filter((r) => r.maybeboard),
      "CATEGORY",
    );
    const value = main.reduce((s, r) => s + (r.unitEur ?? 0) * r.quantity, 0);
    const missing = main.reduce((s, r) => s + r.missing, 0);
    return { main, visible, hiddenRows, maybeSections, value, missing, tagged: main.filter((r) => r.tags.length > 0).length };
  }, [view, groupBy, onlyBracket]);

  if (!view || !shaped) {
    return (
      <>
        <TopBar page="decks" folder={folder} />
        <main className="page wide">
          <a href="/decks" className="small">
            ← Decks
          </a>
          {error ? <div className="bar error">{error}</div> : <p className="muted center">Loading… (the first visit looks the cards up on Scryfall)</p>}
        </main>
      </>
    );
  }

  const d = view.deck;
  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const costOf = new Map(view.stats.map((s) => [s.scryfallId, s.manaCost]));

  const row = (r: DeckRow) => {
    const basic = BASICS.has(r.name.toLowerCase());
    return (
      <li key={r.lineUid} className="deck-row" onMouseMove={hover.follow(r.card.imageUrl)} onMouseLeave={hover.hide}>
        <span className="deck-qty num">{r.quantity}</span>
        <span className="grow deck-card-name">
          {r.name}
          {r.finish !== "NONFOIL" && <span className="foil-text" title={r.finish === "ETCHED" ? "Etched" : "Foil"}> ✦</span>}
          {r.addedInApp && (
            <span className="ok-text" title="Added in the app">
              {" "}
              +
            </span>
          )}
          {r.tags.map((t) => (
            <span key={t} className={TAG_LABELS[t].strict ? "tag rz-strict" : "tag rz-soft"}>
              {TAG_LABELS[t].label}
            </span>
          ))}
          <span className="muted small">
            {" "}
            · {r.card.setCode.toUpperCase()}
            {r.original ? " (yours)" : ""}
          </span>
        </span>
        <span className="deck-cost">
          <ManaCost cost={costOf.get(r.original?.scryfallId ?? r.card.scryfallId) ?? null} size={14} />
        </span>
        <span className="deck-own small">
          {basic || r.maybeboard ? null : r.missing > 0 ? (
            <span className="warn-text" title={`You own ${r.owned} in any printing`}>
              {r.missing === r.quantity ? "missing" : `${r.missing} missing`}
            </span>
          ) : (
            <span className="ok-text">owned</span>
          )}
        </span>
        <span className="num nowrap deck-price">{r.unitEur != null ? money(r.unitEur * r.quantity) : "–"}</span>
      </li>
    );
  };

  const block = (title: string, rows: DeckRow[], key: string) => (
    <section key={key} className="deck-section">
      <h3 className="mini-head">
        {title} <span className="muted small">· {rows.reduce((s, r) => s + r.quantity, 0)} · {money(rows.reduce((s, r) => s + (r.unitEur ?? 0) * r.quantity, 0))}</span>
      </h3>
      <ul className="plain-list deck-list">{rows.map(row)}</ul>
    </section>
  );

  const source = view.powerSource;
  return (
    <>
      <TopBar page="decks" folder={folder} />
      <main className="page wide">
        <a href="/decks" className="small">
          ← Decks
        </a>
        <header className="deck-banner" style={view.cover ? { backgroundImage: `url("${view.cover}")` } : undefined}>
          <div className="deck-banner-in">
            <h1>{d.name}</h1>
            <div>
              {d.commanders || "No commander"} <ColourPips identity={d.colorIdentity} size={16} />
            </div>
            <div className="small deck-banner-sub">
              {[view.local ? "Local deck" : `by ${d.owner}`, d.origin, d.folderName, `${d.cardCount} cards`, money(shaped.value)].filter(Boolean).join(" · ")}
            </div>
          </div>
        </header>
        {view.readOnly && <div className="bar warn">Read only: the app on a phone is newer than this website.</div>}
        {notice && (
          <div className="bar ok" role="status">
            {notice}
            <button className="link" onClick={() => setNotice(null)}>
              Dismiss
            </button>
          </div>
        )}
        {error && <div className="bar error">{error}</div>}
        {view.detailsError && <div className="bar warn">Card details: {view.detailsError} The stats use what is known so far.</div>}

        <section className="card deck-scores">
          <div className="score">
            <span className="muted small">Bracket</span>
            <strong>{d.bracketRealistic ?? "–"}</strong>
            <span className="small">{bracketName(d.bracketRealistic) ?? "not scored"}</span>
            {d.bracketBaseline != null && <span className="muted small">baseline {d.bracketBaseline}</span>}
          </div>
          <div className="score">
            <span className="muted small">Power ({POWER_SOURCES[source].label})</span>
            <strong>{view.power != null ? formatPower(view.power, source) : "–"}</strong>
            {d.archetype && <span className="small">{d.archetype}</span>}
          </div>
          <div className="score">
            <span className="muted small">Saltiness</span>
            <strong>{d.saltPercent != null ? `${Math.round(d.saltPercent)}%` : "–"}</strong>
          </div>
          <div className="score">
            <span className="muted small">Missing</span>
            <strong className={shaped.missing > 0 ? "warn-text" : "ok-text"}>{shaped.missing}</strong>
            <span className="small">copies</span>
          </div>
          <div className="deck-actions">
            <button className="btn small-btn" disabled={!view.salt} onClick={() => setRz("BRACKET")} title={view.salt ? "Show and print the bracket card" : "Commander Salt hasn't scored this deck yet"}>
              Bracket card
            </button>
            <button className="btn small-btn" disabled={!view.salt} onClick={() => setRz("POWER")}>
              Power card
            </button>
            <button className="btn small-btn" onClick={() => setMissingOpen(true)}>
              Cards I&apos;m missing
            </button>
            {!view.local && (
              <a className="btn small-btn" href={`https://archidekt.com/decks/${d.archidektId}`} target="_blank" rel="noreferrer">
                Archidekt
              </a>
            )}
            {d.saltId && (
              <a className="btn small-btn" href={`https://commandersalt.com/details/deck/${d.saltId}`} target="_blank" rel="noreferrer">
                Commander Salt
              </a>
            )}
          </div>
          {d.scoredAt && <p className="muted small">Scored by Commander Salt, {new Date(d.scoredAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}</p>}
          {!d.scoredAt && d.scoreError && <p className="warn-text small">Not scored: {d.scoreError}</p>}
        </section>

        <details className="card" open={showStats} onToggle={(e) => setShowStats((e.target as HTMLDetailsElement).open)}>
          <summary>
            <strong>Stats</strong>
          </summary>
          {showStats && <DeckStatsPanel cards={view.stats} identity={d.colorIdentity} tokenInfo={view.tokenInfo} hover={hover} />}
        </details>

        <div className="controls">
          <div className="seg text" role="group" aria-label="Group by">
            <button className={groupBy === "CATEGORY" ? "on" : ""} onClick={() => setGroupBy("CATEGORY")}>
              Category
            </button>
            <button className={groupBy === "TYPE" ? "on" : ""} onClick={() => setGroupBy("TYPE")}>
              Card type
            </button>
          </div>
          {shaped.tagged > 0 && (
            <button className={onlyBracket ? "chip on" : "chip"} onClick={() => setOnlyBracket((x) => !x)}>
              Only bracket cards · {shaped.tagged}
            </button>
          )}
          {shaped.maybeSections.length > 0 && (
            <button className={maybe ? "chip on" : "chip"} onClick={() => setMaybe((x) => !x)}>
              Show maybeboard
            </button>
          )}
          <span className="spacer" />
          <label className="check small">
            <input type="checkbox" checked={hoverOn} onChange={(e) => setHoverOn(e.target.checked)} />
            <span>Card on hover</span>
          </label>
        </div>
        {view.myPrintings && view.rows.some((r) => r.original) && (
          <p className="muted small">Shown in the printings you own (&quot;yours&quot;), as in the app; the deck itself keeps its printings.</p>
        )}

        <div className="deck-columns">
          {shaped.visible.map((s) => block(s.title, s.rows, s.title))}
          {shaped.hiddenRows.length > 0 && block("Hidden categories", shaped.hiddenRows, "\u0000hidden")}
        </div>
        {maybe && (
          <>
            <h2 className="section-head">Maybeboard</h2>
            <div className="deck-columns">{shaped.maybeSections.map((s) => block(s.title, s.rows, `m:${s.title}`))}</div>
          </>
        )}
        <p className="muted small">
          Prices: {view.priceTypeLabel}, at the printings shown. Missing: copies the collection lacks in any printing (copies you own count once, however many
          decks use them).
        </p>
      </main>
      {hover.picture}
      {missingOpen && <MissingDialog view={view} money={money} onClose={() => setMissingOpen(false)} onDone={(t) => (setMissingOpen(false), setNotice(t))} />}
      {rz && view.salt && (
        <RuleZeroDialog view={view} start={rz} onClose={() => setRz(null)} />
      )}
    </>
  );
}

function MissingDialog({ view, money, onClose, onDone }: { view: Answer; money: (eur: number) => string; onClose: () => void; onDone: (text: string) => void }) {
  const [skipBasics, setSkipBasics] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  // The app's DeckToCollection.plan: copies you own in any printing count once, in the deck's order.
  const list = useMemo(() => {
    const left = new Map<string, number>();
    for (const r of view.rows) if (!r.maybeboard) left.set(r.name.toLowerCase(), r.owned);
    const out: [DeckRow, number][] = [];
    for (const r of view.rows) {
      if (r.maybeboard || (skipBasics && BASICS.has(r.name.toLowerCase()))) continue;
      const k = r.name.toLowerCase();
      const have = left.get(k) ?? 0;
      const used = Math.min(have, r.quantity);
      left.set(k, have - used);
      if (r.quantity - used > 0) out.push([r, r.quantity - used]);
    }
    return out.sort((a, b) => caseless(a[0].name, b[0].name));
  }, [view, skipBasics]);
  const copies = list.reduce((s, [, n]) => s + n, 0);
  const cost = list.reduce((s, [r, n]) => s + (r.unitEur ?? 0) * n, 0);
  const text = list.map(([r, n]) => `${n} ${r.name}`).join("\n");

  async function toWishlist() {
    setBusy(true);
    setError(null);
    const r = await api<{ added?: number }>(`/api/decks/${view.id}`, { action: "missing-to-wishlist", skipBasics });
    setBusy(false);
    if (r.ok && r.data.added !== undefined) onDone(`Added ${r.data.added} card${r.data.added === 1 ? "" : "s"} to the wishlist.`);
    else setError(r.data.error ?? "That did not work.");
  }

  return (
    <Dialog title="Cards I'm missing" onClose={() => !busy && onClose()}>
      <div className="stack">
        <p className="small">
          {copies === 0
            ? "You own every card of this deck (any printing counts)."
            : `${copies} card${copies === 1 ? "" : "s"} missing · about ${money(cost)} (${view.priceTypeLabel}, at the decklist's printings)`}
        </p>
        <label className="check small">
          <input type="checkbox" checked={skipBasics} onChange={(e) => setSkipBasics(e.target.checked)} />
          <span>Skip basic lands</span>
        </label>
        {list.length > 0 && (
          <ul className="plain-list">
            {list.map(([r, n]) => (
              <li key={r.lineUid} className="small">
                <span className="grow">
                  {n}× {r.name}
                  {r.owned > 0 && <span className="muted"> · you own {r.owned} of {r.quantity}</span>}
                </span>
                <span className="num">{r.unitEur != null ? money(r.unitEur * n) : "–"}</span>
              </li>
            ))}
          </ul>
        )}
        {copies > 0 && <p className="muted small">The copied list (&quot;1 Card name&quot; per line) can be pasted into a Cardmarket wants list or Archidekt.</p>}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <button
            className="btn"
            disabled={copies === 0}
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(text);
                setCopied(true);
              } catch {
                setError("The browser didn't allow copying.");
              }
            }}
          >
            {copied ? "Copied" : "Copy list"}
          </button>
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={busy}>
            Close
          </button>
          <button className="btn primary" disabled={busy || copies === 0 || view.readOnly} onClick={toWishlist}>
            Add to wishlist
          </button>
        </div>
      </div>
    </Dialog>
  );
}

function RuleZeroDialog({ view, start, onClose }: { view: Answer; start: "BRACKET" | "POWER"; onClose: () => void }) {
  const [inkSaver, setInkSaver] = useState(false);
  const [which, setWhich] = useState<"BOTH" | "BRACKET" | "POWER">(start);
  const salt = view.salt!;
  const deck = view.deck;
  const sv = view.scrollVault as Parameters<typeof PowerCard>[0]["scrollVault"];
  return (
    <Dialog title="Rule-zero cards" onClose={onClose}>
      <div className="stack">
        <div className="inline">
          <div className="seg text" role="group" aria-label="Cards">
            {(
              [
                ["BRACKET", "Bracket"],
                ["POWER", "Power level"],
                ["BOTH", "Both"],
              ] as const
            ).map(([k, l]) => (
              <button key={k} className={which === k ? "on" : ""} onClick={() => setWhich(k)}>
                {l}
              </button>
            ))}
          </div>
          <label className="check small">
            <input type="checkbox" checked={inkSaver} onChange={(e) => setInkSaver(e.target.checked)} />
            <span>Ink saver (light headers without the commander art)</span>
          </label>
        </div>
        <div className="rz-print">
          {which !== "POWER" && <BracketCard deck={deck} cover={view.cover} data={salt} inkSaver={inkSaver} />}
          {which !== "BRACKET" && (
            <PowerCard deck={deck} cover={view.cover} data={salt} source={view.powerSource} power={view.power} error={view.powerError} scrollVault={sv} inkSaver={inkSaver} />
          )}
        </div>
        <div className="dialog-actions">
          {deck.saltId && (
            <a className="btn" href={`https://commandersalt.com/details/deck/${deck.saltId}`} target="_blank" rel="noreferrer">
              Commander Salt&apos;s originals
            </a>
          )}
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Close
          </button>
          <button className="btn primary" onClick={() => window.print()}>
            Print
          </button>
        </div>
      </div>
    </Dialog>
  );
}
