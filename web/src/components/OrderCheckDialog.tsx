"use client";

import { useState } from "react";
import { api } from "@/components/api";
import { FORMAT_LABELS, shopOf, type Candidate } from "@/lib/orders";
import {
  additions,
  byHand,
  countOf,
  extraCount,
  isProblem,
  lineLabel,
  lineStatus,
  orderedCount,
  problemLines,
  report,
  STATUSES,
  STATUS_LABELS,
  type CheckLine,
  type CheckResult,
} from "@/lib/packagecheck";
import { Dialog } from "./Dialog";
import { base64, NEW, plural, suggestedBinder, today, type ReadAnswer } from "./OrderImportDialog";

const SHOWN = ["RECEIVED", "BY_HAND", "PRINTING_UNSURE", "WRONG_LANGUAGE", "WRONG_PRINTING", "MISSING"] as const;

/**
 * Check an order (the app's PackageCheckScreen, since 1.32): compares the scanned cards with the order's file, lists
 * what's missing, in the wrong language or printing, and what wasn't ordered, with a problem report to copy for the
 * shop and "Add to collection…" with the order's prices, condition and finish.
 */
export function OrderCheckDialog({ onDone, onClose }: { onDone: (summary: string) => void; onClose: () => void }) {
  const [picked, setPicked] = useState<File | null>(null);
  const [read, setRead] = useState<ReadAnswer | null>(null);
  const [result, setResult] = useState<CheckResult | null>(null);
  const [accepted, setAccepted] = useState<Set<number>>(new Set());
  const [adding, setAdding] = useState(false);
  const [binder, setBinder] = useState<string>(NEW);
  const [newName, setNewName] = useState("");
  const [extras, setExtras] = useState(true);
  const [date, setDate] = useState(today());
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function check(r: ReadAnswer, acc: Set<number>) {
    const c = await api<{ result?: CheckResult }>("/api/scans/check", { action: "check", file: r.file, candidates: r.candidates, accepted: [...acc] });
    if (!c.ok || !c.data.result) {
      setError(c.data.error ?? "Could not check the order.");
      return false;
    }
    setResult(c.data.result);
    return true;
  }

  async function readFile() {
    if (!picked) return;
    if (picked.size > 8_000_000) return setError("That file is bigger than 8 MB.");
    setBusy("Reading the order and finding its cards on Scryfall… (a big Cardmarket order takes a minute or two)");
    setError(null);
    const r = await api<ReadAnswer>("/api/orders/read", { data: await base64(picked), name: picked.name });
    if (!r.ok || !r.data.candidates) {
      setBusy(null);
      return setError(r.data.error ?? "Could not read the order.");
    }
    setRead(r.data);
    setNewName(suggestedBinder(picked.name, shopOf(r.data.file.format)));
    await check(r.data, new Set());
    setBusy(null);
  }

  async function mark(index: number, on: boolean) {
    if (!read) return;
    const next = new Set(accepted);
    if (on) next.add(index);
    else next.delete(index);
    setBusy("Checking…");
    if (await check(read, next)) setAccepted(next);
    setBusy(null);
  }

  async function add() {
    if (!read) return;
    setBusy("Adding the cards…");
    setError(null);
    const r = await api<{ added?: number; newOrders?: number }>("/api/scans/check", {
      action: "add",
      file: read.file,
      candidates: read.candidates,
      accepted: [...accepted],
      extras,
      binder: binder === NEW || binder === "" ? null : binder,
      newBinder: binder === NEW ? newName.trim() : null,
      date,
    });
    setBusy(null);
    if (!r.ok || r.data.added === undefined) return setError(r.data.error ?? "Adding the cards did not work.");
    const where = binder === NEW ? newName.trim() : binder === "" ? "Unsorted" : (read.binders.find((b) => b.uid === binder)?.name ?? "the binder");
    onDone(
      `Added ${plural(r.data.added, "card")} to ${where}; they're off the scan list. ` +
        (r.data.newOrders ? "The order is in the history." : "The order was in the history already; it's updated."),
    );
  }

  async function copyReport() {
    if (!result || !picked) return;
    try {
      await navigator.clipboard.writeText(report(result, picked.name));
      setCopied(true);
    } catch {
      setError("The browser didn't allow copying.");
    }
  }

  const scanned = result ? result.lines.reduce((n, l) => n + l.parts.filter((p) => p.scan).reduce((m, p) => m + p.quantity, 0), 0) + extraCount(result) : 0;
  const lines = result ? [...result.lines].sort((a, b) => STATUSES.indexOf(lineStatus(b)) - STATUSES.indexOf(lineStatus(a)) || a.cand.index - b.cand.index) : [];
  const toAdd = result ? additions(result, extras).reduce((n, x) => n + x.quantity, 0) : 0;
  const dateKnown = read ? read.file.lines.every((l) => l.date) : false;

  return (
    <Dialog title={read ? `Check ${FORMAT_LABELS[read.file.format]}` : "Check an order"} onClose={() => !busy && onClose()}>
      <div className="stack">
        {!read && (
          <>
            <p className="muted small">
              Compares the scanned cards with the order&apos;s file: CardTrader&apos;s order export (Excel), or Cardmarket&apos;s
              “ArticlesFromShipment” or “Purchased Articles” file (CSV). It lists missing cards, wrong languages or printings and
              cards nobody ordered, with a problem report to copy for the shop. The camera can&apos;t see foil, condition, signed or
              altered: those lines say to check by hand.
            </p>
            <label className="field">
              <span>Order file</span>
              <input type="file" accept=".xls,.xlsx,.csv,text/csv" disabled={!!busy} onChange={(e) => setPicked(e.target.files?.[0] ?? null)} />
            </label>
          </>
        )}
        {result && read && (
          <>
            <p className="small">
              <strong>
                {plural(orderedCount(result), "card")} ordered · {scanned} scanned
              </strong>{" "}
              <span className="muted">· {picked?.name}</span>
            </p>
            <div className="check-counts small">
              {SHOWN.filter((s) => countOf(result, s) > 0).map((s) => (
                <span key={s} className={isProblem(s) ? "warn-text" : "ok-text"}>
                  {STATUS_LABELS[s]}: {countOf(result, s)}
                </span>
              ))}
              {extraCount(result) > 0 && <span>Not in the order: {extraCount(result)}</span>}
            </div>
            {problemLines(result).length === 0 && <p className="ok-text small">Everything ordered was scanned.</p>}
            <div className="order-review">
              <ul className="plain-list check-lines">
                {lines.map((l) => (
                  <CheckRow key={l.cand.index} l={l} accepted={accepted.has(l.cand.index)} busy={!!busy} onMark={(on) => mark(l.cand.index, on)} />
                ))}
              </ul>
              {result.extras.length > 0 && (
                <>
                  <h3 className="mini-head">Scanned, not in the order ({extraCount(result)})</h3>
                  <ul className="plain-list check-lines">
                    {result.extras.map((x, i) => (
                      <li key={i}>
                        <span className="grow">
                          {x.quantity > 1 ? `${x.quantity}× ` : ""}
                          {x.scan.card.name}
                          <span className="muted small">
                            {" "}
                            · {x.scan.card.setCode.toUpperCase()} #{x.scan.card.collectorNumber}
                            {x.scan.language && x.scan.language !== "EN" ? ` · ${x.scan.language}` : ""}
                            {x.scan.exactPrinting === false ? " · printing guessed by the scanner" : ""}
                          </span>
                        </span>
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </div>
            {adding && (
              <div className="stack check-add">
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
                {result.extras.length > 0 && (
                  <label className="check small">
                    <input type="checkbox" checked={extras} onChange={(e) => setExtras(e.target.checked)} />
                    <span>Also the {plural(extraCount(result), "scanned card")} that aren&apos;t in the order</span>
                  </label>
                )}
                {!dateKnown && (
                  <label className="field">
                    <span>Bought on</span>
                    <input type="date" value={date} disabled={!!busy} onChange={(e) => setDate(e.target.value)} />
                  </label>
                )}
                <p className="muted small">
                  {plural(toAdd, "card")} go in with the order&apos;s price, condition and finish (cards in the wrong language or printing as
                  scanned), and leave the scan list. Missing cards aren&apos;t added.
                </p>
              </div>
            )}
          </>
        )}
        {busy && <p className="muted small">{busy}</p>}
        {error && <div className="bar error">{error}</div>}
        <div className="inline">
          {!read && (
            <button className="btn primary" disabled={!picked || !!busy} onClick={readFile}>
              Check
            </button>
          )}
          {result && !adding && (
            <>
              <button className="btn primary" disabled={!!busy || additions(result, true).length === 0} onClick={() => setAdding(true)}>
                Add to collection…
              </button>
              <button className="btn" disabled={!!busy} onClick={copyReport}>
                {copied ? "Copied" : "Copy the problem report"}
              </button>
            </>
          )}
          {result && adding && (
            <button className="btn primary" disabled={!!busy || toAdd === 0 || (binder === NEW && !newName.trim())} onClick={add}>
              Add {plural(toAdd, "card")}
            </button>
          )}
          <button className="btn" disabled={!!busy} onClick={adding ? () => setAdding(false) : onClose}>
            {adding ? "Back" : "Close"}
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** One order line with its result: received, wrong language or printing, missing, and "It's here" for a problem. */
function CheckRow({ l, accepted, busy, onMark }: { l: CheckLine; accepted: boolean; busy: boolean; onMark: (on: boolean) => void }) {
  const st = lineStatus(l);
  const c: Candidate = l.cand;
  const card = c.guessed ? null : c.card;
  const where = card ? `${card.setCode.toUpperCase()} #${card.collectorNumber}` : [c.line.setCode, c.line.number ? `#${c.line.number}` : ""].filter(Boolean).join(" ");
  const hand = byHand(l);
  const wrong = l.parts.filter((p) => p.scan && (p.status === "WRONG_LANGUAGE" || p.status === "WRONG_PRINTING"));
  return (
    <li className={isProblem(st) ? "check-line problem" : "check-line"}>
      <span className="grow">
        {c.line.quantity > 1 ? `${c.line.quantity}× ` : ""}
        {card?.name ?? lineLabel(c.line)}
        <span className="muted small">
          {where ? ` · ${where}` : ""}
          {c.line.versionKnown && c.line.languageKnown && c.line.language !== "EN" ? ` · ${c.line.language}` : ""}
        </span>
        <div className="small">
          <span className={isProblem(st) ? "warn-text" : "ok-text"}>
            {l.parts.length > 1
              ? l.parts.map((p) => `${STATUS_LABELS[p.status]} ${p.quantity}`).join(" · ")
              : STATUS_LABELS[st]}
          </span>
          {wrong.map((p, i) => (
            <span key={i} className="muted">
              {" "}
              · got {p.status === "WRONG_LANGUAGE" ? p.scan!.language : `${p.scan!.card.setCode.toUpperCase()} #${p.scan!.card.collectorNumber}`}
            </span>
          ))}
        </div>
        {hand.length > 0 && <div className="small foil-text">Check by hand: {hand.join(", ")}</div>}
      </span>
      {isProblem(st) && !accepted && (
        <button className="link small" disabled={busy} onClick={() => onMark(true)}>
          It&apos;s here
        </button>
      )}
      {accepted && (
        <button className="link small" disabled={busy} onClick={() => onMark(false)}>
          Undo “it&apos;s here”
        </button>
      )}
    </li>
  );
}
