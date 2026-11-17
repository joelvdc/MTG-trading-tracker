// A precon's deck as its page shows it: built in memory from MTGJSON's list, in a copy of the account's sync file
// (never saved, so it can't change the collection). Shared by the precon's view and win-turn routes. Server only.
import { createDeck, NO_LOOKUPS } from "./deckedit";
import { cachedPreconLines } from "./deckimport";
import { syncDecks } from "./decks";
import type { SyncDeck } from "./deckshared";
import type { SyncFile } from "./syncfile";

export interface PreconDeck {
  /** The deck's name as its page shows it. */
  name: string;
  /** A copy of the account's sync file with the deck in it (never written anywhere). */
  file: SyncFile;
  id: number;
  /** The deck as the pages take it (null if it couldn't be found again). */
  sd: SyncDeck | null;
}

/** Builds one precon's deck in memory from [fileName], in a copy of [file]. */
export async function buildPreconDeck(file: SyncFile, fileName: string): Promise<PreconDeck> {
  const p = await cachedPreconLines(fileName);
  const copy = structuredClone(file);
  const id = createDeck(copy, p.name, p.lines, p.origin, p.identity, Date.now(), NO_LOOKUPS);
  return { name: p.name, file: copy, id, sd: syncDecks(copy).find((d) => d.deck.archidektId === id) ?? null };
}
