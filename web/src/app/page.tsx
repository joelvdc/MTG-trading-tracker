"use client";

import { useEffect } from "react";
import { api } from "@/components/api";

export default function Home() {
  useEffect(() => {
    api<{ loggedIn: boolean; folder?: string }>("/api/session").then((r) => {
      window.location.replace(!r.data.loggedIn ? "/login" : r.data.folder ? "/wishlist" : "/settings");
    });
  }, []);
  return <main className="page center muted">Loading…</main>;
}
