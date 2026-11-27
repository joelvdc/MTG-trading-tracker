// Links to a card at the shops (the app's CardRef.cardmarketUrl; CardTrader by name) and to its pages elsewhere.
import type { CardRef } from "@/lib/syncfile";

/** Cardmarket's search for a card's name (when its printing, and so its product number, is unknown). */
export function cardmarketUrlByName(name: string): string {
  return "https://www.cardmarket.com/en/Magic/Products/Search?searchString=" + encodeURIComponent(name);
}

/** This printing's Cardmarket page, or a name search when Cardmarket's product number is unknown. */
export function cardmarketUrlFor(card: CardRef, foil: boolean): string {
  const id = foil ? (card.cardmarketFoilId ?? card.cardmarketId) : card.cardmarketId;
  if (id == null) return cardmarketUrlByName(card.name);
  return `https://www.cardmarket.com/en/Magic/Products?idProduct=${id}`;
}

/** A CardTrader search for a card's name (an exact printing needs the owner's CardTrader key, kept on the phone). */
export function cardtraderUrlByName(name: string): string {
  return "https://www.cardtrader.com/en/search?q=" + encodeURIComponent(name);
}

/** A CardTrader search for the card. */
export function cardtraderUrlFor(card: CardRef): string {
  return cardtraderUrlByName(card.name);
}

/** The card's page on EDHREC (the app's RecCardDialog link). */
export function edhrecCardSlug(name: string): string {
  return name
    .split(" // ")[0]
    .toLowerCase()
    .replace(/[^a-z0-9 -]/g, "")
    .trim()
    .replace(/[ -]+/g, "-");
}

/** The card's EDHREC page, from its name. */
export function edhrecUrlFor(name: string): string {
  return `https://edhrec.com/cards/${edhrecCardSlug(name)}`;
}
