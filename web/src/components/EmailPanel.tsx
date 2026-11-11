"use client";

import { useEffect, useState } from "react";
import { api, formatDate } from "./api";

// The same order as lib/weekly.ts DAY_NAMES (that file is server-side).
const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

interface Settings {
  address: string;
  confirmed: boolean;
  enabled: boolean;
  day: number;
  lastSent: number | null;
}

/** Settings → Weekly email: the address (confirmed by a link), the weekday, on or off, and a test email. */
export function EmailPanel() {
  const [loaded, setLoaded] = useState<{ settings: Settings | null; mailReady: boolean } | null>(null);
  const [address, setAddress] = useState("");
  const [day, setDay] = useState(0);
  const [enabled, setEnabled] = useState(true);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function load() {
    api<{ settings: Settings | null; mailReady: boolean }>("/api/account/email").then((r) => {
      if (!r.ok) return setError(r.data.error ?? "Could not read the email settings.");
      setLoaded(r.data);
      if (r.data.settings) {
        setAddress(r.data.settings.address);
        setDay(r.data.settings.day);
        setEnabled(r.data.settings.enabled);
      }
    });
  }
  useEffect(load, []);

  async function act(body: object) {
    setBusy(true);
    setError(null);
    setNote(null);
    const r = await api<{ note?: string }>("/api/account/email", body);
    setBusy(false);
    if (!r.ok) setError(r.data.error ?? "That didn't work.");
    else setNote(r.data.note ?? "Saved.");
    load();
  }

  const s = loaded?.settings ?? null;
  const changed = !s || s.address !== address.trim() || s.day !== day || s.enabled !== enabled;
  return (
    <section className="card stack">
      <h2 className="section-head">Weekly email</h2>
      <p className="muted small">
        Once a week: your collection&apos;s value against a week ago, the cards you own that went up or down most, wishlist cards that reached
        your alert price, and changes in your decks (new game changers, legality problems). In your price source and currency.
      </p>
      {loaded && !loaded.mailReady && <div className="bar warn">Sending email isn&apos;t set up on the website yet. You can save your address already.</div>}
      {error && <div className="bar error">{error}</div>}
      {note && <div className="bar">{note}</div>}
      <label className="stack tight">
        <span className="small">Email address</span>
        <input type="email" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="you@example.com" autoComplete="email" />
      </label>
      <label className="stack tight">
        <span className="small">Day of the week (it arrives in the morning)</span>
        <select value={day} onChange={(e) => setDay(Number(e.target.value))}>
          {DAY_NAMES.map((n, i) => (
            <option key={n} value={i}>
              {n}
            </option>
          ))}
        </select>
      </label>
      <label className="check small">
        <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
        <span>Send the weekly email</span>
      </label>
      {s && (
        <p className="muted small">
          {s.confirmed ? "Address confirmed." : "Waiting for you to click the link in the confirmation email."}
          {s.lastSent ? ` Last sent ${formatDate(s.lastSent)}.` : ""}
        </p>
      )}
      <div className="controls">
        <button className="btn primary" disabled={busy || !address.trim() || !changed} onClick={() => act({ action: "save", address: address.trim(), day, enabled })}>
          Save
        </button>
        {s && !s.confirmed && (
          <button className="btn" disabled={busy} onClick={() => act({ action: "save", address: s.address, day: s.day, enabled: s.enabled })}>
            Send the confirmation again
          </button>
        )}
        {s?.confirmed && (
          <button className="btn" disabled={busy || !loaded?.mailReady} onClick={() => act({ action: "test" })}>
            Send a test email now
          </button>
        )}
        {s && (
          <button className="btn" disabled={busy} onClick={() => window.confirm("Forget the email address and stop the weekly email?") && act({ action: "remove" })}>
            Remove
          </button>
        )}
      </div>
    </section>
  );
}
