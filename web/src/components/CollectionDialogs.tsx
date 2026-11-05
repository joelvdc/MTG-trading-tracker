"use client";

import { useState } from "react";
import { Dialog } from "@/components/Dialog";
import type { Paid } from "@/lib/collection";
import { BAR_UNSORTED, BAR_WISHLIST, type CollBinder } from "@/lib/collview";
import { SORT_FIELDS, type SortField, type SortLevel } from "@/lib/colquery";

const PRESETS: [string, SortLevel[]][] = [
  ["Name", [{ field: "NAME", reversed: false }]],
  ["Color, then name", [{ field: "COLOR", reversed: false }, { field: "NAME", reversed: false }]],
  ["Set and number", [{ field: "NUMBER", reversed: false }]],
  ["Type, mana value, name", [{ field: "TYPE", reversed: false }, { field: "MANA_VALUE", reversed: false }, { field: "NAME", reversed: false }]],
  ["Most valuable", [{ field: "VALUE", reversed: false }]],
  ["Newest", [{ field: "RECENT", reversed: false }]],
  ["Rising lately", [{ field: "CHANGE_PCT", reversed: false }]],
  ["Falling lately", [{ field: "CHANGE_PCT", reversed: true }]],
];

/** "Name (A to Z), then set (A to Z)". */
export function sortText(levels: SortLevel[]): string {
  return levels
    .map((l) => {
      const f = SORT_FIELDS.find((x) => x.field === l.field) ?? SORT_FIELDS[0];
      return `${f.label.toLowerCase()} (${(l.reversed ? f.backward : f.forward).toLowerCase()})`;
    })
    .join(", then ");
}

/** Sorting in layers ("Color, then name"), each with its own direction, up to 3 (the app's SortDialog). */
export function SortDialog({ initial, onSave, onClose }: { initial: SortLevel[]; onSave: (levels: SortLevel[]) => void; onClose: () => void }) {
  const [levels, setLevels] = useState<SortLevel[]>(initial.length ? initial : [{ field: "NAME", reversed: false }]);
  const set = (i: number, l: SortLevel) => setLevels(levels.map((x, j) => (j === i ? l : x)));
  const same = (a: SortLevel[], b: SortLevel[]) => JSON.stringify(a) === JSON.stringify(b);
  return (
    <Dialog title="Sort" onClose={onClose}>
      <div className="stack">
        <div className="chips">
          {PRESETS.map(([label, spec]) => (
            <button key={label} type="button" className={same(levels, spec) ? "chip on" : "chip"} onClick={() => setLevels(spec)}>
              {label}
            </button>
          ))}
        </div>
        {levels.map((l, i) => {
          const f = SORT_FIELDS.find((x) => x.field === l.field) ?? SORT_FIELDS[0];
          return (
            <div key={i} className="sort-level">
              <span className="muted small">{i === 0 ? "Sort by" : "then by"}</span>
              <div className="inline">
                <select
                  aria-label={`Level ${i + 1}: field`}
                  value={l.field}
                  onChange={(e) => set(i, { field: e.target.value as SortField, reversed: false })}
                >
                  {SORT_FIELDS.filter((x) => x.field === l.field || !levels.some((y) => y.field === x.field)).map((x) => (
                    <option key={x.field} value={x.field}>
                      {x.label}
                    </option>
                  ))}
                </select>
                <select aria-label={`Level ${i + 1}: order`} value={l.reversed ? "r" : "f"} onChange={(e) => set(i, { ...l, reversed: e.target.value === "r" })}>
                  <option value="f">{f.forward}</option>
                  <option value="r">{f.backward}</option>
                </select>
                {levels.length > 1 && (
                  <button type="button" className="link" aria-label={`Remove level ${i + 1}`} onClick={() => setLevels(levels.filter((_, j) => j !== i))}>
                    Remove
                  </button>
                )}
              </div>
            </div>
          );
        })}
        {levels.length < 3 && (
          <div>
            <button
              type="button"
              className="link"
              onClick={() => {
                const next = SORT_FIELDS.find((x) => !levels.some((y) => y.field === x.field));
                if (next) setLevels([...levels, { field: next.field, reversed: false }]);
              }}
            >
              ＋ Add a level
            </button>
          </div>
        )}
        {levels.some((l) => l.field === "CHANGE_PCT" || l.field === "CHANGE_MONEY") && (
          <p className="muted small">
            The change is Cardmarket&apos;s trend price against its 30-day average, for one copy. Cards without a 30-day average go last.
          </p>
        )}
        <p className="muted small">Color, type and mana value need Scryfall&apos;s card details; cards still without them go last.</p>
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn primary" onClick={() => onSave(levels)}>
            Sort
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** Moves every card of a binder (or Unsorted) into another, optionally deleting the binder afterwards. */
export function MergeDialog({
  from,
  fromName,
  copies,
  binders,
  busy,
  onSave,
  onClose,
}: {
  /** The binder's uid; null for Unsorted. */
  from: string | null;
  fromName: string;
  copies: number;
  binders: CollBinder[];
  busy: boolean;
  onSave: (into: string | null, deleteSource: boolean) => void;
  onClose: () => void;
}) {
  const choices = [{ uid: "", name: "Unsorted" }, ...binders].filter((b) => (b.uid || null) !== from);
  const [into, setInto] = useState(choices[0]?.uid ?? "");
  const [deleteSource, setDeleteSource] = useState(from !== null);
  return (
    <Dialog title={from === null ? "Move all Unsorted cards" : `Merge “${fromName}”`} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          onSave(into || null, from !== null && deleteSource);
        }}
      >
        <p>
          Moves all {copies} card{copies === 1 ? "" : "s"} {from === null ? "that aren't in a binder" : `in “${fromName}”`} into the binder you pick. The
          same card in the same condition, language and finish becomes one pile.
        </p>
        <label className="field">
          <span>Into</span>
          <select value={into} onChange={(e) => setInto(e.target.value)}>
            {choices.map((b) => (
              <option key={b.uid || "unsorted"} value={b.uid}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        {from !== null && (
          <label className="check">
            <input type="checkbox" checked={deleteSource} onChange={(e) => setDeleteSource(e.target.checked)} />
            <span>Delete “{fromName}” afterwards</span>
          </label>
        )}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={busy || choices.length === 0}>
            {from === null ? "Move" : "Merge"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

/** Deletes a binder: its cards go to Unsorted, or out of the collection too. */
export function DeleteBinderDialog({
  name,
  copies,
  busy,
  onSave,
  onClose,
}: {
  name: string;
  copies: number;
  busy: boolean;
  onSave: (deleteCards: boolean) => void;
  onClose: () => void;
}) {
  const [deleteCards, setDeleteCards] = useState(false);
  return (
    <Dialog title={`Delete “${name}”?`} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          onSave(deleteCards);
        }}
      >
        {copies > 0 ? (
          <div className="stack tight">
            <label className="check">
              <input type="radio" name="cards" checked={!deleteCards} onChange={() => setDeleteCards(false)} />
              <span>
                Keep its {copies} card{copies === 1 ? "" : "s"}: they go to Unsorted
              </span>
            </label>
            <label className="check">
              <input type="radio" name="cards" checked={deleteCards} onChange={() => setDeleteCards(true)} />
              <span>
                Delete its {copies} card{copies === 1 ? "" : "s"} from the collection too
              </span>
            </label>
          </div>
        ) : (
          <p>The binder is empty.</p>
        )}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn danger" disabled={busy}>
            Delete binder{deleteCards ? " and cards" : ""}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

/** "Arrange binders": the binder bar's order, as in the app (synced, so the phones show it too). */
export function ArrangeDialog({
  keys,
  usual,
  binders,
  busy,
  onSave,
  onClose,
}: {
  keys: string[];
  /** The usual order (nothing arranged). */
  usual: string[];
  binders: CollBinder[];
  busy: boolean;
  onSave: (keys: string[]) => void;
  onClose: () => void;
}) {
  const [order, setOrder] = useState(keys);
  const name = (k: string) =>
    k === BAR_UNSORTED ? "Unsorted" : k === BAR_WISHLIST ? "★ Wishlist" : (binders.find((b) => `b:${b.uid}` === k)?.name ?? k);
  const move = (i: number, by: number) => {
    const to = i + by;
    if (to < 0 || to >= order.length) return;
    const next = [...order];
    next.splice(to, 0, next.splice(i, 1)[0]);
    setOrder(next);
  };
  return (
    <Dialog title="Arrange binders" onClose={onClose}>
      <div className="stack">
        <p className="muted small">The order of the binder bar, here and in the app on every phone.</p>
        <ol className="arrange-list">
          {order.map((k, i) => (
            <li key={k}>
              <span>{name(k)}</span>
              <span className="spacer" />
              <button type="button" className="btn small-btn" aria-label={`Move ${name(k)} up`} disabled={i === 0} onClick={() => move(i, -1)}>
                ↑
              </button>
              <button type="button" className="btn small-btn" aria-label={`Move ${name(k)} down`} disabled={i === order.length - 1} onClick={() => move(i, 1)}>
                ↓
              </button>
            </li>
          ))}
        </ol>
        <div className="dialog-actions">
          <button type="button" className="link" onClick={() => setOrder(usual)}>
            Usual order
          </button>
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn primary" disabled={busy} onClick={() => onSave(JSON.stringify(order) === JSON.stringify(usual) ? [] : order)}>
            Save
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** The purchase price per copy for several selected piles at once (the app's selection "Edit" dialog). */
export function PaidDialog({
  piles,
  currency,
  busy,
  onSave,
  onClose,
}: {
  piles: number;
  currency: string;
  busy: boolean;
  onSave: (paid: Paid) => void;
  onClose: () => void;
}) {
  const [amount, setAmount] = useState("");
  const [cur, setCur] = useState(currency);
  const text = amount.trim().replace(",", ".");
  const n = text === "" ? null : Number(text);
  const ok = n === null || (Number.isFinite(n) && n >= 0);
  return (
    <Dialog title="Purchase price" onClose={onClose}>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (ok) onSave(n === null ? null : { amount: n, currency: cur });
        }}
      >
        <p>
          What one copy cost, for all {piles} selected pile{piles === 1 ? "" : "s"}. Leave it empty to clear their purchase price.
        </p>
        <div className="inline">
          <label className="field grow">
            <span>Per copy</span>
            <input type="text" autoFocus inputMode="decimal" placeholder="0.00" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </label>
          <label className="field">
            <span>Currency</span>
            <select value={cur} onChange={(e) => setCur(e.target.value)}>
              {["EUR", "DKK", "USD"].map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </label>
        </div>
        {!ok && <p className="warn-text small">Enter a price of 0 or more.</p>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={busy || !ok}>
            {n === null ? "Clear the price" : "Set the price"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
