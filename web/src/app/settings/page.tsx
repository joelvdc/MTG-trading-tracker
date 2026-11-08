"use client";

import { useCallback, useEffect, useState } from "react";
import { AccountPanel } from "@/components/AccountPanel";
import { AppSettingsPanel, BackupsPanel } from "@/components/AppSettingsPanel";
import { TopBar } from "@/components/Nav";
import { api } from "@/components/api";

interface Session {
  loggedIn: boolean;
  folder?: string;
}

/** Settings for a website account: the app's synced settings, the backups, and the account (Nextcloud only as a backup). */
export default function SettingsPage() {
  const [session, setSession] = useState<Session | null>(null);
  const goLogin = useCallback(() => window.location.replace("/login"), []);

  useEffect(() => {
    api<Session>("/api/session").then((r) => {
      if (!r.data.loggedIn) return goLogin();
      setSession(r.data);
    });
  }, [goLogin]);

  async function logout() {
    await api("/api/logout", {});
    goLogin();
  }

  if (!session) return <main className="page center muted">Loading…</main>;

  return (
    <>
      <TopBar page="settings" folder={session.folder} />
      <main className="page narrow">
        <div className="stack">
          <AppSettingsPanel />
          <BackupsPanel />
          <AccountPanel onLogout={logout} />
        </div>
      </main>
    </>
  );
}
