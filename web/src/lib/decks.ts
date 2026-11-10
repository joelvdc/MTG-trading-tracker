// The decks in the sync file (the app's SyncDeck: Deck + DeckCard lines) and what the decks pages show: the list
// (DecksScreen), a deck's cards by category or type (DeckGrouping), owned and missing copies (DeckToCollection,
// DeckPrintings), bracket tags (SaltCard.tagsByCard) and prices. Shared pieces: deckshared.ts.
import { frontName } from "./collview";
import { categories, included } from "./deckedit";
import { recListKey } from "./recshared";
import {
  caseless,
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
  type DeckCategory,
  type DeckGroup,
  type DeckRow,
  type DeckUse,
  type DecksView,
  type DeckView,
  type HeldPile,
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
  if (d.retired) return "RETIRED";
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
        modifiedAt: d.archidektId < 0 ? (d.updatedAt > 0 ? d.updatedAt : null) : (d.archidektUpdatedAt ?? null),
        valueEur: main.reduce((s, c) => s + (lineUnit(setup, c) ?? 0) * c.quantity, 0),
        missing: missingPlan(
          main.map((c) => ({ name: c.card.name, quantity: c.quantity })),
          owned,
          true,
        ).reduce((s, [, n]) => s + n, 0),
        recKey: recListKey(main.map((c) => ({ name: c.card.name, commander: c.commander }))),
      };
    }),
    currency: cur,
    perEuro: rate(cur, prices?.rates ?? null) ?? 1,
    powerSource: source,
    powerLabel: POWER_SOURCES[source].label,
    sort: p.deckSort ?? "NAME",
    sortReversed: p.deckSortReversed === "true",
    readOnly: !canWrite(file),
    archidektUser: owner,
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

/** The printings you own, each printing and finish once (DeckScreen's printing dialog). */
function choicesOf(copies: Copy[]): DeckRow["choices"] {
  const by = new Map<string, DeckRow["choices"][number]>();
  for (const x of copies) {
    const f = finishOf(x.foil, x.etched);
    const value = `${x.card.scryfallId}|${f}`;
    const had = by.get(value);
    if (had) had.quantity += x.quantity;
    else by.set(value, { value, label: `${x.card.setName || x.card.setCode.toUpperCase()} #${x.card.collectorNumber}${f === "FOIL" ? " · foil" : f === "ETCHED" ? " · etched" : ""}`, quantity: x.quantity });
  }
  return [...by.values()];
}

/** The deck's categories: those of its lines (first category) and those in categoryInfo, Commander first. */
function categoriesOf(sd: SyncDeck): DeckCategory[] {
  const info = categories(sd.deck.categoryInfo);
  const out = new Map<string, DeckCategory>();
  const add = (name: string) => {
    const k = name.toLowerCase();
    if (!out.has(k)) out.set(k, { name, included: included(name, info), cards: 0 });
    return out.get(k)!;
  };
  for (const c of sd.cards) add(c.category).cards += c.quantity;
  for (const c of info) add(c.name);
  return [...out.values()].sort((a, b) => (a.name.toLowerCase() === "commander" ? -1 : b.name.toLowerCase() === "commander" ? 1 : caseless(a.name, b.name)));
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
  const binderNames = new Map(binders.map((b) => [b.uid, b.name]));
  const pilesByName = new Map<string, HeldPile[]>();
  for (const s of collection(file)) {
    const k = s.item.card.name.toLowerCase();
    if (!pilesByName.has(k)) pilesByName.set(k, []);
    pilesByName.get(k)!.push({
      scryfallId: s.item.card.scryfallId,
      setCode: s.item.card.setCode,
      setName: s.item.card.setName,
      collectorNumber: s.item.card.collectorNumber,
      finish: finishOf(s.item.foil, s.item.etched),
      condition: s.item.condition,
      binder: (s.binder && binderNames.get(s.binder)) || "Unsorted",
      quantity: s.item.quantity,
    });
  }
  // The other decks' copies of each card, by name (as sharedCards matches them), main deck and maybeboard apart.
  const usesByName = new Map<string, DeckUse[]>();
  for (const other of [...syncDecks(file)].filter((x) => x.deck.archidektId !== id && !x.deck.retired).sort((a, b) => caseless(a.deck.name, b.deck.name))) {
    const here = new Map<string, DeckUse>();
    for (const c of other.cards) {
      if (isBasicName(c.card.name)) continue;
      const k = `${c.card.name.toLowerCase()}|${c.maybeboard}`;
      const u = here.get(k) ?? { deckId: other.deck.archidektId, deckName: other.deck.name, quantity: 0, maybeboard: c.maybeboard };
      u.quantity += c.quantity;
      here.set(k, u);
    }
    for (const [k, u] of here) {
      const name = k.slice(0, k.lastIndexOf("|"));
      if (!usesByName.has(name)) usesByName.set(name, []);
      usesByName.get(name)!.push(u);
    }
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
    const shown = finishOf(foil, etched);
    const held = !isBasicName(key) ? (pilesByName.get(c.card.name.toLowerCase()) ?? []) : [];
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
      exact: held.filter((h) => h.scryfallId === card.scryfallId && h.finish === shown).reduce((n, h) => n + h.quantity, 0),
      held,
      otherDecks: usesByName.get(c.card.name.toLowerCase()) ?? [],
      missing: missing.get(c) ?? 0,
      tags: t,
      choices: !isBasicName(key) && !c.maybeboard ? choicesOf(copiesByName.get(key) ?? []) : [],
      picked: picks[key] ?? null,
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
    recKey: recListKey(sd.cards.filter((c) => !c.maybeboard).map((c) => ({ name: c.card.name, commander: c.commander }))),
    hidden: Object.keys(decodePicks(p.deckHidden)[String(id)] ?? {}),
    tokenPicks: decodePicks(p.deckTokenPicks)[String(id)] ?? {},
    categories: categoriesOf(sd),
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

/** A card in more than one deck (the app's SharedCardsScreen): the decks that use it and how many copies you own. */
export interface SharedCard {
  name: string;
  imageUrl: string | null;
  owned: number;
  need: number;
  uses: { deckId: number; deckName: string; quantity: number }[];
}

/**
 * Cards that are in more than one deck (DeckDao.observeUsage + DeckUsage.byName): the main deck only, basic lands left
 * out, by name; most decks first, then by name. The decks are in name order.
 */
export function sharedCards(file: SyncFile): SharedCard[] {
  const owned = ownedByName(file);
  const by = new Map<string, SharedCard>();
  const decks = [...syncDecks(file)].filter((d) => !d.deck.retired).sort((a, b) => caseless(a.deck.name, b.deck.name));
  for (const { deck, cards } of decks) {
    const here = new Map<string, { name: string; quantity: number; imageUrl: string | null }>();
    for (const c of cards) {
      if (c.maybeboard || isBasicName(c.card.name)) continue;
      const k = c.card.name.toLowerCase();
      const h = here.get(k) ?? { name: c.card.name, quantity: 0, imageUrl: null };
      h.quantity += c.quantity;
      h.imageUrl = h.imageUrl && c.card.imageUrl ? (h.imageUrl > c.card.imageUrl ? h.imageUrl : c.card.imageUrl) : (h.imageUrl ?? c.card.imageUrl ?? null);
      here.set(k, h);
    }
    for (const [k, h] of here) {
      const s = by.get(k) ?? { name: h.name, imageUrl: null, owned: owned.get(k) ?? 0, need: 0, uses: [] };
      s.imageUrl ??= h.imageUrl;
      s.need += h.quantity;
      s.uses.push({ deckId: deck.archidektId, deckName: deck.name, quantity: h.quantity });
      by.set(k, s);
    }
  }
  return [...by.entries()]
    .filter(([, s]) => s.uses.length > 1)
    .sort(([ka, a], [kb, b]) => b.uses.length - a.uses.length || (ka < kb ? -1 : ka > kb ? 1 : 0))
    .map(([, s]) => s);
}

/** A card's use (a recommended card's window): the decks that play it and how many copies you own. */
export interface CardUse {
  owned: number;
  uses: { deckId: number; deckName: string; quantity: number }[];
}

/**
 * The decks with a card in the main deck (basic lands left out, retired decks don't count, like [sharedCards]),
 * in deck name order, and your copies of it by name.
 */
export function cardUse(file: SyncFile, name: string): CardUse {
  const k = name.toLowerCase();
  const uses: CardUse["uses"] = [];
  for (const { deck, cards } of syncDecks(file)) {
    if (deck.retired) continue;
    let quantity = 0;
    for (const c of cards) {
      if (c.maybeboard || isBasicName(c.card.name)) continue;
      if (c.card.name.toLowerCase() === k) quantity += c.quantity;
    }
    if (quantity > 0) uses.push({ deckId: deck.archidektId, deckName: deck.name, quantity });
  }
  uses.sort((a, b) => caseless(a.deckName, b.deckName));
  return { owned: ownedByName(file).get(k) ?? 0, uses };
}

/** What the rule-zero cards of one deck need (the deck page's RuleZeroDialog), for "Rule-zero cards of all decks". */
export interface RuleZeroDeck {
  id: number;
  deck: DeckView["deck"];
  cover: string | null;
  salt: NonNullable<DeckView["salt"]>;
  scrollVault: Record<string, Json> | null;
  power: number | null;
  powerError: string | null;
}

/** Every deck Commander Salt has scored, in name order, with what its bracket and power cards show. */
export function ruleZeroDecks(file: SyncFile): { decks: RuleZeroDeck[]; powerSource: PowerSource } {
  const source = powerSource(prefs(file).powerSource);
  const decks: RuleZeroDeck[] = [];
  for (const { deck: d } of [...syncDecks(file)].filter((x) => !x.deck.retired).sort((a, b) => caseless(a.deck.name, b.deck.name))) {
    const salt = decodeSalt(d.saltCard);
    if (!salt) continue;
    const { saltCard, scrollVault, categoryInfo, ...plain } = d;
    void saltCard;
    void categoryInfo;
    let sv: Record<string, Json> | null = null;
    try {
      sv = scrollVault ? (JSON.parse(scrollVault) as Record<string, Json>) : null;
    } catch {
      sv = null;
    }
    decks.push({ id: d.archidektId, deck: plain, cover: coverUrl(d), salt, scrollVault: sv, power: powerOf(d, source), powerError: powerError(d, source) });
  }
  return { decks, powerSource: source };
}
