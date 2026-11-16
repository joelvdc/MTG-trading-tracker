"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { DEFAULT_MODEL, DEFAULT_SECTIONS, FOCUS_LABELS, SECTION_LABELS, type AiFocus, type AiLength, type AiSection } from "@/lib/aiprimershared";
import { MAX_NOTE, MAX_PRIMER } from "@/lib/decknotes";
import { api } from "./api";

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
 * The deck page's Primer section, at the bottom of the page: the primer in full with Edit, or "No primer yet." with
 * "Write a primer"; [editing] swaps in the editor in the same place (any deck; the caller decides whether it may be
 * edited).
 */
export function PrimerSection({
  deckId,
  primer,
  canEdit,
  editing,
  onEdit,
  onCancel,
  onSave,
}: {
  deckId: number;
  primer: string | null;
  canEdit: boolean;
  editing: boolean;
  onEdit: () => void;
  onCancel: () => void;
  onSave: (text: string) => Promise<string | null>;
}) {
  const text = (primer ?? "").trim();
  return (
    <section className="card deck-primer" id="primer" aria-label="Primer">
      <div className="primer-top">
        <h2 className="primer-title">Primer</h2>
        {canEdit && !editing && text !== "" && (
          <button type="button" className="link small" onClick={onEdit}>
            Edit
          </button>
        )}
      </div>
      {editing ? (
        <PrimerEditor deckId={deckId} start={text} onCancel={onCancel} onSave={onSave} />
      ) : text === "" ? (
        <div className="stack">
          <p className="muted">No primer yet.</p>
          {canEdit && (
            <div>
              <button type="button" className="btn primary" onClick={onEdit}>
                Write a primer
              </button>
            </div>
          )}
        </div>
      ) : (
        <PrimerText text={text} />
      )}
    </section>
  );
}

/**
 * The primer in place on the page: a box that grows with its text, Edit/Preview, Save (the page posts it and offers the
 * Undo) and Cancel; "Write with AI" is folded under the box. Leaving the page with changes asks first.
 */
export function PrimerEditor({ deckId, start, onCancel, onSave }: { deckId: number; start: string; onCancel: () => void; onSave: (text: string) => Promise<string | null> }) {
  const [text, setText] = useState(start);
  const [preview, setPreview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [written, setWritten] = useState<string | null>(null);
  const box = useRef<HTMLTextAreaElement | null>(null);
  const dirty = text !== start;

  // A changed primer that isn't saved yet makes the browser ask before the page goes.
  useEffect(() => {
    if (!dirty) return;
    const onLeave = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", onLeave);
    return () => window.removeEventListener("beforeunload", onLeave);
  }, [dirty]);

  // The box grows with its text, from about a dozen rows up.
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${el.scrollHeight + 2}px`;
  }, [text, preview]);

  async function save() {
    setBusy(true);
    setError(null);
    const err = await onSave(text);
    setBusy(false);
    if (err) setError(err);
  }

  function cancel() {
    if (busy) return;
    if (dirty && !confirm("Throw away your changes?")) return;
    onCancel();
  }

  return (
    <div className="stack primer-editor">
      <div className="primer-tabs-row">
        <div className="seg text" role="group" aria-label="Primer view">
          <button type="button" className={preview ? "" : "on"} onClick={() => setPreview(false)}>
            Edit
          </button>
          <button type="button" className={preview ? "on" : ""} onClick={() => setPreview(true)}>
            Preview
          </button>
        </div>
        <span className="muted small primer-hint"># heading, - bullet, **bold**</span>
        <span className="muted small primer-count">
          {text.length} of {MAX_PRIMER} characters
        </span>
      </div>
      {preview ? (
        <div className="primer-preview">
          {text.trim() !== "" ? <PrimerText text={text} /> : <p className="muted small">Nothing to preview yet.</p>}
        </div>
      ) : (
        <textarea
          ref={box}
          aria-label="Primer"
          rows={12}
          maxLength={MAX_PRIMER}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="What the deck does, how to play it, its main lines…"
        />
      )}
      <WriteWithAi
        deckId={deckId}
        text={text}
        onError={setError}
        onWritten={(value, note) => {
          setText(value);
          setPreview(true);
          setWritten(note);
        }}
      />
      {written && <p className="ok-text small">{written}</p>}
      {error && <div className="bar error">{error}</div>}
      <div className="primer-actions">
        <button type="button" className="btn" disabled={busy} onClick={cancel}>
          Cancel
        </button>
        <span className="spacer" />
        <button type="button" className="btn primary" disabled={busy} onClick={save}>
          Save
        </button>
      </div>
    </div>
  );
}

/** What GET /api/ai/models answers. */
interface AiModels {
  configured: boolean;
  models: string[];
  defaultModel: string;
}

/** The last model, focus, length and sections the owner picked, kept in this browser. */
const AI_MODEL_KEY = "mtgtrader.aiModel";
const AI_OPTIONS_KEY = "mtgtrader.aiOptions";

/** Every section, in the order the prompt writes them. */
const ALL_SECTIONS = Object.keys(SECTION_LABELS) as AiSection[];

interface KeptOptions {
  focus: AiFocus;
  length: AiLength;
  sections: AiSection[];
}

/** The AI options kept in this browser, checked; null when there are none or they can't be read. */
function readKeptOptions(): KeptOptions | null {
  try {
    const raw = JSON.parse(localStorage.getItem(AI_OPTIONS_KEY) ?? "null") as Partial<KeptOptions> | null;
    if (!raw || typeof raw !== "object") return null;
    const focus = typeof raw.focus === "string" && raw.focus in FOCUS_LABELS ? (raw.focus as AiFocus) : "CASUAL";
    const length = raw.length === "SHORT" || raw.length === "FULL" ? raw.length : "FULL";
    const sections = Array.isArray(raw.sections) ? raw.sections.filter((s): s is AiSection => typeof s === "string" && s in SECTION_LABELS) : [...DEFAULT_SECTIONS];
    return { focus, length, sections };
  } catch {
    return null;
  }
}

/** Keeps the AI options for the next visit, the sections in their own order. */
function keepOptions(options: KeptOptions): void {
  try {
    localStorage.setItem(AI_OPTIONS_KEY, JSON.stringify({ ...options, sections: ALL_SECTIONS.filter((s) => options.sections.includes(s)) }));
  } catch {
    // Not remembered.
  }
}

/**
 * "Write with AI" under the primer's box: the model, the focus, the length, the sections and, with text already
 * there, whether to improve it or replace it. The answer goes in the box as a draft; nothing is saved here.
 */
function WriteWithAi({ deckId, text, onWritten, onError }: { deckId: number; text: string; onWritten: (text: string, note: string) => void; onError: (message: string | null) => void }) {
  const [models, setModels] = useState<AiModels | null>(null);
  const [model, setModel] = useState(DEFAULT_MODEL);
  const [focus, setFocus] = useState<AiFocus>("CASUAL");
  const [length, setLength] = useState<AiLength>("FULL");
  const [sections, setSections] = useState<AiSection[]>([...DEFAULT_SECTIONS]);
  const [startMode, setStartMode] = useState<"IMPROVE" | "REPLACE">("IMPROVE");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    try {
      const kept = localStorage.getItem(AI_MODEL_KEY);
      if (kept) setModel(kept);
    } catch {
      // Not remembered.
    }
    const kept = readKeptOptions();
    if (kept) {
      setFocus(kept.focus);
      setLength(kept.length);
      setSections(kept.sections);
    }
    let live = true;
    api<AiModels>("/api/ai/models").then((r) => {
      if (!live) return;
      if (r.ok) {
        setModels({ configured: r.data.configured, models: r.data.models ?? [], defaultModel: r.data.defaultModel ?? DEFAULT_MODEL });
      } else {
        // The default model still works if only the list is missing; an AI that isn't set up says so on Write.
        setModels({ configured: true, models: [DEFAULT_MODEL], defaultModel: DEFAULT_MODEL });
        onError(r.data.error ?? "Could not load the AI models.");
      }
    });
    return () => {
      live = false;
    };
    // Once, when the section opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const list = models === null ? [] : models.models.length > 0 ? models.models : [models.defaultModel];
  const chosen = list.includes(model) ? model : (models?.defaultModel ?? DEFAULT_MODEL);
  const ordered = ALL_SECTIONS.filter((s) => sections.includes(s));

  function pickModel(value: string) {
    setModel(value);
    try {
      localStorage.setItem(AI_MODEL_KEY, value);
    } catch {
      // Not remembered.
    }
  }

  /** Changes one kept option and remembers it with the others. */
  function pick(next: Partial<KeptOptions>) {
    const value: KeptOptions = { focus, length, sections, ...next };
    setFocus(value.focus);
    setLength(value.length);
    setSections(value.sections);
    keepOptions(value);
  }

  async function write() {
    setBusy(true);
    onError(null);
    const r = await api<{ text?: string; model?: string; left?: number }>(`/api/decks/${deckId}/aiprimer`, {
      model: chosen,
      focus,
      length,
      sections: ordered,
      startFrom: startMode === "IMPROVE" && text.trim() !== "" ? text : null,
    });
    setBusy(false);
    if (!r.ok || !r.data.text) {
      onError(r.data.error ?? "That did not work.");
      return;
    }
    const wrote = r.data.model ?? chosen;
    const left = r.data.left;
    onWritten(r.data.text, `Written by ${wrote}. Read it, change what you like, then Save.${left != null ? ` ${left} left today.` : ""}`);
  }

  return (
    <details className="primer-ai">
      <summary>Write with AI</summary>
      {models === null ? (
        <p className="muted small primer-ai-body">Loading the models…</p>
      ) : !models.configured ? (
        <p className="small primer-ai-body">AI writing isn&apos;t set up on this website.</p>
      ) : (
        <div className="primer-ai-body">
          <div className="primer-ai-row">
            <label className="primer-ai-field small">
              <span className="muted">Model</span>
              <select value={chosen} onChange={(e) => pickModel(e.target.value)} disabled={busy} aria-label="AI model">
                {list.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="primer-ai-row" role="group" aria-label="AI focus">
            <span className="muted small">Focus</span>
            {(Object.keys(FOCUS_LABELS) as AiFocus[]).map((f) => (
              <label key={f} className="primer-choice small">
                <input type="radio" name="ai-focus" checked={focus === f} onChange={() => pick({ focus: f })} disabled={busy} />
                <span>{FOCUS_LABELS[f]}</span>
              </label>
            ))}
          </div>
          <div className="primer-ai-row" role="group" aria-label="AI length">
            <span className="muted small">Length</span>
            {(["SHORT", "FULL"] as AiLength[]).map((l) => (
              <label key={l} className="primer-choice small">
                <input type="radio" name="ai-length" checked={length === l} onChange={() => pick({ length: l })} disabled={busy} />
                <span>{l === "SHORT" ? "Short" : "Full"}</span>
              </label>
            ))}
          </div>
          <div className="primer-ai-row primer-ai-sections" role="group" aria-label="AI sections">
            <span className="muted small">Sections</span>
            {ALL_SECTIONS.map((s) => (
              <label key={s} className="primer-choice small">
                <input type="checkbox" checked={sections.includes(s)} onChange={() => pick({ sections: sections.includes(s) ? sections.filter((x) => x !== s) : [...sections, s] })} disabled={busy} />
                <span>{SECTION_LABELS[s]}</span>
              </label>
            ))}
          </div>
          {text.trim() !== "" && (
            <div className="primer-ai-row" role="group" aria-label="AI start">
              <span className="muted small">Your text</span>
              <label className="primer-choice small">
                <input type="radio" name="ai-start" checked={startMode === "IMPROVE"} onChange={() => setStartMode("IMPROVE")} disabled={busy} />
                <span>Improve my text</span>
              </label>
              <label className="primer-choice small">
                <input type="radio" name="ai-start" checked={startMode === "REPLACE"} onChange={() => setStartMode("REPLACE")} disabled={busy} />
                <span>Replace it</span>
              </label>
            </div>
          )}
          <div className="primer-ai-row">
            <button type="button" className="btn small-btn" disabled={busy} onClick={write}>
              {busy ? "Writing… (up to a minute)" : "Write"}
            </button>
          </div>
        </div>
      )}
    </details>
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
