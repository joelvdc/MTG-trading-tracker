"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/components/api";

const POLL_MS = 2000;
const GIVE_UP_MS = 20 * 60 * 1000;

export default function LoginPage() {
  const [phase, setPhase] = useState<"form" | "starting" | "waiting">("form");
  const [message, setMessage] = useState<string | null>(null);
  const [link, setLink] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const run = useRef(0);
  const [accounts, setAccounts] = useState(false);
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");
  const [invite, setInvite] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api<{ loggedIn: boolean; accounts?: boolean }>("/api/session").then((r) => {
      if (r.data.loggedIn) window.location.replace("/");
      setAccounts(Boolean(r.data.accounts));
    });
    return () => {
      run.current++;
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  const stop = useCallback(() => {
    run.current++;
    if (timer.current) clearTimeout(timer.current);
    setPhase("form");
    setLink(null);
  }, []);

  function poll(id: number, startedAt: number) {
    timer.current = setTimeout(async () => {
      if (id !== run.current) return;
      if (Date.now() - startedAt > GIVE_UP_MS) {
        stop();
        setMessage("The login timed out. Please start again.");
        return;
      }
      const r = await api<{ status?: string; message?: string }>("/api/login/poll", {});
      if (id !== run.current) return;
      const status = r.data.status;
      if (status === "done") {
        window.location.replace("/");
      } else if (status === "expired") {
        stop();
        setMessage("The login timed out. Please start again.");
      } else if (status === "refused") {
        stop();
        setMessage(r.data.message ?? "The login was refused.");
      } else {
        poll(id, startedAt);
      }
    }, POLL_MS);
  }

  async function accountLogin(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    if (mode === "register" && password !== password2) return setMessage("The two passwords don't match.");
    setBusy(true);
    const r =
      mode === "login"
        ? await api<{ ok?: boolean }>("/api/account/login", { username, password })
        : await api<{ ok?: boolean }>("/api/account/register", { username, password, invite });
    if (r.ok && r.data.ok) return window.location.replace("/");
    setBusy(false);
    setMessage(r.data.error ?? "That did not work.");
  }

  async function start(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    setLink(null);
    // Open a blank tab now (inside the click), so the browser doesn't block it; it gets its address later.
    const tab = window.open("", "_blank");
    setPhase("starting");
    const r = await api<{ loginUrl?: string }>("/api/login/start", {});
    const url = r.data.loginUrl;
    if (!url) {
      tab?.close();
      setPhase("form");
      setMessage(r.data.error ?? "Could not start the login.");
      return;
    }
    if (tab) tab.location.href = url;
    else setLink(url);
    const id = ++run.current;
    setPhase("waiting");
    poll(id, Date.now());
  }

  return (
    <main className="page narrow">
      <div className="card login-card">
        <h1>MTG Trader</h1>
        {phase !== "waiting" ? (
          <>
            {message && <div className="bar error">{message}</div>}
            {accounts && (
              <form onSubmit={accountLogin} className="stack">
                <input aria-label="User name" placeholder="User name" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
                <input
                  type="password"
                  aria-label="Password"
                  placeholder={mode === "register" ? "Password (at least 10 characters)" : "Password"}
                  autoComplete={mode === "register" ? "new-password" : "current-password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                {mode === "register" && (
                  <>
                    <input type="password" aria-label="Password again" placeholder="Password again" autoComplete="new-password" value={password2} onChange={(e) => setPassword2(e.target.value)} />
                    <input aria-label="Invite code" placeholder="Invite code (from the owner)" value={invite} onChange={(e) => setInvite(e.target.value)} />
                  </>
                )}
                <button className="btn primary" type="submit" disabled={busy || !username || !password}>
                  {busy ? "…" : mode === "login" ? "Log in" : "Make my account"}
                </button>
                <button className="link small" type="button" onClick={() => (setMode(mode === "login" ? "register" : "login"), setMessage(null))}>
                  {mode === "login" ? "New here? Make an account with an invite code" : "I have an account: log in"}
                </button>
                <p className="muted small">Forgot your password? Ask the owner to reset it.</p>
              </form>
            )}
            <form onSubmit={start} className="stack">
              {accounts && <p className="muted small">Not moved to an account yet? Log in with Nextcloud, then make your account under Settings.</p>}
              <button className={accounts ? "btn" : "btn primary"} type="submit" disabled={phase === "starting"}>
                {phase === "starting" ? "Starting…" : "Log in with Nextcloud"}
              </button>
            </form>
          </>
        ) : (
          <div className="stack">
            <p>Approve the login in the Nextcloud tab… This page continues by itself.</p>
            {link && (
              <p>
                Your browser blocked the new tab.{" "}
                <a href={link} target="_blank" rel="noreferrer">
                  Open the Nextcloud approval page
                </a>
                .
              </p>
            )}
            <button className="btn" type="button" onClick={stop}>
              Cancel
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
