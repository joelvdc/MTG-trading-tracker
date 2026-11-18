"use client";

import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type MouseEvent, type ReactNode } from "react";
import Link from "next/link";
import { DEFAULT_MODEL, DEFAULT_SECTIONS, FOCUS_LABELS, SECTION_LABELS, type AiFocus, type AiLength, type AiSection } from "@/lib/aiprimershared";
import { linkSegments, MAX_NOTE, MAX_PRIMER, nameKey } from "@/lib/decknotes";
import { api } from "./api";

/** How a primer's [[card]] links meet the deck: which card a name is, and the picture that follows the mouse. */
export interface PrimerCardLinks {
  find: (name: string) => { open: () => void; picture: string | null } | null;
  follow: (url: string | null) => (e: MouseEvent) => void;
  hide: () => void;
}

/**
 * A line's text with **bold** as bold and [[card]] links as buttons that open the card (a link to a card the deck
 * hasn't got is its name in bold); everything else stays as it is (never HTML).
 */
function lineText(text: string, cards?: PrimerCardLinks): ReactNode[] {
  const parts = text.split("**");
  // Only pairs count: a lone ** at the end of a line is shown as it is.
  const paired = parts.length % 2 === 1 ? parts.length : parts.length - 1;
  const out: ReactNode[] = [];
  parts.forEach((part, i) => {
    const bold = i < paired && i % 2 === 1;
    linkSegments(part).forEach((segment, j) => {
      const key = `${i}-${j}`;
      if (segment.kind === "text") {
        out.push(bold ? <strong key={key}>{segment.text}</strong> : segment.text);
        return;
      }
      const hit = cards?.find(segment.name) ?? null;
      if (!cards || !hit) {
        out.push(<strong key={key}>{segment.name}</strong>);
        return;
      }
      out.push(
        <button
          key={key}
          type="button"
          className="primer-card"
          onClick={() => {
            cards.hide();
            hit.open();
          }}
          onMouseMove={(e) => cards.follow(hit.picture)(e)}
          onMouseLeave={cards.hide}
        >
          {segment.name}
        </button>,
      );
    });
  });
  return out;
}

/** One line of a primer: a heading (#, ##, ###), a bullet (- ), a paragraph gap, or plain text. */
function PrimerLine({ line, cards }: { line: string; cards?: PrimerCardLinks }) {
  if (line.trim() === "") return <div className="primer-gap" aria-hidden="true" />;
  if (line.startsWith("### ")) return <p className="primer-line primer-h3">{lineText(line.slice(4), cards)}</p>;
  if (line.startsWith("## ")) return <p className="primer-line primer-h2">{lineText(line.slice(3), cards)}</p>;
  if (line.startsWith("# ")) return <p className="primer-line primer-h1">{lineText(line.slice(2), cards)}</p>;
  if (line.startsWith("- ")) return <p className="primer-line primer-bullet">{lineText(line.slice(2), cards)}</p>;
  return <p className="primer-line">{lineText(line, cards)}</p>;
}

/** A primer as it is shown (spec 3): headings, bullets, bold and paragraph gaps, nothing else; card links when [cards]. */
export function PrimerText({ text, cards }: { text: string; cards?: PrimerCardLinks }) {
  return (
    <div className="primer-text">
      {text.split("\n").map((line, i) => (
        <PrimerLine key={i} line={line} cards={cards} />
      ))}
    </div>
  );
}

/**
 * The deck page's Primer section, at the bottom of the page: the primer in full with Edit, or "No primer yet." with
 * "Write a primer"; [editing] swaps in the editor in the same place (any deck; the caller decides whether it may be
 * edited). Foldable like Tokens and Stats: the page keeps whether it is folded ([open] and [onToggle]).
 */
export function PrimerSection({
  deckId,
  primer,
  canEdit,
  editing,
  onEdit,
  onCancel,
  onSave,
  cards,
  cardNames = [],
  open,
  onToggle,
}: {
  deckId: number;
  primer: string | null;
  canEdit: boolean;
  editing: boolean;
  onEdit: () => void;
  onCancel: () => void;
  onSave: (text: string) => Promise<string | null>;
  cards?: PrimerCardLinks;
  cardNames?: string[];
  open: boolean;
  onToggle: (open: boolean) => void;
}) {
  const text = (primer ?? "").trim();
  return (
    <details
      className="card deck-primer"
      id="primer"
      aria-label="Primer"
      open={open}
      onToggle={(e) => onToggle((e.currentTarget as HTMLDetailsElement).open)}
    >
      <summary>
        <strong>Primer</strong>
        {canEdit && !editing && text !== "" && (
          <button
            type="button"
            className="link small"
            onClick={(e) => {
              // The Edit link doesn't fold the section.
              e.preventDefault();
              e.stopPropagation();
              onEdit();
            }}
          >
            Edit
          </button>
        )}
      </summary>
      {editing ? (
        <PrimerEditor deckId={deckId} start={text} onCancel={onCancel} onSave={onSave} cards={cards} cardNames={cardNames} />
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
        <PrimerText text={text} cards={cards} />
      )}
    </details>
  );
}

/** The "/" word the caret is at, when a card list can open there: a "/" at the start of the text or after a space or "(". */
interface CardWord {
  /** Where the "/" is. */
  start: number;
  /** The caret, at the end of what was typed after the "/". */
  end: number;
  query: string;
}

/** The "/" word the caret is at, or null when the caret isn't in one (no "/", or it follows a word). */
function cardWord(value: string, caret: number): CardWord | null {
  const before = value.slice(0, caret);
  const start = before.lastIndexOf("/");
  if (start < 0) return null;
  if (start > 0 && !" \n(".includes(before[start - 1])) return null;
  const query = before.slice(start + 1);
  if (query.includes("\n") || /[[\]]/.test(query)) return null;
  return { start, end: caret, query };
}

/** The deck's cards that match what is typed after the "/": those starting with it first, then those containing it. */
function cardMatches(names: string[], query: string): string[] {
  const key = nameKey(query);
  const starts = names.filter((n) => nameKey(n).startsWith(key));
  const inside = names.filter((n) => !starts.includes(n) && nameKey(n).includes(key));
  return [...starts, ...inside].slice(0, 8);
}

/** The font and spacing a hidden copy of the box's text needs to wrap exactly as the box does. */
const MIRROR_STYLES = ["font-family", "font-size", "font-weight", "font-style", "letter-spacing", "line-height", "text-transform", "text-indent", "tab-size"];

/**
 * Where the caret's line ends in the primer's box, measured with a hidden copy of its text in the same place ("/" list
 * of cards opens just under it).
 */
function caretSpot(box: HTMLTextAreaElement, copy: HTMLDivElement, caret: number): { left: number; top: number } {
  const style = window.getComputedStyle(box);
  for (const name of MIRROR_STYLES) copy.style.setProperty(name, style.getPropertyValue(name));
  const size = (name: string) => Number.parseFloat(style.getPropertyValue(name)) || 0;
  // The copy starts at the box's corner with the box's border and padding, so its lines sit where the box's do.
  copy.style.paddingTop = `${size("padding-top") + size("border-top-width")}px`;
  copy.style.paddingRight = `${size("padding-right") + size("border-right-width")}px`;
  copy.style.paddingBottom = `${size("padding-bottom") + size("border-bottom-width")}px`;
  copy.style.paddingLeft = `${size("padding-left") + size("border-left-width")}px`;
  copy.style.width = `${box.clientWidth - size("padding-left") - size("padding-right")}px`;
  copy.textContent = box.value.slice(0, caret);
  const mark = document.createElement("span");
  mark.textContent = "\u200b";
  copy.append(mark);
  const line = size("line-height") || size("font-size") * 1.4;
  const at = mark.getBoundingClientRect();
  const corner = copy.getBoundingClientRect();
  // A list that would run off the right edge stays inside the box (it is 200px wide at least).
  const room = copy.parentElement?.clientWidth ?? 320;
  return { left: Math.max(0, Math.min(at.left - corner.left, Math.max(0, room - 200))), top: at.top - corner.top + line };
}

/**
 * The primer in place on the page: a box that grows with its text, Edit/Preview, Save (the page posts it and offers the
 * Undo) and Cancel; a "/" in the box lists the deck's cards to link; "Write with AI" is folded under the box. Leaving
 * the page with changes asks first.
 */
export function PrimerEditor({
  deckId,
  start,
  cards,
  cardNames,
  onCancel,
  onSave,
}: {
  deckId: number;
  start: string;
  cards?: PrimerCardLinks;
  cardNames: string[];
  onCancel: () => void;
  onSave: (text: string) => Promise<string | null>;
}) {
  const [text, setText] = useState(start);
  const [preview, setPreview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [written, setWritten] = useState<string | null>(null);
  // The "/" list: the word it is for and the card highlighted, where it opens, and where the caret goes after a link.
  const [pick, setPick] = useState<(CardWord & { active: number }) | null>(null);
  const [spot, setSpot] = useState<{ left: number; top: number } | null>(null);
  const [caretAt, setCaretAt] = useState<number | null>(null);
  const box = useRef<HTMLTextAreaElement | null>(null);
  const copy = useRef<HTMLDivElement | null>(null);
  const found = pick === null ? [] : cardMatches(cardNames, pick.query);
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

  // After a card is linked, the caret goes after the link.
  useEffect(() => {
    const el = box.current;
    if (caretAt === null || !el) return;
    el.focus();
    el.setSelectionRange(caretAt, caretAt);
    setCaretAt(null);
  }, [caretAt]);

  // The list opens under the caret's line.
  const caretInBox = pick?.end ?? null;
  useLayoutEffect(() => {
    const el = box.current;
    const mirror = copy.current;
    if (caretInBox === null || !el || !mirror) {
      setSpot(null);
      return;
    }
    setSpot(caretSpot(el, mirror, caretInBox));
  }, [caretInBox, found.length, text]);

  /** Shows the "/" list for where the caret is, or closes it (nothing matches, another word, a new line). */
  function remember(value: string, at: number) {
    const word = cardWord(value, at);
    const matches = word === null ? [] : cardMatches(cardNames, word.query);
    if (word === null || matches.length === 0) {
      setPick(null);
      return;
    }
    setPick((old) => (old && old.start === word.start && old.query === word.query ? { ...word, active: Math.min(old.active, matches.length - 1) } : { ...word, active: 0 }));
  }

  /** Puts [[Name]] where the "/" and what was typed are, and moves the caret after it. */
  function linkCard(name: string) {
    if (pick === null) return;
    const link = `[[${name}]]`;
    setText(text.slice(0, pick.start) + link + text.slice(pick.end));
    setPick(null);
    setCaretAt(pick.start + link.length);
  }

  /** Up/Down pick a card, Enter, Tab or a click links it, Escape closes the list (Enter is no new line while it's open). */
  function boxKeys(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (pick === null || found.length === 0) return;
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      const step = e.key === "ArrowDown" ? 1 : found.length - 1;
      setPick((old) => (old === null ? old : { ...old, active: (old.active + step) % found.length }));
      return;
    }
    if (e.key === "Enter" || e.key === "Tab") {
      e.preventDefault();
      linkCard(found[Math.min(pick.active, found.length - 1)]);
      return;
    }
    if (e.key === "Escape") {
      e.preventDefault();
      setPick(null);
    }
  }

  async function save() {
    setBusy(true);
    setError(null);
    const err = await onSave(text);
    setBusy(false);
    if (err) setError(err);
  }

  /** Switches between Edit and Preview; the "/" list is for typing, so it closes. */
  function show(on: boolean) {
    setPreview(on);
    setPick(null);
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
          <button type="button" className={preview ? "" : "on"} onClick={() => show(false)}>
            Edit
          </button>
          <button type="button" className={preview ? "on" : ""} onClick={() => show(true)}>
            Preview
          </button>
        </div>
        <span className="muted small primer-hint"># heading, - bullet, **bold**, / to link a card</span>
        <span className="muted small primer-count">
          {text.length} of {MAX_PRIMER} characters
        </span>
      </div>
      {preview ? (
        <div className="primer-preview">
          {text.trim() !== "" ? <PrimerText text={text} cards={cards} /> : <p className="muted small">Nothing to preview yet.</p>}
        </div>
      ) : (
        <div className="primer-box">
          <textarea
            ref={box}
            aria-label="Primer"
            rows={12}
            maxLength={MAX_PRIMER}
            value={text}
            onChange={(e) => {
              setText(e.target.value);
              remember(e.target.value, e.target.selectionStart);
            }}
            onSelect={(e) => remember(e.currentTarget.value, e.currentTarget.selectionStart)}
            onKeyDown={boxKeys}
            onBlur={() => setPick(null)}
            placeholder="What the deck does, how to play it, its main lines…"
          />
          {pick !== null && found.length > 0 && (
            <>
              <div className="primer-mirror" ref={copy} aria-hidden="true" />
              <ul className="primer-cardlist" role="listbox" aria-label="Deck cards" style={spot === null ? undefined : { left: spot.left, top: spot.top }}>
                {found.map((name, i) => (
                  <li
                    key={name}
                    role="option"
                    aria-selected={i === pick.active}
                    className={i === pick.active ? "on" : undefined}
                    onMouseDown={(e) => {
                      e.preventDefault();
                      linkCard(name);
                    }}
                  >
                    {name}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
      <WriteWithAi
        deckId={deckId}
        text={text}
        onError={setError}
        onWritten={(value, note) => {
          setText(value);
          setPreview(true);
          setPick(null);
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
  needsKey?: boolean;
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
        setModels({ configured: r.data.configured, models: r.data.models ?? [], defaultModel: r.data.defaultModel ?? DEFAULT_MODEL, needsKey: r.data.needsKey === true });
      } else {
        // The default model still works if only the list is missing; an AI that isn't set up says so on Write.
        setModels({ configured: true, models: [DEFAULT_MODEL], defaultModel: DEFAULT_MODEL, needsKey: false });
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
    const r = await api<{ text?: string; model?: string; left?: number; seconds?: number; outputTokens?: number | null; reasoningTokens?: number | null }>(`/api/decks/${deckId}/aiprimer`, {
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
    const seconds = typeof r.data.seconds === "number" ? Math.round(r.data.seconds * 10) / 10 : null;
    const tokens = typeof r.data.outputTokens === "number" ? r.data.outputTokens : null;
    const thinking = typeof r.data.reasoningTokens === "number" && r.data.reasoningTokens > 0 ? r.data.reasoningTokens : null;
    const count = (n: number) => n.toLocaleString("en-GB");
    const said = [`Written by ${wrote}${seconds === null ? "" : ` in ${seconds} s`}`];
    if (tokens !== null) {
      said.push(`${count(tokens)} tokens${thinking === null ? "" : ` (${count(thinking)} thinking)`}`);
      if (seconds !== null && seconds > 0) said.push(`${count(Math.round(tokens / seconds))} tokens/s`);
    }
    onWritten(r.data.text, `${said.join(" · ")}. Read it, change what you like, then Save.${left != null ? ` ${left} left today.` : ""}`);
  }

  return (
    <details className="primer-ai">
      <summary>Write with AI</summary>
      {models === null ? (
        <p className="muted small primer-ai-body">Loading the models…</p>
      ) : models.needsKey ? (
        <p className="small primer-ai-body">
          To write primers with AI, add your own OpenCode Go or OpenRouter key under <Link href="/settings">Settings → AI key</Link>.
        </p>
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
