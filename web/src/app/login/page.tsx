"use client";

import { useEffect, useState } from "react";
import { Logo } from "@/components/Logo";
import { api } from "@/components/api";

/** Logging in with a website account (or making one with an invite code). Nextcloud is only a backup place now. */
export default function LoginPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [accounts, setAccounts] = useState<boolean | null>(null);
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
  }, []);

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

  return (
    <main className="page narrow">
      <div className="card login-card">
        <h1 className="login-title">
          <Logo size={44} />
          MTG Trader
        </h1>
        {message && <div className="bar error">{message}</div>}
        {accounts === false && <div className="bar error">Logging in isn&apos;t available right now (the website&apos;s database isn&apos;t reachable). Try again later.</div>}
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
          <button className="btn primary" type="submit" disabled={busy || accounts === false || !username || !password}>
            {busy ? "…" : mode === "login" ? "Log in" : "Make my account"}
          </button>
          <button className="link small" type="button" onClick={() => (setMode(mode === "login" ? "register" : "login"), setMessage(null))}>
            {mode === "login" ? "New here? Make an account with an invite code" : "I have an account: log in"}
          </button>
          <p className="muted small">Forgot your password? Ask the owner to reset it.</p>
        </form>
      </div>
    </main>
  );
}
