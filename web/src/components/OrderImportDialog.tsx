"use client";

import { useState } from "react";
import { api } from "@/components/api";
import type { Candidate, OrderFile } from "@/lib/orders";
import { FORMAT_LABELS, shopOf } from "@/lib/orders";
import { Dialog } from "./Dialog";

interface ReadAnswer {
  file: OrderFile;
  candidates: Candidate[];
  owned: Record<string, number>;
  binders: { uid: string; name: string }[];
}

const NEW = "__new__";

function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** The app's suggested binder: "CardTrader 23 Jun 2026" (the day in CardTrader's file name), else today. */
function suggestedBinder(fileName: string, shop: string): string {
  const m = shop === "CardTrader" ? /(20\d{2})(\d{2})(\d{2})/.exec(fileName) : null;
  const d = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date();
  const ok = !Number.isNaN(d.getTime()) ? d : new Date();
  return `${shop} ${ok.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" })}`;
}

async function base64(f: File): Promise<string> {
  const bytes = new Uint8Array(await f.arrayBuffer());
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

/**
 * Import an order (CardTrader's Excel export, or a Cardmarket file), like the app: every card is looked up on
 * Scryfall, then shown with a tick box, switches to leave out tokens and basic lands, and a binder to put them in.
 */
export function OrderImportDialog({ onDone, onClose }: { onDone: (summary: string) => void; onClose: () => void }) {
  const [picked, setPicked] = useState<File | null>(null);
  const [date, setDate] = useState(today());
  const [read, setRead] = useState<ReadAnswer | null>(null);
  const [checked, setChecked] = useState<Set<number>>(new Set());
  const [skipTokens, setSkipTokens] = useState(true);
  const [skipBasics, setSkipBasics] = useState(true);
  const [binder, setBinder] = useState<string>(NEW);
  const [newName, setNewName] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function readFile() {
    if (!picked) return;
    if (picked.size > 8_000_000) return setError("That file is bigger than 8 MB.");
    setBusy("Reading the order and finding its cards on Scryfall… (a big Cardmarket order takes a minute or two)");
    setError(null);
    const r = await api<ReadAnswer>("/api/orders/read", { data: await base64(picked), name: picked.name });
    setBusy(null);
    if (!r.ok || !r.data.candidates) return setError(r.data.error ?? "Could not read the order.");
    setRead(r.data);
    setChecked(new Set(r.data.candidates.filter((c) => c.card && !c.token && !c.basicLand).map((c) => c.index)));
    setNewName(suggestedBinder(picked.name, shopOf(r.data.file.format)));
  }

  const cands = read?.candidates ?? [];
  const found = cands.filter((c) => c.card);
  const missing = cands.filter((c) => !c.card);
  const tokens = cands.filter((c) => c.token);
  const basics = cands.filter((c) => c.basicLand);
  const copies = (list: Candidate[]) => list.reduce((n, c) => n + c.line.quantity, 0);
  const chosen = found.filter((c) => checked.has(c.index));
  const toCheck = found.filter((c) => c.guessed).length;
  const dateKnown = read ? read.file.lines.every((l) => l.date) : false;

  function toggleGroup(list: Candidate[], leaveOut: boolean) {
    const ids = list.filter((c) => c.card).map((c) => c.index);
    setChecked((s) => {
      const next = new Set(s);
      for (const i of ids) {
        if (leaveOut) next.delete(i);
        else next.add(i);
      }
      return next;
    });
  }

  async function importNow() {
    if (!read) return;
    setBusy("Adding the cards…");
    setError(null);
    const r = await api<{ added?: number; newOrders?: number }>("/api/orders/import", {
      file: read.file,
      cards: read.candidates.map((c) => c.card),
      add: chosen.map((c) => c.index),
      binder: binder === NEW || binder === "" ? null : binder,
      newBinder: binder === NEW ? newName.trim() : null,
      date,
    });
    setBusy(null);
    if (!r.ok || r.data.added === undefined) return setError(r.data.error ?? "The import did not work.");
    const where = binder === NEW ? newName.trim() : binder === "" ? "Unsorted" : (read.binders.find((b) => b.uid === binder)?.name ?? "the binder");
    const left = found.filter((c) => !checked.has(c.index));
    const parts = [`Added ${plural(r.data.added, "card")} to ${where}.`];
    const lt = copies(left.filter((c) => c.token));
    const lb = copies(left.filter((c) => c.basicLand));
    if (lt) parts.push(`${plural(lt, "token")} left out.`);
    if (lb) parts.push(`${plural(lb, "basic land")} left out.`);
    if (missing.length) parts.push(`${plural(copies(missing), "card")} not found.`);
    parts.push(r.data.newOrders ? "The order is in the history." : "The order was in the history already; it's updated.");
    onDone(parts.join(" "));
  }

  const title = read ? `Import ${FORMAT_LABELS[read.file.format]}` : "Import an order";
  const lineText = (c: Candidate) => {
    const l = c.line;
    const bits = [
      c.card ? `${c.card.setCode.toUpperCase()} #${c.card.collectorNumber}` : l.setCode ? `${l.setCode} #${l.number}` : l.setName,
      l.foil ? "foil" : "",
      l.versionKnown ? l.condition : "",
      l.versionKnown && l.languageKnown && l.language !== "EN" ? l.language : "",
      l.signed ? "signed" : "",
      l.altered ? "altered" : "",
      l.price != null ? `€${l.price.toFixed(2)}` : "",
    ].filter(Boolean);
    return bits.join(" · ");
  };

  return (
    <Dialog title={title} onClose={() => !busy && onClose()}>
      <div className="stack">
        {!read && (
          <>
            <p className="muted small">
              CardTrader&apos;s order export (Excel, from the order&apos;s page), or Cardmarket&apos;s “ArticlesFromShipment” file (a
              shipment&apos;s page → Export) or “Purchased Articles” file (a month of purchases). The cards are found on Scryfall; you
              tick which to add to the collection, each with what it cost as its purchase price. The order also goes into the order
              history.
            </p>
            <label className="field">
              <span>Order file</span>
              <input type="file" accept=".xls,.xlsx,.csv,text/csv" disabled={!!busy} onChange={(e) => setPicked(e.target.files?.[0] ?? null)} />
            </label>
            <label className="field">
              <span>Bought on (used when the file has no date)</span>
              <input type="date" value={date} disabled={!!busy} onChange={(e) => setDate(e.target.value)} />
            </label>
          </>
        )}
        {read && (
          <>
            <p className="small">
              {picked?.name} · {plural(copies(cands), "card")}
              {read.file.otherItems > 0 ? ` · ${read.file.otherItems} other items left out (sleeves, sealed, other games)` : ""}
            </p>
            {read.file.format === "CARDMARKET_MONTHLY" && (
              <div className="bar warn">
                Cardmarket&apos;s monthly file doesn&apos;t say the finish, language or condition: the cards come in as non-foil,
                English, near mint. A shipment&apos;s own file (“ArticlesFromShipment”) has those.
              </div>
            )}
            {toCheck > 0 && <div className="bar warn">{plural(toCheck, "card")} found by name only: check the printing (marked “check”).</div>}
            <div className="inline">
              <label className="check">
                <input
                  type="checkbox"
                  checked={skipTokens}
                  onChange={(e) => {
                    setSkipTokens(e.target.checked);
                    toggleGroup(tokens, e.target.checked);
                  }}
                />
                <span>Leave out tokens ({copies(tokens)})</span>
              </label>
              <label className="check">
                <input
                  type="checkbox"
                  checked={skipBasics}
                  onChange={(e) => {
                    setSkipBasics(e.target.checked);
                    toggleGroup(basics, e.target.checked);
                  }}
                />
                <span>Leave out basic lands ({copies(basics)})</span>
              </label>
            </div>
            <div className="order-review">
              {missing.length > 0 && (
                <>
                  <h3 className="mini-head warn-text">Not found on Scryfall ({missing.length})</h3>
                  <ul className="plain-list">
                    {missing.map((c) => (
                      <li key={c.index}>
                        <span className="grow">
                          {c.line.quantity > 1 ? `${c.line.quantity}× ` : ""}
                          {c.line.name || (c.line.productId !== null ? `Cardmarket product ${c.line.productId}` : "Unknown card")}
                          <span className="muted small"> · {lineText(c)}</span>
                        </span>
                      </li>
                    ))}
                  </ul>
                </>
              )}
              <h3 className="mini-head">Found ({found.length})</h3>
              <ul className="plain-list">
                {found.map((c) => {
                  const own = read.owned[c.card!.scryfallId] ?? 0;
                  return (
                    <li key={c.index}>
                      <label className="check grow">
                        <input
                          type="checkbox"
                          checked={checked.has(c.index)}
                          onChange={() =>
                            setChecked((s) => {
                              const next = new Set(s);
                              if (next.has(c.index)) next.delete(c.index);
                              else next.add(c.index);
                              return next;
                            })
                          }
                        />
                        <span>
                          {c.line.quantity > 1 ? `${c.line.quantity}× ` : ""}
                          {c.card!.name}
                          <span className="muted small"> · {lineText(c)}</span>
                          {c.token && <span className="tag">Token</span>}
                          {c.basicLand && <span className="tag">Basic land</span>}
                          {c.guessed && <span className="tag warn">check</span>}
                          {own > 0 && <span className="muted small"> · you own {own} already</span>}
                        </span>
                      </label>
                    </li>
                  );
                })}
              </ul>
            </div>
            <label className="field">
              <span>Add them to</span>
              <select value={binder} onChange={(e) => setBinder(e.target.value)} disabled={!!busy}>
                <option value={NEW}>A new binder…</option>
                <option value="">Unsorted</option>
                {read.binders.map((b) => (
                  <option key={b.uid} value={b.uid}>
                    {b.name}
                  </option>
                ))}
              </select>
            </label>
            {binder === NEW && (
              <label className="field">
                <span>New binder&apos;s name</span>
                <input type="text" maxLength={60} value={newName} onChange={(e) => setNewName(e.target.value)} disabled={!!busy} />
              </label>
            )}
            {!dateKnown && (
              <label className="field">
                <span>Bought on</span>
                <input type="date" value={date} disabled={!!busy} onChange={(e) => setDate(e.target.value)} />
              </label>
            )}
          </>
        )}
        {busy && <div className="bar">{busy}</div>}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose} disabled={!!busy}>
            Cancel
          </button>
          {!read ? (
            <button className="btn primary" disabled={!!busy || !picked} onClick={readFile}>
              Read order
            </button>
          ) : (
            <button className="btn primary" disabled={!!busy || (binder === NEW && !newName.trim())} onClick={importNow}>
              {chosen.length > 0 ? `Add ${plural(copies(chosen), "card")}` : "Only remember the order"}
            </button>
          )}
        </div>
      </div>
    </Dialog>
  );
}

