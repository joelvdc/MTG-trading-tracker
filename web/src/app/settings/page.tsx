"use client";

import { useCallback, useEffect, useState } from "react";
import { TopBar } from "@/components/Nav";
import { api } from "@/components/api";

interface Session {
  loggedIn: boolean;
  user?: string;
  server?: string;
  folder?: string;
}

interface Listing {
  path: string;
  folders: string[];
  hasSync: boolean;
}

const join = (path: string, name: string) => (path ? `${path}/${name}` : name);
const parentOf = (path: string) => path.split("/").slice(0, -1).join("/");

export default function SettingsPage() {
  const [session, setSession] = useState<Session | null>(null);
  const [listing, setListing] = useState<Listing | null>(null);
  const [browsing, setBrowsing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const goLogin = useCallback(() => window.location.replace("/login"), []);

  const open = useCallback(
    async (path: string) => {
      setBusy(true);
      setMessage(null);
      const r = await api<Listing>(`/api/folders?path=${encodeURIComponent(path)}`);
      setBusy(false);
      if (r.status === 401 || r.data.loggedOut) return goLogin();
      if (r.ok && Array.isArray(r.data.folders)) setListing(r.data);
      else setMessage(r.data.error ?? "Could not list the folder.");
    },
    [goLogin],
  );

  useEffect(() => {
    api<Session>("/api/session").then((r) => {
      if (!r.data.loggedIn) return goLogin();
      setSession(r.data);
      // No folder yet (just logged in): start picking one straight away.
      if (!r.data.folder) {
        setBrowsing(true);
        open("");
      }
    });
  }, [goLogin, open]);

  function startBrowsing() {
    setSaved(null);
    setBrowsing(true);
    open(session?.folder ? parentOf(session.folder) : "");
  }

  async function choose(folder: string) {
    setBusy(true);
    setMessage(null);
    const r = await api<{ ok?: boolean; folder?: string }>("/api/folder", { folder });
    setBusy(false);
    if (r.status === 401 || r.data.loggedOut) return goLogin();
    if (!r.ok || !r.data.folder) {
      setMessage(r.data.error ?? "Could not use that folder.");
      return;
    }
    const first = !session?.folder;
    setSession((s) => (s ? { ...s, folder: r.data.folder } : s));
    setBrowsing(false);
    setListing(null);
    if (first) window.location.replace("/collection");
    else setSaved(r.data.folder);
  }

  async function logout() {
    await api("/api/logout", {});
    goLogin();
  }

  if (!session) return <main className="page center muted">Loading…</main>;

  const crumbs = listing ? listing.path.split("/").filter(Boolean) : [];

  return (
    <>
      {session.folder ? (
        <TopBar page="settings" folder={session.folder} />
      ) : (
        <header className="topbar">
          <div className="topbar-in">
            <strong>MTG Trader · Settings</strong>
          </div>
        </header>
      )}
      <main className="page narrow">
        <div className="stack">
          <section className="card stack">
            <h2 className="section-head">Sync folder</h2>
            {session.folder ? (
              <p>
                <strong>{session.folder}</strong>
              </p>
            ) : (
              <p>Pick the folder chosen in the app under Settings → Sync with Nextcloud.</p>
            )}
            {saved && <div className="bar ok">Now using “{saved}”.</div>}
            {message && (
              <div className="bar error" role="alert">
                {message}
              </div>
            )}
            {!browsing ? (
              <div className="inline">
                <button className="btn" onClick={startBrowsing}>
                  Change folder
                </button>
              </div>
            ) : (
              <div className="stack">
                <nav className="crumbs" aria-label="Folder path">
                  <button className="link" disabled={busy} onClick={() => open("")}>
                    Nextcloud
                  </button>
                  {crumbs.map((name, i) => (
                    <span key={i}>
                      {" / "}
                      <button className="link" disabled={busy} onClick={() => open(crumbs.slice(0, i + 1).join("/"))}>
                        {name}
                      </button>
                    </span>
                  ))}
                </nav>
                {listing && listing.path !== "" && (
                  <div className={listing.hasSync ? "bar ok" : "bar"}>
                    {listing.hasSync ? (
                      <>
                        This folder has the app’s sync file.{" "}
                        <button className="btn primary small-btn" disabled={busy} onClick={() => choose(listing.path)}>
                          Use this folder
                        </button>
                      </>
                    ) : (
                      "No sync file from the app in this folder."
                    )}
                  </div>
                )}
                {!listing ? (
                  <p className="muted">{busy ? "Loading…" : ""}</p>
                ) : listing.folders.length === 0 ? (
                  <p className="muted">No folders inside.</p>
                ) : (
                  <ul className="folders">
                    {listing.folders.map((name) => (
                      <li key={name}>
                        <button className="folder" disabled={busy} onClick={() => open(join(listing.path, name))}>
                          <span aria-hidden="true">📁</span> {name}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                {session.folder && (
                  <div className="inline">
                    <button className="btn" disabled={busy} onClick={() => setBrowsing(false)}>
                      Cancel
                    </button>
                  </div>
                )}
              </div>
            )}
          </section>

          <section className="card stack">
            <h2 className="section-head">Account</h2>
            <p>
              Logged in as <strong>{session.user}</strong>
              <br />
              <span className="muted small">{session.server}</span>
            </p>
            <div className="inline">
              <button className="btn" onClick={logout}>
                Log out
              </button>
            </div>
            <small className="muted">Logging out also removes this website’s app password from your Nextcloud.</small>
          </section>
        </div>
      </main>
    </>
  );
}
