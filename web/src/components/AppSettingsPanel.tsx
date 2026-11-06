"use client";

import { useEffect, useState } from "react";
import { api } from "@/components/api";
import { CURRENCY_CHOICES, POWER_SOURCE_CHOICES, PRICE_SOURCES, PRICE_TYPE_CHOICES, RECEIVED_CLEAR, type AppSettings } from "@/lib/appsettings";
import { CONDITIONS } from "@/lib/cards";

/** The app's synced settings: changed here, the phones take them at their next sync. */
export function AppSettingsPanel() {
  const [saved, setSaved] = useState<AppSettings | null>(null);
  const [s, setS] = useState<AppSettings | null>(null);
  const [readOnly, setReadOnly] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    api<{ settings?: AppSettings; readOnly?: boolean }>("/api/appsettings").then((r) => {
      if (r.ok && r.data.settings) {
        setSaved(r.data.settings);
        setS(r.data.settings);
        setReadOnly(!!r.data.readOnly);
      } else setMessage({ ok: false, text: r.data.error ?? "Could not read the app's settings." });
    });
  }, []);

  if (!s || !saved) {
    return (
      <section className="card stack">
        <h2 className="section-head">App settings</h2>
        {message ? <div className="bar error">{message.text}</div> : <p className="muted">Loading…</p>}
      </section>
    );
  }

  const set = <K extends keyof AppSettings>(k: K, v: AppSettings[K]) => {
    setMessage(null);
    setS({ ...s, [k]: v });
  };
  const changes = Object.fromEntries(
    (Object.keys(s) as (keyof AppSettings)[]).filter((k) => JSON.stringify(s[k]) !== JSON.stringify(saved[k])).map((k) => [k, s[k]]),
  ) as Partial<AppSettings>;
  const dirty = Object.keys(changes).length > 0;

  async function save() {
    setBusy(true);
    const r = await api<{ settings?: AppSettings }>("/api/appsettings", { changes });
    setBusy(false);
    if (r.ok && r.data.settings) {
      setSaved(r.data.settings);
      setS(r.data.settings);
      setMessage({ ok: true, text: "Saved. The phones take the new settings at their next sync." });
    } else setMessage({ ok: false, text: r.data.error ?? "Could not save the settings." });
  }

  const select = (k: "priceSource" | "priceType" | "currency" | "alertPriceType" | "receivedClear" | "powerSource", label: string, list: [string, string][]) => (
    <label className="field">
      <span>{label}</span>
      <select value={s[k]} disabled={readOnly} onChange={(e) => set(k, e.target.value)}>
        {list.map(([v, l]) => (
          <option key={v} value={v}>
            {l}
          </option>
        ))}
      </select>
    </label>
  );
  const check = (k: "conditionAdjust" | "wishSections" | "receivedToCollection", label: string) => (
    <label className="check">
      <input type="checkbox" checked={s[k]} disabled={readOnly} onChange={(e) => set(k, e.target.checked)} />
      <span>{label}</span>
    </label>
  );

  return (
    <section className="card stack">
      <h2 className="section-head">App settings</h2>
      <p className="muted small">The app&apos;s settings that every phone shares. Changes here reach the phones at their next sync.</p>
      {readOnly && <div className="bar warn">Read only: the app on a phone is newer than this website.</div>}
      <h3 className="mini-head">Prices</h3>
      <div className="form-grid">
        {select("priceSource", "Price source", PRICE_SOURCES)}
        {select("priceType", "Cardmarket price", PRICE_TYPE_CHOICES)}
        {select("currency", "Currency", CURRENCY_CHOICES)}
        {select("alertPriceType", "Price alerts compare", PRICE_TYPE_CHOICES)}
      </div>
      {check("conditionAdjust", "Lower prices for the card's condition")}
      {s.conditionAdjust && (
        <div className="form-grid">
          {CONDITIONS.map(([c, name]) => (
            <label key={c} className="field">
              <span>{name} (% of near mint)</span>
              <input
                type="number"
                min={0}
                max={100}
                disabled={readOnly}
                value={s.conditionPcts[c] ?? 100}
                onChange={(e) => set("conditionPcts", { ...s.conditionPcts, [c]: Math.max(0, Math.min(100, Math.round(Number(e.target.value) || 0))) })}
              />
            </label>
          ))}
        </div>
      )}
      <h3 className="mini-head">Trades</h3>
      <label className="field">
        <span>Fair trade margin: ±{s.tolerancePct}%</span>
        <input type="range" min={0} max={20} step={1} disabled={readOnly} value={s.tolerancePct} onChange={(e) => set("tolerancePct", Number(e.target.value))} />
      </label>
      <h3 className="mini-head">Wishlist</h3>
      {check("wishSections", "Ordered cards in their own section")}
      {check("receivedToCollection", "Add received cards to the collection")}
      {select("receivedClear", "Clear received cards", RECEIVED_CLEAR)}
      <h3 className="mini-head">Decks</h3>
      <div className="form-grid">
        {select("powerSource", "Power level from", POWER_SOURCE_CHOICES)}
        <label className="field">
          <span>Your Archidekt username</span>
          <input type="text" maxLength={100} disabled={readOnly} value={s.ownArchidektUser} placeholder="Whose decks are your own" onChange={(e) => set("ownArchidektUser", e.target.value)} />
        </label>
      </div>
      <p className="muted small">
        The phones work out the power level from the chosen source at their next re-score. The username decides which Archidekt decks are listed as your own.
      </p>
      {message && <div className={message.ok ? "bar ok" : "bar error"}>{message.text}</div>}
      <div className="inline">
        <button className="btn primary" disabled={busy || readOnly || !dirty} onClick={save}>
          Save settings
        </button>
        {dirty && (
          <button className="btn" disabled={busy} onClick={() => setS(saved)}>
            Undo changes
          </button>
        )}
      </div>
    </section>
  );
}

/** The website's daily copies of the sync file, to download (restoring one is done in the app). */
export function BackupsPanel() {
  const [list, setList] = useState<{ backups: string[]; folder: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    api<{ backups?: string[]; folder?: string }>("/api/backups").then((r) => {
      if (r.ok && Array.isArray(r.data.backups)) setList({ backups: r.data.backups, folder: r.data.folder ?? "" });
      else setError(r.data.error ?? "Could not list the backups.");
    });
  }, []);
  return (
    <section className="card stack">
      <h2 className="section-head">Website backups</h2>
      <p className="muted small">
        Before its first change of a day, the website keeps a copy of the sync file (the last 10 days). To go back to one, download
        it and restore it in the app: Settings → Backups → Restore from a file…
      </p>
      {error && <div className="bar error">{error}</div>}
      {list && list.backups.length === 0 && <p className="muted">No backups yet: the website hasn&apos;t changed anything.</p>}
      {list && list.backups.length > 0 && (
        <ul className="plain-list">
          {list.backups.map((n) => (
            <li key={n}>
              <a href={`/api/backups?name=${encodeURIComponent(n)}`} download>
                {n.slice(5, 15)}
              </a>{" "}
              <span className="muted small">{n}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
