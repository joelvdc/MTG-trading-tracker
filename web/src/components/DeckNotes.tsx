"use client";

import { useState, type ReactNode } from "react";
import { MAX_NOTE, MAX_PRIMER } from "@/lib/decknotes";
import { Dialog } from "./Dialog";

/** How many lines a long primer shows before "Show all". */
const COLLAPSED_LINES = 6;

/** A line's text with **bold** as bold; everything else stays as it is (never HTML). */
function lineText(text: string): ReactNode {
  if (!text.includes("**")) return text;
  const parts = text.split("**");
  // Only pairs count: a lone ** at the end of a line is shown as it is.
  const paired = parts.length % 2 === 1 ? parts.length : parts.length - 1;
  return parts.map((part, i) => (i % 2 === 1 && i < paired ? <strong key={i}>{part}</strong> : part));
}

/** One line of a primer: a heading (#, ##, ###), a bullet (- ), a paragraph gap, or plain text. */
function PrimerLine({ line }: { line: string }) {
  if (line.trim() === "") return <div className="primer-gap" aria-hidden="true" />;
  if (line.startsWith("### ")) return <p className="primer-line primer-h3">{lineText(line.slice(4))}</p>;
  if (line.startsWith("## ")) return <p className="primer-line primer-h2">{lineText(line.slice(3))}</p>;
  if (line.startsWith("# ")) return <p className="primer-line primer-h1">{lineText(line.slice(2))}</p>;
  if (line.startsWith("- ")) return <p className="primer-line primer-bullet">{lineText(line.slice(2))}</p>;
  return <p className="primer-line">{lineText(line)}</p>;
}

/** A primer as it is shown (spec 3): headings, bullets, bold and paragraph gaps, nothing else. */
export function PrimerText({ text }: { text: string }) {
  return (
    <div className="primer-text">
      {text.split("\n").map((line, i) => (
        <PrimerLine key={i} line={line} />
      ))}
    </div>
  );
}

/**
 * The deck page's Primer section: the primer's first six lines with "Show all"/"Show less", Edit beside the title, and
 * a small "Add a primer" when there is none (any deck; the caller decides whether it may be edited).
 */
export function DeckPrimer({ primer, canEdit, onEdit }: { primer: string | null; canEdit: boolean; onEdit: () => void }) {
  const [open, setOpen] = useState(false);
  const text = (primer ?? "").trim();
  if (!text) {
    return canEdit ? (
      <p className="deck-primer-add small">
        <button type="button" className="link" onClick={onEdit}>
          Add a primer
        </button>
      </p>
    ) : null;
  }
  const lines = text.split("\n");
  const long = lines.length > COLLAPSED_LINES;
  return (
    <section className="card deck-primer" aria-label="Primer">
      <div className="primer-top">
        <h2 className="primer-title">Primer</h2>
        {canEdit && (
          <button type="button" className="link small" onClick={onEdit}>
            Edit
          </button>
        )}
      </div>
      <PrimerText text={open || !long ? text : lines.slice(0, COLLAPSED_LINES).join("\n")} />
      {long && (
        <button type="button" className="link small primer-more" onClick={() => setOpen((v) => !v)}>
          {open ? "Show less" : "Show all"}
        </button>
      )}
    </section>
  );
}

/** The primer in a large text box; Save hands the text over (the page posts it and offers the Undo). */
export function PrimerDialog({ start, onClose, onSave }: { start: string; onClose: () => void; onSave: (text: string) => Promise<string | null> }) {
  const [text, setText] = useState(start);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog title="Primer" onClose={() => !busy && onClose()}>
      <div className="stack">
        <p className="muted small">
          The deck&apos;s plan, in plain text: a line starting with #, ## or ### is a heading, - makes a bullet and **text** is bold. Up to {MAX_PRIMER} characters.
        </p>
        <textarea
          autoFocus
          aria-label="Primer"
          rows={12}
          maxLength={MAX_PRIMER}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="What the deck does, how to play it, its main lines…"
        />
        <p className="muted small">
          {text.length} of {MAX_PRIMER} characters.
        </p>
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError(null);
              const err = await onSave(text);
              setBusy(false);
              if (err) setError(err);
            }}
          >
            Save
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** One card's note in its card window (any deck); an empty note removes it. */
export function CardNoteEditor({ note, onSave }: { note: string; onSave: (note: string) => Promise<string | null> }) {
  const [text, setText] = useState(note);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const unchanged = text.trim() === note.trim();
  return (
    <section>
      <h3 className="mini-head">Note</h3>
      <textarea
        aria-label="Note on this card"
        rows={3}
        maxLength={MAX_NOTE}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          setSaved(false);
        }}
        placeholder="A note for this card in the deck"
      />
      <div className="inline">
        <button
          type="button"
          className="btn small-btn"
          disabled={busy || unchanged}
          onClick={async () => {
            setBusy(true);
            setError(null);
            const err = await onSave(text);
            setBusy(false);
            if (err) {
              setError(err);
              return;
            }
            setSaved(true);
          }}
        >
          Save
        </button>
        {saved && <span className="ok-text small">Saved.</span>}
        <span className="muted small">Empty removes the note.</span>
      </div>
      {error && <div className="bar error">{error}</div>}
    </section>
  );
}
