// @vitest-environment node
import crypto from "node:crypto";
import { readFileSync } from "node:fs";
import path from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { applyCollectionEdit } from "@/lib/collection";
import { collection, parse, prefs, type CardRef, type SyncFile } from "@/lib/syncfile";

// The website's sort and find routes (/api/sort, /api/find) against a small sync file: the store stands in for the
// account's folder, the prices and card details are empty, and the network is never touched.

const held: { file: SyncFile; wrote: boolean } = { file: null as unknown as SyncFile, wrote: false };

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return { ...actual, currentAccount: async () => ({ kind: "user", accountId: 1, username: "joel", admin: true, device: "test" }) };
});

// loadSync answers the file; editSync hands the same file to the change, which edits it in place.
vi.mock("@/lib/store", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/store")>();
  return {
    ...actual,
    loadSync: async () => ({ file: held.file, etag: null, bytes: new Uint8Array() }),
    editSync: async (_a: unknown, change: (f: SyncFile, now: number) => void) => {
      held.wrote = true;
      change(held.file, 4000);
      return held.file;
    },
  };
});

vi.mock("@/lib/pricestore", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/pricestore")>();
  return { ...actual, loadPrices: async () => null };
});
vi.mock("@/lib/carddetails", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/carddetails")>();
  return { ...actual, loadDetails: async () => null };
});
vi.mock("@/lib/cardkingdom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/cardkingdom")>();
  return { ...actual, loadCardKingdom: async () => null };
});

import { GET as sortGet, POST as sortPost } from "@/app/api/sort/route";
import { POST as findPost } from "@/app/api/find/route";
import type { SortView } from "@/app/api/sort/route";
import type { FindView } from "@/app/api/find/route";
import type { FindLine } from "@/lib/findcards";
import { sortLogOf, sortSettledOf, sortSkipsOf } from "@/lib/collsort";

afterEach(() => vi.restoreAllMocks());

const card = (name: string): CardRef => ({
  scryfallId: crypto
    .createHash("md5")
    .update(name)
    .digest("hex")
    .replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, "$1-$2-$3-$4-$5"),
  name,
  setCode: "tst",
  setName: "Test Set",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: null,
  fallbackEur: 1.5,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: false,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

/**
 * The example sync file (the app writes it), with a deck binder, two binders for the sort and a few piles: Sol Rings
 * in Unsorted and in the deck's binder, Cheap Bears in Unsorted and in Not bulk.
 */
function file(values: Record<string, string> = {}): SyncFile {
  const f = parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
  const sol = collection(f)[0].item.card;
  const bear = card("Cheap Bear");
  const deck = applyCollectionEdit(f, { type: "addBinder", name: "Blue deck" }, 1000).uid!;
  const top = applyCollectionEdit(f, { type: "addBinder", name: "Collection binder" }, 1000).uid!;
  const mid = applyCollectionEdit(f, { type: "addBinder", name: "Not bulk" }, 1000).uid!;
  applyCollectionEdit(f, { type: "add", card: sol, quantity: 1, binder: deck }, 1000);
  applyCollectionEdit(f, { type: "add", card: sol, quantity: 2 }, 1000);
  applyCollectionEdit(f, { type: "add", card: bear, quantity: 4, binder: mid }, 1000);
  applyCollectionEdit(f, { type: "add", card: bear, quantity: 2 }, 1000);
  (f.data.decks as unknown as { deck: { archidektId: number } }[])[0].deck.archidektId = 7;
  Object.assign((f.data.prefs as { values: Record<string, string> }).values, { sortTop: top, sortMid: mid, ...values });
  return f;
}

const uidOf = (f: SyncFile, name: string) => (f.data.binders as unknown as { name: string; uid: string }[]).find((b) => b.name === name)!.uid;

/**
 * The example file with the three sort binders and its Sol Rings under the €0.50 floor (with no price file, a card
 * is worth its Scryfall fallback price): once settled, that copy leaves Unsorted, so the plan has a move with a reason.
 */
function settledFile(values: Record<string, string> = {}): SyncFile {
  const f = file({ sortSettled: "true", ...values });
  const bulk = applyCollectionEdit(f, { type: "addBinder", name: "Bulk" }, 1000).uid!;
  Object.assign((f.data.prefs as { values: Record<string, string> }).values, { sortBulk: bulk });
  for (const s of collection(f) as unknown as { item: { card: Record<string, unknown> } }[]) s.item.card = { ...s.item.card, cardmarketId: null, fallbackEur: 0.2 };
  return f;
}

const req = (p: string, payload: unknown, headers: Record<string, string> = { "x-requested-with": "mtgtrader" }) =>
  new Request(`https://mtgtrader.vercel.app${p}`, { method: "POST", headers: { "content-type": "application/json", ...headers }, body: JSON.stringify(payload) });

const body = async <T>(r: Response) => (await r.json()) as T & { error?: string };

describe("the sort route", () => {
  it("answers the plan with the places, the binders and the rules", async () => {
    held.file = file();
    const r = await sortGet();
    expect(r.status).toBe(200);
    const b = await body<SortView>(r);
    expect(b.places.top).toEqual({ uid: uidOf(held.file as SyncFile, "Collection binder"), name: "Collection binder" });
    expect(b.places.mid?.name).toBe("Not bulk");
    expect(b.places.bulk).toBeNull();
    expect(b.settled).toBe(false);
    expect(b.declined).toBe(0);
    // Every binder, with a flag for the ones the sort uses.
    const decks = uidOf(held.file as SyncFile, "Blue deck");
    expect(b.binders.find((x) => x.uid === decks)).toMatchObject({ name: "Blue deck", used: false });
    expect(b.binders.find((x) => x.uid === b.places.top?.uid)).toMatchObject({ used: true });
    // The first sort has no weekly cap.
    expect(b.plan.budget).toBe(-1);
    expect(b.rules).toMatchObject({ maxTop: 200, maxMid: 1000, weekly: 10 });
    expect(b.plan.moves.length).toBeGreaterThan(0);
  });

  it("writes the moves' reasons in the account's currency", async () => {
    // Euros first: the moves in Unsorted are under the floor (they say so) or go up by value.
    held.file = settledFile({ currency: "EUR" });
    const euros = (await body<SortView>(await sortGet())).plan.moves;
    expect(euros.map((m) => m.reason)).toEqual(["under €0.50", "€0.20", "€0.20"]);
    // The same account in kroner: the reasons are in kroner too, as every price on the page is.
    held.file = settledFile({ currency: "DKK" });
    const kroner = (await body<SortView>(await sortGet())).plan.moves;
    expect(kroner.map((m) => m.reason)).toEqual(["under 3.73 kr.", "1.49 kr.", "1.49 kr."]);
    expect(kroner.some((m) => m.reason.includes("€"))).toBe(false);
  });

  it("waits for an account", async () => {
    const auth = await import("@/lib/auth");
    vi.spyOn(auth, "currentAccount").mockResolvedValueOnce(null);
    expect((await sortGet()).status).toBe(401);
  });

  it("makes the binders and saves the rules (setup)", async () => {
    held.file = file();
    held.wrote = false;
    const rules = { maxTop: 50, maxMid: 300, maxBulk: 0, floor: 0.5, mix: 60, weekly: 4 };
    const r = await sortPost(req("/api/sort", { action: "setup", places: { bulk: { newName: "Bulk" } }, rules }));
    expect(r.status).toBe(200);
    const p = prefs(held.file as SyncFile);
    expect(p.sortBulk).toBe(uidOf(held.file as SyncFile, "Bulk"));
    expect(p.sortMaxTop).toBe("50");
    expect(p.sortFloor).toBe("0.5");
    expect((await body<SortView>(r)).rules).toEqual(rules);
  });

  it("refuses settings that can't be used", async () => {
    held.file = file();
    held.wrote = false;
    const r = await sortPost(req("/api/sort", { action: "setup", places: {}, rules: { maxTop: -1, maxMid: 1, maxBulk: 0, floor: 0.5, mix: 50, weekly: 10 } }));
    expect(r.status).toBe(400);
    expect(held.wrote).toBe(false);
  });

  it("moves the ticked moves, remembers the unticked ones and settles (apply)", async () => {
    held.file = file();
    const plan = (await body<SortView>(await sortGet())).plan;
    const kept = plan.moves[0];
    const declined = plan.moves[1];
    expect(kept).toBeTruthy();
    expect(declined).toBeTruthy();
    const move = (m: typeof kept) => ({ key: m.key, from: m.from, to: m.to, copies: m.copies });
    const r = await sortPost(req("/api/sort", { action: "apply", accepted: [move(kept)], declined: [move(declined)] }));
    expect(r.status).toBe(200);
    const f = held.file as SyncFile;
    expect(sortSettledOf(prefs(f))).toBe(true);
    expect(sortLogOf(prefs(f)).length).toBeGreaterThan(0);
    expect([...sortSkipsOf(prefs(f))]).toEqual([`${declined.key}>${declined.to}`]);
    const b = await body<SortView>(r);
    expect(b.settled).toBe(true);
    expect(b.declined).toBe(1);
    expect(b.plan.moves.some((m) => m.key === declined.key && m.to === declined.to)).toBe(false);
    expect(b.plan.budget).toBeGreaterThanOrEqual(0);
  });

  it("refuses moves that aren't in the plan, and requests from other sites", async () => {
    held.file = file();
    held.wrote = false;
    expect((await sortPost(req("/api/sort", { action: "apply", accepted: [{ key: "nope", from: "unsorted", to: "top", copies: 1 }], declined: [] }))).status).toBe(400);
    expect((await sortPost(req("/api/sort", { action: "apply", accepted: [], declined: [] }))).status).toBe(400);
    expect((await sortPost(req("/api/sort", { action: "nope" }))).status).toBe(400);
    expect((await sortPost(req("/api/sort", { action: "startover" }, {}))).status).toBe(403);
    expect(held.wrote).toBe(false);
  });

  it("suggests declined moves again, and starts over", async () => {
    held.file = file({ sortSettled: "true", sortSkips: "k>top\nj>mid", sortLog: "19900|k|1|0" });
    expect((await body<SortView>(await sortPost(req("/api/sort", { action: "unskip" })))).declined).toBe(0);
    expect(sortSkipsOf(prefs(held.file as SyncFile)).size).toBe(0);
    expect((await body<SortView>(await sortPost(req("/api/sort", { action: "startover" })))).settled).toBe(false);
    expect(sortLogOf(prefs(held.file as SyncFile))).toEqual([]);
  });
});

describe("the find route", () => {
  it("finds the copies of a pasted list, binder by binder, without writing the file", async () => {
    held.file = file();
    held.wrote = false;
    const before = JSON.stringify(held.file);
    const r = await findPost(req("/api/find", { text: "2 Sol Ring\n1 Cheap Bear", skipBasics: true }));
    expect(r.status).toBe(200);
    const b = await body<FindView>(r);
    expect(b.total).toEqual({ wanted: 3, found: 3, inDecks: 0, notOwned: 0 });
    // Unsorted comes first in the bar (the app's default); both Sol Rings there do for two, and the deck's is spare.
    expect(b.groups.map((g) => [g.binderName, g.lines.map((l: FindLine) => `${l.copies} ${l.name}`)])).toEqual([["Unsorted", ["1 Cheap Bear", "2 Sol Ring"]]]);
    expect(held.wrote).toBe(false);
    expect(JSON.stringify(held.file)).toBe(before);
  });

  it("finds a deck's cards, only the named ones, with the maybeboard left out", async () => {
    held.file = file();
    const r = await findPost(req("/api/find", { deck: 7, names: ["Sol Ring"], skipBasics: true }));
    expect(r.status).toBe(200);
    const b = await body<FindView>(r);
    expect(b.fromDeck).toBe(7);
    expect(b.deckName).toBe("Vohar Danse");
    // The deck's own Sol Ring doesn't count against it: the spare copy is found.
    expect(b.total).toMatchObject({ found: 1 });
    expect(b.groups.flatMap((g) => g.lines.map((l: FindLine) => l.name))).toEqual(["Sol Ring"]);
  });

  it("says what isn't owned, and leaves basic lands out by default", async () => {
    held.file = file();
    const b = await body<FindView>(await findPost(req("/api/find", { text: "5 Sol Ring\n2 Island" })));
    expect(b.total.wanted).toBe(5);
    // One Sol Ring is left after what the deck uses: that one is only in the deck, another isn't owned.
    expect(b.onlyInDecks).toEqual([{ name: "Sol Ring", copies: 1, decks: ["Vohar Danse"] }]);
    expect(b.notOwned).toEqual([{ name: "Sol Ring", copies: 1 }]);
    const all = await body<FindView>(await findPost(req("/api/find", { text: "5 Sol Ring\n2 Island", skipBasics: false })));
    expect(all.total.wanted).toBe(7);
    expect(all.notOwned.map((l) => l.name)).toContain("Island");
  });

  it("answers 404 for a deck that isn't there, 400 without a list, and 403 from other sites", async () => {
    held.file = file();
    expect((await findPost(req("/api/find", { deck: 99, skipBasics: true }))).status).toBe(404);
    expect((await findPost(req("/api/find", { text: "  ", skipBasics: true }))).status).toBe(400);
    expect((await findPost(req("/api/find", { text: "1 Sol Ring" }, {}))).status).toBe(403);
  });
});
