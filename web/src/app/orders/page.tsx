"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/components/api";
import { Dialog } from "@/components/Dialog";
import { TopBar } from "@/components/Nav";
import { OrderImportDialog } from "@/components/OrderImportDialog";
import { PreconDialog } from "@/components/PreconDialog";
import { PurchasePricesDialog } from "@/components/PurchasePricesDialog";
import { format, type Currency } from "@/lib/money";
import type { OrderEdit, OrderRow, OrdersView } from "@/lib/orders";

function niceDate(iso: string): string {
  const d = new Date(`${iso}T12:00:00`);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
}

/** The order history (the app's Orders screen): what was bought, what it cost and what it's worth now. */
export default function OrdersPage() {
  const [view, setView] = useState<OrdersView | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [open, setOpen] = useState<Set<string>>(new Set());
  const [importing, setImporting] = useState(false);
  const [precon, setPrecon] = useState(false);
  const [pricing, setPricing] = useState(false);
  const [dating, setDating] = useState<OrderRow | null>(null);
  const [deleting, setDeleting] = useState<OrderRow | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const [s, o] = await Promise.all([
      api<{ loggedIn: boolean; folder?: string }>("/api/session"),
      api<OrdersView & { noFolder?: boolean }>("/api/orders"),
    ]);
    if (!s.data.loggedIn || o.status === 401 || o.data.loggedOut) return window.location.replace("/login");
    if (!s.data.folder || o.data.noFolder) return window.location.replace("/settings");
    setFolder(s.data.folder ?? "");
    if (o.ok && Array.isArray(o.data.orders)) setView(o.data);
    else setError(o.data.error ?? "Could not load the orders.");
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function edit(e: OrderEdit) {
    setBusy(true);
    setError(null);
    const r = await api<OrdersView>("/api/orders", { edit: e });
    setBusy(false);
    if (r.ok && Array.isArray(r.data.orders)) {
      setView(r.data);
      return true;
    }
    setError(r.data.error ?? "That change did not work.");
    return false;
  }

  if (!view) {
    return (
      <>
        <TopBar page="orders" folder={folder} />
        <main className="page">{error ? <div className="bar error">{error}</div> : <p className="muted center">Loading…</p>}</main>
      </>
    );
  }

  const money = (eur: number) => format(eur * view.perEuro, view.currency as Currency);
  const total = view.orders.reduce(
    (t, o) => ({ paid: t.paid + o.paid, value: t.value + o.worth.value, pricedPaid: t.pricedPaid + o.worth.paid, cards: t.cards + o.cards }),
    { paid: 0, value: 0, pricedPaid: 0, cards: 0 },
  );
  const gain = (value: number, paid: number) => {
    if (paid <= 0) return null;
    const d = value - paid;
    const pct = (d / paid) * 100;
    return (
      <span className={d >= 0 ? "verdict up" : "verdict down"}>
        {d >= 0 ? "+" : "−"}
        {money(Math.abs(d))} ({d >= 0 ? "+" : "−"}
        {Math.abs(pct).toFixed(0)}%)
      </span>
    );
  };

  return (
    <>
      <TopBar page="orders" folder={folder} />
      <main className="page">
        <div className="inline">
          <p className="muted small grow">
            Orders from CardTrader and Cardmarket, and preconstructed decks: what they cost, and what their cards are worth now (
            {view.priceTypeLabel}). Cards without a price today are left out of both sides.
          </p>
          <button className="btn primary" disabled={view.readOnly || busy} onClick={() => setImporting(true)}>
            Import an order…
          </button>
          <button className="btn" disabled={view.readOnly || busy} onClick={() => setPrecon(true)}>
            Add a precon…
          </button>
          <button className="btn" disabled={view.readOnly || busy} onClick={() => setPricing(true)} title="Purchase prices for cards you already have">
            Prices from orders…
          </button>
        </div>
        {view.readOnly && <div className="bar warn">Read only: the app on a phone is newer than this website.</div>}
        {notice && (
          <div className="bar ok" role="status">
            {notice}
            <button className="link" onClick={() => setNotice(null)}>
              Dismiss
            </button>
          </div>
        )}
        {error && <div className="bar error">{error}</div>}
        {view.orders.length === 0 ? (
          <p className="muted center">No orders yet. Import one, or add a precon.</p>
        ) : (
          <section className="card stack">
            <div className="inline">
              <strong className="grow">
                {view.orders.length} order{view.orders.length === 1 ? "" : "s"} · {total.cards} cards · paid {money(total.paid)}
              </strong>
              <span>worth {money(total.value)}</span>
              {gain(total.value, total.pricedPaid)}
            </div>
          </section>
        )}
        {view.orders.map((o) => {
          const shown = open.has(o.uid);
          return (
            <article key={o.uid} className="trade">
              <header className="trade-title">
                <div>
                  <strong>{o.title}</strong>
                  <div className="muted small">
                    {niceDate(o.date)} · {o.shop} · {o.cards} card{o.cards === 1 ? "" : "s"}
                    {o.worth.unpriced > 0 ? ` · ${o.worth.unpriced} without a price today` : ""}
                  </div>
                </div>
                <div className="order-sums">
                  <span>paid {money(o.paid)}</span>
                  <span>worth {money(o.worth.value)}</span>
                  {gain(o.worth.value, o.worth.paid)}
                </div>
              </header>
              <div className="inline">
                <button
                  className="link"
                  onClick={() =>
                    setOpen((s) => {
                      const n = new Set(s);
                      if (n.has(o.uid)) n.delete(o.uid);
                      else n.add(o.uid);
                      return n;
                    })
                  }
                >
                  {shown ? "Hide the cards" : "Show the cards"}
                </button>
                <span className="spacer" />
                <button className="btn small-btn" disabled={view.readOnly || busy} onClick={() => setDating(o)}>
                  Change date
                </button>
                <button className="btn small-btn" disabled={view.readOnly || busy} onClick={() => setDeleting(o)}>
                  Delete
                </button>
              </div>
              {shown && (
                <ul className="plain-list">
                  {o.lines.map((l, i) => (
                    <li key={i}>
                      <span className="grow">
                        {l.quantity > 1 ? `${l.quantity}× ` : ""}
                        {l.name}
                        <span className="muted small">
                          {l.setCode ? ` · ${l.setCode.toUpperCase()}${l.number ? ` #${l.number}` : ""}` : ""}
                          {l.foil ? " · foil" : ""}
                          {l.condition ? ` · ${l.condition}` : ""}
                          {l.language && l.language !== "EN" ? ` · ${l.language}` : ""}
                        </span>
                      </span>
                      <span className="num nowrap">
                        {l.price != null ? money(l.price) : "–"}
                        <span className="muted small"> → {l.now != null ? money(l.now) : "–"}</span>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </article>
          );
        })}
      </main>
      {importing && (
        <OrderImportDialog
          onClose={() => setImporting(false)}
          onDone={(text) => {
            setImporting(false);
            setNotice(text);
            load();
          }}
        />
      )}
      {precon && (
        <PreconDialog
          onClose={() => setPrecon(false)}
          onDone={(text) => {
            setPrecon(false);
            setNotice(text);
            load();
          }}
        />
      )}
      {pricing && (
        <PurchasePricesDialog
          onClose={() => setPricing(false)}
          onDone={(text) => {
            setPricing(false);
            setNotice(text);
          }}
        />
      )}
      {dating && <DateDialog order={dating} busy={busy} onClose={() => setDating(null)} onSave={async (d) => (await edit({ type: "date", uid: dating.uid, date: d })) && setDating(null)} />}
      {deleting && (
        <Dialog title="Delete this order?" onClose={() => setDeleting(null)}>
          <div className="stack">
            <p>
              “{deleting.title}” leaves the order history on every phone. Its cards stay in the collection, with their purchase prices.
            </p>
            <div className="dialog-actions">
              <span className="spacer" />
              <button className="btn" onClick={() => setDeleting(null)}>
                Cancel
              </button>
              <button className="btn danger" disabled={busy} onClick={async () => (await edit({ type: "delete", uid: deleting.uid })) && setDeleting(null)}>
                Delete
              </button>
            </div>
          </div>
        </Dialog>
      )}
    </>
  );
}

function DateDialog({ order, busy, onSave, onClose }: { order: OrderRow; busy: boolean; onSave: (d: string) => void; onClose: () => void }) {
  const [d, setD] = useState(order.date);
  return (
    <Dialog title="When was it bought?" onClose={onClose}>
      <div className="stack">
        <label className="field">
          <span>{order.title}</span>
          <input type="date" value={d} onChange={(e) => setD(e.target.value)} />
        </label>
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn primary" disabled={busy || !d || d === order.date} onClick={() => onSave(d)}>
            Save
          </button>
        </div>
      </div>
    </Dialog>
  );
}
