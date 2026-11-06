"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "./api";

interface DeviceRow {
  id: string;
  name: string;
  createdAt: number;
  lastUsed: number;
}

interface AccountInfo {
  username: string;
  admin: boolean;
  device: string;
  devices: DeviceRow[];
  backup: { server: string; folder: string } | null;
  syncedAt: number | null;
  accounts?: { id: number; username: string; admin: boolean; createdAt: number }[];
  invites?: { code: string; createdAt: number; usedBy: string | null; usedAt: number | null }[];
}

const when = (ms: number) => new Date(ms).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" });

/** A new password typed twice; null while they don't match or are too short. */
function usePasswordPair() {
  const [a, setA] = useState("");
  const [b, setB] = useState("");
  const problem = a.length > 0 && a.length < 10 ? "At least 10 characters." : b.length > 0 && a !== b ? "The two don't match." : null;
  return { a, b, setA, setB, problem, value: a.length >= 10 && a === b ? a : null };
}

/**
 * Logged in with Nextcloud: make the website account (the move). The current sync file is copied into it and
 * Nextcloud stays as the nightly backup; nothing on Nextcloud changes.
 */
export function MoveToAccountCard() {
  const [username, setUsername] = useState("");
  const pw = usePasswordPair();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function move(e: React.FormEvent) {
    e.preventDefault();
    if (!pw.value) return;
    setBusy(true);
    setError(null);
    const r = await api<{ ok?: boolean }>("/api/account/move", { username, password: pw.value });
    if (r.ok && r.data.ok) window.location.replace("/settings");
    else {
      setBusy(false);
      setError(r.data.error ?? "That did not work.");
    }
  }

  return (
    <section className="card stack">
      <h2 className="section-head">Your website account</h2>
      <p className="small">
        Make the account you&apos;ll log in with from now on, here and in the app. Your collection, decks and everything else in this sync folder are
        copied into it; Nextcloud then keeps a backup copy every night (web-backups/ in this folder). Nothing on Nextcloud is changed or deleted.
      </p>
      <form className="stack" onSubmit={move}>
        <label className="stack">
          <span className="muted small">User name (letters, digits, . - _)</span>
          <input autoComplete="username" value={username} maxLength={32} onChange={(e) => setUsername(e.target.value)} />
        </label>
        <label className="stack">
          <span className="muted small">Password (at least 10 characters)</span>
          <input type="password" autoComplete="new-password" value={pw.a} onChange={(e) => pw.setA(e.target.value)} />
        </label>
        <label className="stack">
          <span className="muted small">The password again</span>
          <input type="password" autoComplete="new-password" value={pw.b} onChange={(e) => pw.setB(e.target.value)} />
        </label>
        {pw.problem && <p className="warn-text small">{pw.problem}</p>}
        {error && <div className="bar error">{error}</div>}
        <div className="inline">
          <button className="btn primary" disabled={busy || !pw.value || username.trim().length < 3}>
            {busy ? "Copying…" : "Make my account and copy my data"}
          </button>
        </div>
        <p className="muted small">Then, on each phone: Settings → Sync → MTG Trader account, and log in with it (app version 1.79 or newer).</p>
      </form>
    </section>
  );
}

/** Logged in with a website account: devices, password, the Nextcloud backup, and for the owner invite codes and resets. */
export function AccountPanel({ onLogout }: { onLogout: () => void }) {
  const [info, setInfo] = useState<AccountInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [current, setCurrent] = useState("");
  const pw = usePasswordPair();
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const r = await api<AccountInfo>("/api/account");
    if (r.ok && r.data.username) setInfo(r.data);
    else setError(r.data.error ?? "Could not load the account.");
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function act(body: Record<string, string>, done?: (data: Record<string, unknown>) => void) {
    setBusy(true);
    setError(null);
    setNotice(null);
    const r = await api<Record<string, unknown>>("/api/account", body);
    setBusy(false);
    if (r.ok && r.data.ok) {
      done?.(r.data);
      await load();
    } else setError((r.data.error as string) ?? "That did not work.");
  }

  if (!info) return <section className="card">{error ? <div className="bar error">{error}</div> : <p className="muted">Loading…</p>}</section>;

  return (
    <>
      <section className="card stack">
        <h2 className="section-head">Account</h2>
        <p>
          Logged in as <strong>{info.username}</strong>
          {info.admin && <span className="muted small"> · owner</span>}
          <br />
          <span className="muted small">{info.syncedAt ? `Last saved ${when(info.syncedAt)}` : "No collection yet: log in with this account in the app to send it here."}</span>
        </p>
        {notice && <div className="bar ok">{notice}</div>}
        {error && <div className="bar error">{error}</div>}
        <h3 className="mini-head">Logged in on</h3>
        <ul className="plain-list cat-list">
          {info.devices.map((d) => (
            <li key={d.id} className="inline">
              <span className="grow small">
                {d.name}
                {d.id === info.device && <strong> (this browser)</strong>}
                <br />
                <span className="muted">last used {when(d.lastUsed)}</span>
              </span>
              {d.id !== info.device && (
                <button className="btn small-btn" disabled={busy} onClick={() => act({ action: "logout-device", id: d.id }, () => setNotice(`${d.name} is logged out.`))}>
                  Log out
                </button>
              )}
            </li>
          ))}
        </ul>
        <div className="inline">
          <button className="btn" onClick={onLogout}>
            Log out here
          </button>
        </div>
      </section>

      <section className="card stack">
        <h2 className="section-head">Password</h2>
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            if (pw.value)
              act({ action: "password", current, next: pw.value }, () => {
                setNotice("Password changed. Other devices are logged out: log in again there.");
                setCurrent("");
                pw.setA("");
                pw.setB("");
              });
          }}
        >
          <input type="password" aria-label="Current password" placeholder="Current password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
          <input type="password" aria-label="New password" placeholder="New password (at least 10 characters)" autoComplete="new-password" value={pw.a} onChange={(e) => pw.setA(e.target.value)} />
          <input type="password" aria-label="New password again" placeholder="New password again" autoComplete="new-password" value={pw.b} onChange={(e) => pw.setB(e.target.value)} />
          {pw.problem && <p className="warn-text small">{pw.problem}</p>}
          <div className="inline">
            <button className="btn" disabled={busy || !pw.value || !current}>
              Change password
            </button>
          </div>
        </form>
      </section>

      <section className="card stack">
        <h2 className="section-head">Nextcloud backup</h2>
        {info.backup ? (
          <>
            <p className="small">
              Every night a copy goes to <strong>{info.backup.folder}/web-backups</strong> on {info.backup.server} (the last 10 days).
            </p>
            <div className="inline">
              <button className="btn" disabled={busy} onClick={() => act({ action: "backup-off" }, () => setNotice("The nightly Nextcloud copy is off."))}>
                Stop the Nextcloud copy
              </button>
            </div>
          </>
        ) : (
          <p className="muted small">Off. The website still keeps its own copy before the first change of each day (Backups, above).</p>
        )}
      </section>

      {info.admin && (
        <section className="card stack">
          <h2 className="section-head">Accounts (owner)</h2>
          <p className="small">
            Someone new needs an invite code to make an account (here or in the app). Each code works once. A forgotten password: reset it here and pass the one-time
            password on; they change it under Settings.
          </p>
          <ul className="plain-list cat-list">
            {info.accounts?.map((a) => (
              <li key={a.id} className="inline">
                <span className="grow small">
                  <strong>{a.username}</strong>
                  {a.admin && " · owner"} <span className="muted">since {when(a.createdAt)}</span>
                </span>
                {a.username !== info.username && (
                  <button
                    className="btn small-btn"
                    disabled={busy}
                    onClick={() => act({ action: "reset", id: String(a.id) }, (d) => setNotice(`New one-time password for ${a.username}: ${d.password as string} (shown once).`))}
                  >
                    Reset password
                  </button>
                )}
              </li>
            ))}
          </ul>
          <div className="inline">
            <button className="btn" disabled={busy} onClick={() => act({ action: "invite" }, (d) => setNotice(`New invite code: ${d.code as string}`))}>
              Make an invite code
            </button>
          </div>
          {info.invites && info.invites.length > 0 && (
            <ul className="plain-list small">
              {info.invites.map((i) => (
                <li key={i.code}>
                  <code>{i.code}</code> <span className="muted">{i.usedBy ? `used by ${i.usedBy}, ${when(i.usedAt!)}` : `not used yet (made ${when(i.createdAt)})`}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </>
  );
}
