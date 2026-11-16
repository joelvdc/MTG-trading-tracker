"use client";

import { useEffect, useId, useState } from "react";
import { nameKey } from "@/lib/decknotes";
import { normaliseTags, ROLES } from "@/lib/deckroles";
import { api } from "./api";
import { Dialog } from "./Dialog";

type TagsAnswer = { cardTags?: Record<string, string[]> };

/** The role names, then every tag the account has on any card, for the tag boxes' suggestions. */
export function useTagChoices(): string[] {
  const [choices, setChoices] = useState<string[]>([...ROLES]);
  useEffect(() => {
    let live = true;
    api<TagsAnswer>("/api/deckprefs").then((r) => {
      if (!live || !r.ok) return;
      const out: string[] = [...ROLES];
      for (const tag of Object.values(r.data.cardTags ?? {}).flat()) {
        if (!out.some((t) => t.toLowerCase() === tag.toLowerCase())) out.push(tag);
      }
      setChoices(out);
    });
    return () => {
      live = false;
    };
  }, []);
  return choices;
}

/** A text box that suggests [choices] as you type; Enter takes it too. */
function TagInput({
  value,
  onChange,
  onEnter,
  choices,
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  onEnter: () => void;
  choices: string[];
  label: string;
}) {
  const id = useId();
  return (
    <>
      <input
        aria-label={label}
        list={id}
        value={value}
        maxLength={30}
        placeholder="Tag"
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key !== "Enter") return;
          e.preventDefault();
          onEnter();
        }}
      />
      <datalist id={id}>
        {choices.map((tag) => (
          <option key={tag} value={tag} />
        ))}
      </datalist>
    </>
  );
}

/**
 * A card's own tags in its card window (the synced pref cardTags, which apply in every deck): removable chips, or its
 * automatic role tags in a lighter style when it has none. Save posts set-tags, then reloads the deck view.
 */
export function CardTagEditor({ name, autoTags, onChanged }: { name: string; autoTags: string[]; onChanged: () => void | Promise<void> }) {
  const [saved, setSaved] = useState<string[] | null>(null);
  const [draft, setDraft] = useState<string[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const choices = useTagChoices();

  useEffect(() => {
    let live = true;
    api<TagsAnswer>("/api/deckprefs").then((r) => {
      if (!live) return;
      const mine = r.ok ? ((r.data.cardTags ?? {})[nameKey(name)] ?? []) : [];
      setSaved(mine);
      setDraft(mine);
      if (!r.ok) setError(r.data.error ?? "Could not load your tags.");
    });
    return () => {
      live = false;
    };
  }, [name]);

  function add() {
    const tag = text.trim();
    if (!tag) return;
    setDraft((old) => normaliseTags([...old, tag]));
    setText("");
    setMessage(null);
  }

  async function save() {
    setBusy(true);
    setError(null);
    setMessage(null);
    const r = await api<TagsAnswer>("/api/deckprefs", { action: "set-tags", name, tags: draft });
    setBusy(false);
    if (!r.ok) {
      setError(r.data.error ?? "That did not work.");
      return;
    }
    const mine = (r.data.cardTags ?? {})[nameKey(name)] ?? [];
    setSaved(mine);
    setDraft(mine);
    setMessage(mine.length > 0 ? "Tags saved." : "Tags cleared.");
    await onChanged();
  }

  const changed = saved !== null && (draft.length !== saved.length || draft.some((t, i) => t !== saved[i]));
  return (
    <section>
      <h3 className="mini-head">Tags</h3>
      <div className="chips">
        {saved === null ? (
          <span className="muted small">Loading…</span>
        ) : draft.length > 0 ? (
          draft.map((tag) => (
            <button
              key={tag}
              type="button"
              className="chip own-tag"
              title="Remove this tag"
              disabled={busy}
              onClick={() => {
                setDraft((old) => old.filter((t) => t.toLowerCase() !== tag.toLowerCase()));
                setMessage(null);
              }}
            >
              {tag} <span aria-hidden="true">×</span>
            </button>
          ))
        ) : (
          <>
            {autoTags.map((tag) => (
              <span key={tag} className="tag auto-tag">
                {tag}
              </span>
            ))}
            <span className="muted small">Automatic: tag it yourself to change</span>
          </>
        )}
      </div>
      <div className="inline">
        <TagInput value={text} onChange={setText} onEnter={add} choices={choices} label={`Add a tag to ${name}`} />
        <button type="button" className="btn small-btn" disabled={busy || !text.trim()} onClick={add}>
          Add
        </button>
        <button type="button" className="btn small-btn" disabled={busy || !changed} onClick={save}>
          Save
        </button>
        {message && <span className="ok-text small">{message}</span>}
      </div>
      <p className="muted small">Your tags apply in every deck.</p>
      {error && <div className="bar error">{error}</div>}
    </section>
  );
}

/** "Tag…" in select mode: one tag added to, or removed from, every ticked card. */
export function BulkTagDialog({ names, onClose, onDone }: { names: string[]; onClose: () => void; onDone: () => void | Promise<void> }) {
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const choices = useTagChoices();
  const n = names.length;

  async function send(add: boolean) {
    const tag = text.trim();
    if (!tag) {
      setError("Give a tag first.");
      return;
    }
    setBusy(true);
    setError(null);
    const r = await api("/api/deckprefs", add ? { action: "bulk-tags", names, add: tag } : { action: "bulk-tags", names, remove: tag });
    setBusy(false);
    if (!r.ok) {
      setError(r.data.error ?? "That did not work.");
      return;
    }
    await onDone();
    onClose();
  }

  return (
    <Dialog title={`Tag ${n} card${n === 1 ? "" : "s"}`} onClose={() => !busy && onClose()}>
      <div className="stack">
        <p className="muted small">
          One tag on {n === 1 ? "this card" : "these cards"}, in every deck you play {n === 1 ? "it" : "them"} in.
        </p>
        <TagInput value={text} onChange={setText} onEnter={() => void send(true)} choices={choices} label="Tag" />
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn" disabled={busy || !text.trim()} onClick={() => void send(false)}>
            Remove tag
          </button>
          <button type="button" className="btn primary" disabled={busy || !text.trim()} onClick={() => void send(true)}>
            Add tag
          </button>
        </div>
      </div>
    </Dialog>
  );
}
