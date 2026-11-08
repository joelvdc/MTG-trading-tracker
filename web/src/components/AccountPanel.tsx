"use client";

import { useCallback, useEffect, useRef, useState } from "react";
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

      <NextcloudBackupCard backup={info.backup} busy={busy} onStop={() => act({ action: "backup-off" }, () => setNotice("The nightly Nextcloud copy is off."))} onConnected={load} />

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

const POLL_MS = 2000;
const GIVE_UP_MS = 20 * 60 * 1000;

/**
 * Nextcloud as the nightly backup place (never a login): "Connect Nextcloud" opens Nextcloud's approval page in a
 * new tab, this card waits for the approval, and the website then writes today's copy to <folder>/web-backups.
 */
function NextcloudBackupCard({
  backup,
  busy,
  onStop,
  onConnected,
}: {
  backup: AccountInfo["backup"];
  busy: boolean;
  onStop: () => void;
  onConnected: () => Promise<void>;
}) {
  const [folder, setFolder] = useState(backup?.folder ?? "MTG Trader");
  const [phase, setPhase] = useState<"idle" | "starting" | "waiting">("idle");
  const [link, setLink] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const run = useRef(0);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      run.current++;
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  function stop(text: string | null) {
    run.current++;
    if (timer.current) clearTimeout(timer.current);
    setPhase("idle");
    setLink(null);
    setMessage(text);
  }

  function poll(id: number, startedAt: number) {
    timer.current = setTimeout(async () => {
      if (id !== run.current) return;
      if (Date.now() - startedAt > GIVE_UP_MS) return stop("Nextcloud wasn't approved in time. Please start again.");
      const r = await api<{ status?: string; message?: string; folder?: string }>("/api/account/backup", { action: "poll" });
      if (id !== run.current) return;
      const status = r.data.status;
      if (status === "done") {
        stop(null);
        setDone(`Connected. Today's copy is already in ${r.data.folder}/web-backups on Nextcloud.`);
        await onConnected();
      } else if (status === "expired") stop("Nextcloud wasn't approved in time. Please start again.");
      else if (status === "refused") stop(r.data.message ?? "Nextcloud refused.");
      else if (!r.ok) stop(r.data.error ?? "Connecting Nextcloud did not work.");
      else poll(id, startedAt);
    }, POLL_MS);
  }

  async function connect(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    setDone(null);
    // A blank tab now (inside the click), so the browser doesn't block it; it gets Nextcloud's address next.
    const tab = window.open("", "_blank");
    setPhase("starting");
    const r = await api<{ loginUrl?: string }>("/api/account/backup", { action: "start", folder });
    if (!r.data.loginUrl) {
      tab?.close();
      return stop(r.data.error ?? "Could not reach Nextcloud.");
    }
    if (tab) tab.location.href = r.data.loginUrl;
    else setLink(r.data.loginUrl);
    const id = ++run.current;
    setPhase("waiting");
    poll(id, Date.now());
  }

  return (
    <section className="card stack">
      <h2 className="section-head">Nextcloud backup</h2>
      <p className="muted small">
        Nextcloud only keeps backups: every night the website copies your data there (the last 10 days). Your phones and this website sync with your
        account, not with Nextcloud.
      </p>
      {backup ? (
        <p className="small">
          On: every night a copy goes to <strong>{backup.folder}/web-backups</strong> on {backup.server}.
        </p>
      ) : (
        <p className="small">Off. The website still keeps its own copy before the first change of each day (Backups, above).</p>
      )}
      {done && <div className="bar ok">{done}</div>}
      {message && <div className="bar error">{message}</div>}
      {phase === "waiting" ? (
        <div className="stack">
          <p className="small">Approve the website in the Nextcloud tab… This card continues by itself.</p>
          {link && (
            <p className="small">
              Your browser blocked the new tab.{" "}
              <a href={link} target="_blank" rel="noreferrer">
                Open Nextcloud&apos;s approval page
              </a>
              .
            </p>
          )}
          <div className="inline">
            <button
              className="btn"
              onClick={() => {
                api("/api/account/backup", { action: "cancel" });
                stop(null);
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <form className="stack" onSubmit={connect}>
          <label className="stack">
            <span className="muted small">Folder on Nextcloud (made if it isn&apos;t there)</span>
            <input value={folder} maxLength={200} onChange={(e) => setFolder(e.target.value)} />
          </label>
          <div className="inline">
            <button className="btn primary" disabled={busy || phase === "starting" || !folder.trim()}>
              {phase === "starting" ? "Starting…" : backup ? "Connect again" : "Connect Nextcloud"}
            </button>
            {backup && (
              <button type="button" className="btn" disabled={busy} onClick={onStop}>
                Stop the Nextcloud copy
              </button>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
