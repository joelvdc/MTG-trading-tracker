// Importing and reloading decks from Archidekt on the website, as the app does (DeckRepository.load / scoreSalt /
// updateAll). Reading Archidekt, Scryfall and Commander Salt happens first ([readArchidektDeck], [scoreDeck]); the
// sync file is then changed in one go ([putLoadedDeck], [putScores]), inside editSync.
import { fetchDeck, identityOf, userDecks, type ArchidektCard, type ArchidektDeck } from "./archidekt";
import { saltDeckId, scoreDeck, type SaltScores } from "./commandersalt";
import { addDeck, detachDeck, encodeCategories, newDeck, sortLines } from "./deckedit";
import { powerSource, type Deck, type DeckCard, type SyncDeck } from "./deckshared";
import { syncDecks } from "./decks";
import { lookupCollection } from "./scryfall";
import type { Account } from "./session";
import { editSync, loadSync } from "./store";
import { bump, prefs, type CardRef, type Json, type SyncFile } from "./syncfile";
import { EditError } from "./wishlist";

/** A deck read from Archidekt with its printings looked up on Scryfall (by Scryfall id; missing ones are left out). */
export interface ReadDeck {
  deck: ArchidektDeck;
  found: Record<string, CardRef>;
}

/** Reads a deck from Archidekt and looks its printings up on Scryfall (when Scryfall fails, Archidekt's facts are used). */
export async function readArchidektDeck(id: number, lookup: (ids: object[]) => Promise<CardRef[]> = lookupCollection): Promise<ReadDeck> {
  const deck = await fetchDeck(id);
  if (deck.cards.length === 0) throw new EditError(`${deck.name.trim() || "This deck"} has no cards.`);
  const ids = [...new Set([...deck.cards, ...deck.maybeboard].map((c) => c.scryfallId))];
  let refs: CardRef[] = [];
  try {
    refs = await lookup(ids.map((x) => ({ id: x })));
  } catch {
    // Archidekt's own names and sets will do.
  }
  return { deck, found: Object.fromEntries(refs.map((r) => [r.scryfallId, r])) };
}

/** A card the way Archidekt names it, when Scryfall didn't answer (DeckRepository.load's fallback CardRef). */
function fallbackRef(c: ArchidektCard): CardRef {
  const id = c.scryfallId;
  return {
    scryfallId: id,
    name: c.name,
    setCode: c.setCode,
    setName: c.setName,
    collectorNumber: c.collectorNumber,
    rarity: "",
    imageUrl: `https://cards.scryfall.io/normal/front/${id[0]}/${id[1]}/${id}.jpg`,
    cardmarketId: null,
    fallbackEur: null,
    fallbackEurFoil: null,
    hasNonFoil: true,
    hasFoil: c.foil,
    foilType: null,
    hasEtched: c.etched,
    flavorName: null,
    cardmarketFoilId: null,
  };
}

function line(r: ReadDeck, c: ArchidektCard, maybeboard: boolean): DeckCard {
  return {
    id: 0,
    deckId: r.deck.id,
    card: r.found[c.scryfallId] ?? fallbackRef(c),
    quantity: c.quantity,
    category: c.category,
    types: c.types,
    cmc: c.cmc,
    commander: c.commander,
    foil: c.foil,
    etched: c.etched,
    gameChanger: c.gameChanger,
    addedInApp: false,
    maybeboard,
    categories: c.categories.join("\n"),
    archidektCardId: c.archidektCardId,
    relationId: c.relationId,
    label: c.label,
    lineUid: crypto.randomUUID(),
  };
}

/**
 * Saves the deck's list as read from Archidekt (DeckRepository.load): new or in place of the old one, keeping its
 * scores until new ones arrive; the power level of a site other than the chosen one is dropped (it belonged to the old
 * list). Returns the saved deck.
 */
export function putLoadedDeck(file: SyncFile, r: ReadDeck, now: number): SyncDeck {
  const a = r.deck;
  const all = syncDecks(file);
  const old = all.find((d) => d.deck.archidektId === a.id)?.deck ?? null;
  const commanders = a.cards.filter((c) => c.commander);
  const source = powerSource(prefs(file).powerSource);
  let deck: Deck = {
    ...(old ?? newDeck(a.id, now, {})),
    name: a.name,
    owner: a.owner,
    commanders: [...new Set(commanders.map((c) => c.name))].join(" & "),
    commanderScryfallId: commanders[0]?.scryfallId ?? null,
    artUrl: a.artUrl,
    colorIdentity: identityOf(a),
    cardCount: a.cards.reduce((s, c) => s + c.quantity, 0),
    importedAt: now,
    archidektUpdatedAt: a.updatedAt ?? old?.archidektUpdatedAt ?? null,
    folderId: a.folderId ?? old?.folderId ?? null,
    folderName: old && old.folderId === a.folderId ? old.folderName : null,
    visibility: a.visibility,
    categoryInfo: encodeCategories(a.categories.map((c) => ({ name: c.name, included: c.includedInDeck, id: c.id }))),
    updatedAt: bump(old?.updatedAt ?? 0, now),
  };
  if (source !== "EDH_POWER_LEVEL") deck = { ...deck, edhPowerLevel: null, edhPowerAt: null, edhPowerError: null };
  if (source !== "SCROLLVAULT") deck = { ...deck, svPowerLevel: null, scrollVault: null, svAt: null, svError: null };
  // Cards added in the app (an old deck's "Added in app" ones and, since 1.86, its local maybeboard) stay, as in the app.
  const kept = (all.find((d) => d.deck.archidektId === a.id)?.cards ?? []).filter((c) => c.addedInApp);
  const sd: SyncDeck = { deck, cards: sortLines([...a.cards.map((c) => line(r, c, false)), ...a.maybeboard.map((c) => line(r, c, true)), ...kept]) };
  file.data.decks = all.filter((d) => d.deck.archidektId !== a.id) as unknown as Json;
  addDeck(file, sd);
  return sd;
}

/** Commander Salt's scores (or why it couldn't score) on the deck (DeckRepository.scoreSalt). */
export function putScores(file: SyncFile, id: number, scores: SaltScores | { error: string }, now: number): void {
  const sd = syncDecks(file).find((d) => d.deck.archidektId === id);
  if (!sd) return;
  const d = sd.deck;
  if ("error" in scores) d.scoreError = scores.error || "unknown error";
  else {
    d.saltCard = scores.saltCard ?? d.saltCard;
    d.saltId = scores.saltId || saltDeckId(id);
    d.powerLevel = scores.powerLevel;
    d.bracketRealistic = scores.bracketRealistic;
    d.bracketBaseline = scores.bracketBaseline;
    d.saltPercent = scores.saltPercent;
    d.archetype = scores.archetype;
    d.scoredAt = now;
    d.scoreError = null;
  }
  d.updatedAt = bump(d.updatedAt ?? 0, now);
  file.data.decks = syncDecks(file).map((x) => (x.deck.archidektId === id ? sd : x)) as unknown as Json;
}

/** What "Update all decks" does with a deck (DeckRepository.updateAll). */
export type UpdateNeed = "changed" | "check" | "unscored";

/**
 * The Archidekt decks to update: [listed] holds when each deck last changed on Archidekt, from its owner's public deck
 * list ("check": not in a public list, so the deck itself is read); unchanged decks without a good score are scored.
 */
export function updatePlan(file: SyncFile, listed: Map<number, number | null>): { id: number; name: string; need: UpdateNeed }[] {
  const out: { id: number; name: string; need: UpdateNeed }[] = [];
  for (const { deck: d } of syncDecks(file)) {
    if (d.archidektId < 0 || d.retired) continue;
    const at = listed.get(d.archidektId);
    const need: UpdateNeed | null =
      at == null ? "check" : at !== d.archidektUpdatedAt ? "changed" : d.scoredAt == null || d.scoreError != null ? "unscored" : null;
    if (need) out.push({ id: d.archidektId, name: d.name, need });
  }
  return out;
}

// ---- The actions (the pages call them one deck at a time, so each request stays short) ----

/**
 * Reads the deck from Archidekt and saves it; with [score], Commander Salt scores it at the same time (its failure is
 * kept on the deck, as in the app). With [local] it becomes a local deck right after (DeckRepository.import). Returns
 * the deck's id (a new one when made local) and Commander Salt's problem, if any.
 */
export async function loadFromArchidekt(
  account: Account,
  id: number,
  opts: { score: boolean; local?: boolean },
): Promise<{ id: number; name: string; scoreError: string | null }> {
  const [read, scores] = await Promise.all([
    readArchidektDeck(id),
    opts.score ? scoreDeck(id).catch((e: unknown) => ({ error: e instanceof Error ? e.message : String(e) })) : Promise.resolve(null),
  ]);
  return editSync(account, (file, now) => {
    putLoadedDeck(file, read, now);
    if (scores) putScores(file, id, scores, now);
    const newId = opts.local ? detachDeck(file, id, now) : id;
    return { id: newId, name: read.deck.name, scoreError: scores && "error" in scores ? scores.error : null };
  });
}

/** Commander Salt scores the deck's list again, without reloading it (the error is kept on the deck and returned). */
export async function scoreAgain(account: Account, id: number): Promise<string | null> {
  const scores = await scoreDeck(id).catch((e: unknown) => ({ error: e instanceof Error ? e.message : String(e) }));
  await editSync(account, (file, now) => putScores(file, id, scores, now));
  return "error" in scores ? scores.error : null;
}

/** When each Archidekt deck last changed there: one public deck list per owner (a failed list leaves its decks to "check"). */
export async function archidektDates(file: SyncFile): Promise<Map<number, number | null>> {
  const listed = new Map<number, number | null>();
  const owners = [...new Set(syncDecks(file).filter((d) => d.deck.archidektId > 0 && d.deck.owner.trim()).map((d) => d.deck.owner.trim()))];
  for (const o of owners) {
    try {
      for (const d of await userDecks(o)) listed.set(d.id, d.updatedAt);
    } catch {
      // Its decks are read one by one.
    }
  }
  return listed;
}

/**
 * One deck of "Update all decks" (DeckRepository.updateAll): reloaded and scored when it changed on Archidekt (for
 * "check", when the deck itself says so), else scored when it has no good score. What was done.
 */
export async function updateOne(account: Account, id: number, need: UpdateNeed): Promise<{ done: "updated" | "scored" | "same"; scoreError: string | null }> {
  if (need === "unscored") return { done: "scored", scoreError: await scoreAgain(account, id) };
  if (need === "check") {
    const { file } = await loadSync(account);
    const d = syncDecks(file).find((x) => x.deck.archidektId === id)?.deck;
    const remote = await fetchDeck(id);
    if (d && remote.updatedAt !== null && remote.updatedAt === d.archidektUpdatedAt) {
      if (d.scoredAt != null && d.scoreError == null) return { done: "same", scoreError: null };
      return { done: "scored", scoreError: await scoreAgain(account, id) };
    }
  }
  const r = await loadFromArchidekt(account, id, { score: true });
  return { done: "updated", scoreError: r.scoreError };
}
