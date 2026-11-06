// The decks in the sync file (the app's SyncDeck: Deck + DeckCard lines) and what the decks pages show: the list
// (DecksScreen), a deck's cards by category or type (DeckGrouping), owned and missing copies (DeckToCollection,
// DeckPrintings), bracket tags (SaltCard.tagsByCard) and prices. Shared pieces: deckshared.ts.
import { frontName } from "./collview";
import {
  coverUrl,
  decodeSalt,
  isBasicName,
  missingPlan,
  POWER_SOURCES,
  powerError,
  powerOf,
  powerSource,
  saltKey,
  tagsByCard,
  type BracketTag,
  type Deck,
  type DeckCard,
  type DeckGroup,
  type DeckRow,
  type DecksView,
  type DeckView,
  type PowerSource,
  type SyncDeck,
} from "./deckshared";
import { currency, rate } from "./money";
import { PRICE_TYPE_LABELS, priceType, type PriceFile } from "./prices";
import { cardmarketPrice, finishOf, pricingSetup, unitPrice, type PricingSetup } from "./pricing";
import { binders as bindersOf, canWrite, collection, prefs, type CardRef, type Json, type SyncFile } from "./syncfile";
import type { DetailsFile } from "./carddetails";
import type { CkFile } from "./cardkingdom";

export * from "./deckshared";

export function syncDecks(file: SyncFile): SyncDeck[] {
  return ((file.data.decks as Json[] | undefined) ?? []) as unknown as SyncDeck[];
}

/** Copies in the collection by lower-case full name (the app's observeOwnedByName). */
export function ownedByName(file: SyncFile): Map<string, number> {
  const m = new Map<string, number>();
  for (const s of collection(file)) {
    const k = s.item.card.name.toLowerCase();
    m.set(k, (m.get(k) ?? 0) + s.item.quantity);
  }
  return m;
}

function setupOf(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): PricingSetup {
  const p = prefs(file);
  return pricingSetup(p, priceType(p.priceType), prices, details, ck);
}

function lineUnit(setup: PricingSetup, c: DeckCard, card: CardRef = c.card, foil = c.foil, etched = c.etched): number | null {
  return unitPrice(setup, card.scryfallId, finishOf(foil, etched), "NM", cardmarketPrice(card, foil, setup.type, setup.prices));
}

export function groupOf(d: Deck, owner: string | null): DeckGroup {
  if (d.archidektId < 0) return "LOCAL";
  return owner && d.owner.toLowerCase() === owner.toLowerCase() ? "OWN" : "OTHERS";
}

/** The Archidekt user whose decks are "own": the one logged in, else the one decks were last imported from. */
export function deckOwner(file: SyncFile): string | null {
  const p = prefs(file);
  const own = (p.ownArchidektUser ?? "").trim() ? p.ownArchidektUser : p.archidektUser;
  return own && own.trim() ? own : null;
}

export function decksView(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): DecksView {
  const p = prefs(file);
  const setup = setupOf(file, prices, details, ck);
  const owned = ownedByName(file);
  const source = powerSource(p.powerSource);
  const owner = deckOwner(file);
  const cur = currency(p.currency);
  return {
    decks: syncDecks(file).map(({ deck: d, cards }) => {
      const main = cards.filter((c) => !c.maybeboard);
      return {
        id: d.archidektId,
        name: d.name,
        owner: d.owner,
        commanders: d.commanders,
        cover: coverUrl(d),
        colorIdentity: d.colorIdentity,
        cardCount: d.cardCount ?? main.reduce((n, c) => n + c.quantity, 0),
        group: groupOf(d, owner),
        folder: d.folderName ?? null,
        origin: d.origin ?? null,
        bracketRealistic: d.bracketRealistic ?? null,
        bracketBaseline: d.bracketBaseline ?? null,
        power: powerOf(d, source),
        saltPercent: d.saltPercent ?? null,
        archetype: d.archetype ?? null,
        archidektUpdatedAt: d.archidektUpdatedAt ?? null,
        valueEur: main.reduce((s, c) => s + (lineUnit(setup, c) ?? 0) * c.quantity, 0),
        missing: missingPlan(
          main.map((c) => ({ name: c.card.name, quantity: c.quantity })),
          owned,
          true,
        ).reduce((s, [, n]) => s + n, 0),
      };
    }),
    currency: cur,
    perEuro: rate(cur, prices?.rates ?? null) ?? 1,
    powerSource: source,
    powerLabel: POWER_SOURCES[source].label,
    sort: p.deckSort ?? "NAME",
    sortReversed: p.deckSortReversed === "true",
    readOnly: !canWrite(file),
  };
}

function decodePicks(s: string | undefined): Record<string, Record<string, string>> {
  try {
    const j = JSON.parse(s ?? "{}");
    return j && typeof j === "object" ? j : {};
  } catch {
    return {};
  }
}

interface Copy {
  card: CardRef;
  foil: boolean;
  etched: boolean;
  quantity: number;
  binder: string | null;
}

/** The printing you own to show for a line (DeckPrintings.match), or null to keep the deck's. */
function ownPrinting(c: DeckCard, copies: Copy[], deckBinder: string | null, tradeBinder: string | null, pick: string | undefined): Copy | null {
  if (copies.length === 0 || pick === "archidekt") return null;
  const finish = finishOf(c.foil, c.etched);
  let choice: Copy | undefined;
  if (pick) {
    const [sid, f] = pick.split("|");
    choice = copies.find((x) => x.card.scryfallId === sid && (!f || finishOf(x.foil, x.etched) === f));
  }
  choice ??= copies.find((x) => deckBinder !== null && x.binder === deckBinder);
  choice ??= copies.find((x) => x.card.scryfallId === c.card.scryfallId && finishOf(x.foil, x.etched) === finish);
  choice ??= copies.find((x) => x.card.scryfallId === c.card.scryfallId);
  if (!choice) {
    const outside = copies.filter((x) => x.binder !== tradeBinder);
    const pool = outside.length ? outside : copies;
    const groups = new Map<string, Copy[]>();
    for (const x of pool) {
      const k = `${x.card.scryfallId}|${finishOf(x.foil, x.etched)}`;
      if (!groups.has(k)) groups.set(k, []);
      groups.get(k)!.push(x);
    }
    let best: [string, Copy[]] | null = null;
    for (const e of groups) {
      if (!best) best = e;
      else {
        const n = e[1].reduce((s, x) => s + x.quantity, 0);
        const bn = best[1].reduce((s, x) => s + x.quantity, 0);
        // maxWith(compareBy(count).thenBy(scryfallId)): the last of the largest wins a tie on the id.
        if (n > bn || (n === bn && e[1][0].card.scryfallId >= best[1][0].card.scryfallId)) best = e;
      }
    }
    choice = best![1][0];
  }
  if (choice.card.scryfallId === c.card.scryfallId && finishOf(choice.foil, choice.etched) === finish) return null;
  return choice;
}

export function deckView(file: SyncFile, id: number, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): DeckView | null {
  const sd = syncDecks(file).find((d) => d.deck.archidektId === id);
  if (!sd) return null;
  const p = prefs(file);
  const d = sd.deck;
  const setup = setupOf(file, prices, details, ck);
  const owned = ownedByName(file);
  const salt = decodeSalt(d.saltCard);
  const tags = salt ? tagsByCard(salt) : new Map<string, BracketTag[]>();
  const off = new Set((p.deckPrintingsOff ?? "").split(",").map((x) => x.trim()));
  const myPrintings = !off.has(String(id));
  const picks = decodePicks(p.deckPicks)[String(id)] ?? {};
  const binders = bindersOf(file).filter((b) => b.uid);
  const deckBinder = binders.find((b) => b.name.trim().toLowerCase() === d.name.trim().toLowerCase())?.uid ?? null;
  const tradeBinder = binders.find((b) => b.name.toLowerCase() === "trade binder")?.uid ?? null;
  const copiesByName = new Map<string, Copy[]>();
  for (const s of collection(file)) {
    const k = frontName(s.item.card.name);
    if (!copiesByName.has(k)) copiesByName.set(k, []);
    copiesByName.get(k)!.push({ card: s.item.card, foil: s.item.foil, etched: s.item.etched, quantity: s.item.quantity, binder: s.binder });
  }
  const main = sd.cards.filter((c) => !c.maybeboard);
  const missing = new Map<DeckCard, number>(missingPlan(main.map((c) => ({ c, name: c.card.name, quantity: c.quantity })), owned, true).map(([l, n]) => [l.c, n]));
  const cur = currency(p.currency);
  const source = powerSource(p.powerSource);
  const rows = sd.cards.map((c): DeckRow => {
    const key = frontName(c.card.name);
    const mine = myPrintings && !isBasicName(key) && !c.maybeboard ? ownPrinting(c, copiesByName.get(key) ?? [], deckBinder, tradeBinder, picks[key]) : null;
    const card = mine?.card ?? c.card;
    const foil = mine ? mine.foil : c.foil;
    const etched = mine ? mine.etched : c.etched;
    const t = [...(tags.get(saltKey(c.card.name)) ?? [])];
    if (c.gameChanger && !t.includes("GAME_CHANGER")) t.unshift("GAME_CHANGER");
    return {
      lineUid: c.lineUid,
      name: c.card.name,
      card,
      original: mine ? c.card : null,
      finish: finishOf(foil, etched),
      quantity: c.quantity,
      category: c.category,
      categories: (c.categories ?? "").split("\n").filter((x) => x.trim()).length ? c.categories.split("\n").filter((x) => x.trim()) : [c.category],
      types: c.types,
      cmc: c.cmc,
      commander: c.commander,
      gameChanger: c.gameChanger,
      addedInApp: c.addedInApp,
      maybeboard: c.maybeboard,
      unitEur: lineUnit(setup, c, card, foil, etched),
      owned: owned.get(c.card.name.toLowerCase()) ?? 0,
      missing: missing.get(c) ?? 0,
      tags: t,
    };
  });
  const { saltCard, scrollVault, categoryInfo, ...plain } = d;
  void saltCard;
  void categoryInfo;
  let sv: Record<string, Json> | null = null;
  try {
    sv = scrollVault ? (JSON.parse(scrollVault) as Record<string, Json>) : null;
  } catch {
    sv = null;
  }
  return {
    id,
    local: id < 0,
    deck: plain,
    cover: coverUrl(d),
    group: groupOf(d, deckOwner(file)),
    salt,
    scrollVault: sv,
    rows,
    hidden: Object.keys(decodePicks(p.deckHidden)[String(id)] ?? {}),
    myPrintings,
    currency: cur,
    perEuro: rate(cur, prices?.rates ?? null) ?? 1,
    priceType: setup.type,
    priceTypeLabel: PRICE_TYPE_LABELS[setup.type],
    powerSource: source,
    power: powerOf(d, source),
    powerError: powerError(d, source),
    readOnly: !canWrite(file),
  };
}
