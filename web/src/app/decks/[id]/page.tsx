"use client";

import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { AddCardDialog, CategoriesDialog, ConfirmDialog, deckAction, LineDialog, NameDialog } from "@/components/DeckEditDialogs";
import { DeckStatsPanel } from "@/components/DeckStatsPanel";
import { Dialog } from "@/components/Dialog";
import { useHoverCard, useHoverSetting } from "@/components/HoverCard";
import { ColourPips, ManaCost } from "@/components/Mana";
import { TopBar } from "@/components/Nav";
import { RecommendationsPanel } from "@/components/RecommendationsPanel";
import { BracketCard, formatPower, PowerCard, SpeedSection } from "@/components/RuleZeroCards";
import { useWinOdds, type WinState } from "@/components/useWinOdds";
import type { StatsCard } from "@/lib/deckstats";
import { bracketName, caseless, POWER_SOURCES, sections, TAG_LABELS, type DeckRow, type DeckView, type GroupBy } from "@/lib/deckshared";
import { format } from "@/lib/money";

type Answer = DeckView & { stats: StatsCard[]; tokenInfo: Record<string, { oi: string | null; pt: string | null }>; detailsError: string | null; saltCard: string | null };

const NO_CARDS: StatsCard[] = [];

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
  const [showRecs, setShowRecs] = useState(false);
  const [missingOpen, setMissingOpen] = useState(false);
  const [rz, setRz] = useState<"BRACKET" | "POWER" | null>(null);
  const [line, setLine] = useState<string | null>(null);
  const [catsOpen, setCatsOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [nameDialog, setNameDialog] = useState<"RENAME" | "COPY" | null>(null);
  const [confirm, setConfirm] = useState<"DELETE" | "DETACH" | null>(null);
  const [hoverOn, setHoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);
  const win = useWinOdds(view?.id ?? 0, view?.stats ?? NO_CARDS, view?.deck.colorIdentity ?? "", view?.saltCard ?? null, view !== null);

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

  /** Loads the deck again after an edit (keeping the page as it is meanwhile). */
  async function reload(text: string | null) {
    const d = await api<Answer>(`/api/decks/${encodeURIComponent(params.id)}`);
    if (d.ok && Array.isArray(d.data.rows)) {
      setView(d.data);
      setError(null);
    } else setError(d.data.error ?? "Could not load the deck.");
    if (text) setNotice(text);
  }

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
      <li
        key={r.lineUid}
        className="deck-row clickable"
        role="button"
        tabIndex={0}
        title={view.local && !view.readOnly ? "Change this card" : "This card"}
        onClick={() => (hover.hide(), setLine(r.lineUid))}
        onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && (e.preventDefault(), setLine(r.lineUid))}
        onMouseMove={hover.follow(r.card.imageUrl)}
        onMouseLeave={hover.hide}
      >
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
  // The app's own power level isn't synced: the website works it out from the win turn, as the phone does.
  const power = view.power ?? (source === "APP" && win.status === "done" && win.win ? win.win.power.power : null);
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
            <strong>{power != null ? formatPower(power, source) : source === "APP" && win.status === "working" ? "…" : "–"}</strong>
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
            {!view.readOnly && (
              <details className="menu">
                <summary className="btn small-btn">Edit ▾</summary>
                <div className="menu-items" onClick={(e) => ((e.currentTarget.parentElement as HTMLDetailsElement).open = false)}>
                  {view.local && <button onClick={() => setAddOpen(true)}>Add a card</button>}
                  <button onClick={() => setCatsOpen(true)}>Categories</button>
                  {view.local && <button onClick={() => setNameDialog("RENAME")}>Rename</button>}
                  <button onClick={() => setNameDialog("COPY")}>Copy as a local deck</button>
                  {!view.local && <button onClick={() => setConfirm("DETACH")}>Make it a local deck</button>}
                  {view.local && (
                    <button className="danger-text" onClick={() => setConfirm("DELETE")}>
                      Delete deck
                    </button>
                  )}
                </div>
              </details>
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
          {showStats && <DeckStatsPanel cards={view.stats} identity={d.colorIdentity} tokenInfo={view.tokenInfo} hover={hover} win={win} />}
        </details>

        <details className="card" open={showRecs} onToggle={(e) => setShowRecs((e.target as HTMLDetailsElement).open)}>
          <summary>
            <strong>Recommendations</strong>
          </summary>
          {showRecs && <RecommendationsPanel view={view} hover={hover} onAdded={(t) => reload(t)} />}
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
        <RuleZeroDialog view={view} start={rz} onClose={() => setRz(null)} win={win} power={power} />
      )}
      {line && view.rows.find((r) => r.lineUid === line) && (
        <LineDialog
          key={line + view.rows.find((r) => r.lineUid === line)!.quantity}
          view={view}
          row={view.rows.find((r) => r.lineUid === line)!}
          onClose={() => setLine(null)}
          onChanged={async (t) => {
            await reload(t);
            if (t) setLine(null);
          }}
        />
      )}
      {catsOpen && <CategoriesDialog view={view} onClose={() => setCatsOpen(false)} onChanged={(t) => reload(t)} />}
      {addOpen && <AddCardDialog view={view} onClose={() => setAddOpen(false)} onChanged={(t) => reload(t)} />}
      {nameDialog && (
        <NameDialog
          title={nameDialog === "RENAME" ? "Rename the deck" : "Copy as a local deck"}
          start={nameDialog === "RENAME" ? d.name : `Copy of ${d.name}`}
          button={nameDialog === "RENAME" ? "Rename" : "Copy"}
          note={nameDialog === "COPY" ? "A local deck with the same cards, categories and maybeboard (not the scores). It syncs to the phones." : undefined}
          onClose={() => setNameDialog(null)}
          onSave={async (name) => {
            const r = await deckAction(view.id, { action: nameDialog === "RENAME" ? "rename" : "copy", name });
            if (r.error) return r.error;
            if (nameDialog === "COPY" && r.id !== undefined) window.location.assign(`/decks/${r.id}`);
            else {
              setNameDialog(null);
              await reload(null);
            }
            return null;
          }}
        />
      )}
      {confirm === "DELETE" && (
        <ConfirmDialog
          title="Delete the deck?"
          text={`${d.name} is deleted here and, at their next sync, on the phones. This can't be undone.`}
          button="Delete"
          danger
          onClose={() => setConfirm(null)}
          onYes={async () => {
            const r = await deckAction(view.id, { action: "delete" });
            if (r.error) return r.error;
            window.location.assign("/decks");
            return null;
          }}
        />
      )}
      {confirm === "DETACH" && (
        <ConfirmDialog
          title="Make it a local deck?"
          text={`${d.name} stops following Archidekt: it can then be changed here and in the app, but changes made on Archidekt no longer come in and nothing is sent back there. Its scores stay. The deck on Archidekt itself isn't touched.`}
          button="Make it local"
          onClose={() => setConfirm(null)}
          onYes={async () => {
            const r = await deckAction(view.id, { action: "detach" });
            if (r.error) return r.error;
            window.location.assign(`/decks/${r.id}`);
            return null;
          }}
        />
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

function RuleZeroDialog({ view, start, onClose, win, power }: { view: Answer; start: "BRACKET" | "POWER"; onClose: () => void; win: WinState; power: number | null }) {
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
          {which !== "POWER" && <BracketCard deck={deck} cover={view.cover} data={salt} inkSaver={inkSaver} speed={win.status === "done" && win.win ? <SpeedSection win={win.win} /> : undefined} />}
          {which !== "BRACKET" && (
            <PowerCard deck={deck} cover={view.cover} data={salt} source={view.powerSource} power={power} error={view.powerError} scrollVault={sv} inkSaver={inkSaver} />
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
