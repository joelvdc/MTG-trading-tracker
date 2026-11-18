"use client";

import { useEffect, useState } from "react";
import { api, formatDate } from "./api";

/** What GET /api/account/aikey answers. */
interface AiKeyInfo {
  usesWebsiteKey: boolean;
  websiteKeySet: boolean;
  /** The service the website's own key belongs to ("Mistral", "OpenCode Go"), or null when it has none. */
  websiteProvider: string | null;
  /** Every service the website has a key for ("Mistral", "OpenCode Go"), in the order the owner can pick between them. */
  websiteProviders: string[];
  providers: { id: string; label: string }[];
  saved: { provider: string; label: string; last4: string; savedAt: number } | null;
  unreadable: boolean;
}

/** Settings → AI key: the account's own key for Write with AI (OpenCode Go, Mistral or OpenRouter). */
export function AiKeyPanel() {
  const [info, setInfo] = useState<AiKeyInfo | null>(null);
  const [provider, setProvider] = useState("");
  const [key, setKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Always read fresh: the panel has no kept answer to show first.
  function load() {
    api<AiKeyInfo>("/api/account/aikey").then((r) => {
      if (!r.ok) return setError(r.data.error ?? "Could not read your AI key.");
      setInfo(r.data);
      setProvider(r.data.saved?.provider ?? r.data.providers[0]?.id ?? "");
    });
  }
  useEffect(load, []);

  async function save() {
    setBusy(true);
    setError(null);
    setNote(null);
    const r = await api<{ note?: string }>("/api/account/aikey", { action: "save", provider, key: key.trim() });
    setBusy(false);
    if (!r.ok) return setError(r.data.error ?? "That didn't work.");
    setKey("");
    setNote(r.data.note ?? "Saved.");
    load();
  }

  async function remove() {
    if (!window.confirm("Remove your AI key from this website?")) return;
    setBusy(true);
    setError(null);
    setNote(null);
    const r = await api<{ note?: string }>("/api/account/aikey", { action: "remove" });
    setBusy(false);
    if (!r.ok) return setError(r.data.error ?? "That didn't work.");
    setNote(r.data.note ?? "Removed.");
    load();
  }

  return (
    <section className="card stack">
      <h2 className="section-head">AI key</h2>
      {info === null ? (
        <p className="muted small">Loading…</p>
      ) : info.usesWebsiteKey ? (
        <p className="muted small">
          {info.websiteKeySet
            ? info.websiteProviders.length > 1
              ? `Your account uses the website's own keys (${info.websiteProviders.join(" and ")}). Choose one when you write a primer.`
              : `Your account uses the website's own ${info.websiteProvider} key.`
            : "The website's own key isn't set up."}
        </p>
      ) : (
        <>
          <p className="muted small">
            Write with AI uses your own key from OpenCode Go, Mistral or OpenRouter, and that service bills you for what you use. When you write a primer, the
            deck&apos;s name, cards, card notes, scores, combos and your current primer text go to the service you pick. Your key is stored encrypted and is never shown
            again.
          </p>
          {info.unreadable && <div className="bar warn">Your saved key can&apos;t be read any more. Please add it again.</div>}
          {info.saved && (
            <p className="small">
              {info.saved.label} key ending in {info.saved.last4}, saved {formatDate(info.saved.savedAt)}.
            </p>
          )}
          <label className="stack tight">
            <span className="small">Service</span>
            <select value={provider} onChange={(e) => setProvider(e.target.value)} disabled={busy}>
              {info.providers.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <label className="stack tight">
            <span className="small">{info.saved ? "New API key" : "API key"}</span>
            <input
              type="password"
              autoComplete="off"
              spellCheck={false}
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="Paste your key here"
            />
          </label>
          <div className="controls">
            <button className="btn primary" disabled={busy || !key.trim()} onClick={save}>
              {busy ? "Checking…" : info.saved ? "Replace key" : "Save"}
            </button>
            {info.saved && (
              <button className="btn" disabled={busy} onClick={remove}>
                Remove
              </button>
            )}
          </div>
        </>
      )}
      {error && <div className="bar error">{error}</div>}
      {note && <div className="bar">{note}</div>}
    </section>
  );
}
