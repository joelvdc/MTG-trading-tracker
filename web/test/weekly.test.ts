import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore, type EmailSettings } from "@/lib/accounts";
import type { LegalRule } from "@/lib/legality";
import type { Mail } from "@/lib/mailer";
import type { PriceFile } from "@/lib/prices";
import { encode, parse, type Json, type SyncFile } from "@/lib/syncfile";
import { cleanAddress, copenhagenDay, newEmailSettings, pileKey, pilePrices, weeklyMail, weeklyReport, type WeeklyInput } from "@/lib/weekly";
import { runWeekly, type WeeklyDeps } from "@/lib/weeklyjob";

// Thursday 12 Nov 2026, 07:30 UTC (08:30 in Copenhagen).
const NOW = Date.UTC(2026, 10, 12, 7, 30);
const SOL = "11111111-1111-1111-1111-111111111111";
const DAY = 86_400_000;

function sample(): SyncFile {
  const f = parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
  // Euros, so the numbers read plainly.
  (f.data.prefs as { values: Record<string, string> }).values.currency = "EUR";
  (f.data.collection as { item: Record<string, Json> }[])[0].item.quantity = 2;
  return f;
}

const priceFile = (trend: number): PriceFile =>
  ({ createdAt: "2026-11-12T02:00:00", madeAt: NOW, rates: { date: "2026-11-11", perEuro: { USD: 1.1, DKK: 7.46 } }, products: { "556001": [trend, trend, trend, trend, trend, trend, null, null, null, null, null, null] } }) as unknown as PriceFile;

const rule = (over: Partial<LegalRule>): LegalRule => ({ ci: "", type: "Artifact", legal: "legal", copies: 1, command: false, pair: [], ...over });

function input(over: Partial<WeeklyInput> = {}): WeeklyInput {
  return {
    file: sample(),
    prices: priceFile(3),
    details: null,
    ck: null,
    priceDays: [{ day: "2026-11-05", prices: { [pileKey(SOL, "NONFOIL", "NM")]: 2 } }],
    valuePoints: [
      { day: "2026-11-04", value: 3.5, cards: 2 },
      { day: "2026-11-05", value: 4, cards: 2 },
    ],
    alerts: [
      { wishUid: "w1", cardName: "Rhystic Study", at: NOW - 2 * DAY, price: 30, level: 32, basis: "cardmarket:low", setCode: "pcy" },
      { wishUid: "w2", cardName: "Old Alert", at: NOW - 9 * DAY, price: 1, level: 2, basis: "cardmarket:low", setCode: null },
    ],
    rules: new Map([["sol ring", rule({})]]),
    gameChangers: new Set(["sol ring"]),
    lastState: {},
    now: NOW,
    ...over,
  };
}

describe("the weekly email", () => {
  it("works out the day in Copenhagen", () => {
    expect(copenhagenDay(NOW)).toEqual({ day: "2026-11-12", weekday: 3 });
    expect(copenhagenDay(Date.UTC(2026, 10, 15, 23, 30)).weekday).toBe(0); // 00:30 on Monday in Copenhagen
  });

  it("keeps each pile's price for the nightly job", () => {
    expect(pilePrices(sample(), priceFile(3), null, null)).toEqual({ [pileKey(SOL, "NONFOIL", "NM")]: 3 });
  });

  it("compares the value and the piles with a week ago", () => {
    const r = weeklyReport(input());
    expect(r.value).toEqual({ now: 6, before: 4, cards: 2 });
    expect(r.gainers).toEqual([{ name: "Sol Ring", set: "C21", finish: "NONFOIL", copies: 2, before: 2, now: 3 }]);
    expect(r.losers).toEqual([]);
    expect(r.alerts.map((a) => a.cardName)).toEqual(["Rhystic Study"]);
  });

  it("waits for a week of prices before naming gainers and losers", () => {
    const r = weeklyReport(input({ priceDays: [{ day: "2026-11-10", prices: {} }], valuePoints: [] }));
    expect(r.gainers).toBeNull();
    expect(r.value.before).toBeNull();
    expect(weeklyMail(r, "joel", "https://site", "https://site/stop", NOW).text).toContain("These start next week");
  });

  it("tells what changed in the decks since the last email", () => {
    // The first email lists the problems there are (the sample deck has one card, not 100).
    const first = weeklyReport(input());
    expect(first.decks).toEqual([{ deck: "Vohar Danse", added: [], removed: [], problems: ["No commander is marked.", "The deck has 1 cards; a Commander deck has exactly 100."] }]);
    // Nothing changed: nothing to say.
    expect(weeklyReport(input({ lastState: first.state })).decks).toEqual([]);
    // Sol Ring leaves the Game Changers list, and gets banned.
    const later = weeklyReport(input({ lastState: first.state, gameChangers: new Set(), rules: new Map([["sol ring", rule({ legal: "banned" })]]) }));
    expect(later.decks).toEqual([{ deck: "Vohar Danse", added: [], removed: ["Sol Ring"], problems: ["Banned in Commander: Sol Ring."] }]);
  });

  it("writes the email in the owner's currency with a stop link", () => {
    const m = weeklyMail(weeklyReport(input()), "joel", "https://mtgtrader.vercel.app", "https://mtgtrader.vercel.app/api/email/stop?t=x", NOW);
    expect(m.subject).toBe("MTG Trader weekly: collection €6.00 (+€2.00)");
    expect(m.text).toContain("Sol Ring (C21): €2.00 → €3.00, +€1.00 (+50.0%) · you own 2");
    expect(m.text).toContain("Rhystic Study (PCY): €30.00, your alert €32.00");
    expect(m.text).toContain("Prices: Cardmarket trend, in EUR.");
    expect(m.html).toContain('href="https://mtgtrader.vercel.app/api/email/stop?t=x"');
    expect(m.html).not.toContain("<script");
  });

  it("checks addresses and keeps the stop link when the address changes", () => {
    expect(cleanAddress(" a.b@example.com ")).toBe("a.b@example.com");
    expect(cleanAddress("nope")).toBeNull();
    let n = 0;
    const token = () => `t${++n}`;
    const s = newEmailSettings("a@b.cd", 2, token, null);
    expect(s).toMatchObject({ address: "a@b.cd", confirmed: false, confirmToken: "t1", stopToken: "t2", day: 2 });
    const confirmed = { ...s, confirmed: true, confirmToken: null };
    expect(newEmailSettings("A@b.cd", 4, token, confirmed)).toMatchObject({ confirmed: true, confirmToken: null, day: 4, stopToken: "t2" });
    expect(newEmailSettings("new@b.cd", 4, token, confirmed)).toMatchObject({ confirmed: false, confirmToken: "t3", stopToken: "t2" });
  });
});

describe("the weekly email job", () => {
  let sent: Mail[];
  const deps = (): WeeklyDeps => ({
    prices: async () => priceFile(3),
    details: async () => null,
    ck: async () => null,
    gameChangers: async () => new Set(["sol ring"]),
    send: async (m) => void sent.push(m),
    rules: async () => new Map([["sol ring", rule({})]]),
  });
  const settings = (over: Partial<EmailSettings>): EmailSettings => ({ address: "joel@example.com", confirmed: true, enabled: true, day: 3, confirmToken: null, stopToken: "stop1", lastSent: null, state: {}, ...over });

  beforeEach(() => {
    process.env.DATABASE_URL = "memory";
    resetMemoryStore();
    sent = [];
  });

  it("sends on the chosen weekday only, once, and remembers what it saw", async () => {
    const store = accountStore()!;
    const a = (await store.create("joel", "x", true, NOW))!;
    const b = (await store.create("anna", "x", false, NOW))!;
    const c = (await store.create("bo", "x", false, NOW))!;
    for (const x of [a, b, c]) await store.putSync(x.id, encode(sample()), null, NOW);
    await store.setEmailSettings(a.id, settings({}));
    await store.setEmailSettings(b.id, settings({ day: 4, address: "anna@example.com" }));
    await store.setEmailSettings(c.id, settings({ confirmed: false, address: "bo@example.com" }));

    const r = await runWeekly(NOW, deps(), true);
    expect(r).toEqual({ weekday: "Thursday", due: 1, sent: 1, failed: 0, mailReady: true });
    expect(sent.map((m) => m.to)).toEqual(["joel@example.com"]);
    expect(sent[0].unsubscribe).toBe("https://mtgtrader.vercel.app/api/email/stop?t=stop1");
    const after = (await store.emailSettings(a.id))!;
    expect(after.lastSent).toBe(NOW);
    expect(Object.keys(after.state.decks as object)).toEqual(["10710772"]);

    // The cron running twice the same morning doesn't send twice.
    await runWeekly(NOW + 60_000, deps(), true);
    expect(sent).toHaveLength(1);
    expect((await store.job("weekly"))?.info).toMatchObject({ due: 0, sent: 0 });
  });

  it("sends nothing while email isn't set up", async () => {
    const store = accountStore()!;
    const a = (await store.create("joel", "x", true, NOW))!;
    await store.putSync(a.id, encode(sample()), null, NOW);
    await store.setEmailSettings(a.id, settings({}));
    expect(await runWeekly(NOW, deps(), false)).toMatchObject({ due: 1, sent: 0, mailReady: false });
    expect(sent).toEqual([]);
  });
});
