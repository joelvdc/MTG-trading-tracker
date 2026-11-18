"use client";

import { useEffect, useRef, useState } from "react";
import { cardsByType, compareText, type CompareCard, type DeckComparison } from "@/lib/deckcompare";
import type { DeckView } from "@/lib/deckshared";
import { api } from "./api";

/** The comparison's answer: the lists, plus what the other side was and what couldn't be read. */
type CompareAnswer = DeckComparison & { otherName: string; otherOwner: string | null; notFound: string[]; unreadable: string[] };

/** Where the other deck comes from: an Archidekt link, another deck of yours, or a pasted list. */
type Source = "archidekt" | "deck" | "text";

/** The three ways the other deck can be given, as the buttons show them. */
const SOURCES: { key: Source; label: string }[] = [
  { key: "archidekt", label: "Archidekt link" },
  { key: "deck", label: "My decks" },
  { key: "text", label: "Pasted list" },
];

/** The total copies of a list (add, remove, both). */
const copies = (cards: CompareCard[]) => cards.reduce((n, c) => n + c.quantity, 0);

/**
 * A deck's groups of cards (by type), the way the compare window lists them: a heading with the group's copies, then
 * one line per card. [owned]: whether to mark the copies you own (the "to add" list only).
 */
function CardGroups({ cards, owned = false }: { cards: CompareCard[]; owned?: boolean }) {
  return (
    <>
      {cardsByType(cards).map((g) => (
        <div key={g.type}>
          <h4 className="compare-type">
            {g.type} <span className="muted small">· {g.count}</span>
          </h4>
          <ul className="compare-list">
            {g.cards.map((c) => (
              <li key={c.name}>
                <span className="compare-qty">{c.quantity}</span>
                <span className="compare-name">{c.name}</span>
                {owned && c.owned > 0 && (
                  <span className="compare-owned" title="You own this card in some printing">
                    ✓ own {c.owned}
                  </span>
                )}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </>
  );
}

/**
 * Compares this deck with an Archidekt deck, another of your decks or a pasted list, by card name (printings are
 * ignored), and only shows the differences: the cards to add, the cards to remove and the cards both decks have.
 * It never changes the deck; a list can be copied to paste into it (Edit → Paste cards…).
 */
export function ComparePanel({ view, onClose }: { view: DeckView; onClose: () => void }) {
  const [source, setSource] = useState<Source>("archidekt");
  const [link, setLink] = useState("");
  const [deckId, setDeckId] = useState<number | null>(null);
  const [text, setText] = useState("");
  // Your other decks (null while they load), and the comparison as it was last made (it stays while a new one fails).
  const [decks, setDecks] = useState<{ id: number; name: string }[] | null>(null);
  const [result, setResult] = useState<CompareAnswer | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Which "Copy list" was pressed ("add" or "remove"), so its button says "Copied".
  const [copied, setCopied] = useState<"add" | "remove" | null>(null);
  // Which request is the newest: a slow, older comparison may not set the answer any more.
  const newest = useRef(0);

  useEffect(() => {
    let live = true;
    // A new deck: no lists from the deck before.
    setResult(null);
    setError(null);
    setDecks(null);
    setDeckId(null);
    (async () => {
      const r = await api<{ decks: { id: number; name: string }[] }>(`/api/decks/${view.id}/compare`);
      if (!live) return;
      if (r.ok && Array.isArray(r.data.decks)) {
        setDecks(r.data.decks);
        // The first one is picked: comparing with another deck takes one click.
        setDeckId(r.data.decks[0]?.id ?? null);
      } else {
        setDecks([]);
        setError(r.data.error ?? "Could not load your decks.");
      }
    })();
    return () => {
      live = false;
    };
  }, [view.id]);

  /** Asks for the comparison and shows it, unless the comparison fails (then the window says why and keeps the old one). */
  async function compare() {
    const n = ++newest.current;
    setError(null);
    setCopied(null);
    setBusy(true);
    const body = source === "archidekt" ? { source: "archidekt", link } : source === "deck" ? { source: "deck", deckId } : { source: "text", text };
    const r = await api<CompareAnswer>(`/api/decks/${view.id}/compare`, body);
    // A newer comparison was asked for meanwhile: its answer is the one to show.
    if (n !== newest.current) return;
    setBusy(false);
    if (r.ok) setResult(r.data);
    else setError(r.data.error ?? "Could not compare the decks.");
  }

  /** Copies a list ("1 Card Name" per line) for Edit → Paste cards…; the button says "Copied" once it's on the clipboard. */
  async function copy(which: "add" | "remove", cards: CompareCard[]) {
    try {
      await navigator.clipboard.writeText(compareText(cards));
      setCopied(which);
      setError(null);
    } catch {
      setError("The browser didn't allow copying.");
    }
  }

  const canCompare = !busy && (source === "archidekt" ? link.trim() !== "" : source === "deck" ? deckId !== null : text.trim() !== "");

  /** A section's "Copy list" button (left out when the list is empty). */
  const copyButton = (which: "add" | "remove", cards: CompareCard[]) =>
    cards.length > 0 && (
      <button type="button" className="btn small-btn" onClick={() => copy(which, cards)}>
        {copied === which ? "Copied" : "Copy list"}
      </button>
    );

  return (
    <div className="stack compare">
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (canCompare) void compare();
        }}
      >
        <div className="controls">
          <div className="seg text" role="group" aria-label="Compare with">
            {SOURCES.map((s) => (
              <button key={s.key} type="button" className={source === s.key ? "on" : ""} disabled={busy} onClick={() => setSource(s.key)}>
                {s.label}
              </button>
            ))}
          </div>
          <span className="spacer" />
          <button type="button" className="btn small-btn" onClick={onClose}>
            Close
          </button>
        </div>

        {source === "archidekt" && (
          <label className="field">
            <span>Link to a public Archidekt deck</span>
            <input type="text" inputMode="url" value={link} placeholder="https://archidekt.com/decks/…" autoFocus onChange={(e) => setLink(e.target.value)} />
          </label>
        )}
        {source === "deck" && (
          <>
            {decks === null && <p className="muted small">Loading your decks…</p>}
            {decks?.length === 0 && <p className="muted small">You have no other decks yet.</p>}
            <label className="field">
              <span>Your deck</span>
              <select value={deckId ?? ""} disabled={!decks || decks.length === 0 || busy} onChange={(e) => setDeckId(e.target.value ? Number(e.target.value) : null)}>
                {(decks ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </label>
          </>
        )}
        {source === "text" && (
          <label className="field">
            <span>Decklist</span>
            <textarea className="deck-text" rows={10} value={text} placeholder="1 Sol Ring" onChange={(e) => setText(e.target.value)} />
          </label>
        )}

        <div className="dialog-actions">
          <span className="spacer" />
          <button type="submit" className="btn primary" disabled={!canCompare}>
            {busy ? "Comparing…" : "Compare"}
          </button>
        </div>
      </form>

      {error && <div className="bar error">{error}</div>}

      {!result && (
        <p className="muted small">
          Cards are matched by name, so different printings count as the same card. Maybeboards are left out. Your deck isn&apos;t changed.
        </p>
      )}

      {result && (
        <>
          <p className="compare-summary">
            Compared with <strong>{result.otherName}</strong>
            {result.otherOwner ? ` by ${result.otherOwner}` : ""}: {copies(result.add)} to add, {copies(result.remove)} to remove, {copies(result.both)} in both.
          </p>
          {result.commanders.differ && (
            <p className="warn-text small">
              Different commander: yours is {result.commanders.mine.join(" & ")}, theirs is {result.commanders.other.join(" & ")}.
            </p>
          )}
          {result.notFound.length > 0 && <p className="warn-text small">Not compared, Scryfall doesn&apos;t know: {result.notFound.join(", ")}.</p>}
          {result.unreadable.length > 0 && <p className="warn-text small">Lines that couldn&apos;t be read: {result.unreadable.join(", ")}.</p>}

          {result.add.length === 0 && result.remove.length === 0 ? (
            <p className="muted small">These decks hold the same cards.</p>
          ) : (
            <div className="compare-columns">
              <section>
                <div className="compare-head">
                  <h3>
                    Cards to add <span className="muted small">· {copies(result.add)}</span>
                  </h3>
                  {copyButton("add", result.add)}
                </div>
                {result.add.length === 0 ? <p className="muted small">Nothing to add.</p> : <CardGroups cards={result.add} owned />}
                {result.add.length > 0 && view.local && !view.readOnly && (
                  <p className="muted small">To add these, copy the list and use Edit → Paste cards…</p>
                )}
              </section>
              <section>
                <div className="compare-head">
                  <h3>
                    Cards to remove <span className="muted small">· {copies(result.remove)}</span>
                  </h3>
                  {copyButton("remove", result.remove)}
                </div>
                {result.remove.length === 0 ? <p className="muted small">Nothing to remove.</p> : <CardGroups cards={result.remove} />}
              </section>
            </div>
          )}

          <details>
            <summary>
              <strong>In both</strong> <span className="muted small">· {copies(result.both)}</span>
            </summary>
            <CardGroups cards={result.both} />
          </details>
        </>
      )}
    </div>
  );
}
