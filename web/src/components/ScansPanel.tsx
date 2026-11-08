"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { api, formatDate } from "@/components/api";
import type { Printing } from "@/components/CardSearch";
import { Dialog } from "@/components/Dialog";
import { useHoverCard } from "@/components/HoverCard";
import { OrderCheckDialog } from "@/components/OrderCheckDialog";
import { ViewSwitch, type Layout } from "@/components/ViewSwitch";
import { CONDITIONS, finishesOf, LANGUAGES } from "@/lib/cards";
import { format } from "@/lib/money";
import type { Finish } from "@/lib/pricing";
import type { ScanEdit, ScanEditResult, ScanRow, ScannedCard, ScanView } from "@/lib/scans";

const FINISH_WORDS: Record<Finish, string> = { NONFOIL: "Nonfoil", FOIL: "Foil", ETCHED: "Etched" };
type Answer = ScanView & { result?: ScanEditResult };

/**
 * The app's Scan tab list, shown in the collection when its "Scanned" chip is picked: cards scanned on a phone wait
 * here until they go to a binder or a deck, or are discarded. Every card starts selected, as in the app.
 */
export function ScansPanel({
  layout,
  onLayout,
  hover,
  onHover,
  onLoggedOut,
  onChanged,
}: {
  layout: Layout;
  onLayout: (l: Layout) => void;
  hover: boolean;
  onHover: (on: boolean) => void;
  onLoggedOut: () => void;
  onChanged: () => void;
}) {
  const [view, setView] = useState<ScanView | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [note, setNote] = useState<{ text: string; undo?: ScannedCard[]; href?: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [unselected, setUnselected] = useState<Set<string>>(new Set());
  const [action, setAction] = useState<"BINDER" | "DECK" | null>(null);
  const [editing, setEditing] = useState<ScanRow | null>(null);
  const [checking, setChecking] = useState(false);
  const { follow, hide, picture } = useHoverCard(hover && layout === "list");

  const load = useCallback(async () => {
    setLoadError(null);
    const r = await api<ScanView>("/api/scans");
    if (r.status === 401 || r.data.loggedOut) return onLoggedOut();
    if (r.ok && Array.isArray(r.data.rows)) setView(r.data);
    else setLoadError(r.data.error ?? "Could not load the scanned cards.");
  }, [onLoggedOut]);

  useEffect(() => {
    load();
  }, [load]);

  /** Sends one change; answers with what it gave back, or null when it didn't work (the old list stays). */
  async function edit(e: ScanEdit): Promise<ScanEditResult | null> {
    setBusy(true);
    setMessage(null);
    const r = await api<Answer>("/api/scans", { edit: e });
    setBusy(false);
    if (r.status === 401 || r.data.loggedOut) {
      onLoggedOut();
      return null;
    }
    if (r.ok && Array.isArray(r.data.rows)) {
      setView(r.data);
      // Like the app: after a change every card left is selected again.
      setUnselected(new Set());
      onChanged();
      return r.data.result ?? {};
    }
    setMessage(r.data.error ?? "That change did not work.");
    return null;
  }

  const toggle = (uid: string) =>
    setUnselected((s) => {
      const n = new Set(s);
      if (n.has(uid)) n.delete(uid);
      else n.add(uid);
      return n;
    });
  const open = (r: ScanRow) => {
    hide();
    setEditing(r);
  };

  const picked = useMemo(() => (view ? view.rows.filter((r) => !unselected.has(r.uid)) : []), [view, unselected]);

  if (!view) {
    return loadError ? (
      <div className="stack">
        <div className="bar error">{loadError}</div>
        <div className="inline">
          <button className="btn" onClick={load}>
            Try again
          </button>
        </div>
      </div>
    ) : (
      <p className="muted center">Loading the scanned cards…</p>
    );
  }

  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const locked = view.readOnly || busy;
  const copies = view.rows.reduce((n, r) => n + r.quantity, 0);
  const value = view.rows.reduce((n, r) => n + (r.unitEur ?? 0) * r.quantity, 0);
  const pickedCopies = picked.reduce((n, r) => n + r.quantity, 0);
  const uids = picked.map((r) => r.uid);

  async function discard() {
    const n = pickedCopies;
    const res = await edit({ type: "discard", uids });
    if (res) setNote({ text: `Discarded ${n} card${n === 1 ? "" : "s"}.`, undo: res.removed });
  }

  return (
    <>
      {message && (
        <div className="bar error" role="alert">
          {message}
          <button className="link" onClick={() => setMessage(null)}>
            Dismiss
          </button>
        </div>
      )}
      {note && (
        <div className="bar ok" role="status">
          {note.text}{" "}
          {note.href && (
            <a className="link" href={note.href}>
              Open the deck
            </a>
          )}
          {note.undo && note.undo.length > 0 && (
            <button
              className="link"
              disabled={locked}
              onClick={async () => {
                if (await edit({ type: "restore", items: note.undo! })) setNote({ text: "The scanned cards are back." });
              }}
            >
              Undo
            </button>
          )}
          <button className="link" onClick={() => setNote(null)}>
            OK
          </button>
        </div>
      )}
      <p className="summary">
        {copies} scanned card{copies === 1 ? "" : "s"} · {money(value)} ({view.priceTypeLabel})
      </p>
      <p className="muted small">Cards scanned with the app wait here until they go to a binder or a deck. Scanning itself happens in the app.</p>

      {view.rows.length === 0 ? (
        <p className="muted center">No scanned cards are waiting.</p>
      ) : (
        <>
          <div className="controls">
            <span className="small">
              {picked.length} of {view.rows.length} selected
            </span>
            <button className="btn small-btn" onClick={() => setUnselected(new Set())}>
              Select all
            </button>
            <button className="btn small-btn" onClick={() => setUnselected(new Set(view.rows.map((r) => r.uid)))}>
              Select none
            </button>
            <span className="spacer" />
            <button className="btn small-btn" disabled={locked} onClick={() => setChecking(true)} title="Compare the scanned cards with an order's file">
              Check an order
            </button>
            <ViewSwitch layout={layout} onLayout={onLayout} hover={hover} onHover={onHover} />
          </div>
          {layout === "list" ? (
            <div className="table-wrap">
              <table className="wish-table scan-table">
                <thead>
                  <tr>
                    <th className="center" aria-label="Selected" />
                    <th>Card</th>
                    <th className="wide-only">Set</th>
                    <th>Finish</th>
                    <th className="wide-only">Cond.</th>
                    <th className="wide-only">Lang.</th>
                    <th className="num">Qty</th>
                    <th className="num">Price</th>
                    <th className="wide-only">Scanned</th>
                  </tr>
                </thead>
                <tbody>
                  {view.rows.map((r) => {
                    const on = !unselected.has(r.uid);
                    return (
                      <tr
                        key={r.uid}
                        className={on ? "wish-row clickable picked" : "wish-row clickable"}
                        onClick={() => open(r)}
                        onMouseEnter={follow(r.card.imageUrl)}
                        onMouseMove={follow(r.card.imageUrl)}
                        onMouseLeave={hide}
                      >
                        <td className="center" onClick={(e) => e.stopPropagation()}>
                          <input type="checkbox" className="tick" aria-label={`Select ${r.card.name}`} checked={on} onChange={() => toggle(r.uid)} />
                        </td>
                        <td className="card-cell">
                          <button
                            className="row-title linkish"
                            title="Fix the printing, finish, condition or copies"
                            onClick={(e) => {
                              e.stopPropagation();
                              open(r);
                            }}
                          >
                            {r.card.name}
                          </button>
                          {!r.exactPrinting && (
                            <span className="warn-text small" title="The scanner only read the name: check the set">
                              {" "}· printing guessed
                            </span>
                          )}
                        </td>
                        <td className="wide-only muted nowrap">
                          {r.card.setCode.toUpperCase()} #{r.card.collectorNumber}
                        </td>
                        <td className="nowrap">
                          <span className={r.finish !== "NONFOIL" ? "foil-text" : "muted"}>{FINISH_WORDS[r.finish]}</span>
                        </td>
                        <td className={r.condition !== "NM" ? "wide-only" : "wide-only muted"}>{r.condition}</td>
                        <td className={r.language !== "EN" ? "wide-only" : "wide-only muted"}>{r.language}</td>
                        <td className="num">{r.quantity}</td>
                        <td className="num nowrap">
                          {r.unitEur != null ? money(r.unitEur * r.quantity) : <span className="muted">–</span>}
                          {r.approx && <span className="muted" title="Cardmarket's price standing in"> *</span>}
                        </td>
                        <td className="wide-only muted nowrap small">{r.scannedAt > 0 ? formatDate(r.scannedAt) : "–"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {picture}
            </div>
          ) : (
            <ul className="wish-grid">
              {view.rows.map((r) => {
                const on = !unselected.has(r.uid);
                return (
                  <li key={r.uid} className={on ? "tile scan-tile picked" : "tile scan-tile"}>
                    <input type="checkbox" className="tick scan-tile-tick" aria-label={`Select ${r.card.name}`} checked={on} onChange={() => toggle(r.uid)} />
                    <button className="tile-in" onClick={() => open(r)}>
                      <div className="tile-img">
                        {r.card.imageUrl ? (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img src={r.card.imageUrl} alt={r.card.name} loading="lazy" />
                        ) : (
                          <span className="tile-noimg">{r.card.name}</span>
                        )}
                        {r.quantity > 1 && <span className="tile-qty">×{r.quantity}</span>}
                        {!r.exactPrinting && <span className="tile-flag">Printing guessed</span>}
                      </div>
                      <div className="tile-name" title={r.card.name}>
                        {r.card.name}
                      </div>
                      <div>{r.unitEur != null ? money(r.unitEur * r.quantity) : <span className="muted">No price</span>}</div>
                      <div className="muted small">
                        {[`${r.card.setCode.toUpperCase()} #${r.card.collectorNumber}`, r.finish !== "NONFOIL" ? FINISH_WORDS[r.finish] : null, r.condition !== "NM" ? r.condition : null, r.language !== "EN" ? r.language : null]
                          .filter(Boolean)
                          .join(" · ")}
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}

      {/* Room under the list for the bar at the bottom of the screen. */}
      {picked.length > 0 && <div className="bulk-room" aria-hidden="true" />}
      {picked.length > 0 && (
        <div className="bulk-bar" role="region" aria-label="Selected scanned cards">
          <strong>{pickedCopies} selected</strong>
          <button className="btn" disabled={locked} onClick={() => setAction("BINDER")}>
            To binder…
          </button>
          <button className="btn" disabled={locked} onClick={() => setAction("DECK")}>
            To deck…
          </button>
          <button className="btn" disabled={locked} onClick={discard}>
            Discard
          </button>
        </div>
      )}

      {action === "BINDER" && (
        <ToBinderDialog
          count={pickedCopies}
          binders={view.binders}
          busy={busy}
          onClose={() => setAction(null)}
          onSend={async (binder, newBinder, keep) => {
            const res = await edit({ type: "toBinder", uids, binder, newBinder, keep });
            if (res) {
              setAction(null);
              const where = newBinder?.trim() || (binder ? (view.binders.find((b) => b.uid === binder)?.name ?? "the binder") : "Unsorted");
              setNote({ text: `Added ${res.copies ?? pickedCopies} card${(res.copies ?? pickedCopies) === 1 ? "" : "s"} to ${where}.` });
            }
          }}
        />
      )}
      {action === "DECK" && (
        <ToDeckDialog
          count={pickedCopies}
          decks={view.decks}
          busy={busy}
          onClose={() => setAction(null)}
          onSend={async (target, keep) => {
            const res = await edit({ type: "toDeck", uids, target, keep });
            if (res) {
              setAction(null);
              const n = res.copies ?? pickedCopies;
              setNote({ text: `Added ${n} card${n === 1 ? "" : "s"} to the deck.`, href: res.deckId != null ? `/decks/${res.deckId}` : undefined });
            }
          }}
        />
      )}
      {checking && (
        <OrderCheckDialog
          onClose={() => setChecking(false)}
          onDone={(summary) => {
            setChecking(false);
            setNote({ text: summary });
            load();
            onChanged();
          }}
        />
      )}
      {editing && (
        <ScanEditDialog
          row={editing}
          busy={busy}
          onClose={() => setEditing(null)}
          onSave={async (e) => {
            if (await edit(e)) setEditing(null);
          }}
          onDiscard={async () => {
            const res = await edit({ type: "discard", uids: [editing.uid] });
            if (res) {
              setEditing(null);
              setNote({ text: `Discarded ${editing.card.name}.`, undo: res.removed });
            }
          }}
        />
      )}
    </>
  );
}

function KeepBox({ keep, onKeep }: { keep: boolean; onKeep: (v: boolean) => void }) {
  return (
    <label className="check small">
      <input type="checkbox" checked={keep} onChange={(e) => onKeep(e.target.checked)} />
      <span>Keep them in the scan list</span>
    </label>
  );
}

/** "Add N card(s) to the collection": a binder, Unsorted or a new binder (the app's ToBinderDialog). */
function ToBinderDialog({
  count,
  binders,
  busy,
  onClose,
  onSend,
}: {
  count: number;
  binders: { uid: string; name: string }[];
  busy: boolean;
  onClose: () => void;
  onSend: (binder: string | null, newBinder: string | undefined, keep: boolean) => void;
}) {
  const [choice, setChoice] = useState("~");
  const [name, setName] = useState("");
  const [keep, setKeep] = useState(false);
  const isNew = choice === "+";
  return (
    <Dialog title={`Add ${count} card${count === 1 ? "" : "s"} to the collection`} onClose={onClose}>
      <div className="stack">
        <select aria-label="Binder" value={choice} onChange={(e) => setChoice(e.target.value)}>
          <option value="~">Unsorted</option>
          {binders.map((b) => (
            <option key={b.uid} value={b.uid}>
              {b.name}
            </option>
          ))}
          <option value="+">New binder…</option>
        </select>
        {isNew && <input aria-label="New binder's name" placeholder="New binder's name" value={name} onChange={(e) => setName(e.target.value)} />}
        <KeepBox keep={keep} onKeep={setKeep} />
        <div className="inline">
          <button className="btn primary" disabled={busy || (isNew && !name.trim())} onClick={() => onSend(isNew || choice === "~" ? null : choice, isNew ? name : undefined, keep)}>
            {busy ? "…" : "Add"}
          </button>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** "Add N card(s) to a deck": a local deck or a new one (the app's ToDeckDialog; decks from Archidekt are edited there). */
function ToDeckDialog({
  count,
  decks,
  busy,
  onClose,
  onSend,
}: {
  count: number;
  decks: { id: number; name: string }[];
  busy: boolean;
  onClose: () => void;
  onSend: (target: { deckId: number } | { newName: string }, keep: boolean) => void;
}) {
  const [choice, setChoice] = useState(decks.length ? String(decks[0].id) : "+");
  const [name, setName] = useState("");
  const [keep, setKeep] = useState(false);
  const isNew = choice === "+";
  return (
    <Dialog title={`Add ${count} card${count === 1 ? "" : "s"} to a deck`} onClose={onClose}>
      <div className="stack">
        <select aria-label="Deck" value={choice} onChange={(e) => setChoice(e.target.value)}>
          {decks.map((d) => (
            <option key={d.id} value={String(d.id)}>
              {d.name}
            </option>
          ))}
          <option value="+">New deck…</option>
        </select>
        {isNew && <input aria-label="Deck name" placeholder="Deck name" value={name} onChange={(e) => setName(e.target.value)} />}
        <p className="muted small">Decks from Archidekt aren&apos;t listed: they&apos;re edited on Archidekt (or make one a local deck on its page first).</p>
        <KeepBox keep={keep} onKeep={setKeep} />
        <div className="inline">
          <button className="btn primary" disabled={busy || (isNew && !name.trim())} onClick={() => onSend(isNew ? { newName: name } : { deckId: Number(choice) }, keep)}>
            {busy ? "…" : "Add"}
          </button>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** Fixes one scan: its printing (another set of the same card), finish, condition, language and copies. */
function ScanEditDialog({
  row,
  busy,
  onClose,
  onSave,
  onDiscard,
}: {
  row: ScanRow;
  busy: boolean;
  onClose: () => void;
  onSave: (e: ScanEdit) => void;
  onDiscard: () => void;
}) {
  const [prints, setPrints] = useState<Printing[] | null>(null);
  const [printId, setPrintId] = useState(row.card.scryfallId);
  const [finish, setFinish] = useState<Finish>(row.finish);
  const [condition, setCondition] = useState(row.condition);
  const [language, setLanguage] = useState(row.language);
  const [quantity, setQuantity] = useState(String(row.quantity));

  useEffect(() => {
    let live = true;
    api<{ results?: Printing[] }>(`/api/cards/prints?name=${encodeURIComponent(row.card.name)}`).then((r) => {
      if (live) setPrints(r.ok ? (r.data.results ?? []) : []);
    });
    return () => {
      live = false;
    };
  }, [row.card.name]);

  const card = prints?.find((p) => p.card.scryfallId === printId)?.card ?? row.card;
  const finishes = finishesOf(card);
  const shownFinish = finishes.includes(finish) ? finish : finishes[0];
  const q = Number(quantity);
  const qOk = Number.isInteger(q) && q >= 1 && q <= 9999;

  return (
    <Dialog title={row.card.name} onClose={onClose}>
      <div className="stack">
        {card.imageUrl && <img className="scan-edit-img" src={card.imageUrl} alt={card.name} />}
        {!row.exactPrinting && <p className="small">Only the name was readable when scanning, so this printing is a guess. Pick the right set below.</p>}
        <label className="stack small">
          Printing
          <select aria-label="Printing" value={printId} onChange={(e) => setPrintId(e.target.value)} disabled={!prints}>
            {!prints && <option value={row.card.scryfallId}>Loading the printings…</option>}
            {prints?.some((p) => p.card.scryfallId === row.card.scryfallId) === false && (
              <option value={row.card.scryfallId}>
                {row.card.setName || row.card.setCode.toUpperCase()} #{row.card.collectorNumber}
              </option>
            )}
            {prints?.map((p) => (
              <option key={p.card.scryfallId} value={p.card.scryfallId}>
                {p.card.setName} ({p.card.setCode.toUpperCase()}) #{p.card.collectorNumber}
                {p.released ? ` · ${p.released.slice(0, 4)}` : ""}
              </option>
            ))}
          </select>
        </label>
        <div className="inline">
          <label className="stack small">
            Finish
            <select aria-label="Finish" value={shownFinish} onChange={(e) => setFinish(e.target.value as Finish)}>
              {finishes.map((f) => (
                <option key={f} value={f}>
                  {FINISH_WORDS[f]}
                </option>
              ))}
            </select>
          </label>
          <label className="stack small">
            Condition
            <select aria-label="Condition" value={condition} onChange={(e) => setCondition(e.target.value)}>
              {CONDITIONS.map(([k, label]) => (
                <option key={k} value={k}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="stack small">
            Language
            <select aria-label="Language" value={language} onChange={(e) => setLanguage(e.target.value)}>
              {LANGUAGES.map(([k, label]) => (
                <option key={k} value={k}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="stack small">
            Copies
            <input aria-label="Copies" type="number" min={1} max={9999} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </label>
        </div>
        <div className="inline">
          <button
            className="btn primary"
            disabled={busy || !qOk}
            onClick={() =>
              onSave({
                type: "update",
                uid: row.uid,
                ...(card.scryfallId !== row.card.scryfallId ? { card } : {}),
                finish: shownFinish,
                condition,
                language,
                quantity: q,
              })
            }
          >
            {busy ? "…" : "Save"}
          </button>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn danger" disabled={busy} onClick={onDiscard}>
            Discard
          </button>
        </div>
      </div>
    </Dialog>
  );
}
