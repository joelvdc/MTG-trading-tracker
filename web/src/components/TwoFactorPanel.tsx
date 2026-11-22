"use client";

import { useEffect, useState } from "react";
import { api } from "./api";

/** What GET /api/account/2fa answers. */
interface TwoFactorInfo {
  on: boolean;
  recoveryLeft: number;
}

/** What POST /api/account/2fa {action:"start"} answers: the secret, a link for the authenticator app and its QR code (SVG). */
interface TwoFactorStart {
  secret: string;
  otpauth: string;
  qr: string;
}

/** Settings → Two-step login: an authenticator app's code on top of the password, with recovery codes. */
export function TwoFactorPanel() {
  const [info, setInfo] = useState<TwoFactorInfo | null>(null);
  const [started, setStarted] = useState<TwoFactorStart | null>(null);
  const [codes, setCodes] = useState<string[] | null>(null);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [offCode, setOffCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Always read fresh: the panel has no kept answer to show first.
  function load() {
    api<TwoFactorInfo>("/api/account/2fa").then((r) => {
      if (!r.ok) return setError(r.data.error ?? "Could not read this setting.");
      setInfo(r.data);
    });
  }
  useEffect(load, []);

  async function act(body: object, done?: (data: Record<string, unknown>) => void) {
    setBusy(true);
    setError(null);
    setNote(null);
    const r = await api<Record<string, unknown>>("/api/account/2fa", body);
    setBusy(false);
    if (!r.ok) return setError(r.data.error ?? "That didn't work.");
    done?.(r.data);
    load();
  }

  function start() {
    act({ action: "start" }, (d) => {
      setStarted(d as unknown as TwoFactorStart);
      setCodes(null);
      setCode("");
    });
  }

  function confirm() {
    act({ action: "confirm", code: code.trim() }, (d) => {
      setCodes(d.codes as string[]);
      setStarted(null);
      setCode("");
    });
  }

  function turnOff() {
    act({ action: "off", password, code: offCode.trim() }, () => {
      setPassword("");
      setOffCode("");
      setNote("Two-step login is off.");
    });
  }

  async function copyCodes() {
    if (!codes) return;
    try {
      await navigator.clipboard.writeText(codes.join("\n"));
      setNote("Copied.");
    } catch {
      setError("Copying did not work: select the codes and copy them yourself.");
    }
  }

  return (
    <section className="card stack">
      <h2 className="section-head">Two-step login</h2>
      {info === null ? (
        <p className="muted small">Loading…</p>
      ) : codes ? (
        <>
          <h3 className="mini-head">Recovery codes</h3>
          <ul className="plain-list small">
            {codes.map((c) => (
              <li key={c}>
                <code>{c}</code>
              </li>
            ))}
          </ul>
          <div className="controls">
            <button className="btn" disabled={busy} onClick={copyCodes}>
              Copy
            </button>
            <button className="btn primary" onClick={() => (setCodes(null), setNote(null))}>
              Done
            </button>
          </div>
        </>
      ) : started ? (
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            confirm();
          }}
        >
          <div style={{ width: 200 }} dangerouslySetInnerHTML={{ __html: started.qr }} />
          <p className="small">
            <code>{started.secret}</code>
          </p>
          <p className="small">
            <a href={started.otpauth}>Open in authenticator app</a>
          </p>
          <label className="stack tight">
            <span className="small">Code</span>
            <input aria-label="Code" placeholder="Code" autoComplete="one-time-code" inputMode="text" value={code} onChange={(e) => setCode(e.target.value)} />
          </label>
          <div className="controls">
            <button className="btn primary" type="submit" disabled={busy || !code.trim()}>
              Confirm
            </button>
            <button className="btn" type="button" disabled={busy} onClick={() => setStarted(null)}>
              Cancel
            </button>
          </div>
        </form>
      ) : info.on ? (
        <>
          <p className="small">
            On · {info.recoveryLeft} recovery code{info.recoveryLeft === 1 ? "" : "s"} left
          </p>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              turnOff();
            }}
          >
            <label className="stack tight">
              <span className="small">Password</span>
              <input type="password" aria-label="Password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
            <label className="stack tight">
              <span className="small">Code</span>
              <input aria-label="Code" autoComplete="one-time-code" inputMode="text" value={offCode} onChange={(e) => setOffCode(e.target.value)} />
            </label>
            <div className="controls">
              <button className="btn" type="submit" disabled={busy || !password || !offCode.trim()}>
                Turn off
              </button>
            </div>
          </form>
        </>
      ) : (
        <div className="controls">
          <button className="btn primary" disabled={busy} onClick={start}>
            Turn on
          </button>
        </div>
      )}
      {error && <div className="bar error">{error}</div>}
      {note && <div className="bar">{note}</div>}
    </section>
  );
}
