"use client";

import Link from "next/link";
import { useEffect, type MouseEvent, type ReactNode } from "react";
import { api, prefetch, warmUp } from "./api";
import { Logo } from "./Logo";

// The sections whose pages show their last answer at once: their data starts loading when the link is about to be
// pressed (hover, focus or touch), so even the first switch is quick.
const DATA: Record<string, string[]> = {
  "/collection": ["/api/session", "/api/collection"],
  "/decks": ["/api/session", "/api/decks"],
};

/** Closes the drop-down menu a click came from (the <details> that holds the item). */
function closeMenu(e: MouseEvent<HTMLElement>) {
  const d = e.currentTarget.closest("details.menu") as HTMLDetailsElement | null;
  if (d) d.open = false;
}

/** One Explore shortcut: an icon drawing, a title and a line saying what it shows. */
type Shortcut = { href: string; title: string; note: string; icon: ReactNode };

/** A small line drawing for an Explore tile (32 px, in the accent colour). */
function TileIcon({ children }: { children: ReactNode }) {
  return (
    <svg
      className="explore-icon"
      width={32}
      height={32}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

/** The shortcuts in the Explore panel, in the order the panel shows them. */
const SHORTCUTS: Shortcut[] = [
  {
    href: "/precons",
    title: "Commander precons",
    note: "Browse every Commander precon",
    icon: (
      <>
        <path d="M3 8l9-4 9 4-9 4-9-4z" />
        <path d="M3 8v8l9 4 9-4V8" />
        <path d="M12 12v8" />
      </>
    ),
  },
  {
    href: "/decks/recommendations",
    title: "Recommendations",
    note: "Cards suggested for your decks",
    icon: (
      <>
        <path d="M12 3l2.3 5.7L20 11l-5.7 2.3L12 19l-2.3-5.7L4 11l5.7-2.3z" />
        <path d="M18.5 17.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z" />
      </>
    ),
  },
  {
    href: "/collection/stats",
    title: "Collection stats",
    note: "Value, colours, risers and fallers",
    icon: (
      <>
        <path d="M3 20h18" />
        <path d="M6 20v-6" />
        <path d="M12 20V5" />
        <path d="M18 20v-9" />
      </>
    ),
  },
  {
    href: "/decks/shared",
    title: "Cards in several decks",
    note: "What your decks share",
    icon: (
      <>
        <circle cx="9" cy="9" r="5" />
        <circle cx="15" cy="15" r="5" />
      </>
    ),
  },
  {
    href: "/decks/cards",
    title: "Bracket cards",
    note: "Print your decks' bracket cards",
    icon: (
      <>
        <path d="M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z" />
        <path d="M9 12l2 2 4-4" />
      </>
    ),
  },
  {
    href: "/tradebinder",
    title: "Trade binder",
    note: "Your cards up for trade",
    icon: (
      <>
        <path d="M4 8h13l-3-3" />
        <path d="M20 16H7l3 3" />
      </>
    ),
  },
];

/** The top bar on every page after logging in: the website's sections, Explore and the account menu. */
export function TopBar({
  page,
  folder,
}: {
  page: "collection" | "stats" | "decks" | "games" | "orders" | "trades" | "settings" | "precons";
  folder?: string;
}) {
  useEffect(() => {
    warmUp();
  }, []);
  const link = (href: string, label: string, on: boolean) => {
    const warm = !on && DATA[href] ? () => prefetch(DATA[href]) : undefined;
    return (
      <Link href={href} className={on ? "nav-link on" : "nav-link"} aria-current={on ? "page" : undefined} onMouseEnter={warm} onFocus={warm} onTouchStart={warm}>
        {label}
      </Link>
    );
  };
  // The user's folder name without the "Account: " that the app puts in front of it.
  const who = folder?.replace(/^Account:\s*/, "").trim() || "Account";
  const accountOn = page === "orders" || page === "settings";

  async function logout() {
    await api("/api/logout", {});
    window.location.replace("/login");
  }

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
        </div>
        <nav className="nav" aria-label="Sections">
          {link("/collection", "Collection", page === "collection" || page === "stats")}
          {link("/decks", "Decks", page === "decks")}
          {link("/games", "Games", page === "games")}
          {link("/trades", "Trades", page === "trades")}
          <span className="nav-divider" aria-hidden="true" />
          <details className="menu explore">
            <summary className={page === "precons" ? "nav-link on" : "nav-link"}>Explore ▾</summary>
            <div className="menu-items explore-panel">
              {SHORTCUTS.map((s) => (
                <Link key={s.href} href={s.href} className="explore-tile" onClick={closeMenu}>
                  <TileIcon>{s.icon}</TileIcon>
                  <span className="explore-text">
                    <strong>{s.title}</strong>
                    <span className="muted small">{s.note}</span>
                  </span>
                  <span className="explore-go" aria-hidden="true">
                    ›
                  </span>
                </Link>
              ))}
            </div>
          </details>
        </nav>
        <details className="menu account">
          <summary className={accountOn ? "nav-link on" : "nav-link"}>{who} ▾</summary>
          <div className="menu-items">
            <Link className="menu-link" href="/orders" onClick={closeMenu}>
              Orders
            </Link>
            <Link className="menu-link" href="/settings" onClick={closeMenu}>
              Settings
            </Link>
            <hr className="menu-sep" />
            <button onClick={logout}>Log out</button>
          </div>
        </details>
      </div>
    </header>
  );
}
