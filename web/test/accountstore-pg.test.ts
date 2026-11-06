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

  it("win turns and job locks", async () => {
    const s = await make();
    const a = (await s.create("joel", "h", true, NOW))!;
    expect(await s.claimJob("winturns:1", NOW, 60_000)).toBe(true);
    expect(await s.claimJob("winturns:1", NOW + 1000, 60_000)).toBe(false);
    expect(await s.claimJob("winturns:1", NOW + 61_000, 60_000)).toBe(true);
    await s.setJob("winturns:1", 0, { done: 1 });
    expect(await s.claimJob("winturns:1", NOW + 62_000, 60_000)).toBe(true);
    await s.putWinTurn(a.id, -1793996277123, { key: "13|abc", result: "null", at: NOW });
    await s.putWinTurn(a.id, 10710772, { key: "13|x", result: '{"label":"a"}', at: NOW });
    await s.putWinTurn(a.id, 10710772, { key: "13|y", result: '{"label":"b"}', at: NOW + 1 });
    expect(Object.fromEntries(await s.winTurns(a.id))).toEqual({
      "-1793996277123": { key: "13|abc", result: "null", at: NOW },
      "10710772": { key: "13|y", result: '{"label":"b"}', at: NOW + 1 },
    });
    await s.dropWinTurns(a.id, [10710772]);
    expect([...(await s.winTurns(a.id)).keys()]).toEqual([-1793996277123]);
  });

  it("value history, alert levels, the alert history and job runs", async () => {
    const s = await make();
    const a = (await s.create("joel", "h", true, NOW))!;
    await s.putValueDays(a.id, [{ day: "2026-11-05", cards: 10, values: { trend: 12.5, "b:B1:trend": 2.25, "b:B1:cards": 3 } }], true);
    // The phone's history sent once: days the website has are kept.
    await s.putValueDays(
      a.id,
      [
        { day: "2026-11-04", cards: 9, values: { trend: 11, "b:B1:trend": 2, "b:B1:cards": 2 } },
        { day: "2026-11-05", cards: 99, values: { trend: 99 } },
      ],
      false,
    );
    await s.putValueDays(a.id, [{ day: "2026-11-06", cards: 11, values: { trend: 13, low: 9 } }], true);
    expect(await s.valueDayCount(a.id)).toBe(3);
    expect(await s.valuePoints(a.id, "trend", null)).toEqual([
      { day: "2026-11-04", value: 11, cards: 9 },
      { day: "2026-11-05", value: 12.5, cards: 10 },
      { day: "2026-11-06", value: 13, cards: 11 },
    ]);
    expect(await s.valuePoints(a.id, "low", null)).toEqual([{ day: "2026-11-06", value: 9, cards: 11 }]);
    expect(await s.valuePoints(a.id, "trend", "B1")).toEqual([
      { day: "2026-11-04", value: 2, cards: 2 },
      { day: "2026-11-05", value: 2.25, cards: 3 },
    ]);

    await s.setAlertLevels(a.id, new Map([["w1", 12], ["w2", 3.5]]));
    await s.setAlertLevels(a.id, new Map([["w2", 4]]));
    expect(Object.fromEntries(await s.alertLevels(a.id))).toEqual({ w2: 4 });

    const ev = (at: number, wishUid = "w1") => ({ wishUid, cardName: "Cultivate", at, price: 0.4, level: 0.5, basis: "cardmarket:low", setCode: null });
    await s.addAlertEvents(a.id, [ev(NOW - 400 * 86_400_000), ev(NOW - 1000)], NOW);
    await s.addAlertEvents(a.id, [ev(NOW, "w2")], NOW);
    expect((await s.alertEvents(a.id)).map((e) => [e.wishUid, e.at])).toEqual([
      ["w2", NOW],
      ["w1", NOW - 1000],
    ]);

    expect(await s.job("nightly")).toBeNull();
    await s.setJob("nightly", NOW, { accounts: 1, alerts: 2 });
    await s.setJob("nightly", NOW + 1, { accounts: 2, alerts: 0 });
    expect(await s.job("nightly")).toEqual({ at: NOW + 1, info: { accounts: 2, alerts: 0 } });
  });
});
