"use client";

/** The top bar on every page after logging in: the website's sections and Settings. */
export function TopBar({ page, folder }: { page: "collection" | "stats" | "decks" | "orders" | "trades" | "settings"; folder?: string }) {
  const link = (href: string, label: string, on: boolean) => (
    <a href={href} className={on ? "nav-link on" : "nav-link"} aria-current={on ? "page" : undefined}>
      {label}
    </a>
  );
  return (
    <header className="topbar">
      <div className="topbar-in wide">
        <div className="brand">
          <strong>MTG Trader</strong>
          {folder && <div className="muted small">{folder}</div>}
        </div>
        <nav className="nav" aria-label="Sections">
          {link("/collection", "Collection", page === "collection" || page === "stats")}
          {link("/decks", "Decks", page === "decks")}
          {link("/orders", "Orders", page === "orders")}
          {link("/trades", "Trades", page === "trades")}
        </nav>
        <a className={page === "settings" ? "btn on" : "btn"} href="/settings">
          Settings
        </a>
      </div>
    </header>
  );
}
