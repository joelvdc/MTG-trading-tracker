"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Logo } from "@/components/Logo";
import { api } from "@/components/api";

/** Setting a new password from the link in the reset email (the token is `t` in the address). */
export default function ResetPage() {
  return (
    <Suspense fallback={null}>
      <ResetCard />
    </Suspense>
  );
}

function ResetCard() {
  const token = useSearchParams().get("t") ?? "";
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");
  const [done, setDone] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const mismatch = password2.length > 0 && password !== password2;
  const ready = password.length >= 10 && password === password2;

  async function send(e: React.FormEvent) {
    e.preventDefault();
    if (!ready) return setMessage("The two passwords don't match.");
    setMessage(null);
    setBusy(true);
    const r = await api<{ ok?: boolean }>("/api/account/reset", { token, password });
    setBusy(false);
    if (!r.ok) return setMessage(r.data.error ?? "That did not work.");
    setPassword("");
    setPassword2("");
    setDone(true);
  }

  return (
    <main className="page narrow">
      <div className="card login-card">
        <h1 className="login-title">
          <Logo size={44} />
          MTG Trader
        </h1>
        {!token ? (
          <div className="stack">
            <div className="bar error">This link isn&apos;t complete.</div>
            <Link className="small" href="/login">
              Log in
            </Link>
          </div>
        ) : done ? (
          <div className="stack">
            <p>Password changed.</p>
            <Link className="small" href="/login">
              Log in
            </Link>
          </div>
        ) : (
          <form onSubmit={send} className="stack">
            {message && <div className="bar error">{message}</div>}
            <input
              type="password"
              aria-label="New password (at least 10 characters)"
              placeholder="New password (at least 10 characters)"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <input
              type="password"
              aria-label="New password again"
              placeholder="New password again"
              autoComplete="new-password"
              value={password2}
              onChange={(e) => setPassword2(e.target.value)}
            />
            {mismatch && <p className="warn-text small">The two passwords don&apos;t match.</p>}
            <button className="btn primary" type="submit" disabled={busy || !ready}>
              {busy ? "…" : "Set password"}
            </button>
            <Link className="small" href="/login">
              Log in
            </Link>
          </form>
        )}
      </div>
    </main>
  );
}
