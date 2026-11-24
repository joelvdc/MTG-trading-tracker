"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/components/api";
import { TopBar } from "@/components/Nav";
import { BracketCardSvg, BracketPrintRoot, PER_PAGE, type BracketCardData } from "@/components/RuleZeroCards";
import type { RuleZeroDeck } from "@/lib/decks";
import type { SaltSum } from "@/lib/localratings";
import type { WinResult } from "@/lib/winodds";

/** A deck's card as the route answers with it: its salt sum is worked out there. */
type CardDeck = Omit<RuleZeroDeck, "stats"> & { saltSum: SaltSum | null };

/** A deck's win turn as this browser kept it (before the website kept them, and for Nextcloud folders); it may be from before a change. */
function keptWin(id: number): WinResult | null {
  try {
    const kept = JSON.parse(localStorage.getItem(`mtg.winOdds.${id}`) ?? "null") as { win: WinResult | null } | null;
    return kept?.win ?? null;
  } catch {
    return null;
  }
}

/** The bracket cards of every rated deck, to look at or print together, nine to an A4 page (the app's "Bracket cards"). */
export default function AllRuleZeroCardsPage() {
  const [cards, setCards] = useState<BracketCardData[] | null>(null);
  const [unrated, setUnrated] = useState<string[]>([]);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [inkSaver, setInkSaver] = useState(false);
  // The indexes (in cards) of the cards not ticked for printing; every card starts ticked and nothing is kept.
  const [off, setOff] = useState<Set<number>>(new Set());

  /** Tick or untick one card for printing. */
  function toggle(i: number) {
    setOff((was) => {
      const next = new Set(was);
      if (next.has(i)) next.delete(i);
      else next.add(i);
      return next;
    });
  }

  useEffect(() => {
    (async () => {
      const [s, d] = await Promise.all([
        api<{ loggedIn: boolean; folder?: string }>("/api/session"),
        api<{ decks?: CardDeck[]; unrated?: string[]; wins?: Record<number, WinResult | null>; noFolder?: boolean }>("/api/decks/cards"),
      ]);
      if (!s.data.loggedIn || d.status === 401 || d.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || d.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (d.ok && Array.isArray(d.data.decks)) {
        const kept = d.data.wins ?? {};
        setCards(
          d.data.decks.map((x) => ({
            deck: x.deck,
            cover: x.cover,
            data: x.salt,
            win: x.id in kept ? kept[x.id] : keptWin(x.id),
            saltSum: x.saltSum ?? null,
            roles: x.roles ?? [],
          })),
        );
        setUnrated(d.data.unrated ?? []);
      } else setError(d.data.error ?? "Could not load the decks.");
    })();
  }, []);

  // The ticked cards: only these are shown printed, and only these count for the pages.
  const chosen = cards ? cards.filter((_, i) => !off.has(i)) : [];
  const pages = Math.ceil(chosen.length / PER_PAGE);

  return (
    <>
      <TopBar page="decks" folder={folder} />
      <main className="page wide">
        <Link href="/decks" className="small">
          ← Decks
        </Link>
        <h1 className="section-head">Bracket cards</h1>
        {error && <div className="bar error">{error}</div>}
        {!cards && !error && <p className="muted center">Loading…</p>}
        {cards && (
          <>
            <div className="controls">
              <label className="check small">
                <input type="checkbox" checked={inkSaver} onChange={(e) => setInkSaver(e.target.checked)} />
                <span>Ink saver</span>
              </label>
              {cards.length > 0 && (
                <>
                  <button className="btn small-btn" onClick={() => setOff(new Set())}>
                    Select all
                  </button>
                  <button className="btn small-btn" onClick={() => setOff(new Set(cards.map((_, i) => i)))}>
                    None
                  </button>
                </>
              )}
              <span className="spacer" />
              <span className="muted small">
                {chosen.length} {chosen.length === 1 ? "card" : "cards"}
                {chosen.length > 0 && ` · ${pages} A4 ${pages === 1 ? "page" : "pages"}`}
              </span>
              <button className="btn primary small-btn" disabled={chosen.length === 0} onClick={() => window.print()}>
                Print
              </button>
            </div>
            {unrated.length > 0 && <p className="muted small">Not rated yet: {unrated.join(", ")}</p>}
            {cards.length === 0 ? (
              <p className="muted center">No deck has been rated yet.</p>
            ) : (
              <div className="rz-grid">
                {cards.map((c, i) => (
                  <label key={i} className={off.has(i) ? "rz-pick off" : "rz-pick"}>
                    <input
                      type="checkbox"
                      className="rz-pick-box"
                      checked={!off.has(i)}
                      onChange={() => toggle(i)}
                      aria-label={`Print ${c.deck.name}`}
                    />
                    <BracketCardSvg card={c} inkSaver={inkSaver} />
                  </label>
                ))}
              </div>
            )}
            {chosen.length > 0 && <BracketPrintRoot cards={chosen} inkSaver={inkSaver} />}
          </>
        )}
      </main>
    </>
  );
}
