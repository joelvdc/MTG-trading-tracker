"use client";

import { useMemo, useState } from "react";
import { cardsByType, type CompareCard } from "@/lib/deckcompare";
import { primaryType, type DeckRow } from "@/lib/deckshared";
import { cardsOf, compareSnapshots, defaultSnapshotName, decklistText, MAX_SNAPSHOT_NAME, type SnapCard, type Snapshot } from "@/lib/decksnapshots";
import { api, formatDate } from "./api";
import { deckAction } from "./DeckEditDialogs";
import { Dialog } from "./Dialog";

/** One side of the comparison: its cards and the bracket, win turn and price it had. */
interface Side {
  label: string;
  cards: SnapCard[];
  bracket: number | null;
  winTurn: string | null;
  eur: number | null;
}

/** The comparison's "Now": the deck as it is, with what the page shows for it. */
const NOW = "\u0000now";

/** A snapshot's own line: its date, copies and the bracket, win turn and price it had (missing parts left out). */
function snapshotLine(s: Snapshot, money: (eur: number) => string): string {
  const parts = [formatDate(s.at), `${s.cards.reduce((n, c) => n + c.quantity, 0)} cards`];
  if (s.bracket != null) parts.push(`B${s.bracket}`);
  if (s.winTurn) parts.push(s.winTurn);
  if (s.eur != null) parts.push(money(s.eur));
  return parts.join(" · ");
}

/** Cards grouped by type, as the compare window lists them: a heading with the group's copies, then one line per card. */
function CardGroups({ cards }: { cards: CompareCard[] }) {
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
              </li>
            ))}
          </ul>
        </div>
      ))}
    </>
  );
}

/**
 * The deck's snapshots (the synced Deck.snapshots, app 1.124): the whole decklist with a name and the bracket, win turn
 * and price it had, so it can be compared with another one later or with the deck as it is now. Taking, renaming and
 * deleting them changes the deck; comparing and copying one as a new deck don't.
 */
export function SnapshotsDialog({
  deckId,
  deckName,
  readOnly,
  snapshots,
  rows,
  bracket,
  winTurn,
  eur,
  money,
  onDone,
  onClose,
}: {
  deckId: number;
  deckName: string;
  readOnly: boolean;
  snapshots: Snapshot[];
  /** The deck's lines as they are now: the comparison's "Now" side, and the types the cards are grouped by. */
  rows: DeckRow[];
  /** What the page shows now for the deck: its bracket, its win turn label and its price in euros (null when unknown). */
  bracket: number | null;
  winTurn: string | null;
  eur: number | null;
  money: (eur: number) => string;
  /** Tells the page what happened, so it loads the deck again. */
  onDone: (text: string) => void;
  onClose: () => void;
}) {
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // The snapshot being renamed (with the name typed so far), deleted, or copied as a deck.
  const [renaming, setRenaming] = useState<string | null>(null);
  const [renameText, setRenameText] = useState("");
  const [deleting, setDeleting] = useState<string | null>(null);
  const [copying, setCopying] = useState<string | null>(null);
  // Comparing: false for the list; else which sides are shown ("Now" or a snapshot's id).
  const [comparing, setComparing] = useState(false);
  const [from, setFrom] = useState(snapshots[0]?.id ?? NOW);
  const [to, setTo] = useState(NOW);

  /** The cards' types, by name, from the deck as it is now; a card that left the deck gets "Other". */
  const typesOf = useMemo(() => new Map(rows.map((r) => [r.name.toLowerCase(), primaryType(r.types)])), [rows]);
  /** A card list as the comparison counts it, in the deck's current types. */
  const asCompare = (cards: { name: string; quantity: number }[]): CompareCard[] =>
    cards.map((c) => ({ name: c.name, quantity: c.quantity, type: typesOf.get(c.name.toLowerCase()) ?? "Other", owned: 0 }));

  const side = (key: string): Side => {
    const s = snapshots.find((x) => x.id === key) ?? null;
    if (!s) return { label: "Now", cards: cardsOf(rows), bracket, winTurn, eur };
    return { label: s.name, cards: s.cards, bracket: s.bracket, winTurn: s.winTurn, eur: s.eur };
  };
  const before = side(from);
  const after = side(to);
  const change = compareSnapshots(before.cards, after.cards);
  const added = Object.entries(change.added).map(([n, q]) => ({ name: n, quantity: q }));
  const removed = Object.entries(change.removed).map(([n, q]) => ({ name: n, quantity: q }));

  /** Takes a snapshot of the deck as it is now, under the name typed (today's date when none is). */
  async function take() {
    setBusy(true);
    setError(null);
    const r = await deckAction(deckId, { action: "snapshot-take", name, bracket, winTurn, eur });
    setBusy(false);
    if (r.error) return setError(r.error);
    setName("");
    onDone("Snapshot taken.");
  }

  /** Gives a snapshot another name. */
  async function rename(snapId: string) {
    setBusy(true);
    setError(null);
    const r = await deckAction(deckId, { action: "snapshot-rename", snapId, name: renameText });
    setBusy(false);
    if (r.error) return setError(r.error);
    setRenaming(null);
    onDone("Snapshot renamed.");
  }

  /** Drops a snapshot. */
  async function remove(snapId: string) {
    setBusy(true);
    setError(null);
    const r = await deckAction(deckId, { action: "snapshot-delete", snapId });
    setBusy(false);
    if (r.error) return setError(r.error);
    setDeleting(null);
    onDone("Snapshot deleted.");
  }

  /** A new local deck from the snapshot's decklist, opened the way a deck made from a pasted list is. */
  async function copyAsDeck(s: Snapshot) {
    setCopying(s.id);
    setError(null);
    const r = await api<{ id?: number }>("/api/decks", {
      action: "from-text",
      name: `${deckName} – ${s.name}`,
      text: decklistText(s),
      useTags: false,
      commander: s.cards.find((c) => c.commander)?.name ?? null,
    });
    if (!r.ok || typeof r.data.id !== "number") {
      setCopying(null);
      return setError(r.data.error ?? "That did not work.");
    }
    window.location.assign(`/decks/${r.data.id}`);
  }

  /** A side's bracket, win turn and price, as the table's three cells. */
  const cells = (s: Side) => [s.bracket != null ? String(s.bracket) : "–", s.winTurn ?? "–", s.eur != null ? money(s.eur) : "–"];

  return (
    <Dialog title="Snapshots" onClose={onClose}>
      <div className="stack">
        {!readOnly && (
          <div className="inline">
            <input
              aria-label="Snapshot name"
              placeholder={defaultSnapshotName(Date.now())}
              maxLength={MAX_SNAPSHOT_NAME}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <button className="btn" disabled={busy} onClick={take}>
              Take snapshot
            </button>
          </div>
        )}
        {error && <div className="bar error">{error}</div>}
        {comparing ? (
          <div className="stack">
            <div className="inline">
              <label className="stack tight">
                <span className="muted small">From</span>
                <select aria-label="From" value={from} onChange={(e) => setFrom(e.target.value)}>
                  {snapshots.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="stack tight">
                <span className="muted small">To</span>
                <select aria-label="To" value={to} onChange={(e) => setTo(e.target.value)}>
                  <option value={NOW}>Now</option>
                  {snapshots.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <table className="odds-table">
              <thead>
                <tr>
                  <th />
                  <th>{before.label}</th>
                  <th>{after.label}</th>
                </tr>
              </thead>
              <tbody>
                {["Bracket", "Win turn", "Price"].map((label, i) => (
                  <tr key={label}>
                    <th>{label}</th>
                    <td>{cells(before)[i]}</td>
                    <td>{cells(after)[i]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {added.length > 0 && (
              <>
                <h3 className="mini-head">Added</h3>
                <CardGroups cards={asCompare(added)} />
              </>
            )}
            {removed.length > 0 && (
              <>
                <h3 className="mini-head">Removed</h3>
                <CardGroups cards={asCompare(removed)} />
              </>
            )}
            <div className="dialog-actions">
              <button type="button" className="btn" onClick={() => setComparing(false)}>
                Back
              </button>
            </div>
          </div>
        ) : (
          <ul className="plain-list history-list">
            {snapshots.map((s) => (
              <li key={s.id} className="stack tight">
                <div className="inline">
                  <strong className="grow">{s.name}</strong>
                  <span className="muted small">{snapshotLine(s, money)}</span>
                </div>
                {renaming === s.id ? (
                  <div className="inline">
                    <input aria-label="Snapshot name" maxLength={MAX_SNAPSHOT_NAME} value={renameText} onChange={(e) => setRenameText(e.target.value)} />
                    <button className="btn" disabled={busy} onClick={() => rename(s.id)}>
                      Save
                    </button>
                    <button className="btn" disabled={busy} onClick={() => setRenaming(null)}>
                      Cancel
                    </button>
                  </div>
                ) : (
                  <div className="inline">
                    <button
                      className="btn small-btn"
                      onClick={() => {
                        setFrom(s.id);
                        setTo(NOW);
                        setComparing(true);
                      }}
                    >
                      Compare
                    </button>
                    {!readOnly && (
                      <>
                        <button
                          className="btn small-btn"
                          onClick={() => {
                            setRenaming(s.id);
                            setRenameText(s.name);
                          }}
                        >
                          Rename
                        </button>
                        <button className="btn small-btn" disabled={copying !== null} onClick={() => copyAsDeck(s)}>
                          Copy as deck
                        </button>
                        {deleting === s.id ? (
                          <>
                            <span className="small">Delete?</span>
                            <button className="btn small-btn danger-text" disabled={busy} onClick={() => remove(s.id)}>
                              Yes
                            </button>
                            <button className="btn small-btn" disabled={busy} onClick={() => setDeleting(null)}>
                              No
                            </button>
                          </>
                        ) : (
                          <button className="btn small-btn danger-text" onClick={() => setDeleting(s.id)}>
                            Delete
                          </button>
                        )}
                      </>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </Dialog>
  );
}
