"use client";

import { useCallback, useEffect, useState } from "react";
import { MAX_PACKAGE_NAME, type DeckPackage } from "@/lib/deckpackages";
import type { DeckView } from "@/lib/deckshared";
import { api } from "./api";
import { ConfirmDialog, deckAction } from "./DeckEditDialogs";
import { Dialog } from "./Dialog";

type PackageUse = Record<string, { full: number[]; partly: number[] }>;
type PrefsAnswer = { packages?: DeckPackage[]; use?: PackageUse };

/** How many copies a package holds. */
const copiesOf = (pkg: DeckPackage) => pkg.cards.reduce((n, card) => n + card.quantity, 0);

/** Select mode's "Save as package…": the ticked lines become a package for any local deck. */
export function SavePackageDialog({ cards, onClose, onDone }: { cards: { name: string; scryfallId: string; quantity: number }[]; onClose: () => void; onDone: (notice: string) => void | Promise<void> }) {
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const n = cards.length;
  return (
    <Dialog title="Save as a package" onClose={() => !busy && onClose()}>
      <div className="stack">
        <p className="muted small">
          {n} card{n === 1 ? "" : "s"} of this deck become a package: a group you can add to a local deck later, here or in the app.
        </p>
        <input autoFocus aria-label="Package name" placeholder="Package name" maxLength={MAX_PACKAGE_NAME} value={name} onChange={(e) => setName(e.target.value)} />
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={busy || !name.trim()}
            onClick={async () => {
              setBusy(true);
              setError(null);
              const r = await api<PrefsAnswer>("/api/deckprefs", { action: "save-package", name: name.trim(), cards });
              setBusy(false);
              if (!r.ok) {
                setError(r.data.error ?? "That did not work.");
                return;
              }
              await onDone(`Package "${name.trim()}" saved.`);
              onClose();
            }}
          >
            Save
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/**
 * The saved card packages (the synced pref deckPackages): what each holds and how many decks use it, adding it to or
 * removing it from a local deck (with Undo), renaming and deleting it, and a new one from pasted text.
 */
export function PackagesDialog({ view, onClose, onChanged }: { view: DeckView; onClose: () => void; onChanged: (notice: string | null) => void | Promise<void> }) {
  const [packages, setPackages] = useState<DeckPackage[] | null>(null);
  const [useMap, setUseMap] = useState<PackageUse>({});
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  /** The id of the package whose action is running ("new" for the text box). */
  const [busy, setBusy] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [deleting, setDeleting] = useState<DeckPackage | null>(null);
  const [fromName, setFromName] = useState("");
  const [fromText, setFromText] = useState("");
  const [notFound, setNotFound] = useState<string[]>([]);
  const editable = view.local && !view.readOnly;

  const refresh = useCallback(async () => {
    const r = await api<PrefsAnswer>("/api/deckprefs");
    if (r.ok) {
      setPackages(r.data.packages ?? []);
      setUseMap(r.data.use ?? {});
    } else {
      setError(r.data.error ?? "Could not load your packages.");
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  /** Adds a package's cards the deck lacks (local decks); the deck page reloads and the Undo bar offers the way back. */
  async function addToDeck(pkg: DeckPackage) {
    setBusy(pkg.id);
    setError(null);
    setMessage(null);
    const r = await deckAction(view.id, { action: "add-package", packageId: pkg.id });
    setBusy(null);
    if (r.error) {
      setError(r.error);
      return;
    }
    const added = r.added ?? 0;
    const already = (r.skipped ?? []).length;
    setMessage(`${added} card${added === 1 ? "" : "s"} added, ${already} already in the deck.`);
    await onChanged(null);
    await refresh();
  }

  /** Removes the package's main-deck lines (local decks), all in one Undo. */
  async function removeFromDeck(pkg: DeckPackage) {
    setBusy(pkg.id);
    setError(null);
    setMessage(null);
    const r = await deckAction(view.id, { action: "remove-package", packageId: pkg.id });
    setBusy(null);
    if (r.error) {
      setError(r.error);
      return;
    }
    setMessage(`${r.removed ?? 0} card${(r.removed ?? 0) === 1 ? "" : "s"} removed.`);
    await onChanged(null);
    await refresh();
  }

  async function rename(pkg: DeckPackage) {
    const name = newName.trim();
    if (!name) return;
    setBusy(pkg.id);
    setError(null);
    const r = await api<PrefsAnswer>("/api/deckprefs", { action: "rename-package", id: pkg.id, name });
    setBusy(null);
    if (!r.ok) {
      setError(r.data.error ?? "That did not work.");
      return;
    }
    setRenaming(null);
    await refresh();
  }

  async function makeFromText() {
    setBusy("new");
    setError(null);
    setMessage(null);
    setNotFound([]);
    const r = await api<{ notFound?: string[] }>("/api/deckprefs", { action: "package-from-text", name: fromName.trim(), text: fromText });
    setBusy(null);
    if (!r.ok) {
      setError(r.data.error ?? "That did not work.");
      return;
    }
    setFromName("");
    setFromText("");
    setNotFound(r.data.notFound ?? []);
    await refresh();
  }

  return (
    <Dialog title="Packages" onClose={() => !busy && onClose()}>
      <div className="stack">
        <p className="muted small">
          Saved groups of cards, kept with your synced data and shown on the phones. Add one to a local deck here, any time.
        </p>
        {message && <div className="bar ok">{message}</div>}
        {error && <div className="bar error">{error}</div>}
        {packages === null && !error ? (
          <p className="muted">Loading…</p>
        ) : packages && packages.length === 0 ? (
          <p className="muted small">No packages yet. Select cards on a deck and use &quot;Save as package…&quot;, or paste a list below.</p>
        ) : (
          <ul className="plain-list package-list">
            {packages?.map((pkg) => {
              const used = useMap[pkg.id] ?? { full: [], partly: [] };
              return (
                <li key={pkg.id} className="stack tight">
                  <div className="inline">
                    <strong className="grow">{pkg.name}</strong>
                    <span className="muted small">
                      {copiesOf(pkg)} card{copiesOf(pkg) === 1 ? "" : "s"}
                    </span>
                  </div>
                  <p className="muted small">
                    Used in {used.full.length} deck{used.full.length === 1 ? "" : "s"} fully, {used.partly.length} partly
                  </p>
                  <details className="package-cards">
                    <summary className="small">
                      {pkg.cards.length} card{pkg.cards.length === 1 ? "" : "s"} in it
                    </summary>
                    <ul className="plain-list small">
                      {pkg.cards.map((card, i) => (
                        <li key={`${card.scryfallId}-${i}`}>
                          {card.quantity}× {card.name}
                        </li>
                      ))}
                    </ul>
                  </details>
                  <div className="inline wrap">
                    {editable && (
                      <>
                        <button type="button" className="btn small-btn" disabled={busy !== null} onClick={() => void addToDeck(pkg)}>
                          Add to this deck
                        </button>
                        <button type="button" className="btn small-btn" disabled={busy !== null} onClick={() => void removeFromDeck(pkg)}>
                          Remove from this deck
                        </button>
                      </>
                    )}
                    <button type="button" className="link small" disabled={busy !== null} onClick={() => (setRenaming(pkg.id), setNewName(pkg.name))}>
                      Rename
                    </button>
                    <button type="button" className="link small" disabled={busy !== null} onClick={() => setDeleting(pkg)}>
                      Delete
                    </button>
                  </div>
                  {renaming === pkg.id && (
                    <div className="inline">
                      <input aria-label="New package name" maxLength={MAX_PACKAGE_NAME} value={newName} onChange={(e) => setNewName(e.target.value)} />
                      <button type="button" className="btn small-btn" disabled={busy !== null || !newName.trim() || newName.trim() === pkg.name} onClick={() => void rename(pkg)}>
                        Save
                      </button>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
        <div className="stack package-new">
          <strong className="small">New package from text…</strong>
          <input aria-label="New package name" placeholder="Package name" maxLength={MAX_PACKAGE_NAME} value={fromName} onChange={(e) => setFromName(e.target.value)} />
          <textarea
            aria-label="The package's cards"
            rows={4}
            value={fromText}
            onChange={(e) => setFromText(e.target.value)}
            placeholder={"One card a line, as in a decklist: 1 Sol Ring…"}
          />
          <div className="inline">
            <button type="button" className="btn small-btn" disabled={busy !== null || !fromName.trim() || !fromText.trim()} onClick={() => void makeFromText()}>
              {busy === "new" ? "Looking the cards up…" : "Save package"}
            </button>
          </div>
          {notFound.length > 0 && <p className="warn-text small">Not found: {notFound.join(", ")}</p>}
        </div>
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" disabled={busy !== null} onClick={onClose}>
            Close
          </button>
        </div>
      </div>
      {deleting && (
        <ConfirmDialog
          title="Delete the package?"
          text={`"${deleting.name}" is deleted for every deck and phone. The decks themselves keep their cards.`}
          button="Delete"
          danger
          onClose={() => setDeleting(null)}
          onYes={async () => {
            const r = await api<PrefsAnswer>("/api/deckprefs", { action: "delete-package", id: deleting.id });
            if (!r.ok) return r.data.error ?? "That did not work.";
            setDeleting(null);
            await refresh();
            return null;
          }}
        />
      )}
    </Dialog>
  );
}
