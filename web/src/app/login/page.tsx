"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Logo } from "@/components/Logo";
import { api } from "@/components/api";

/** Logging in with a website account (or making one with an invite code), with a code when two-step login is on. */
export default function LoginPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [accounts, setAccounts] = useState<boolean | null>(null);
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");
  const [invite, setInvite] = useState("");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  // The account has two-step login: the code field is shown and the password is sent again with it.
  const [needCode, setNeedCode] = useState(false);
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
    type Answer = { ok?: boolean; needCode?: boolean };
    const r: Awaited<ReturnType<typeof api<Answer>>> =
      mode === "login"
        ? await api<Answer>("/api/account/login", needCode ? { username, password, code } : { username, password })
        : await api<Answer>("/api/account/register", { username, password, email: email.trim(), invite });
    if (r.ok && r.data.ok) return window.location.replace("/");
    setBusy(false);
    if (r.ok && r.data.needCode) {
      setNeedCode(true);
      if (code) setMessage("That code did not work.");
      return;
    }
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
              <input type="email" aria-label="Email" placeholder="Email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
              <input aria-label="Invite code" placeholder="Invite code (from the owner)" value={invite} onChange={(e) => setInvite(e.target.value)} />
            </>
          )}
          {mode === "login" && needCode && (
            <input
              aria-label="Code"
              placeholder="Code"
              autoComplete="one-time-code"
              inputMode="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
          )}
          <button
            className="btn primary"
            type="submit"
            disabled={busy || accounts === false || !username || !password || (mode === "register" && !email.trim())}
          >
            {busy ? "…" : mode === "login" ? "Log in" : "Make my account"}
          </button>
          <button
            className="link small"
            type="button"
            onClick={() => {
              setMode(mode === "login" ? "register" : "login");
              setMessage(null);
              setNeedCode(false);
              setCode("");
            }}
          >
            {mode === "login" ? "New here? Make an account with an invite code" : "I have an account: log in"}
          </button>
          {mode === "login" && (
            <Link className="small" href="/forgot">
              Forgot password?
            </Link>
          )}
        </form>
      </div>
    </main>
  );
}
