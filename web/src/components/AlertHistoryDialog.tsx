"use client";

import { useEffect, useState } from "react";
import { api } from "@/components/api";
import { Dialog } from "@/components/Dialog";
import type { AlertEvent } from "@/lib/accounts";

const TYPE_LABELS: Record<string, string> = {
  trend: "trend price",
  avg: "average sell price",
  avg30: "30-day average",
  avg7: "7-day average",
  avg1: "1-day average",
  low: "lowest listing",
};

/** "Cardmarket's lowest listing (MH2)" (AlertHistory.where, without CardTrader). */
export function alertWhere(e: AlertEvent): string {
  const type = e.basis.startsWith("cardmarket:") ? e.basis.slice("cardmarket:".length) : "trend";
  return `Cardmarket's ${TYPE_LABELS[type] ?? type}` + (e.setCode ? ` (${e.setCode.toUpperCase()})` : "");
}

/**
 * Wishlist → Alert history: the price alerts the website found reached at night (a year, at most 500 lines). The
 * phones keep their own history, with CardTrader's offers too.
 */
export function AlertHistoryDialog({ money, onClose }: { money: (eur: number) => string; onClose: () => void }) {
  const [events, setEvents] = useState<AlertEvent[] | null>(null);
  const [available, setAvailable] = useState(true);

  useEffect(() => {
    (async () => {
      const r = await api<{ events?: AlertEvent[]; available?: boolean }>("/api/wishlist/alerts");
      setAvailable(r.data.available !== false);
      setEvents(Array.isArray(r.data.events) ? r.data.events : []);
    })();
  }, []);

  const date = (ms: number) => new Date(ms).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });

  return (
    <Dialog title="Alert history" onClose={onClose}>
      <p className="muted small">
        Each night the website checks your alert levels against Cardmarket&apos;s prices. A card shows up here once when its price reaches your
        level, and again only after it has been above the level in between. Your phones keep their own history, with CardTrader too.
      </p>
      {!available ? (
        <p className="muted">The alert history needs an MTG Trader account (log in with one instead of Nextcloud).</p>
      ) : events === null ? (
        <p className="muted">Loading…</p>
      ) : events.length === 0 ? (
        <p className="muted">No alerts reached yet.</p>
      ) : (
        <ul className="alert-list">
          {events.map((e, i) => (
            <li key={`${e.wishUid}-${e.at}-${i}`}>
              <strong>{e.cardName}</strong>
              <div className="muted small">
                Reached {money(e.price)} on {date(e.at)} · your level {money(e.level)} · {alertWhere(e)}
              </div>
            </li>
          ))}
        </ul>
      )}
      <div className="dialog-actions">
        <button className="btn" onClick={onClose}>
          Close
        </button>
      </div>
    </Dialog>
  );
}
