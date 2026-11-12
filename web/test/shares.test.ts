import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore, type AccountStore } from "@/lib/accounts";
import { encodeSeats } from "@/lib/games";
import { hasTarget, sharedView, shareTitle, validCode } from "@/lib/shares";
import { makeShare, NotSynced, shareAnswer, shareLines } from "@/lib/shareweb";
import { encode, parse, type Json, type SyncFile } from "@/lib/syncfile";

const NOW = Date.UTC(2026, 10, 12, 10);
const BINDER = "b0000000000000000000000000000001";
const DECK = "10710772";

function sample(): SyncFile {
  const f = parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
  const item = (f.data.collection as { item: Record<string, Json> }[])[0].item;
  Object.assign(item, { purchasePrice: 12.5, notes: "bought from Anna" });
  f.data.games = [
    {
      id: 1,
      startedAt: NOW - 3_600_000,
      endedAt: NOW,
      durationMs: 3_600_000,
      startingLife: 40,
      turnsOn: false,
      rounds: 9,
      winType: "COMBAT",
      note: "Anna was tired",
      draw: false,
      manual: true,
      seats: encodeSeats([
        { seat: 0, name: "Joel", me: true, place: 1, deckId: Number(DECK), deckName: "Vohar Danse" },
        { seat: 1, name: "Anna", place: 2 },
      ]),
      turns: null,
      uid: "g1",
      updatedAt: NOW,
    },
  ];
  return f;
}

let store: AccountStore;
let accountId: number;

beforeEach(async () => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
  store = accountStore()!;
  accountId = (await store.create("joel", "x", true, NOW))!.id;
  await store.putSync(accountId, encode(sample()), null, NOW);
});

const req = new Request("https://mtgtrader.vercel.app/api/shares");

describe("shared links", () => {
  it("knows which items exist", () => {
    const f = sample();
    expect(hasTarget(f, "collection", "")).toBe(true);
    expect(hasTarget(f, "binder", BINDER)).toBe(true);
    expect(hasTarget(f, "binder", "~")).toBe(true);
    expect(hasTarget(f, "binder", "nope")).toBe(false);
    expect(hasTarget(f, "deck", DECK)).toBe(true);
    expect(hasTarget(f, "deck", "12")).toBe(false);
    expect(hasTarget(f, "game", "g1")).toBe(true);
    expect(shareTitle(f, "game", "g1")).toBe("Game of 12 Nov 2026, won by Joel");
  });

  it("makes one link per item, and switches it on again", async () => {
    const f = sample();
    const a = await makeShare(store, accountId, f, "deck", DECK, NOW);
    expect(validCode(a.link.code)).toBe(true);
    expect(a.link.on).toBe(true);
    const again = await makeShare(store, accountId, f, "deck", DECK, NOW + 1);
    expect(again.link.code).toBe(a.link.code);
    await store.setShareOn(accountId, a.link.code, false);
    const back = await makeShare(store, accountId, f, "deck", DECK, NOW + 2);
    expect(back).toMatchObject({ turnedOn: true, link: { code: a.link.code, on: true } });
    await expect(makeShare(store, accountId, f, "deck", "12", NOW)).rejects.toBeInstanceOf(NotSynced);
    await expect(makeShare(store, accountId, f, "trades", "", NOW)).rejects.toThrow("can't be shared");
  });

  it("shows prices but never purchase prices or notes", () => {
    const v = sharedView(sample(), { kind: "collection", target: "" }, "joel", null, null, null)!;
    expect(v.kind).toBe("collection");
    const text = JSON.stringify(v);
    expect(text).not.toContain("12.5");
    expect(text).not.toContain("bought from Anna");
    expect(text).not.toContain("purchase");
    if (v.kind !== "collection") throw new Error();
    expect(v.piles[0]).toMatchObject({ name: "Sol Ring", binder: "Main", quantity: 1 });
    expect(v.prices.line).toMatch(/^Prices: Cardmarket trend, in DKK · Scryfall/);
    const binder = sharedView(sample(), { kind: "binder", target: "~" }, "joel", null, null, null)!;
    expect(binder.kind === "binder" && binder.piles).toEqual([]);
  });

  it("leaves a game's note out", () => {
    const v = sharedView(sample(), { kind: "game", target: "g1" }, "joel", null, null, null)!;
    expect(v.kind === "game" && v.game.note).toBeNull();
    expect(JSON.stringify(v)).not.toContain("Anna was tired");
  });

  it("answers off, gone and unknown links, and counts openings", async () => {
    const f = sample();
    const { link } = await makeShare(store, accountId, f, "binder", BINDER, NOW);
    const ok = await shareAnswer(store, link.code, NOW);
    expect(ok.state).toBe("ok");
    expect(ok.state === "ok" && ok.view.owner).toBe("joel");
    await store.setShareOn(accountId, link.code, false);
    expect((await shareAnswer(store, link.code, NOW)).state).toBe("off");
    expect((await shareAnswer(store, "AAAAAAAAAAAAAAAAAAAAAA", NOW)).state).toBe("unknown");
    await store.setShareOn(accountId, link.code, true);
    // The binder is deleted on a phone.
    const g = sample();
    g.data.binders = [];
    await store.putSync(accountId, encode(g), 1, NOW);
    expect((await shareAnswer(store, link.code, NOW)).state).toBe("gone");
    const lines = shareLines(req, await store.shares(accountId), g);
    expect(lines[0]).toMatchObject({ title: null, opened: 1, url: `https://mtgtrader.vercel.app/s/${link.code}` });
  });

  it("names the links in the Settings list", async () => {
    const f = sample();
    await makeShare(store, accountId, f, "collection", "ignored", NOW);
    await makeShare(store, accountId, f, "deck", DECK, NOW + 1);
    await makeShare(store, accountId, f, "game", "g1", NOW + 2);
    const lines = shareLines(req, await store.shares(accountId), f);
    expect(lines.map((l) => l.title)).toEqual(["Game of 12 Nov 2026, won by Joel", "Deck: Vohar Danse", "Whole collection"]);
    expect(await store.removeShare(accountId, lines[0].code)).toBe(true);
    expect(await store.shares(accountId)).toHaveLength(2);
  });

  it("shares the wishlist: still-wanted cards only, without notes, alerts or orders", async () => {
    const f = sample();
    expect(hasTarget(f, "wishlist", "")).toBe(true);
    expect(shareTitle(f, "wishlist", "")).toBe("Wishlist");
    const v = sharedView(f, { kind: "wishlist", target: "" }, "joel", null, null, null)!;
    expect(v.kind).toBe("wishlist");
    if (v.kind !== "wishlist") return;
    // Rhystic Study is ordered, so it's left out; Sol Ring (any printing) stays.
    expect(v.wants.map((w) => w.name)).not.toContain("Rhystic Study");
    const sol = v.wants.find((w) => w.name === "Sol Ring")!;
    expect(sol).toMatchObject({ quantity: 2, set: null, foil: false });
    const text = JSON.stringify(v);
    expect(text).not.toContain("for Vohar");
    expect(text).not.toContain("alert");
    const { link } = await makeShare(store, accountId, f, "wishlist", "anything", NOW);
    expect(link.target).toBe("");
    expect(shareLines(req, [link], f)[0].title).toBe("Wishlist");
  });
});
