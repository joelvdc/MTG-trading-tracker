"use client";

import { useEffect, useState } from "react";
import type { CardUse } from "@/lib/decks";
import type { DeckView } from "@/lib/deckshared";
import { format } from "@/lib/money";
import { recFacts, type RecCard, type RecSource } from "@/lib/recshared";
import { api } from "./api";
import { cardmarketUrlFor, cardtraderUrlFor } from "./cardLinks";
import { CardRulesTabs } from "./CardRules";
import { Dialog } from "./Dialog";
import { FlipPicture } from "./FlipPicture";
import type { RecTarget } from "./RecommendationsPanel";

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
 * that put it in the deck (a local deck), its maybeboard (a deck from Archidekt: its local one) or the wishlist.
 */
export function RecCardDialog({
  view,
  rec,
  source,
  canDeck,
  canMaybe,
  onWishlist,
  inMaybe,
  adding,
  onSend,
  onClose,
}: {
  view: DeckView;
  rec: RecCard;
  source: RecSource;
  canDeck: boolean;
  canMaybe: boolean;
  /** Already on the wishlist. */
  onWishlist: boolean;
  /** Put in the maybeboard on this visit. */
  inMaybe: boolean;
  /** "deck:Name", "maybe:Name" or "wish:Name" while that is being saved. */
  adding: string | null;
  onSend: (to: RecTarget) => void;
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
  const money = (eur: number | null) => (eur == null ? "–" : format(eur * view.perEuro, view.currency));
  if (!card) return null;
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
          <div className="price-line">
            <strong>{money(card.fallbackEur)}</strong>
            {card.fallbackEurFoil != null && <span className="muted"> · foil {money(card.fallbackEurFoil)}</span>}
          </div>
          <div className="shop-links">
            {canDeck && (
              <button className="btn small-btn primary" disabled={adding !== null} onClick={() => onSend("deck")} title="Add one copy to the deck">
                {adding === `deck:${rec.name}` ? "Adding…" : "Add to deck"}
              </button>
            )}
            {canMaybe && (
              <button
                className="btn small-btn"
                disabled={adding !== null || inMaybe}
                onClick={() => onSend("maybe")}
                title={view.local ? "Add one copy to the maybeboard" : "Add one copy to the deck's local maybeboard (Archidekt doesn't see it)"}
              >
                {adding === `maybe:${rec.name}` ? "Adding…" : inMaybe ? "✓ In the maybeboard" : "Add to maybeboard"}
              </button>
            )}
            <button className="btn small-btn" disabled={adding !== null || onWishlist || view.readOnly} onClick={() => onSend("wish")}>
              {adding === `wish:${rec.name}` ? "Adding…" : onWishlist ? "★ On the wishlist" : "☆ Add to wishlist"}
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
          {source === "edhrec" ? "Recommended by EDHREC for this commander." : "Recommended by recommander.cards for this commander and decklist."}
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
