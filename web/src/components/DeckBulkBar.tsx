"use client";

import { useEffect, useState } from "react";
import { pickPrinting, type PrintChoice } from "@/lib/bulkprint";
import type { DeckRow, DeckView } from "@/lib/deckshared";
import type { Finish } from "@/lib/pricing";
import { api } from "./api";
import type { Printing } from "./CardSearch";
import { deckAction } from "./DeckEditDialogs";
import { SavePackageDialog } from "./DeckPackages";
import { BulkTagDialog } from "./DeckTags";

const COMMANDER = "Commander";
const MAYBEBOARD = "Maybeboard";

const PRINTING_CHOICES: [PrintChoice, string][] = [
  ["CHEAPEST", "Cheapest"],
  ["NEWEST", "Newest"],
  ["OLDEST", "Oldest"],
  ["OWNED", "One I own"],
];

const FINISH_CHOICES: [Finish, string][] = [
  ["NONFOIL", "Non-foil"],
  ["FOIL", "Foil"],
  ["ETCHED", "Etched"],
];

/** The categories cards can be moved to, Commander and Maybeboard included (the deck dialogs' `targets`). */
function targets(view: DeckView): string[] {
  const names = view.categories.map((c) => c.name);
  for (const n of [COMMANDER, MAYBEBOARD]) if (!names.some((x) => x.toLowerCase() === n.toLowerCase())) names.push(n);
  return names;
}

/** One line as Moxfield writes it: "1 Sol Ring (CMR) 263", with " *F*" or " *E*" for a foil or etched line. */
function moxfieldLine(r: DeckRow): string {
  const finish = r.finish === "ETCHED" ? " *E*" : r.finish === "FOIL" ? " *F*" : "";
  return `${r.quantity} ${r.card.name} (${r.card.setCode.toUpperCase()}) ${r.card.collectorNumber}${finish}`.trimEnd();
}

/**
 * The bar at the bottom of the deck page while lines are selected: move, copies, printing, finish and remove for a
 * local deck, and copying the selected lines as text for every deck. Errors stay in the bar; every control waits while
 * an action runs.
 */
export function DeckBulkBar({
  view,
  rows,
  onDone,
  onClose,
}: {
  view: DeckView;
  rows: DeckRow[];
  onDone: (notice: string) => void | Promise<void>;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [copies, setCopies] = useState("1");
  const [tagOpen, setTagOpen] = useState(false);
  const [packageOpen, setPackageOpen] = useState(false);
  const n = rows.length;
  const uids = rows.map((r) => r.lineUid);
  const wanted = Math.floor(Number(copies));
  const copiesOk = Number.isFinite(wanted) && wanted >= 1 && wanted <= 99;

  useEffect(() => {
    if (!copied) return;
    const t = window.setTimeout(() => setCopied(false), 1600);
    return () => window.clearTimeout(t);
  }, [copied]);

  /** Sends one deck action; shows its error in the bar, else tells the page what happened. */
  async function run(body: Record<string, unknown>, notice: string) {
    setBusy(true);
    setError(null);
    const r = await deckAction(view.id, body);
    if (r.error) {
      setBusy(false);
      setError(r.error);
      return;
    }
    await onDone(notice);
    setBusy(false);
  }

  async function moveTo(category: string) {
    if (!category) return;
    await run({ action: "move", uids, category }, `Moved ${n} card${n === 1 ? "" : "s"} to ${category}.`);
  }

  async function setQuantity() {
    if (!copiesOk) return;
    await run({ action: "bulk-quantity", uids, quantity: wanted }, `Set ${n} line${n === 1 ? "" : "s"} to ${wanted} cop${wanted === 1 ? "y" : "ies"}.`);
  }

  async function setFinish(finish: Finish) {
    await run({ action: "bulk-finish", uids, finish }, `Changed the finish of ${n} card${n === 1 ? "" : "s"}.`);
  }

  async function remove() {
    if (!window.confirm(`Remove ${n} line${n === 1 ? "" : "s"} from the deck?`)) return;
    await run({ action: "remove", uids }, `Removed ${n} line${n === 1 ? "" : "s"} from the deck.`);
  }

  /** Looks the printings up one line after another, then changes them all in one action. */
  async function setPrinting(choice: PrintChoice) {
    setBusy(true);
    setError(null);
    const changes: { uid: string; card: Printing["card"] }[] = [];
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i];
      setProgress(`Finding printings ${i + 1} of ${rows.length}…`);
      const res = await api<{ results?: Printing[] }>(`/api/cards/prints?name=${encodeURIComponent(r.name)}`);
      if (!res.ok) {
        setProgress(null);
        setBusy(false);
        setError(res.data.error ?? "Could not load the printings.");
        return;
      }
      const prints = res.data.results ?? [];
      const owned = new Set(r.held.map((h) => h.scryfallId));
      const current = (r.original ?? r.card).scryfallId;
      const id = pickPrinting(prints, choice, r.finish !== "NONFOIL", owned, current);
      const found = id ? prints.find((p) => p.card.scryfallId === id) : undefined;
      if (found) changes.push({ uid: r.lineUid, card: found.card });
    }
    setProgress(null);
    if (changes.length === 0) {
      setBusy(false);
      setError("Nothing to change.");
      return;
    }
    const r = await deckAction(view.id, { action: "bulk-printing", changes });
    if (r.error) {
      setBusy(false);
      setError(r.error);
      return;
    }
    await onDone(`Changed the printing of ${changes.length} card${changes.length === 1 ? "" : "s"}.`);
    setBusy(false);
  }

  async function copyText() {
    setBusy(true);
    try {
      await navigator.clipboard.writeText(rows.map(moxfieldLine).join("\n"));
      setError(null);
      setCopied(true);
    } catch {
      setError("The browser didn't allow copying.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="bulk-bar deck-bulk-bar" role="region" aria-label="Selected cards">
        <strong>{n} selected</strong>
        {error && <div className="bar error">{error}</div>}
        {progress && <span className="muted small">{progress}</span>}
        {view.local ? (
          <>
            <select aria-label="Move to" disabled={busy} value="" onChange={(e) => e.target.value && void moveTo(e.target.value)}>
              <option value="">Move to…</option>
              {targets(view).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
            <label className="inline small">
              <span className="muted">Copies</span>
              <input aria-label="Copies" type="number" min={1} max={99} value={copies} disabled={busy} onChange={(e) => setCopies(e.target.value)} />
            </label>
            <button className="btn small-btn" disabled={busy || !copiesOk} onClick={setQuantity}>
              Set
            </button>
            <select aria-label="Printing" disabled={busy} value="" onChange={(e) => e.target.value && void setPrinting(e.target.value as PrintChoice)}>
              <option value="">Printing…</option>
              {PRINTING_CHOICES.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
            <select aria-label="Finish" disabled={busy} value="" onChange={(e) => e.target.value && void setFinish(e.target.value as Finish)}>
              <option value="">Finish…</option>
              {FINISH_CHOICES.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
            <button className="btn small-btn danger" disabled={busy} onClick={remove}>
              Remove
            </button>
          </>
        ) : (
          <span className="muted small">Cards of a deck from Archidekt are changed on Archidekt.</span>
        )}
        <button className="btn small-btn" disabled={busy} onClick={() => setTagOpen(true)}>
          Tag…
        </button>
        <button className="btn small-btn" disabled={busy} onClick={() => setPackageOpen(true)}>
          Save as package…
        </button>
        <a
          className="btn small-btn"
          href={`/find?deck=${view.id}&names=${encodeURIComponent([...new Set(rows.map((r) => r.name))].join("|"))}`}
          title="See which binder these cards are in"
        >
          Find
        </a>
        <button className="btn small-btn" disabled={busy} onClick={copyText}>
          {copied ? "Copied" : "Copy as text"}
        </button>
        <span className="spacer" />
        <button className="btn small-btn" disabled={busy} onClick={onClose}>
          Done
        </button>
      </div>
      {tagOpen && <BulkTagDialog names={rows.map((r) => r.name)} onClose={() => setTagOpen(false)} onDone={() => onDone("Tags changed.")} />}
      {packageOpen && (
        <SavePackageDialog
          cards={rows.map((r) => ({ name: r.name, scryfallId: (r.original ?? r.card).scryfallId, quantity: r.quantity }))}
          onClose={() => setPackageOpen(false)}
          onDone={(notice) => onDone(notice)}
        />
      )}
    </>
  );
}
