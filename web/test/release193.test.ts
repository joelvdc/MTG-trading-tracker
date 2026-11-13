import { beforeEach, describe, expect, it, vi } from "vitest";

// App 1.93: phones get TCGplayer and Card Kingdom prices from the website, like Cardmarket's since 1.81.

const blob = vi.hoisted(() => ({ uploadedAt: new Date(), etag: '"abc"', bytes: new Uint8Array([0x1f, 0x8b, 1, 2]) }));
vi.mock("@vercel/blob", () => ({
  get: vi.fn(async () => ({
    statusCode: 200,
    stream: new Response(blob.bytes).body,
    blob: { uploadedAt: blob.uploadedAt, etag: blob.etag, size: blob.bytes.length },
  })),
  put: vi.fn(),
}));

import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { addDevice } from "@/lib/users";
import { PHONE_MAX_IDS, tcgplayerFor, type CardInfo, type DetailsFile } from "@/lib/carddetails";
import { POST as tcgplayer } from "@/app/api/app/tcgplayer/route";
import { GET as cardKingdom } from "@/app/api/app/cardkingdom/route";

const NOW = Date.UTC(2026, 10, 13, 12);
const ID1 = "0afa0e33-4804-4b00-b625-c2d6b61090fc";
const ID2 = "1d8d1a6a-30a7-4d2c-8b65-9a7c3c9d9c8e";
let token: string;

beforeEach(async () => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
  const store = accountStore()!;
  const account = (await store.create("joel", "x", true, NOW))!;
  token = (await addDevice(store, account, "Test phone", NOW)).token;
  blob.uploadedAt = new Date();
});

const info = (u: CardInfo["u"], at: number): CardInfo => ({ c: "", i: "", t: "Artifact", v: 1, r: 1, d: "2021-01-01", g: false, l: "normal", u, tu: "https://tcgplayer.example/1", at });

describe("TCGplayer prices for phones", () => {
  it("answers only the printings asked for that the website keeps", () => {
    const file: DetailsFile = { madeAt: NOW, cards: { [ID1]: info([1.5, 3, null], NOW - 1000) } };
    expect(tcgplayerFor(file, [ID1, ID2])).toEqual({ [ID1]: { u: [1.5, 3, null], tu: "https://tcgplayer.example/1", at: NOW - 1000 } });
  });

  const post = (body: unknown, auth = true) =>
    tcgplayer(new Request("https://mtgtrader.vercel.app/api/app/tcgplayer", {
      method: "POST",
      headers: { "content-type": "application/json", ...(auth ? { authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(body),
    }));

  it("wants a phone's login and a list of ids that isn't too long", async () => {
    expect((await post({ ids: [ID1] }, false)).status).toBe(401);
    expect((await post({ ids: "x" })).status).toBe(400);
    expect((await post({ ids: Array.from({ length: PHONE_MAX_IDS + 1 }, () => ID1) })).status).toBe(400);
    const r = await post({ ids: [ID1, "not an id"] });
    expect(r.status).toBe(200);
    expect(await r.json()).toHaveProperty("cards");
  });
});

describe("Card Kingdom prices for phones", () => {
  const get = (headers: Record<string, string> = {}) =>
    cardKingdom(new Request("https://mtgtrader.vercel.app/api/app/cardkingdom", { headers: { authorization: `Bearer ${token}`, ...headers } }));

  it("sends the stored file with its ETag, or 304 when the phone has it", async () => {
    const r = await get();
    expect(r.status).toBe(200);
    expect(r.headers.get("etag")).toBe('"abc"');
    expect(new Uint8Array(await r.arrayBuffer())).toEqual(blob.bytes);
    expect((await get({ "if-none-match": '"abc"' })).status).toBe(304);
  });

  it("says 503 when the daily job hasn't refreshed it for two days, so the phone asks Card Kingdom", async () => {
    blob.uploadedAt = new Date(Date.now() - 49 * 3600_000);
    expect((await get()).status).toBe(503);
  });

  it("wants a phone's login", async () => {
    token = "not-a-real-token-but-long-enough";
    expect((await get()).status).toBe(401);
  });
});
