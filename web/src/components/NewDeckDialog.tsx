"use client";

import { useEffect, useState } from "react";
import type { PreconInfo } from "@/lib/precons";
import { api } from "./api";
import { Dialog } from "./Dialog";

type Kind = "TEXT" | "PRECON" | "EMPTY";

interface Preview {
  cards: number;
  lines: number;
  exact: number;
  byName: number;
  notFound: string[];
  unreadable: string[];
  commanders: string[];
  hasTags: boolean;
  names: string[];
}

/** A new local deck (the app's "New deck" sheet): from a pasted decklist, a preconstructed deck, or empty. */
export function NewDeckDialog({ onClose }: { onClose: () => void }) {
  const [kind, setKind] = useState<Kind>("TEXT");
  const [name, setName] = useState("");
  const [text, setText] = useState("");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [useTags, setUseTags] = useState(true);
  const [commander, setCommander] = useState<string>("");
  const [query, setQuery] = useState("");
  const [precons, setPrecons] = useState<{ decks: PreconInfo[]; more: number } | null>(null);
  const [precon, setPrecon] = useState<PreconInfo | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (kind !== "PRECON") return;
    const t = setTimeout(async () => {
      const r = await api<{ decks?: PreconInfo[]; more?: number }>(`/api/precons?q=${encodeURIComponent(query)}`);
      if (r.ok && r.data.decks) setPrecons({ decks: r.data.decks, more: r.data.more ?? 0 });
      else setError(r.data.error ?? "Could not get MTGJSON's list of decks.");
    }, 250);
    return () => clearTimeout(t);
  }, [kind, query]);

  async function look() {
    setBusy("Looking the cards up on Scryfall…");
    setError(null);
    const r = await api<Preview>("/api/decks", { action: "text-preview", text });
    setBusy(null);
    if (r.ok && Array.isArray(r.data.names)) {
      setPreview(r.data);
      setCommander(r.data.commanders.length === 1 ? r.data.commanders[0] : "");
    } else setError(r.data.error ?? "That did not work.");
  }

  async function make() {
    setError(null);
    const body =
      kind === "EMPTY"
        ? { action: "new", name }
        : kind === "TEXT"
          ? { action: "from-text", name, text, useTags, commander: preview && preview.commanders.length !== 1 && commander ? commander : null }
          : { action: "from-precon", name, fileName: precon?.fileName };
    setBusy(kind === "EMPTY" ? "Making the deck…" : "Making the deck (looking the cards up on Scryfall)…");
    const r = await api<{ id?: number }>("/api/decks", body);
    if (r.ok && typeof r.data.id === "number") window.location.assign(`/decks/${r.data.id}`);
    else {
      setBusy(null);
      setError(r.data.error ?? "That did not work.");
    }
  }

  const ready = kind === "EMPTY" ? name.trim() !== "" : kind === "TEXT" ? preview !== null && name.trim() !== "" && preview.names.length > 0 : precon !== null;

  return (
    <Dialog title="New local deck" onClose={() => !busy && onClose()}>
      <div className="stack">
        <div className="seg text" role="group" aria-label="Start from">
          {(
            [
              ["TEXT", "Paste a list"],
              ["PRECON", "A precon"],
              ["EMPTY", "Empty"],
            ] as const
          ).map(([k, l]) => (
            <button key={k} className={kind === k ? "on" : ""} onClick={() => (setKind(k), setError(null))} disabled={!!busy}>
              {l}
            </button>
          ))}
        </div>
        {kind === "TEXT" && (
          <>
            <textarea
              aria-label="Decklist"
              rows={8}
              placeholder={"1 Sol Ring\n1x Arcane Signet (cmr) 297 [Ramp]\n…\n(Moxfield, Archidekt and MTG Arena exports work, with headings such as Commander or Sideboard)"}
              value={text}
              onChange={(e) => (setText(e.target.value), setPreview(null))}
            />
            {!preview && (
              <div className="inline">
                <button className="btn" disabled={!!busy || !text.trim()} onClick={look}>
                  Look up the cards
                </button>
              </div>
            )}
            {preview && (
              <div className="stack small">
                <p>
                  {preview.cards} cards in {preview.lines} lines: {preview.exact} found by set and number, {preview.byName} by name
                  {preview.notFound.length > 0 ? `, ${preview.notFound.length} not found` : ""}.
                </p>
                {preview.notFound.length > 0 && <p className="warn-text new-deck-preview">Not found (left out): {preview.notFound.join(", ")}</p>}
                {preview.unreadable.length > 0 && <p className="muted new-deck-preview">Lines that aren&apos;t cards: {preview.unreadable.join(" · ")}</p>}
                {preview.commanders.length === 1 ? (
                  <p>Commander: {preview.commanders[0]}</p>
                ) : (
                  <label className="stack">
                    <span className="muted">{preview.commanders.length > 1 ? `Commanders: ${preview.commanders.join(" & ")}. Or pick one:` : "Commander (the list doesn't say)"}</span>
                    <select value={commander} onChange={(e) => setCommander(e.target.value)}>
                      <option value="">{preview.commanders.length > 1 ? "Keep both" : "None"}</option>
                      {preview.names.map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                {preview.hasTags && (
                  <label className="check">
                    <input type="checkbox" checked={useTags} onChange={(e) => setUseTags(e.target.checked)} />
                    <span>Use the list&apos;s tags as categories (else each card&apos;s auto category)</span>
                  </label>
                )}
              </div>
            )}
          </>
        )}
        {kind === "PRECON" && (
          <>
            <input type="search" aria-label="Search precons" placeholder="Search by name or set code" value={query} onChange={(e) => setQuery(e.target.value)} />
            <ul className="plain-list precon-list">
              {precons?.decks.map((d) => (
                <li key={d.fileName}>
                  <label className="check small">
                    <input type="radio" name="precon" checked={precon?.fileName === d.fileName} onChange={() => (setPrecon(d), setName((n) => n || d.name))} />
                    <span>
                      {d.name} <span className="muted">· {d.code} {d.releaseDate?.slice(0, 4)}</span>
                    </span>
                  </label>
                </li>
              ))}
              {precons && precons.more > 0 && <li className="muted small">…and {precons.more} more: search to narrow down.</li>}
            </ul>
            <p className="muted small">The whole deck from MTGJSON, basic lands too, each card in its auto category (Ramp, Draw, Removal…). Adding its cards to the collection is done on the Orders page (Add a precon).</p>
          </>
        )}
        <label className="stack">
          <span className="muted small">Deck name</span>
          <input aria-label="Deck name" value={name} maxLength={200} placeholder={kind === "PRECON" ? "The precon's name" : "New deck"} onChange={(e) => setName(e.target.value)} />
        </label>
        {busy && <p className="muted small">{busy}</p>}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={!!busy}>
            Cancel
          </button>
          <button className="btn primary" disabled={!!busy || !ready} onClick={make}>
            Make the deck
          </button>
        </div>
      </div>
    </Dialog>
  );
}
