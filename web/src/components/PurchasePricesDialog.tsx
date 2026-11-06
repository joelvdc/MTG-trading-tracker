"use client";

import { useState } from "react";
import { api } from "@/components/api";
import type { Change, Plan } from "@/lib/purchaseprices";
import { Dialog } from "./Dialog";

const CHANGE_TEXT: Record<Change, string> = {
  NEW: "new",
  AVERAGED: "averaged",
  REPLACED: "replaced",
  KEPT: "kept",
};

async function base64(f: File): Promise<string> {
  const bytes = new Uint8Array(await f.arrayBuffer());
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

/**
 * Purchase prices for cards already in the collection, from order files (the app's "Purchase prices from orders"):
 * nothing is added, only the prices of the piles the orders bought are set.
 */
export function PurchasePricesDialog({ onDone, onClose }: { onDone: (summary: string) => void; onClose: () => void }) {
  const [files, setFiles] = useState<{ data: string; name: string }[]>([]);
  const [overwrite, setOverwrite] = useState(false);
  const [choices, setChoices] = useState<Record<number, string[]>>({});
  const [p, setP] = useState<Plan | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function check(nextOverwrite = overwrite, nextChoices = choices, list = files) {
    if (list.length === 0) return;
    setBusy(true);
    setError(null);
    const r = await api<{ plan?: Plan }>("/api/purchaseprices", { files: list, overwrite: nextOverwrite, choices: nextChoices });
    setBusy(false);
    if (r.ok && r.data.plan) setP(r.data.plan);
    else setError(r.data.error ?? "Could not read the files.");
  }

  async function apply() {
    setBusy(true);
    setError(null);
    const r = await api<{ changed?: number }>("/api/purchaseprices", { files, overwrite, choices, apply: true });
    setBusy(false);
    if (r.ok && r.data.changed !== undefined) onDone(`Purchase prices set on ${r.data.changed} pile${r.data.changed === 1 ? "" : "s"}.`);
    else setError(r.data.error ?? "Setting the prices did not work.");
  }

  const changing = p?.rows.filter((r) => r.change !== "KEPT") ?? [];

  return (
    <Dialog title="Purchase prices from orders" onClose={() => !busy && onClose()}>
      <div className="stack">
        <p className="muted small">
          For cards you already have: pick order files (CardTrader&apos;s Excel export, Cardmarket&apos;s shipment or monthly files) and
          each card they bought gets what you paid as its purchase price. Nothing is added to the collection.
        </p>
        <label className="field">
          <span>Order files</span>
          <input
            type="file"
            multiple
            accept=".xls,.xlsx,.csv,text/csv"
            disabled={busy}
            onChange={async (e) => {
              const picked = [...(e.target.files ?? [])];
              const list = await Promise.all(picked.map(async (f) => ({ data: await base64(f), name: f.name })));
              setFiles(list);
              setChoices({});
              setP(null);
              await check(overwrite, {}, list);
            }}
          />
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={overwrite}
            disabled={busy}
            onChange={(e) => {
              setOverwrite(e.target.checked);
              check(e.target.checked);
            }}
          />
          <span>Replace prices the cards have already (else a pile with more copies than were bought gets the average)</span>
        </label>
        {p && (
          <div className="order-review">
            <h3 className="mini-head">
              {changing.length === 1 ? "1 pile gets a new price" : `${changing.length} piles get a new price`}
            </h3>
            {p.rows.length > 0 && (
              <ul className="plain-list">
                {p.rows.map((r) => (
                  <li key={r.uid}>
                    <span className="grow">
                      {r.name}
                      <span className="muted small">
                        {" "}
                        · {r.setCode.toUpperCase()} #{r.number}
                        {r.foil ? " · foil" : ""} · {r.condition} · {r.language} · {r.quantity} owned, {r.bought} bought
                      </span>
                    </span>
                    <span className="num nowrap">
                      {r.had != null && r.change !== "NEW" ? `€${r.had.toFixed(2)} → ` : ""}€{r.newPrice.toFixed(2)}
                      <span className={r.change === "KEPT" ? "tag" : "tag warn"}>{CHANGE_TEXT[r.change]}</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {p.open.length > 0 && (
              <>
                <h3 className="mini-head warn-text">Which cards did these buy? ({p.open.length})</h3>
                {p.open.map((o) => (
                  <div key={o.index} className="stack tight">
                    <div className="small">
                      <strong>{o.label}</strong>
                      {o.why && <span className="muted"> · {o.why}</span>}
                    </div>
                    {o.candidates.map((c) => (
                      <label key={c.uid} className="check small">
                        <input
                          type="checkbox"
                          disabled={busy}
                          checked={(choices[o.index] ?? []).includes(c.uid)}
                          onChange={(e) => {
                            const had = choices[o.index] ?? [];
                            const next = { ...choices, [o.index]: e.target.checked ? [...had, c.uid] : had.filter((u) => u !== c.uid) };
                            setChoices(next);
                            check(overwrite, next);
                          }}
                        />
                        <span>{c.text}</span>
                      </label>
                    ))}
                  </div>
                ))}
              </>
            )}
            {p.notOwned.length > 0 && (
              <>
                <h3 className="mini-head">Not in the collection ({p.notOwned.length})</h3>
                <ul className="plain-list">
                  {p.notOwned.map((n, i) => (
                    <li key={i} className="muted small">
                      {n}
                    </li>
                  ))}
                </ul>
              </>
            )}
            {p.withoutPrice > 0 && <p className="muted small">{p.withoutPrice} lines without a price were skipped.</p>}
          </div>
        )}
        {busy && <div className="bar">Working…</div>}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn primary" disabled={busy || changing.length === 0} onClick={apply}>
            Set {changing.length} price{changing.length === 1 ? "" : "s"}
          </button>
        </div>
      </div>
    </Dialog>
  );
}
