"use client";

import { useCallback, useEffect, useState } from "react";
import { api, formatDate } from "./api";
import { Dialog } from "./Dialog";

type Kind = "deck" | "game" | "binder" | "collection" | "wishlist";

async function copy(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

/**
 * The Share window: makes (or finds) the item's link, copies it and shows it. Links never expire; Settings → Shared
 * links switches them off. [what] names the item in the window ("this deck", "the whole collection"…).
 */
export function ShareDialog({ kind, target, what, onClose }: { kind: Kind; target: string; what: string; onClose: () => void }) {
  const [state, setState] = useState<{ url?: string; error?: string; copied?: boolean; turnedOn?: boolean }>({});
  useEffect(() => {
    api<{ url: string; turnedOn?: boolean }>("/api/shares", { action: "make", kind, target }).then(async (r) => {
      if (!r.ok) return setState({ error: r.data.error ?? "The link couldn't be made." });
      setState({ url: r.data.url, turnedOn: r.data.turnedOn, copied: await copy(r.data.url) });
    });
  }, [kind, target]);
  return (
    <Dialog title="Share" onClose={onClose}>
      <div className="stack">
        {state.error && <div className="bar error">{state.error}</div>}
        {!state.url && !state.error && <p className="muted">Making the link…</p>}
        {state.url && (
          <>
            <p className="small">
              Anyone with this link can see {what}, with your prices (not what you paid). It stays the same and always shows things as they
              are now.{state.turnedOn ? " It was switched off; it's on again." : ""}
            </p>
            <input type="text" readOnly value={state.url} aria-label="The link" onFocus={(e) => e.target.select()} />
            <p className="muted small">{state.copied ? "Copied. " : ""}Switch it off any time in Settings → Shared links.</p>
          </>
        )}
        <div className="dialog-actions">
          <span className="spacer" />
          {state.url && (
            <>
              <a className="btn" href={state.url} target="_blank" rel="noreferrer">
                Open
              </a>
              <button
                className="btn"
                onClick={async () => {
                  const ok = await copy(state.url!);
                  setState((s) => ({ ...s, copied: ok }));
                }}
              >
                Copy
              </button>
            </>
          )}
          <button className="btn primary" onClick={onClose}>
            Done
          </button>
        </div>
      </div>
    </Dialog>
  );
}

interface Line {
  code: string;
  kind: Kind;
  title: string | null;
  on: boolean;
  createdAt: number;
  opened: number;
  lastOpened: number | null;
  url: string;
}

/** Settings → Shared links: every link with an on/off switch, Copy and Delete. */
export function SharedLinksPanel() {
  const [lines, setLines] = useState<Line[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);
  const load = useCallback(() => {
    api<{ links: Line[] }>("/api/shares").then((r) => {
      if (r.ok) setLines(r.data.links);
      else setError(r.data.error ?? "Could not list the shared links.");
    });
  }, []);
  useEffect(load, [load]);

  async function act(body: object) {
    const r = await api("/api/shares", body);
    if (!r.ok) setError(r.data.error ?? "That didn't work.");
    load();
  }

  return (
    <section className="card stack">
      <h2 className="section-head">Shared links</h2>
      <p className="muted small">
        Links made with Share (on a deck, a game, a binder or the collection, here or in the app). Anyone with a link sees that item with
        your prices, never what you paid. Switch a link off to stop it working; switching it on again brings the same link back. Delete
        forgets it for good.
      </p>
      {error && <div className="bar error">{error}</div>}
      {lines && lines.length === 0 && <p className="muted">No shared links yet.</p>}
      {lines && lines.length > 0 && (
        <ul className="share-list">
          {lines.map((l) => (
            <li key={l.code}>
              <div className="grow">
                <div>{l.title ?? <span className="muted">(no longer exists)</span>}</div>
                <div className="muted small">
                  Made {formatDate(l.createdAt)} · opened {l.opened} time{l.opened === 1 ? "" : "s"}
                  {l.lastOpened ? `, last ${formatDate(l.lastOpened)}` : ""}
                </div>
              </div>
              <label className="check small">
                <input type="checkbox" checked={l.on} onChange={(e) => act({ action: "on", code: l.code, on: e.target.checked })} />
                <span>{l.on ? "On" : "Off"}</span>
              </label>
              <button
                className="btn small-btn"
                disabled={!l.on}
                onClick={async () => {
                  if (await copy(l.url)) setCopied(l.code);
                }}
              >
                {copied === l.code ? "Copied" : "Copy"}
              </button>
              <button className="btn small-btn" onClick={() => window.confirm("Delete this link? It stops working for good.") && act({ action: "delete", code: l.code })}>
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
