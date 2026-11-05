// Links to a card at the shops (the app's CardRef.cardmarketUrl; CardTrader by name).
import type { CardRef } from "@/lib/syncfile";

/** This printing's Cardmarket page, or a name search when Cardmarket's product number is unknown. */
export function cardmarketUrlFor(card: CardRef, foil: boolean): string {
  const id = foil ? (card.cardmarketFoilId ?? card.cardmarketId) : card.cardmarketId;
  if (id == null) return "https://www.cardmarket.com/en/Magic/Products/Search?searchString=" + encodeURIComponent(card.name);
  return `https://www.cardmarket.com/en/Magic/Products?idProduct=${id}`;
}

/** A CardTrader search for the card's name (an exact printing needs the owner's CardTrader key, kept on the phone). */
export function cardtraderUrlFor(card: CardRef): string {
  return "https://www.cardtrader.com/en/search?q=" + encodeURIComponent(card.name);
}
