"use client";

import { useEffect } from "react";
import { api } from "@/components/api";

export default function Home() {
  useEffect(() => {
    api<{ loggedIn: boolean }>("/api/session").then((r) => {
      window.location.replace(r.data.loggedIn ? "/wishlist" : "/login");
    });
  }, []);
  return <main className="page center muted">Loading…</main>;
}
