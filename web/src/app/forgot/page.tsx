"use client";

import Link from "next/link";
import { useState } from "react";
import { Logo } from "@/components/Logo";
import { api } from "@/components/api";

/** Asking for a password reset link by email, for an account whose email is confirmed. */
export default function ForgotPage() {
  const [who, setWho] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function send(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    const r = await api<{ ok?: boolean }>("/api/account/forgot", { who: who.trim() });
    setBusy(false);
    if (!r.ok) return setError(r.data.error ?? "That did not work.");
    setSent(true);
  }

  return (
    <main className="page narrow">
      <div className="card login-card">
        <h1 className="login-title">
          <Logo size={44} />
          MTG Trader
        </h1>
        {error && <div className="bar error">{error}</div>}
        {sent ? (
          <div className="stack">
            <p>If that account has a confirmed email, a link is on its way.</p>
            <Link className="small" href="/login">
              Back to log in
            </Link>
          </div>
        ) : (
          <form onSubmit={send} className="stack">
            <input
              aria-label="User name or email"
              placeholder="User name or email"
              autoComplete="username"
              value={who}
              onChange={(e) => setWho(e.target.value)}
            />
            <button className="btn primary" type="submit" disabled={busy || !who.trim()}>
              {busy ? "…" : "Send link"}
            </button>
            <Link className="small" href="/login">
              Back to log in
            </Link>
          </form>
        )}
      </div>
    </main>
  );
}
