import { PGlite } from "@electric-sql/pglite";
import { describe, expect, it, vi } from "vitest";

// Starting a PGlite database takes a few seconds while the other test files run.
vi.setConfig({ testTimeout: 30_000 });
import { memoryStore, sqlStore, type AccountStore, type Row } from "@/lib/accounts";

/**
 * Every account store call on a real Postgres (PGlite) as well as in memory, with real millisecond times, so a query
 * Postgres rejects (as the device "last used" update did on 2026-11-06) fails here and not on the website.
 */
const NOW = 1_793_968_645_412;

async function pgStore(): Promise<AccountStore> {
  const db = new PGlite();
  return sqlStore(async (text, params) => (await db.query<Row>(text, params)).rows);
}

describe.each([
  ["memory", async () => memoryStore()],
  ["Postgres", pgStore],
])("the account store (%s)", (_, make) => {
  it("accounts, invites and lockouts", async () => {
    const s = await make();
    const a = (await s.create("joel", "scrypt$x$y", true, NOW))!;
    expect(a).toMatchObject({ username: "joel", admin: true, createdAt: NOW });
    expect(await s.create("joel", "h", false, NOW)).toBeNull();
    expect(await s.count()).toBe(1);
    expect((await s.byName("joel"))?.passwordHash).toBe("scrypt$x$y");
    expect((await s.byId(a.id))?.username).toBe("joel");
    await s.setFailures(a.id, 8, NOW + 900_000);
    expect((await s.byName("joel"))?.lockedUntil).toBe(NOW + 900_000);
    await s.setPassword(a.id, "scrypt$a$b");
    expect(await s.byName("joel")).toMatchObject({ failures: 0, lockedUntil: 0, passwordHash: "scrypt$a$b" });
    await s.addInvite("ABCD-EFGH", a.id, NOW);
    expect(await s.useInvite("ABCD-EFGH", "kid", NOW + 1)).toBe(true);
    expect(await s.useInvite("ABCD-EFGH", "kid2", NOW + 2)).toBe(false);
    expect((await s.invites())[0]).toMatchObject({ code: "ABCD-EFGH", usedBy: "kid", usedAt: NOW + 1 });
    expect((await s.list()).map((x) => x.username)).toEqual(["joel"]);
  });

  it("devices", async () => {
    const s = await make();
    const a = (await s.create("joel", "h", true, NOW))!;
    await s.addDevice("d1", a.id, "hash1", "App · Pixel", NOW);
    expect((await s.deviceByToken("hash1"))?.id).toBe("d1");
    await s.touchDevice("d1", NOW + 1000); // too soon: not written
    expect((await s.device("d1"))?.lastUsed).toBe(NOW);
    await s.touchDevice("d1", NOW + 3_600_000);
    expect((await s.device("d1"))?.lastUsed).toBe(NOW + 3_600_000);
    expect((await s.devices(a.id)).map((d) => d.id)).toEqual(["d1"]);
    await s.removeDevice("d1");
    expect(await s.device("d1")).toBeNull();
    await s.addDevice("d2", a.id, "hash2", "Website", NOW);
    await s.removeDevices(a.id);
    expect(await s.devices(a.id)).toEqual([]);
  });

  it("the sync file, backups and the backup place", async () => {
    const s = await make();
    const a = (await s.create("joel", "h", true, NOW))!;
    expect(await s.getSync(a.id)).toBeNull();
    expect(await s.putSync(a.id, new Uint8Array([1, 2]), null, NOW)).toBe(1);
    expect(await s.putSync(a.id, new Uint8Array([3]), null, NOW)).toBeNull();
    expect(await s.putSync(a.id, new Uint8Array([3]), 1, NOW + 5)).toBe(2);
    expect(await s.putSync(a.id, new Uint8Array([4]), 1, NOW + 6)).toBeNull();
    const got = (await s.getSync(a.id))!;
    expect([...got.bytes]).toEqual([3]);
    expect(got).toMatchObject({ version: 2, writtenAt: NOW + 5 });
    for (let d = 1; d <= 12; d++) await s.backupOnce(a.id, `2026-11-${String(d).padStart(2, "0")}`, new Uint8Array([d]), 10);
    expect(await s.backups(a.id)).toHaveLength(10);
    expect([...((await s.backup(a.id, "2026-11-12")) ?? [])]).toEqual([12]);
    const t = { server: "https://cloud.test", loginName: "joel", userId: "joel", sealedPassword: "x", folder: "MTG Trader" };
    await s.setBackupTarget(a.id, t);
    expect(await s.backupTarget(a.id)).toEqual(t);
    expect(await s.withBackupTargets()).toEqual([{ accountId: a.id, target: t }]);
    await s.setBackupTarget(a.id, null);
    expect(await s.backupTarget(a.id)).toBeNull();
  });

  it("the auto categories table", async () => {
    const s = await make();
    expect(await s.tagCategoryStats()).toEqual({ cards: 0, updatedAt: null });
    expect((await s.tagCategories([])).size).toBe(0);
    await s.replaceTagCategories([{ key: "cultivate", categories: ["Ramp", "Tutor"] }, { key: "old card", categories: ["Draw"] }], NOW);
    // The next rebuild replaces everything: cards no longer tagged are gone.
    await s.replaceTagCategories([{ key: "cultivate", categories: ["Ramp", "Tutor"] }, { key: "counterspell", categories: ["Removal"] }], NOW + 1);
    const got = await s.tagCategories(["cultivate", "old card", "counterspell", "nope"]);
    expect(Object.fromEntries(got)).toEqual({ cultivate: ["Ramp", "Tutor"], counterspell: ["Removal"] });
    expect(await s.tagCategoryStats()).toEqual({ cards: 2, updatedAt: NOW + 1 });
  });
});
