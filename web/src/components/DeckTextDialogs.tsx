"use client";

import { useEffect, useState } from "react";
import { api } from "./api";
import { offerUndo, snapshotOf } from "./DeckUndo";
import { Dialog } from "./Dialog";

/** The ways a deck can be written out: Moxfield's and Archidekt's imports, MTG Arena's, or a plain list. */
type Format = "moxfield" | "archidekt" | "arena" | "plain";

const FORMATS: { key: Format; label: string }[] = [
  { key: "moxfield", label: "Moxfield" },
  { key: "archidekt", label: "Archidekt" },
  { key: "arena", label: "MTG Arena" },
  { key: "plain", label: "Plain list" },
];

/** Where each format's text goes. */
const NOTES: Record<Format, string> = {
  moxfield: "Paste into Moxfield's or Archidekt's import, or into another deck here.",
  archidekt: "Paste into Moxfield's or Archidekt's import, or into another deck here.",
  arena: "For MTG Arena's import.",
  plain: 'One "1 Card name" per line.',
};

const FORMAT_KEY = "mtg.deckTextFormat";

/** The format picked last time in this browser (Moxfield when it isn't remembered). */
function lastFormat(): Format {
  try {
    const saved = localStorage.getItem(FORMAT_KEY);
    return FORMATS.some((f) => f.key === saved) ? (saved as Format) : "moxfield";
  } catch {
    return "moxfield";
  }
}

/**
 * The deck's cards as text to copy elsewhere (the app's "Copy as text"): Moxfield's, Archidekt's, MTG Arena's or a
 * plain list, with or without the categories and the maybeboard. Every change asks for the text again.
 */
export function CopyTextDialog({ deckId, hasMaybe, onClose }: { deckId: number; hasMaybe: boolean; onClose: () => void }) {
  const [format, setFormat] = useState<Format>(lastFormat);
  const [categories, setCategories] = useState(true);
  const [maybeboard, setMaybeboard] = useState(true);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    try {
      localStorage.setItem(FORMAT_KEY, format);
    } catch {
      // Not remembered.
    }
  }, [format]);

  useEffect(() => {
    let live = true;
    setBusy(true);
    setError(null);
    setCopied(false);
    (async () => {
      const r = await api<{ text?: string }>(`/api/decks/${deckId}`, { action: "export", format, categories, maybeboard });
      if (!live) return;
      setBusy(false);
      if (r.ok && typeof r.data.text === "string") setText(r.data.text);
      else setError(r.data.error ?? "That did not work.");
    })();
    return () => {
      live = false;
    };
  }, [deckId, format, categories, maybeboard]);

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setError(null);
    } catch {
      setError("The browser didn't allow copying.");
    }
  }

  return (
    <Dialog title="Copy deck as text" onClose={() => !busy && onClose()}>
      <div className="stack">
        <div className="seg text" role="group" aria-label="Format">
          {FORMATS.map((f) => (
            <button key={f.key} className={format === f.key ? "on" : ""} onClick={() => setFormat(f.key)}>
              {f.label}
            </button>
          ))}
        </div>
        <p className="muted small">{NOTES[format]}</p>
        <label className="check small">
          <input type="checkbox" checked={categories} disabled={format === "arena" || format === "plain"} onChange={(e) => setCategories(e.target.checked)} />
          <span>Categories</span>
        </label>
        {hasMaybe && (
          <label className="check small">
            <input type="checkbox" checked={maybeboard} onChange={(e) => setMaybeboard(e.target.checked)} />
            <span>Maybeboard</span>
          </label>
        )}
        <textarea className="deck-text" aria-label="The deck as text" rows={16} readOnly value={text} />
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={busy}>
            Close
          </button>
          <button className="btn primary" disabled={busy || !text} onClick={copy}>
            {copied ? "Copied" : "Copy"}
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** What a pasted list holds, and which of its cards the deck has already. */
interface PastePreview {
  cards: number;
  lines: number;
  exact: number;
  byName: number;
  notFound: string[];
  unreadable: string[];
  commanders: string[];
  hasTags: boolean;
  names: string[];
  inDeck: string[];
}

/**
 * A pasted decklist into this deck (local decks): its cards are looked up first ("Check") and the window says what it
 * found, what it couldn't find and what the deck has already, then adds the rest.
 */
export function PasteCardsDialog({ deckId, onClose, onDone }: { deckId: number; onClose: () => void; onDone: (notice: string) => void }) {
  const [text, setText] = useState("");
  const [board, setBoard] = useState<"MAIN" | "MAYBE">("MAIN");
  const [useTags, setUseTags] = useState(true);
  const [preview, setPreview] = useState<PastePreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function check() {
    setBusy(true);
    setError(null);
    const r = await api<PastePreview>(`/api/decks/${deckId}`, { action: "paste-preview", text, board });
    setBusy(false);
    if (r.ok && Array.isArray(r.data.names)) {
      setPreview(r.data);
      setUseTags(true);
    } else {
      setPreview(null);
      setError(r.data.error ?? "That did not work.");
    }
  }

  async function add() {
    setBusy(true);
    setError(null);
    const r = await api<{ added?: number; skipped?: string[]; before?: unknown }>(`/api/decks/${deckId}`, { action: "paste", text, board, useTags });
    setBusy(false);
    if (!r.ok || r.data.added === undefined) {
      setError(r.data.error ?? "That did not work.");
      return;
    }
    const before = snapshotOf(r.data.before);
    if (before) offerUndo(deckId, before, "Cards added");
    const added = r.data.added;
    const skipped = r.data.skipped ?? [];
    onDone(`Added ${added} card${added === 1 ? "" : "s"}.${skipped.length ? ` Skipped ${skipped.length} already in the deck.` : ""}`);
  }

  return (
    <Dialog title="Paste cards into this deck" onClose={() => !busy && onClose()}>
      <div className="stack">
        <textarea
          aria-label="The cards to paste"
          rows={12}
          placeholder={"1 Sol Ring\n1 Arcane Signet\n…"}
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setPreview(null);
          }}
        />
        <div className="inline">
          <div className="seg text" role="group" aria-label="Board">
            {(
              [
                ["MAIN", "Main deck"],
                ["MAYBE", "Maybeboard"],
              ] as const
            ).map(([k, l]) => (
              <button
                key={k}
                className={board === k ? "on" : ""}
                disabled={busy}
                onClick={() => {
                  setBoard(k);
                  setPreview(null);
                }}
              >
                {l}
              </button>
            ))}
          </div>
        </div>
        {preview && (
          <div className="stack small">
            <p>
              {preview.cards} card{preview.cards === 1 ? "" : "s"} found ({preview.exact} exact printing{preview.exact === 1 ? "" : "s"}, {preview.byName} by name)
            </p>
            {preview.notFound.length > 0 && <p className="warn-text new-deck-preview">Not found: {preview.notFound.join(", ")}</p>}
            {preview.unreadable.length > 0 && <p className="muted new-deck-preview">Lines that aren&apos;t cards: {preview.unreadable.join(" · ")}</p>}
            {preview.inDeck.length > 0 && (
              <p className="warn-text new-deck-preview">Already in the deck, will be skipped: {preview.inDeck.join(", ")}. Basic lands are added anyway.</p>
            )}
            {preview.hasTags && (
              <label className="check">
                <input type="checkbox" checked={useTags} onChange={(e) => setUseTags(e.target.checked)} />
                <span>Use the list&apos;s categories</span>
              </label>
            )}
          </div>
        )}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <button className="btn" disabled={busy || !text.trim()} onClick={check}>
            Check
          </button>
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={busy}>
            Close
          </button>
          <button className="btn primary" disabled={busy || !preview || preview.cards === 0} onClick={add}>
            {preview ? `Add ${preview.cards} card${preview.cards === 1 ? "" : "s"}` : "Add cards"}
          </button>
        </div>
      </div>
    </Dialog>
  );
}
