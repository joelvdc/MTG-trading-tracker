import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, describe, expect, it } from "vitest";
import { accountStore, checkPassword, cleanUsername, hashPassword, memoryStore, normalizeCode, resetMemoryStore } from "@/lib/accounts";
import type { UserSession } from "@/lib/session";
import { editSync, loadSync } from "@/lib/store";
import { encode, parse, wishlist } from "@/lib/syncfile";
import { AccountError, addDevice, changePassword, checkLogin, createFromNextcloud, deviceFor, makeInvite, MAX_FAILURES, register, resetPassword } from "@/lib/users";

const sample = () => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));

describe("passwords", () => {
  it("check the right one only", async () => {
    const h = await hashPassword("correct horse battery");
    expect(await checkPassword("correct horse battery", h)).toBe(true);
    expect(await checkPassword("correct horse batterY", h)).toBe(false);
    expect(h).not.toContain("correct");
  });
  it("user names and invite codes are tidied", () => {
    expect(cleanUsername("  Joel ")).toBe("joel");
    expect(cleanUsername("a")).toBeNull();
    expect(cleanUsername("no spaces")).toBeNull();
    expect(normalizeCode(" k7qd m2xp ")).toBe("K7QD-M2XP");
  });
});

describe("accounts", () => {
  it("the first account from a Nextcloud login is the owner; later ones need an invite, used once", async () => {
    const s = memoryStore();
    const owner = await createFromNextcloud(s, "Joel", "a long password", 1);
    expect(owner).toMatchObject({ username: "joel", admin: true });
    await expect(register(s, "NOPE-NOPE", "kid", "another long one", 2)).rejects.toThrow(AccountError);
    const code = await makeInvite(s, owner, 3);
    const kid = await register(s, code.toLowerCase(), "Kid", "another long one", 4);
    expect(kid.admin).toBe(false);
    await expect(register(s, code, "kid2", "another long one", 5)).rejects.toThrow(/invite/);
    await expect(makeInvite(s, kid, 6)).rejects.toThrow(/owner/);
    await expect(createFromNextcloud(s, "joel", "a long password", 7)).rejects.toThrow(/taken/);
  });

  it("rejects short passwords", async () => {
    await expect(createFromNextcloud(memoryStore(), "joel", "short", 1)).rejects.toThrow(/10 characters/);
  });

  it("waits after too many wrong passwords", async () => {
    const s = memoryStore();
    await createFromNextcloud(s, "joel", "a long password", 1);
    for (let i = 0; i < MAX_FAILURES; i++) await expect(checkLogin(s, "joel", "wrong password", 1000)).rejects.toThrow(/Wrong/);
    await expect(checkLogin(s, "joel", "a long password", 2000)).rejects.toThrow(/Too many/);
    expect((await checkLogin(s, "JOEL", "a long password", 1000 + 16 * 60000)).username).toBe("joel");
    await expect(checkLogin(s, "nobody", "a long password", 1)).rejects.toThrow(/Wrong/);
  });

  it("devices log in with a token; a reset logs every device out", async () => {
    const s = memoryStore();
    const owner = await createFromNextcloud(s, "joel", "a long password", 1);
    const kid = await register(s, await makeInvite(s, owner, 2), "kid", "another long one", 3);
    const phone = await addDevice(s, kid, "App · Pixel", 4);
    expect((await deviceFor(s, phone.token, 5))?.account.username).toBe("kid");
    expect(await deviceFor(s, "not-a-real-token-at-all-xxxxxxxx", 5)).toBeNull();
    const temp = await resetPassword(s, owner, kid.id);
    expect(await deviceFor(s, phone.token, 6)).toBeNull();
    expect((await checkLogin(s, "kid", temp, 7)).id).toBe(kid.id);
    await expect(resetPassword(s, kid, owner.id)).rejects.toThrow(/owner/);
  });

  it("changing the password keeps this device and logs out the others", async () => {
    const s = memoryStore();
    const a = await createFromNextcloud(s, "joel", "a long password", 1);
    const here = await addDevice(s, a, "Website", 2);
    const there = await addDevice(s, a, "App", 3);
    await expect(changePassword(s, a, "wrong", "a new long password", here.id)).rejects.toThrow(/current/);
    await changePassword(s, a, "a long password", "a new long password", here.id);
    expect(await deviceFor(s, here.token, 4)).not.toBeNull();
    expect(await deviceFor(s, there.token, 4)).toBeNull();
  });
});

describe("an account's sync file", () => {
  it("saves only over the version it read (like Nextcloud's ETag)", async () => {
    const s = memoryStore();
    const bytes = new Uint8Array([1, 2, 3]);
    expect(await s.putSync(1, bytes, null, 1)).toBe(1);
    expect(await s.putSync(1, bytes, null, 2)).toBeNull();
    expect(await s.putSync(1, bytes, 1, 3)).toBe(2);
    expect(await s.putSync(1, bytes, 1, 4)).toBeNull();
  });

  it("keeps one backup a day, the newest ten", async () => {
    const s = memoryStore();
    for (let d = 1; d <= 12; d++) await s.backupOnce(1, `2026-11-${String(d).padStart(2, "0")}`, new Uint8Array([d]), 10);
    await s.backupOnce(1, "2026-11-12", new Uint8Array([99]), 10);
    const days = await s.backups(1);
    expect(days).toHaveLength(10);
    expect(days[0]).toBe("2026-11-12");
    expect((await s.backup(1, "2026-11-12"))?.[0]).toBe(12);
  });

  it("the website's edits go to the account's file, redone when a phone saved in between", async () => {
    process.env.DATABASE_URL = "memory";
    resetMemoryStore();
    const store = accountStore()!;
    const a = await createFromNextcloud(store, "joel", "a long password", 1);
    await store.putSync(a.id, encode(sample()), null, 1);
    const session: UserSession = { kind: "user", accountId: a.id, username: "joel", admin: true, device: "x" };
    const before = wishlist((await loadSync(session)).file).length;
    let tries = 0;
    // A phone saves while the website is editing (only the first time): the website's save must fail and be redone.
    const put = store.putSync.bind(store);
    let phoneSaved = false;
    store.putSync = async (id, bytes, ifVersion, now) => {
      if (!phoneSaved) {
        phoneSaved = true;
        const cur = (await store.getSync(id))!;
        await put(id, cur.bytes, cur.version, 2);
      }
      return put(id, bytes, ifVersion, now);
    };
    await editSync(
      session,
      (file) => {
        tries++;
        const items = wishlist(file);
        items.splice(0, 1);
        (file.data as Record<string, unknown>).wishlist = items;
      },
      () => 3,
    );
    expect(tries).toBe(2);
    expect(wishlist((await loadSync(session)).file).length).toBe(before - 1);
    expect(await store.backups(a.id)).toEqual(["1970-01-01"]);
  });
});

beforeEach(() => {
  delete process.env.DATABASE_URL;
  resetMemoryStore();
});
