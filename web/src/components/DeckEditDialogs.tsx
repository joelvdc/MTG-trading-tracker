"use client";

import { useEffect, useState } from "react";
import { finishesOf, FINISH_LABELS } from "@/lib/cards";
import { allCombos, saltKey, type DeckRow, type DeckView, type HeldPile } from "@/lib/deckshared";
import { format } from "@/lib/money";
import type { Finish } from "@/lib/pricing";
import { api } from "./api";
import { CardRulesTabs } from "./CardRules";
import { CardSearch, type Printing } from "./CardSearch";
import { ComboLine } from "./ComboLine";
import { Dialog } from "./Dialog";

const COMMANDER = "Commander";
const MAYBEBOARD = "Maybeboard";
const AUTO = "\u0000auto";
const NEW = "\u0000new";

/** Sends one deck action; answers with the error to show, or null when it worked. */
export async function deckAction(id: number, body: Record<string, unknown>): Promise<{ error: string | null; id?: number }> {
  const r = await api<{ ok?: boolean; id?: number }>(`/api/decks/${id}`, body);
  return r.ok ? { error: null, id: r.data.id } : { error: r.data.error ?? "That did not work." };
}

/** The categories cards can be moved to, Commander and Maybeboard included. */
function targets(view: DeckView): string[] {
  const names = view.categories.map((c) => c.name);
  for (const n of [COMMANDER, MAYBEBOARD]) if (!names.some((x) => x.toLowerCase() === n.toLowerCase())) names.push(n);
  return names;
}

function CategoryPicker({ view, value, onChange, allowAuto, autoLabel }: { view: DeckView; value: string; onChange: (v: string) => void; allowAuto: boolean; autoLabel: string }) {
  const [newName, setNewName] = useState("");
  const list = targets(view);
  const choice = value === AUTO || list.includes(value) ? value : NEW;
  return (
    <div className="stack">
      <select
        aria-label="Category"
        value={choice}
        onChange={(e) => {
          const v = e.target.value;
          onChange(v === NEW ? newName : v);
        }}
      >
        {allowAuto && <option value={AUTO}>{autoLabel}</option>}
        {list.map((n) => (
          <option key={n} value={n}>
            {n}
          </option>
        ))}
        <option value={NEW}>New category…</option>
      </select>
      {choice === NEW && (
        <input
          aria-label="New category name"
          placeholder="Category name"
          value={newName}
          onChange={(e) => {
            setNewName(e.target.value);
            onChange(e.target.value);
          }}
        />
      )}
    </div>
  );
}

const FINISH_WORDS: Record<Finish, string> = { NONFOIL: "nonfoil", FOIL: "foil", ETCHED: "etched" };

/** "Owned · 2 foil · 1 nonfoil": the copies you own of one printing, by finish. */
function ownedText(piles: HeldPile[]): string {
  const by = new Map<Finish, number>();
  for (const h of piles) by.set(h.finish, (by.get(h.finish) ?? 0) + h.quantity);
  return `Owned · ${[...by].map(([f, n]) => `${n} ${FINISH_WORDS[f]}`).join(" · ")}`;
}

/** "In your collection" and "In your decks": how many copies you own, where, and which other decks have the card. */
function CardOwnership({ row }: { row: DeckRow }) {
  return (
    <>
      <section>
        <h3 className="mini-head">In your collection</h3>
        {row.owned === 0 ? (
          <p className="muted small">You own none.</p>
        ) : (
          <>
            <p className="small">
              <strong>You own {row.owned}</strong> cop{row.owned === 1 ? "y" : "ies"} in all printings{row.exact > 0 ? ` (${row.exact} of the printing shown)` : ""}.
            </p>
            <ul className="plain-list pile-list small">
              {row.held.map((h, i) => (
                <li key={i}>
                  <span className="owned-dot" aria-hidden="true" />
                  {h.quantity}× {h.setName || h.setCode.toUpperCase()} ({h.setCode.toUpperCase()}) #{h.collectorNumber} · {FINISH_WORDS[h.finish]} · {h.condition} · <span className="binder-name">{h.binder}</span>
                </li>
              ))}
            </ul>
            <a className="small" href={`/collection?search=${encodeURIComponent(row.name)}&binder=`}>
              Open in collection
            </a>
          </>
        )}
      </section>
      <section>
        <h3 className="mini-head">In your decks</h3>
        {row.otherDecks.length === 0 ? (
          <p className="muted small">In no other deck.</p>
        ) : (
          <ul className="plain-list small">
            {row.otherDecks.map((d, i) => (
              <li key={i}>
                <a href={`/decks/${d.deckId}`}>{d.deckName}</a> <span className="muted">×{d.quantity}{d.maybeboard ? " (maybeboard)" : ""}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

/** Every printing of the card, those you own first (green), then the others newest first; picking one changes the deck line. */
function ChangePrintingDialog({ view, row, onBack, onClose, onChanged }: { view: DeckView; row: DeckRow; onBack: () => void; onClose: () => void; onChanged: (notice: string | null) => void }) {
  const [prints, setPrints] = useState<Printing[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const current = (row.original ?? row.card).scryfallId;
  const money = (eur: number | null) => (eur == null ? "–" : format(eur * view.perEuro, view.currency));

  useEffect(() => {
    let live = true;
    api<{ results?: Printing[] }>(`/api/cards/prints?name=${encodeURIComponent(row.card.name)}`).then((r) => {
      if (!live) return;
      if (r.ok) setPrints(r.data.results ?? []);
      else setError(r.data.error ?? "Could not load the printings.");
    });
    return () => {
      live = false;
    };
  }, [row.card.name]);

  const piles = (id: string) => row.held.filter((h) => h.scryfallId === id);
  const byNewest = (a: Printing, b: Printing) => (a.released < b.released ? 1 : a.released > b.released ? -1 : 0);
  const sorted = prints ? [...prints.filter((p) => piles(p.card.scryfallId).length > 0).sort(byNewest), ...prints.filter((p) => piles(p.card.scryfallId).length === 0).sort(byNewest)] : [];

  async function pick(p: Printing, finish: Finish) {
    setBusy(true);
    setError(null);
    const r = await deckAction(view.id, { action: "printing", uid: row.lineUid, card: p.card, finish });
    setBusy(false);
    if (r.error) setError(r.error);
    else onChanged(`${row.name} now uses ${p.card.setName || p.card.setCode.toUpperCase()} #${p.card.collectorNumber}.`);
  }

  return (
    <Dialog title={`Change printing: ${row.name}`} onClose={() => !busy && onClose()}>
      <div className="stack">
        {error && <div className="bar error">{error}</div>}
        {prints === null && !error ? (
          <div className="muted">Loading printings…</div>
        ) : sorted.length === 0 ? (
          <div className="muted">No printings found.</div>
        ) : (
          <ul className="print-list">
            {sorted.map((p) => {
              const mine = piles(p.card.scryfallId);
              const finishes = finishesOf(p.card);
              return (
                <li key={p.card.scryfallId} className={`${mine.length ? "owned" : ""} ${p.card.scryfallId === current ? "current" : ""}`}>
                  {p.card.imageUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={p.card.imageUrl} alt="" loading="lazy" />
                  ) : (
                    <div className="thumb empty" />
                  )}
                  <div className="grow stack">
                    <span>
                      {p.card.setName || p.card.setCode.toUpperCase()} <span className="muted small">({p.card.setCode.toUpperCase()}) #{p.card.collectorNumber} · {p.released.slice(0, 4)}</span>
                    </span>
                    {mine.length > 0 && <span className="owned-badge">{ownedText(mine)}</span>}
                    <span className="small muted">
                      {p.card.scryfallId === current ? "In the deck now · " : ""}
                      {p.eur != null || p.card.hasNonFoil ? money(p.eur) : ""}
                      {p.eurFoil != null ? ` / foil ${money(p.eurFoil)}` : ""}
                    </span>
                  </div>
                  <div className="picks">
                    {finishes.map((f) => (
                      <button key={f} className="btn small-btn" disabled={busy || (p.card.scryfallId === current && f === row.finish)} onClick={() => pick(p, f)}>
                        {finishes.length > 1 ? `Use ${FINISH_WORDS[f]}` : "Use"}
                      </button>
                    ))}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
        <div className="dialog-actions">
          <button className="btn" disabled={busy} onClick={onBack}>
            Back
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/**
 * A card of the deck (the app's line menu): its picture and text, how many copies you own and which other decks have
 * it, and, in the Versions tab, its quantity, category, commander and maybeboard (local decks). Local decks can change
 * the line's printing. The Rulings tab shows legality and rulings.
 */
export function LineDialog({ view, row, onClose, onChanged }: { view: DeckView; row: DeckRow; onClose: () => void; onChanged: (notice: string | null) => void }) {
  const [qty, setQty] = useState(String(row.quantity));
  const [category, setCategory] = useState(row.category);
  const cardCombos = view.salt ? allCombos(view.salt).filter((c) => c.cards.some((n) => saltKey(n) === saltKey(row.name))) : [];
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [changing, setChanging] = useState(false);
  const editable = view.local && !view.readOnly;

  async function run(body: Record<string, unknown>, notice: string | null) {
    setBusy(true);
    setError(null);
    const r = await deckAction(view.id, body);
    setBusy(false);
    if (r.error) setError(r.error);
    else onChanged(notice);
  }

  const q = Math.floor(Number(qty));
  if (changing) return <ChangePrintingDialog view={view} row={row} onBack={() => setChanging(false)} onClose={onClose} onChanged={onChanged} />;
  return (
    <Dialog title={row.name} onClose={() => !busy && onClose()}>
      <div className="stack">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        {row.card.imageUrl && <img src={row.card.imageUrl} alt="" className="line-picture" />}
        <p className="muted small">
          {row.types} · {row.card.setName || row.card.setCode.toUpperCase()} #{row.card.collectorNumber}
          {row.finish !== "NONFOIL" ? ` · ${FINISH_LABELS[row.finish].toLowerCase()}` : ""}
          {row.original ? " (a printing you own; the deck has another)" : ""}
        </p>
        {editable && (
          <div className="inline">
            <button className="btn small-btn" disabled={busy} onClick={() => setChanging(true)}>
              Change printing
            </button>
          </div>
        )}
        <CardRulesTabs scryfallId={row.card.scryfallId} between={<CardOwnership row={row} />}>
        {editable ? (
          <>
            <div className="inline">
              <label className="inline small">
                <span className="muted">Copies</span>
                <input type="number" min={0} max={999} value={qty} onChange={(e) => setQty(e.target.value)} style={{ width: 80 }} />
              </label>
              <button className="btn small-btn" disabled={busy || !Number.isFinite(q) || q < 0 || q === row.quantity} onClick={() => run({ action: "set-quantity", uid: row.lineUid, quantity: q }, q === 0 ? `${row.name} removed.` : null)}>
                Save
              </button>
            </div>
            <div className="stack">
              <span className="muted small">Category</span>
              <CategoryPicker view={view} value={category} onChange={setCategory} allowAuto autoLabel="Its auto category" />
              <div className="inline">
                <button
                  className="btn small-btn"
                  disabled={busy || !category.trim() || category === row.category}
                  onClick={() => run({ action: "move", uids: [row.lineUid], category: category === AUTO ? null : category.trim() }, null)}
                >
                  Move
                </button>
                {!row.commander && (
                  <button className="btn small-btn" disabled={busy} onClick={() => run({ action: "move", uids: [row.lineUid], category: COMMANDER }, `${row.name} is a commander now.`)}>
                    Make commander
                  </button>
                )}
                {row.maybeboard ? (
                  <button className="btn small-btn" disabled={busy} onClick={() => run({ action: "move", uids: [row.lineUid], category: null }, `${row.name} is back in the deck.`)}>
                    Back into the deck
                  </button>
                ) : (
                  <button className="btn small-btn" disabled={busy} onClick={() => run({ action: "move", uids: [row.lineUid], category: MAYBEBOARD }, `${row.name} went to the maybeboard.`)}>
                    To maybeboard
                  </button>
                )}
              </div>
            </div>
          </>
        ) : (
          !view.local && <p className="muted small">Cards of a deck from Archidekt are changed on Archidekt, or make it a local deck first.</p>
        )}
        {cardCombos.length > 0 && (
          <ul className="combo-list">
            {cardCombos.map((c, i) => (
              <ComboLine key={i} combo={c} cardName={row.name} />
            ))}
          </ul>
        )}
        </CardRulesTabs>
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          {editable && (
            <button className="btn danger" disabled={busy} onClick={() => run({ action: "remove", uids: [row.lineUid] }, `${row.name} removed.`)}>
              Remove
            </button>
          )}
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={busy}>
            Close
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** The app's "Manage categories": show or hide each one (every deck); rename, delete or add (local decks). */
export function CategoriesDialog({ view, onClose, onChanged }: { view: DeckView; onClose: () => void; onChanged: (notice: string | null) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [deleting, setDeleting] = useState<string | null>(null);
  const [moveTo, setMoveTo] = useState(AUTO);
  const [adding, setAdding] = useState("");
  const editable = view.local && !view.readOnly;
  const hidden = new Set(view.hidden);

  async function run(body: Record<string, unknown>, notice: string | null) {
    setBusy(true);
    setError(null);
    const r = await deckAction(view.id, body);
    setBusy(false);
    if (r.error) setError(r.error);
    else {
      setRenaming(null);
      setDeleting(null);
      setAdding("");
      onChanged(notice);
    }
  }

  return (
    <Dialog title="Categories" onClose={() => !busy && onClose()}>
      <div className="stack">
        <p className="muted small">Hidden categories are left out of the deck&apos;s sections (their cards go under &quot;Hidden categories&quot;), on the phones too.</p>
        <ul className="plain-list cat-list">
          {view.categories.map((c) => {
            const special = [COMMANDER, MAYBEBOARD].includes(c.name) || c.name.toLowerCase() === "commander" || c.name.toLowerCase() === "maybeboard";
            return (
              <li key={c.name} className="stack">
                <div className="inline">
                  <label className="check small grow">
                    <input
                      type="checkbox"
                      checked={!hidden.has(c.name.toLowerCase())}
                      disabled={busy || view.readOnly || !c.included}
                      onChange={(e) => run({ action: "hide-category", name: c.name, hidden: !e.target.checked }, null)}
                    />
                    <span>
                      {c.name} <span className="muted">· {c.cards}</span>
                      {!c.included && <span className="muted"> · left out of the deck</span>}
                    </span>
                  </label>
                  {editable && !special && (
                    <>
                      <button className="link small" disabled={busy} onClick={() => (setRenaming(c.name), setNewName(c.name), setDeleting(null))}>
                        Rename
                      </button>
                      <button className="link small" disabled={busy} onClick={() => (setDeleting(c.name), setMoveTo(AUTO), setRenaming(null))}>
                        Delete
                      </button>
                    </>
                  )}
                </div>
                {renaming === c.name && (
                  <div className="inline">
                    <input aria-label="New name" value={newName} onChange={(e) => setNewName(e.target.value)} />
                    <button className="btn small-btn" disabled={busy || !newName.trim() || newName.trim() === c.name} onClick={() => run({ action: "rename-category", from: c.name, to: newName.trim() }, null)}>
                      Save
                    </button>
                  </div>
                )}
                {deleting === c.name && (
                  <div className="stack">
                    <span className="small">{c.cards > 0 ? `Its ${c.cards} card${c.cards === 1 ? "" : "s"} go to:` : "It holds no cards."}</span>
                    {c.cards > 0 && <CategoryPicker view={{ ...view, categories: view.categories.filter((x) => x.name !== c.name) }} value={moveTo} onChange={setMoveTo} allowAuto autoLabel="Each card to its auto category" />}
                    <div className="inline">
                      <button className="btn small-btn danger" disabled={busy || !moveTo.trim()} onClick={() => run({ action: "delete-category", name: c.name, moveTo: moveTo === AUTO ? null : moveTo.trim() }, `Category ${c.name} deleted.`)}>
                        Delete {c.name}
                      </button>
                    </div>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
        {editable && (
          <div className="inline">
            <input aria-label="New category" placeholder="New category" value={adding} onChange={(e) => setAdding(e.target.value)} />
            <button className="btn small-btn" disabled={busy || !adding.trim()} onClick={() => run({ action: "add-category", name: adding.trim() }, null)}>
              Add
            </button>
          </div>
        )}
        {editable && (
          <div className="stack">
            <div className="inline">
              <button className="btn small-btn" disabled={busy} onClick={() => run({ action: "auto-all" }, "Every card is in its auto category.")}>
                Put every card in its auto category
              </button>
            </div>
            <p className="muted small">
              Auto categories come from Scryfall&apos;s card tags (Ramp, Removal, Draw, Tutor, Recursion, Protection; a card&apos;s other ones go along), else the card type. The commander and the maybeboard stay
              where they are.
            </p>
          </div>
        )}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={busy}>
            Close
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** Search a card, pick a printing, then the copies, finish and category (the app's add to deck). */
export function AddCardDialog({ view, onClose, onChanged }: { view: DeckView; onClose: () => void; onChanged: (notice: string) => void }) {
  const [picked, setPicked] = useState<Printing | null>(null);
  const [finish, setFinish] = useState<Finish>("NONFOIL");
  const [qty, setQty] = useState("1");
  const [category, setCategory] = useState(AUTO);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [added, setAdded] = useState<string[]>([]);

  async function add() {
    if (!picked) return;
    setBusy(true);
    setError(null);
    const r = await deckAction(view.id, { action: "add-card", card: picked.card, finish, quantity: Math.max(1, Math.floor(Number(qty)) || 1), category: category === AUTO ? null : category.trim() });
    setBusy(false);
    if (r.error) setError(r.error);
    else {
      setAdded((a) => [...a, picked.card.name]);
      setPicked(null);
      setQty("1");
      onChanged(`${picked.card.name} added.`);
    }
  }

  return (
    <Dialog title="Add a card" onClose={() => !busy && onClose()}>
      {!picked ? (
        <div className="stack">
          <CardSearch
            currency={view.currency}
            perEuro={view.perEuro}
            onPick={(p) => {
              setPicked(p);
              setFinish(finishesOf(p.card)[0] ?? "NONFOIL");
            }}
            onClose={onClose}
          />
          {added.length > 0 && <p className="muted small">Added: {added.join(", ")}</p>}
        </div>
      ) : (
        <div className="stack">
          <p>
            <strong>{picked.card.name}</strong> <span className="muted small">· {picked.card.setName} #{picked.card.collectorNumber}</span>
          </p>
          <div className="inline">
            <label className="inline small">
              <span className="muted">Copies</span>
              <input type="number" min={1} max={999} value={qty} onChange={(e) => setQty(e.target.value)} style={{ width: 80 }} />
            </label>
            {finishesOf(picked.card).length > 1 && (
              <div className="seg text" role="group" aria-label="Finish">
                {finishesOf(picked.card).map((f) => (
                  <button key={f} className={finish === f ? "on" : ""} onClick={() => setFinish(f)}>
                    {FINISH_LABELS[f]}
                  </button>
                ))}
              </div>
            )}
          </div>
          <span className="muted small">Category</span>
          <CategoryPicker view={view} value={category} onChange={setCategory} allowAuto autoLabel="Its auto category" />
          {error && <div className="bar error">{error}</div>}
          <div className="dialog-actions">
            <button className="btn" disabled={busy} onClick={() => setPicked(null)}>
              Back
            </button>
            <span className="spacer" />
            <button className="btn" onClick={onClose} disabled={busy}>
              Close
            </button>
            <button className="btn primary" disabled={busy || !category.trim()} onClick={add}>
              Add to deck
            </button>
          </div>
        </div>
      )}
    </Dialog>
  );
}

/** Rename or copy a deck: one name field. */
export function NameDialog({
  title,
  start,
  button,
  note,
  onSave,
  onClose,
}: {
  title: string;
  start: string;
  button: string;
  note?: string;
  onSave: (name: string) => Promise<string | null>;
  onClose: () => void;
}) {
  const [name, setName] = useState(start);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog title={title} onClose={() => !busy && onClose()}>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError(null);
          const err = await onSave(name.trim());
          setBusy(false);
          if (err) setError(err);
        }}
      >
        <input autoFocus aria-label="Deck name" value={name} maxLength={200} onChange={(e) => setName(e.target.value)} />
        {note && <p className="muted small">{note}</p>}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={busy || !name.trim()}>
            {button}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

/** A yes/no question before something that can't be undone here. */
export function ConfirmDialog({
  title,
  text,
  button,
  danger,
  onYes,
  onClose,
}: {
  title: string;
  text: string;
  button: string;
  danger?: boolean;
  onYes: () => Promise<string | null>;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog title={title} onClose={() => !busy && onClose()}>
      <div className="stack">
        <p className="small">{text}</p>
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            className={danger ? "btn danger" : "btn primary"}
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError(null);
              const err = await onYes();
              setBusy(false);
              if (err) setError(err);
            }}
          >
            {button}
          </button>
        </div>
      </div>
    </Dialog>
  );
}
