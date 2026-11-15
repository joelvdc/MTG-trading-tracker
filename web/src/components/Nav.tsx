"use client";

import Link from "next/link";
import { useEffect } from "react";
import { prefetch, warmUp } from "./api";
import { Logo } from "./Logo";

// The sections whose pages show their last answer at once: their data starts loading when the link is about to be
// pressed (hover, focus or touch), so even the first switch is quick.
const DATA: Record<string, string[]> = {
  "/collection": ["/api/session", "/api/collection"],
  "/decks": ["/api/session", "/api/decks"],
};

/** The top bar on every page after logging in: the website's sections and Settings. */
export function TopBar({ page, folder }: { page: "collection" | "stats" | "decks" | "games" | "orders" | "trades" | "settings"; folder?: string }) {
  useEffect(() => { warmUp(); }, []);
  const link = (href: string, label: string, on: boolean) => {
    const warm = !on && DATA[href] ? () => prefetch(DATA[href]) : undefined;
    return (
      <Link href={href} className={on ? "nav-link on" : "nav-link"} aria-current={on ? "page" : undefined} onMouseEnter={warm} onFocus={warm} onTouchStart={warm}>
        {label}
      </Link>
    );
  };
  return (
    <header className="topbar">
      <div className="topbar-in wide">
        <div className="brand">
          <div className="brand-name">
            <Logo size={28} />
            <strong>
              MTG <span className="brand-accent">Trader</span>
            </strong>
          </div>
          {folder && <div className="muted small">{folder}</div>}
        </div>
        <nav className="nav" aria-label="Sections">
          {link("/collection", "Collection", page === "collection" || page === "stats")}
          {link("/decks", "Decks", page === "decks")}
          {link("/games", "Games", page === "games")}
          {link("/orders", "Orders", page === "orders")}
          {link("/trades", "Trades", page === "trades")}
        </nav>
        <Link className={page === "settings" ? "btn on" : "btn"} href="/settings">
          Settings
        </Link>
      </div>
    </header>
  );
}
