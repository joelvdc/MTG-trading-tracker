"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { ArchidektImportDialog, ArchidektUpdateDialog } from "@/components/ArchidektDialogs";
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
  ["MODIFIED", "Last modified"],
];
const GROUPS: DeckGroup[] = ["OWN", "OTHERS", "LOCAL", "RETIRED"];
/** In the folded-groups list: the Retired section is hidden altogether (app 1.83: "Hide retired decks"). */
const HIDE_RETIRED = "hide:RETIRED";

/** The app's DeckSorting.sort: decks without the value sorted on always come last, by name. */
function sortDecks(decks: DeckSummary[], sort: Sort, reverse: boolean): DeckSummary[] {
  const byName = (a: DeckSummary, b: DeckSummary) => caseless(a.name, b.name);
  if (sort === "NAME") return [...decks].sort((a, b) => (reverse ? -byName(a, b) : byName(a, b)));
  const key = (d: DeckSummary): number | null => (sort === "POWER" ? d.power : sort === "BRACKET" ? d.bracketRealistic : d.modifiedAt);
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
  // Folded groups (your decks, other people's, local), remembered in this browser like the app's Decks tab.
  const [folded, setFolded] = useState<Set<string>>(new Set());
  useEffect(() => {
    try {
      setFolded(new Set(JSON.parse(localStorage.getItem("mtg.decksFolded") ?? "[]") as string[]));
    } catch {
      setFolded(new Set());
    }
  }, []);
  const setFold = (g: string, fold: boolean) =>
    setFolded((old) => {
      if (old.has(g) === fold) return old;
      const next = new Set(old);
      if (fold) next.add(g);
      else next.delete(g);
      try {
        localStorage.setItem("mtg.decksFolded", JSON.stringify([...next]));
      } catch {}
      return next;
    });
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sort, setSort] = useState<Sort>("NAME");
  const [reverse, setReverse] = useState(false);
  const [query, setQuery] = useState("");
  const [newOpen, setNewOpen] = useState(false);
  const [deleting, setDeleting] = useState<DeckSummary | null>(null);
  const [retiring, setRetiring] = useState<DeckSummary | null>(null);
  const [archidekt, setArchidekt] = useState<"import" | "update" | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloads, setReloads] = useState(0);

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
  }, [reloads]);

  const groups = useMemo(() => {
    if (!view) return [];
    const q = query.trim().toLowerCase();
    const list = q ? view.decks.filter((d) => `${d.name} ${d.commanders} ${d.owner} ${d.folder ?? ""}`.toLowerCase().includes(q)) : view.decks;
    const sorted = sortDecks(list, sort, reverse);
    return GROUPS.filter((g) => g !== "RETIRED" || !folded.has(HIDE_RETIRED))
      .map((g) => ({ g, decks: sorted.filter((d) => d.group === g) }))
      .filter((x) => x.decks.length > 0);
  }, [view, sort, reverse, query, folded]);

  /** Retires a deck from the delete question (it keeps its page and games). */
  async function retireDeck(d: DeckSummary): Promise<string | null> {
    const r = await deckAction(d.id, { action: "retire", retired: true });
    if (r.error) return r.error;
    setView((v) => (v ? { ...v, decks: v.decks.map((x) => (x.id === d.id ? { ...x, group: "RETIRED" as const } : x)) } : v));
    setDeleting(null);
    setRetiring(null);
    return null;
  }

  /** Brings a retired deck back (its group comes from the server, so the list loads again). */
  async function bringBack(d: DeckSummary) {
    const r = await deckAction(d.id, { action: "retire", retired: false });
    if (r.error) setError(r.error);
    else {
      setNotice(`${d.name} is back.`);
      setReloads((n) => n + 1);
    }
  }

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
        {notice && (
          <div className="bar ok" role="status">
            {notice}
          </div>
        )}
        <div className="controls decks-controls">
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
          {view.decks.some((d) => d.group === "RETIRED") && (
            <button className="btn small-btn" onClick={() => setFold(HIDE_RETIRED, !folded.has(HIDE_RETIRED))}>
              {folded.has(HIDE_RETIRED) ? "Show retired decks" : "Hide retired decks"}
            </button>
          )}
          <a className="btn small-btn" href="/decks/recommendations">
            Recommendations
          </a>
          <a className="btn small-btn" href="/decks/shared">
            Cards in several decks
          </a>
          <a className="btn small-btn" href="/decks/cards">
            Rule-zero cards of all decks
          </a>
          {!view.readOnly && (
            <button className="btn small-btn" onClick={() => setArchidekt("update")} title="Reload the decks changed on Archidekt and score them again">
              Update all decks
            </button>
          )}
          {!view.readOnly && (
            <button className="btn small-btn" onClick={() => setArchidekt("import")}>
              Import from Archidekt
            </button>
          )}
          {!view.readOnly && (
            <button className="btn primary small-btn" onClick={() => setNewOpen(true)}>
              New deck
            </button>
          )}
        </div>
        {view.decks.length === 0 && <p className="muted center">No decks yet. Import them in the app from Archidekt, or make a local deck here with New deck.</p>}
        {groups.map(({ g, decks }) => (
          <details key={g} className="stack fold" open={!folded.has(g)} onToggle={(e) => setFold(g, !(e.currentTarget as HTMLDetailsElement).open)}>
            <summary>
              <h2 className="section-head">
                {GROUP_TITLES[g]} <span className="muted small">· {decks.length}</span>
              </h2>
            </summary>
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
                    {d.legalProblems ? (
                      <span className="warn-text" title="Not Commander legal: see the deck page">
                        · ⚠ Not legal
                      </span>
                    ) : null}
                    {d.folder && <span>· {d.folder}</span>}
                    {d.origin && <span>· {d.origin}</span>}
                    {g === "OTHERS" && d.owner && <span>· {d.owner}</span>}
                  </div>
                </a>
                {!view.readOnly && (
                  <button
                    className="tile-delete tile-retire"
                    title={d.group === "RETIRED" ? `Bring back ${d.name}` : `Retire ${d.name}`}
                    aria-label={d.group === "RETIRED" ? `Bring back ${d.name}` : `Retire ${d.name}`}
                    onClick={() => (d.group === "RETIRED" ? bringBack(d) : setRetiring(d))}
                  >
                    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                      {d.group === "RETIRED" ? (
                        <path fill="currentColor" d="M20.55 5.22l-1.39-1.68A1.45 1.45 0 0 0 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.22A2 2 0 0 0 3 6.5V19a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6.5c0-.48-.17-.93-.45-1.28zM12 9.5l5.5 5.5H14v2h-4v-2H6.5L12 9.5zM5.12 5l.82-1h12l.93 1H5.12z" />
                      ) : (
                        <path fill="currentColor" d="M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z" />
                      )}
                    </svg>
                  </button>
                )}
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
          </details>
        ))}
        <p className="muted small">
          Values are the decks&apos; cards at their own printings (maybeboard left out). Missing: copies the collection lacks in any printing, basic lands left
          out. Power levels: {view.powerLabel}
          {view.powerSource === "APP" ? " (from each deck's win turn, which the website works out by itself when a decklist changes)" : ""}.
        </p>
      </main>
      {newOpen && <NewDeckDialog onClose={() => setNewOpen(false)} />}
      {archidekt === "import" && (
        <ArchidektImportDialog
          user={view.archidektUser}
          onClose={() => setArchidekt(null)}
          onDone={(text, openId) => {
            if (openId !== undefined) return window.location.assign(`/decks/${openId}`);
            setArchidekt(null);
            setNotice(text);
            setReloads((n) => n + 1);
          }}
        />
      )}
      {archidekt === "update" && (
        <ArchidektUpdateDialog
          onClose={() => setArchidekt(null)}
          onDone={(text) => {
            setArchidekt(null);
            setNotice(text);
            setReloads((n) => n + 1);
          }}
        />
      )}
      {retiring && (
        <ConfirmDialog
          title="Retire the deck?"
          text={`${retiring.name} moves to Retired, with its page, games and stats. It leaves the seat pickers, Update all decks and the collection checks (In a deck, the trade binder’s keep for my decks). Bring it back at any time.`}
          button="Retire"
          onClose={() => setRetiring(null)}
          onYes={() => retireDeck(retiring)}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete the deck?"
          other={deleting.games && deleting.group !== "RETIRED" ? { label: "Retire instead", run: () => retireDeck(deleting) } : undefined}
          text={`${deleting.games && deleting.group !== "RETIRED" ? `It played ${deleting.games} recorded game(s). The games stay either way, but retiring the deck keeps its page and stats too. ` : ""}${deleting.name} is deleted here and, at their next sync, on the phones. This can't be undone.${deleting.id < 0 ? "" : " The deck on Archidekt itself isn't touched; importing it again in the app brings it back."}`}
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
