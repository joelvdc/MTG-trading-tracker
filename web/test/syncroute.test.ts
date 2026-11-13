import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { addDevice } from "@/lib/users";
import { encode, parse } from "@/lib/syncfile";
import { GET, PUT } from "@/app/api/sync/route";

// The phones' /api/sync (app 1.79+): the version rules that keep two phones from saving over each other.

const NOW = Date.UTC(2026, 10, 13, 10);
const URL = "https://mtgtrader.vercel.app/api/sync";
let token: string;
const file = () => encode(parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8")));

const put = (headers: Record<string, string>) =>
  PUT(new Request(URL, { method: "PUT", headers: { authorization: `Bearer ${token}`, ...headers }, body: new Uint8Array(file()) }));

beforeEach(async () => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
  const store = accountStore()!;
  const account = (await store.create("joel", "x", true, NOW))!;
  token = (await addDevice(store, account, "Test phone", NOW)).token;
});

describe("/api/sync", () => {
  it("saves the first file only with If-None-Match: *, and then only over the version it read", async () => {
    expect((await put({ "if-none-match": "*" })).status).toBe(201);
    expect((await put({ "if-none-match": "*" })).status).toBe(412);
    const got = await GET(new Request(URL, { headers: { authorization: `Bearer ${token}` } }));
    const etag = got.headers.get("etag")!;
    expect(got.status).toBe(200);
    expect((await put({ "if-match": etag })).status).toBe(204);
    // That version is gone now: a phone still holding it reads again and merges.
    expect((await put({ "if-match": etag })).status).toBe(412);
  });

  it("refuses a save without either header instead of saving over the account's file", async () => {
    expect((await put({ "if-none-match": "*" })).status).toBe(201);
    expect((await put({})).status).toBe(428);
  });

  it("wants a phone's login", async () => {
    token = "not-a-real-token-but-long-enough";
    expect((await put({ "if-none-match": "*" })).status).toBe(401);
  });
});
