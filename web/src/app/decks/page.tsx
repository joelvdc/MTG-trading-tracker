"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { ConfirmDialog, deckAction } from "@/components/DeckEditDialogs";
import { ColourPips } from "@/components/Mana";
import { TopBar } from "@/components/Nav";
import { NewDeckDialog } from "@/components/NewDeckDialog";
import { formatPower } from "@/components/RuleZeroCards";
import { bracketName, caseless, GROUP_TITLES, type DeckGroup, type DeckSummary, type DecksView } from "@/lib/deckshared";
import { format } from "@/lib/money";

type Sort = "NAME" | "POWER" | "BRACKET" | "MODIFIED";
const SORTS: [Sort, string][] = [
  ["NAME", "Name"],
  ["POWER", "Power level"],
  ["BRACKET", "Bracket"],
  ["MODIFIED", "Last modified on Archidekt"],
];
const GROUPS: DeckGroup[] = ["OWN", "OTHERS", "LOCAL"];

/** The app's DeckSorting.sort: decks without the value sorted on always come last, by name. */
function sortDecks(decks: DeckSummary[], sort: Sort, reverse: boolean): DeckSummary[] {
  const byName = (a: DeckSummary, b: DeckSummary) => caseless(a.name, b.name);
  if (sort === "NAME") return [...decks].sort((a, b) => (reverse ? -byName(a, b) : byName(a, b)));
  const key = (d: DeckSummary): number | null => (sort === "POWER" ? d.power : sort === "BRACKET" ? d.bracketRealistic : d.archidektUpdatedAt);
  const known = decks.filter((d) => key(d) !== null);
  const unknown = decks.filter((d) => key(d) === null).sort(byName);
  const then =
    sort === "BRACKET"
      ? (a: DeckSummary, b: DeckSummary) => (b.bracketBaseline ?? 0) - (a.bracketBaseline ?? 0) || (b.power ?? 0) - (a.power ?? 0) || caseless(a.name, b.name)
      : byName;
  const sorted = known.sort((a, b) => key(b)! - key(a)! || then(a, b));
  return [...(reverse ? sorted.reverse() : sorted), ...unknown];
}

/** The decks (the app's Decks tab): Archidekt decks and local ones, with their scores, value and missing cards. */
export default function DecksPage() {
  const [view, setView] = useState<DecksView | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sort, setSort] = useState<Sort>("NAME");
  const [reverse, setReverse] = useState(false);
  const [query, setQuery] = useState("");
  const [newOpen, setNewOpen] = useState(false);
  const [deleting, setDeleting] = useState<DeckSummary | null>(null);

  useEffect(() => {
    (async () => {
      const [s, d] = await Promise.all([api<{ loggedIn: boolean; folder?: string }>("/api/session"), api<DecksView & { noFolder?: boolean }>("/api/decks")]);
      if (!s.data.loggedIn || d.status === 401 || d.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || d.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (d.ok && Array.isArray(d.data.decks)) {
        setView(d.data);
        const saved = SORTS.find(([k]) => k === d.data.sort)?.[0];
        if (saved) setSort(saved);
        setReverse(d.data.sortReversed);
      } else setError(d.data.error ?? "Could not load the decks.");
    })();
  }, []);

  const groups = useMemo(() => {
    if (!view) return [];
    const q = query.trim().toLowerCase();
    const list = q ? view.decks.filter((d) => `${d.name} ${d.commanders} ${d.owner} ${d.folder ?? ""}`.toLowerCase().includes(q)) : view.decks;
    const sorted = sortDecks(list, sort, reverse);
    return GROUPS.map((g) => ({ g, decks: sorted.filter((d) => d.group === g) })).filter((x) => x.decks.length > 0);
  }, [view, sort, reverse, query]);

  if (!view) {
    return (
      <>
        <TopBar page="decks" folder={folder} />
        <main className="page wide">{error ? <div className="bar error">{error}</div> : <p className="muted center">Loading…</p>}</main>
      </>
    );
  }
  const money = (eur: number) => format(eur * view.perEuro, view.currency);

  return (
    <>
      <TopBar page="decks" folder={folder} />
      <main className="page wide">
        <div className="controls">
          <input type="search" placeholder="Search decks, commanders, owners" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search decks" />
          <label className="inline small">
            <span className="muted">Sort</span>
            <select value={sort} onChange={(e) => setSort(e.target.value as Sort)}>
              {SORTS.map(([k, l]) => (
                <option key={k} value={k}>
                  {l}
                </option>
              ))}
            </select>
          </label>
          <button className="btn small-btn" onClick={() => setReverse((r) => !r)} title="Reverse the order">
            {reverse ? "▲" : "▼"}
          </button>
          <span className="spacer" />
          <a className="btn small-btn" href="/decks/shared">
            Cards in several decks
          </a>
          <a className="btn small-btn" href="/decks/cards">
            Rule-zero cards of all decks
          </a>
          {!view.readOnly && (
            <button className="btn primary small-btn" onClick={() => setNewOpen(true)}>
              New deck
            </button>
          )}
        </div>
        {view.decks.length === 0 && <p className="muted center">No decks yet. Import them in the app from Archidekt, or make a local deck here with New deck.</p>}
        {groups.map(({ g, decks }) => (
          <section key={g} className="stack">
            <h2 className="section-head">
              {GROUP_TITLES[g]} <span className="muted small">· {decks.length}</span>
            </h2>
            <div className="deck-grid">
              {decks.map((d) => (
                <div key={d.id} className="deck-tile-wrap">
                <a className="deck-tile" href={`/decks/${d.id}`}>
                  <div className="deck-cover" style={d.cover ? { backgroundImage: `url("${d.cover}")` } : undefined}>
                    <div className="deck-cover-in">
                      <strong className="deck-name">{d.name}</strong>
                      <span className="deck-commanders">
                        {d.commanders || "No commander"} <ColourPips identity={d.colorIdentity} />
                      </span>
                    </div>
                  </div>
                  <div className="deck-facts">
                    <span className={`bracket-chip b${d.bracketRealistic ?? 0}`} title={d.bracketRealistic ? `Bracket ${d.bracketRealistic} (${bracketName(d.bracketRealistic)}), baseline ${d.bracketBaseline ?? "–"}` : "Not scored yet"}>
                      {d.bracketRealistic ? `B${d.bracketRealistic}` : "–"}
                    </span>
                    <span title={`Power level (${view.powerLabel})`}>{d.power != null ? `⚡ ${formatPower(d.power, view.powerSource)}` : <span className="muted">⚡ –</span>}</span>
                    <span className="muted">{d.cardCount} cards</span>
                    <span className="spacer" />
                    <span className="num">{money(d.valueEur)}</span>
                  </div>
                  <div className="deck-facts muted small">
                    {d.missing > 0 ? <span className="warn-text">{d.missing} missing</span> : <span className="ok-text">All owned</span>}
                    {d.folder && <span>· {d.folder}</span>}
                    {d.origin && <span>· {d.origin}</span>}
                    {g === "OTHERS" && d.owner && <span>· {d.owner}</span>}
                  </div>
                </a>
                {!view.readOnly && (
                  <button className="tile-delete" title={`Delete ${d.name}`} aria-label={`Delete ${d.name}`} onClick={() => setDeleting(d)}>
                    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                      <path fill="currentColor" d="M9 3h6l1 2h4v2H4V5h4l1-2zm-3 6h12l-1 12H7L6 9zm4 2v8h2v-8h-2zm4 0v8h2v-8h-2z" />
                    </svg>
                  </button>
                )}
                </div>
              ))}
            </div>
          </section>
        ))}
        <p className="muted small">
          Values are the decks&apos; cards at their own printings (maybeboard left out). Missing: copies the collection lacks in any printing, basic lands left
          out. Power levels: {view.powerLabel}
          {view.powerSource === "APP" ? " (worked out on the phone; not synced)" : ""}.
        </p>
      </main>
      {newOpen && <NewDeckDialog onClose={() => setNewOpen(false)} />}
      {deleting && (
        <ConfirmDialog
          title="Delete the deck?"
          text={`${deleting.name} is deleted here and, at their next sync, on the phones. This can't be undone.${deleting.id < 0 ? "" : " The deck on Archidekt itself isn't touched; importing it again in the app brings it back."}`}
          button="Delete"
          danger
          onClose={() => setDeleting(null)}
          onYes={async () => {
            const r = await deckAction(deleting.id, { action: "delete" });
            if (r.error) return r.error;
            setView((v) => (v ? { ...v, decks: v.decks.filter((x) => x.id !== deleting.id) } : v));
            setDeleting(null);
            return null;
          }}
        />
      )}
    </>
  );
}
