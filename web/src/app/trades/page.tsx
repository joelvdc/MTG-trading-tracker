"use client";

import { useEffect, useState } from "react";
import { api, formatDate } from "@/components/api";
import { TopBar } from "@/components/Nav";
import { displayName, finishName } from "@/lib/colquery";
import { format } from "@/lib/money";
import type { TradeCard, TradeLine, TradesView, Verdict } from "@/lib/tradeview";

const VERDICTS: Record<Verdict, string> = {
  EMPTY: "No cards yet",
  FAIR: "Fair trade",
  FAVORS_YOU: "In your favour",
  FAVORS_THEM: "In their favour",
};

/** The trades from the app, to look at (they are made and applied in the app). */
export default function TradesPage() {
  const [view, setView] = useState<TradesView | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const [s, t] = await Promise.all([
        api<{ loggedIn: boolean; folder?: string }>("/api/session"),
        api<TradesView & { noFolder?: boolean }>("/api/trades"),
      ]);
      if (!s.data.loggedIn || t.status === 401 || t.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || t.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (t.ok && Array.isArray(t.data.trades)) setView(t.data);
      else setError(t.data.error ?? "Could not load the trades.");
    })();
  }, []);

  if (!view) {
    return (
      <>
        <TopBar page="trades" folder={folder} />
        <main className="page">{error ? <div className="bar error">{error}</div> : <p className="muted center">Loading…</p>}</main>
      </>
    );
  }

  const money = (eur: number) => format(eur * view.perEuro, view.currency);

  function Side({ title, lines, total }: { title: string; lines: TradeLine[]; total: number }) {
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
            {lines.map((l, i) => (
              <li key={i}>
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
    const get = t.lines.filter((l) => l.side === "GET");
    const give = t.lines.filter((l) => l.side === "GIVE");
    const cls = t.verdict === "FAIR" ? "verdict fair" : t.verdict === "FAVORS_YOU" ? "verdict up" : t.verdict === "FAVORS_THEM" ? "verdict down" : "verdict";
    return (
      <article className="trade">
        <header className="trade-title">
          <div>
            <strong>{t.partner || "Trade"}</strong>
            <div className="muted small">
              {formatDate(t.createdAt)}
              {t.applied ? ` · applied${t.appliedAt ? ` ${formatDate(t.appliedAt)}` : ""}` : " · not applied yet"}
            </div>
          </div>
          <span className={cls}>
            {VERDICTS[t.verdict]}
            {t.verdict !== "EMPTY" && t.verdict !== "FAIR" ? ` (${Math.abs(t.pct).toFixed(1)}%)` : ""}
          </span>
        </header>
        <div className="trade-sides">
          <Side title="You get" lines={get} total={t.get} />
          <Side title="You give" lines={give} total={t.give} />
        </div>
        {t.notes && <p className="muted small">{t.notes}</p>}
      </article>
    );
  }

  const open = view.trades.filter((t) => !t.applied);
  const done = view.trades.filter((t) => t.applied);

  return (
    <>
      <TopBar page="trades" folder={folder} />
      <main className="page">
        <p className="muted small">
          Trades are made and applied in the app; here you can look at them. Prices are those saved when each card was added
          ({view.priceTypeLabel}); a difference up to {view.tolerancePct}% counts as fair.
        </p>
        {view.trades.length === 0 && <p className="muted center">No trades yet.</p>}
        {open.length > 0 && <h2>Open</h2>}
        {open.map((t) => (
          <Trade key={t.uid} t={t} />
        ))}
        {done.length > 0 && <h2>Applied</h2>}
        {done.map((t) => (
          <Trade key={t.uid} t={t} />
        ))}
      </main>
    </>
  );
}
