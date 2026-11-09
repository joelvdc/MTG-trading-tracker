"use client";

import { useEffect, useState } from "react";
import type { CardUse } from "@/lib/decks";
import { recFacts, type RecCard, type RecSource } from "@/lib/recshared";
import { api } from "./api";
import { cardmarketUrlFor, cardtraderUrlFor } from "./cardLinks";
import { CardRulesTabs } from "./CardRules";
import { Dialog } from "./Dialog";
import { FlipPicture } from "./FlipPicture";
import { addKey, type RecDeck, type RecTarget } from "./RecTiles";

/** The card's page on EDHREC (the app's RecCardDialog link). */
function edhrecCardSlug(name: string): string {
  return name
    .split(" // ")[0]
    .toLowerCase()
    .replace(/[^a-z0-9 -]/g, "")
    .trim()
    .replace(/[ -]+/g, "-");
}

/**
 * A recommended card's window (the app's RecCardDialog), near-full-screen: its picture, what the source says,
 * whether you own it and which decks use it, the card text and rulings, its price and shop links, and the buttons
 * that put it in a deck (a local deck), a deck's maybeboard (a deck from Archidekt: its local one) or the wishlist.
 * With one deck the buttons are plain; with several (the all-decks page) each deck gets a row of its own.
 */
export function RecCardDialog({
  rec,
  source,
  money,
  readOnly,
  decks,
  named = false,
  onWishlist,
  inMaybe,
  inDeck,
  adding,
  onSend,
  onClose,
}: {
  rec: RecCard;
  source: RecSource;
  money: (eur: number | null) => string;
  /** The sync file can't be changed (no buttons that save). */
  readOnly: boolean;
  /** The decks it's recommended for. */
  decks: RecDeck[];
  /** Name the deck(s) even when there's one (the all-decks page). */
  named?: boolean;
  /** Already on the wishlist. */
  onWishlist: boolean;
  /** Put in that deck's maybeboard on this visit. */
  inMaybe: (deckId: number) => boolean;
  /** Put in that (local) deck on this visit. */
  inDeck: (deckId: number) => boolean;
  /** addKey(…) while that is being saved. */
  adding: string | null;
  onSend: (to: RecTarget, deckId: number | null) => void;
  onClose: () => void;
}) {
  const [use, setUse] = useState<CardUse | null>(null);
  const card = rec.card;
  useEffect(() => {
    let live = true;
    api<CardUse>(`/api/decks/carduse?name=${encodeURIComponent(rec.name)}`).then((r) => {
      if (live && r.ok) setUse(r.data);
    });
    return () => {
      live = false;
    };
  }, [rec.name]);
  if (!card) return null;
  const busy = adding !== null;
  const rows = named || decks.length > 1;
  const deckButtons = (d: RecDeck, named: boolean) => (
    <>
      {d.local && (
        <button className="btn small-btn primary" disabled={busy || inDeck(d.id)} onClick={() => onSend("deck", d.id)} title="Add one copy to the deck">
          {adding === addKey("deck", d.id, rec.name) ? "Adding…" : inDeck(d.id) ? "✓ In the deck" : named ? "+ Deck" : "Add to deck"}
        </button>
      )}
      <button
        className="btn small-btn"
        disabled={busy || inMaybe(d.id)}
        onClick={() => onSend("maybe", d.id)}
        title={d.local ? "Add one copy to the maybeboard" : "Add one copy to the deck's local maybeboard (Archidekt doesn't see it)"}
      >
        {adding === addKey("maybe", d.id, rec.name) ? "Adding…" : inMaybe(d.id) ? "✓ In the maybeboard" : named ? "+ Maybe" : "Add to maybeboard"}
      </button>
    </>
  );
  return (
    <Dialog title={rec.name} onClose={onClose}>
      <div className="dialog-top">
        {card.imageUrl && <FlipPicture url={card.imageUrl} className="big-img" alt={rec.name} typeLine={rec.typeLine} />}
        <div className="dialog-info">
          <div className="muted">
            {rec.typeLine} · {card.setName || card.setCode.toUpperCase()} #{card.collectorNumber} · {card.rarity}
          </div>
          <div className="muted small">
            {recFacts(rec)}
            {rec.isNew && " · new"}
            {rec.releasedAt ? ` · released ${rec.releasedAt}` : ""}
          </div>
          <div className="small">{use ? (use.owned > 0 ? `You own ${use.owned}` : "Not in your collection") : "Checking your collection…"}</div>
          {use && (
            <div className="small muted">
              {use.uses.length === 0
                ? "Not in any deck."
                : `In ${use.uses.length === 1 ? "1 deck" : `${use.uses.length} decks`}: ${use.uses.map((u) => `${u.deckName} ×${u.quantity}`).join(", ")}`}
            </div>
          )}
          {!readOnly && rows && (
            <ul className="plain-list rec-deck-rows">
              {decks.map((d) => (
                <li key={d.id}>
                  <a href={`/decks/${d.id}`} className="grow">
                    {d.name}
                  </a>
                  {deckButtons(d, true)}
                </li>
              ))}
            </ul>
          )}
          <div className="price-line">
            <strong>{money(card.fallbackEur)}</strong>
            {card.fallbackEurFoil != null && <span className="muted"> · foil {money(card.fallbackEurFoil)}</span>}
          </div>
          <div className="shop-links">
            {!readOnly && !rows && decks.length === 1 && deckButtons(decks[0], false)}
            <button className="btn small-btn" disabled={busy || onWishlist || readOnly} onClick={() => onSend("wish", null)}>
              {adding === addKey("wish", null, rec.name) ? "Adding…" : onWishlist ? "★ On the wishlist" : "☆ Add to wishlist"}
            </button>
            <a className="btn small-btn" href={`https://edhrec.com/cards/${edhrecCardSlug(rec.name)}`} target="_blank" rel="noreferrer">
              EDHREC ↗
            </a>
            <a className="btn small-btn" href={cardmarketUrlFor(card, false)} target="_blank" rel="noreferrer">
              Cardmarket ↗
            </a>
            <a className="btn small-btn" href={cardtraderUrlFor(card)} target="_blank" rel="noreferrer">
              CardTrader ↗
            </a>
          </div>
        </div>
      </div>
      <CardRulesTabs scryfallId={card.scryfallId}>
        <p className="muted small">
          {source === "edhrec" ? "Recommended by EDHREC for " : "Recommended by recommander.cards for "}
          {!rows ? (source === "edhrec" ? "this commander." : "this commander and decklist.") : `${decks.map((d) => d.name).join(", ")}.`}
        </p>
      </CardRulesTabs>
      <div className="dialog-actions">
        <span className="spacer" />
        <button className="btn" onClick={onClose}>
          Close
        </button>
      </div>
    </Dialog>
  );
}
