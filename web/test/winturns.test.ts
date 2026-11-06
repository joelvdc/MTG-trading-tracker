import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { memoryStore, type AccountStore } from "@/lib/accounts";
import { syncDecks } from "@/lib/decks";
import type { SyncDeck } from "@/lib/deckshared";
import { encode, parse, type Json, type SyncFile } from "@/lib/syncfile";
import { DECK_MS, runWinTurns, winSource, type WinSource } from "@/lib/winturns";

const NOW = 1_793_968_645_412;

/** The app's sample file with its deck given a commander, and a second deck without one. */
function file(): SyncFile {
  const f = parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
  const [d] = syncDecks(f);
  const withCommander: SyncDeck = { ...d, cards: d.cards.map((c, i) => ({ ...c, commander: i === 0, maybeboard: false })) };
  const without: SyncDeck = { deck: { ...d.deck, archidektId: -5, name: "No commander" }, cards: d.cards.map((c) => ({ ...c, commander: false })) };
  f.data.decks = [without, withCommander] as unknown as Json;
  return f;
}

async function setup(f: SyncFile = file()): Promise<{ store: AccountStore; id: number }> {
  const store = memoryStore();
  const a = (await store.create("joel", "h", true, NOW))!;
  await store.putSync(a.id, encode(f), null, NOW);
  return { store, id: a.id };
}

function player() {
  const played: number[] = [];
  return { played, play: (s: WinSource) => (played.push(s.deckId), JSON.stringify({ label: `deck ${s.deckId}`, key: s.key })) };
}

const details = async () => ({});

describe("win turns worked out on the website", () => {
  it("works out decks with a commander once, until their list changes", async () => {
    const { store, id } = await setup();
    const p = player();
    const r = await runWinTurns(id, NOW + 10 * DECK_MS, { store, play: p.play, details, clock: () => NOW });
    expect(r).toEqual({ done: 1, left: 0, decks: 1, ran: true });
    expect(p.played).toEqual([10710772]);
    const kept = (await store.winTurns(id)).get(10710772)!;
    expect(JSON.parse(kept.result).label).toBe("deck 10710772");

    // Nothing changed: nothing to do.
    expect((await runWinTurns(id, NOW + 10 * DECK_MS, { store, play: p.play, details, clock: () => NOW })).done).toBe(0);

    // The list changed (one more copy): worked out again.
    const f = file();
    const decks = syncDecks(f);
    decks[1].cards[0].quantity += 1;
    f.data.decks = decks as unknown as Json;
    await store.putSync(id, encode(f), 1, NOW);
    expect((await runWinTurns(id, NOW + 10 * DECK_MS, { store, play: p.play, details, clock: () => NOW })).done).toBe(1);
    expect((await store.winTurns(id)).get(10710772)!.key).not.toBe(kept.key);
  });

  it("uses the same key as the browser and stops before its time is up", async () => {
    const { store, id } = await setup();
    const sd = syncDecks(file()).find((d) => d.deck.archidektId === 10710772)!;
    expect(winSource(sd, {})!.key).toMatch(/^\d+\|[0-9a-z]+$/);
    expect(winSource(syncDecks(file()).find((d) => d.deck.archidektId === -5)!, {})).toBeNull();
    const p = player();
    const r = await runWinTurns(id, NOW + DECK_MS - 1, { store, play: p.play, details, clock: () => NOW });
    expect(r).toMatchObject({ done: 0, left: 1 });
    expect(p.played).toEqual([]);
  });

  it("runs once per account at a time, and forgets decks that are gone", async () => {
    const { store, id } = await setup();
    await store.putWinTurn(id, 999, { key: "old", result: "null", at: NOW });
    expect(await store.claimJob(`winturns:${id}`, NOW, 60_000)).toBe(true);
    const p = player();
    expect((await runWinTurns(id, NOW + 10 * DECK_MS, { store, play: p.play, details, clock: () => NOW })).ran).toBe(false);
    await store.setJob(`winturns:${id}`, 0, {});
    await runWinTurns(id, NOW + 10 * DECK_MS, { store, play: p.play, details, clock: () => NOW });
    expect([...(await store.winTurns(id)).keys()]).toEqual([10710772]);
  });

  it("keeps a deck the app's code can't play as having none", async () => {
    const { store, id } = await setup();
    const r = await runWinTurns(id, NOW + 10 * DECK_MS, {
      store,
      details,
      clock: () => NOW,
      play: () => {
        throw new Error("boom");
      },
    });
    expect(r.done).toBe(1);
    expect((await store.winTurns(id)).get(10710772)!.result).toBe("null");
  });
});
