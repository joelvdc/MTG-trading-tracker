import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";

// The website's /api/backups as a logged-in account (the session cookie is faked below).

const NOW = Date.UTC(2026, 10, 13, 10);
let cookie: string | undefined;

vi.mock("next/headers", () => ({ cookies: async () => ({ get: (n: string) => (n === "mtgt_session" && cookie ? { value: cookie } : undefined) }) }));

import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { addDevice } from "@/lib/users";
import { encode, parse } from "@/lib/syncfile";
import { seal, type UserSession } from "@/lib/session";
import { KEEP_DB_BACKUPS } from "@/lib/store";
import { GET } from "@/app/api/backups/route";

const file = () => encode(parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8")));
const get = (query = "") => GET(new Request(`https://mtgtrader.vercel.app/api/backups${query}`));

beforeEach(async () => {
  process.env.DATABASE_URL = "memory";
  delete process.env.SESSION_SECRET;
  resetMemoryStore();
  process.env.SESSION_SECRET = Buffer.alloc(32, 7).toString("base64");
  const store = accountStore()!;
  const account = (await store.create("joel", "x", true, NOW))!;
  const device = await addDevice(store, account, "Website", NOW);
  const session: UserSession = { kind: "user", accountId: account.id, username: "joel", admin: true, device: device.id };
  cookie = seal(session, 30 * 86_400_000);
});

describe("/api/backups", () => {
  it("needs a login", async () => {
    cookie = undefined;
    expect((await get()).status).toBe(401);
  });

  it("downloads the file as it is now, named for today (UTC)", async () => {
    const s = accountStore()!;
    const a = (await s.byName("joel"))!;
    expect((await get("?name=now")).status).toBe(404);
    await s.putSync(a.id, file(), null, Date.now());
    const r = await get("?name=now");
    expect(r.status).toBe(200);
    expect(r.headers.get("content-type")).toBe("application/gzip");
    const today = new Date().toISOString().slice(0, 10);
    expect(r.headers.get("content-disposition")).toBe(`attachment; filename="mtg-trader-${today}.json.gz"`);
    expect([...new Uint8Array(await r.arrayBuffer())]).toEqual([...file()]);
  });

  it("downloads one of the kept days, and refuses an unknown name", async () => {
    const s = accountStore()!;
    const a = (await s.byName("joel"))!;
    await s.putSync(a.id, file(), null, NOW);
    await s.backupOnce(a.id, "2026-11-10", new Uint8Array([1, 2, 3]), KEEP_DB_BACKUPS);
    const r = await get("?name=sync-2026-11-10.json.gz");
    expect(r.status).toBe(200);
    expect(r.headers.get("content-disposition")).toBe('attachment; filename="sync-2026-11-10.json.gz"');
    expect((await get("?name=sync-2026-11-09.json.gz")).status).toBe(404);
    expect((await get("?name=../etc/passwd")).status).toBe(404);
  });
});
