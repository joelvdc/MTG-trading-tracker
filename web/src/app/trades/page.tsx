"use client";

import { useCallback, useEffect, useState } from "react";
import { api, formatDate } from "@/components/api";
import { TopBar } from "@/components/Nav";
import { AddTradeCardDialog, CompleteTradeDialog, TradeDialog, verdictClass, verdictText } from "@/components/TradeDialogs";
import { displayName, finishName } from "@/lib/colquery";
import { format } from "@/lib/money";
import { tradesCsv } from "@/lib/tradecsv";
import type { Side, SyncTradeJ, TradeEdit } from "@/lib/tradeedit";
import type { TradeCard, TradeLine, TradesView } from "@/lib/tradeview";

type Answer = TradesView & { editedUid?: string | null; missing?: number | null; removed?: SyncTradeJ | null };

/** The trades (the app's Trades tab): made, changed, completed, undone, deleted and exported here as on the phone. */
export default function TradesPage() {
  const [view, setView] = useState<TradesView | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ text: string; undo?: SyncTradeJ } | null>(null);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const [adding, setAdding] = useState<Side | null>(null);
  const [completing, setCompleting] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [s, t] = await Promise.all([api<{ loggedIn: boolean; folder?: string }>("/api/session"), api<TradesView & { noFolder?: boolean }>("/api/trades")]);
    if (!s.data.loggedIn || t.status === 401 || t.data.loggedOut) return window.location.replace("/login");
    if (!s.data.folder || t.data.noFolder) return window.location.replace("/settings");
    setFolder(s.data.folder ?? "");
    if (t.ok && Array.isArray(t.data.trades)) setView(t.data);
    else setError(t.data.error ?? "Could not load the trades.");
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** Sends one change; the answer (or null when it didn't work, with the reason shown). */
  async function edit(e: TradeEdit): Promise<Answer | null> {
    setBusy(true);
    setMessage(null);
    const r = await api<Answer>("/api/trades", { edit: e });
    setBusy(false);
    if (r.status === 401 || r.data.loggedOut) {
      window.location.replace("/login");
      return null;
    }
    if (r.ok && Array.isArray(r.data.trades)) {
      setView(r.data);
      return r.data;
    }
    setMessage(r.data.error ?? "That change did not work.");
    return null;
  }

  if (!view) {
    return (
      <>
        <TopBar page="trades" folder={folder} />
        <main className="page">{error ? <div className="bar error">{error}</div> : <p className="muted center">Loading…</p>}</main>
      </>
    );
  }

  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const locked = busy || view.readOnly;
  const editingTrade = editing ? view.trades.find((t) => t.uid === editing) : undefined;
  const completingTrade = completing ? view.trades.find((t) => t.uid === completing) : undefined;

  function exportCsv() {
    const url = URL.createObjectURL(new Blob([tradesCsv(view!)], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `mtg-trader-trades-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function Lines({ title, lines, total }: { title: string; lines: TradeLine[]; total: number }) {
    return (
      <div className="trade-side">
        <div className="trade-head">
          <strong>{title}</strong>
          <span>{money(total)}</span>
        </div>
        {lines.length === 0 ? (
          <p className="muted small">Nothing</p>
        ) : (
          <ul className="plain-list">
            {lines.map((l) => (
              <li key={l.key}>
                <span className="grow">
                  {l.quantity > 1 ? `${l.quantity}× ` : ""}
                  {displayName(l.card)}
                  <span className="muted small">
                    {" "}
                    · {l.card.setCode.toUpperCase()}
                    {l.finish !== "NONFOIL" ? ` · ${finishName(l)}` : ""} · {l.condition}
                    {l.language !== "EN" ? ` · ${l.language}` : ""}
                  </span>
                </span>
                <span className="num nowrap">
                  {l.unitEur != null ? money(l.unitEur * l.quantity) : "–"}
                  {l.custom && <span className="muted small"> (agreed)</span>}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  }

  function Trade({ t }: { t: TradeCard }) {
    return (
      <article className="trade">
        <header className="trade-title">
          <div>
            <strong>{t.partner || "Trade"}</strong>
            <div className="muted small">
              {formatDate(t.createdAt)}
              {t.applied ? ` · completed${t.appliedAt ? ` ${formatDate(t.appliedAt)}` : ""}${t.binderName ? `, cards in ${t.binderName}` : ""}` : " · not completed yet"}
            </div>
          </div>
          <span className={verdictClass(t)}>{verdictText(t)}</span>
        </header>
        <div className="trade-sides">
          <Lines title="You get" lines={t.lines.filter((l) => l.side === "GET")} total={t.get} />
          <Lines title="You give" lines={t.lines.filter((l) => l.side === "GIVE")} total={t.give} />
        </div>
        {t.notes && <p className="muted small">{t.notes}</p>}
        <div className="trade-actions">
          {t.applied ? (
            <button
              className="btn"
              disabled={locked}
              title="Takes the received cards out again and puts the given ones back, so the trade can be changed"
              onClick={async () => {
                if (await edit({ type: "undo", uid: t.uid })) setNotice({ text: "The trade is undone; your collection is as it was before it." });
              }}
            >
              Undo completing
            </button>
          ) : (
            <>
              <button className="btn" disabled={locked} onClick={() => setEditing(t.uid)}>
                Edit
              </button>
              <button className="btn primary" disabled={locked || t.lines.length === 0} onClick={() => setCompleting(t.uid)}>
                Complete…
              </button>
            </>
          )}
          <span className="spacer" />
          {deleting === t.uid ? (
            <>
              <span className="small">Delete this trade?{t.applied ? " Your collection stays as it is." : ""}</span>
              <button
                className="btn danger"
                disabled={locked}
                onClick={async () => {
                  setDeleting(null);
                  const r = await edit({ type: "remove", uid: t.uid });
                  if (r) setNotice({ text: `Deleted the trade${t.partner ? ` with ${t.partner}` : ""}.`, undo: r.removed ?? undefined });
                }}
              >
                Yes, delete
              </button>
              <button className="btn" onClick={() => setDeleting(null)}>
                No
              </button>
            </>
          ) : (
            <button className="btn danger" disabled={locked} onClick={() => setDeleting(t.uid)}>
              Delete
            </button>
          )}
        </div>
      </article>
    );
  }

  const open = view.trades.filter((t) => !t.applied);
  const done = view.trades.filter((t) => t.applied);

  return (
    <>
      <TopBar page="trades" folder={folder} />
      <main className="page">
        {view.readOnly && <div className="bar warn">Read only: the app on a phone is newer than this website. Changes are switched off.</div>}
        {message && (
          <div className="bar error" role="alert">
            {message}
            <button className="link" onClick={() => setMessage(null)}>
              Dismiss
            </button>
          </div>
        )}
        {notice && (
          <div className="bar ok" role="status">
            {notice.text}
            {notice.undo && (
              <button
                className="link"
                disabled={locked}
                onClick={async () => {
                  const t = notice.undo!;
                  if (await edit({ type: "restore", trade: t })) setNotice({ text: "The trade is back." });
                }}
              >
                Undo
              </button>
            )}
            <button className="link" onClick={() => setNotice(null)}>
              Dismiss
            </button>
          </div>
        )}
        <div className="controls">
          <button
            className="btn primary"
            disabled={locked}
            onClick={async () => {
              const r = await edit({ type: "new" });
              if (r?.editedUid) setEditing(r.editedUid);
            }}
          >
            New trade
          </button>
          <button className="btn" disabled={view.trades.length === 0} onClick={exportCsv}>
            Export CSV
          </button>
        </div>
        <p className="muted small">
          Prices are those saved when each card was added ({view.priceTypeLabel}); a difference up to {view.tolerancePct}% counts as fair. Completing a trade updates
          your collection; the phones get the change at their next sync.
        </p>
        {view.trades.length === 0 && <p className="muted center">No trades yet.</p>}
        {open.length > 0 && <h2>Open</h2>}
        {open.map((t) => (
          <Trade key={t.uid} t={t} />
        ))}
        {done.length > 0 && <h2>Completed</h2>}
        {done.map((t) => (
          <Trade key={t.uid} t={t} />
        ))}
      </main>

      {editingTrade && !adding && (
        <TradeDialog trade={editingTrade} view={view} busy={busy} onEdit={edit} onAdd={setAdding} onClose={() => setEditing(null)} />
      )}
      {editingTrade && adding && (
        <AddTradeCardDialog
          side={adding}
          view={view}
          busy={busy}
          onClose={() => setAdding(null)}
          onAdd={async (e) => {
            if (await edit({ type: "addLine", uid: editingTrade.uid, side: adding, ...e })) setAdding(null);
          }}
        />
      )}
      {completingTrade && (
        <CompleteTradeDialog
          trade={completingTrade}
          view={view}
          busy={busy}
          onClose={() => setCompleting(null)}
          onComplete={async (binder) => {
            const r = await edit({ type: "apply", uid: completingTrade.uid, binder });
            if (!r) return;
            setCompleting(null);
            const missing = r.missing ?? 0;
            setNotice({
              text:
                missing > 0
                  ? `Trade completed. ${missing} given card${missing === 1 ? " was" : "s were"} not in your collection, so nothing was taken out for ${missing === 1 ? "it" : "them"}.`
                  : "Trade completed; your collection is updated.",
            });
          }}
        />
      )}
    </>
  );
}
