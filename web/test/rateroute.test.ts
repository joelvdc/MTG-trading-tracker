import { beforeEach, describe, expect, it, vi } from "vitest";

// The public /api/rate, as the phone app asks it: no login, but the app's header.

const combos = vi.fn(async (_commanders: string[], _main: { name: string; quantity: number }[]) => [] as unknown[]);
const facts = vi.fn(async (names: string[]) => new Map(names.map((n) => [n, { tags: [], gameChanger: n === "Rhystic Study", salt: n === "Rhystic Study" ? 2.5 : null }])));

vi.mock("@/lib/spellbook", () => ({ findCombos: (...args: unknown[]) => combos(...(args as [string[], { name: string; quantity: number }[]])) }));
vi.mock("@/lib/cardfacts", () => ({ cardFactsByName: (names: string[]) => facts(names) }));

import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { POST } from "@/app/api/rate/route";

const card = (name: string, over: Record<string, unknown> = {}) => ({ name, quantity: 1, cmc: 2, types: "Creature", commander: false, ...over });
const ask = (body: unknown, header: string | null = "mtgtrader") =>
  POST(
    new Request("https://mtgtrader.vercel.app/api/rate", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(header ? { "X-Requested-With": header } : {}) },
      body: JSON.stringify(body),
    }),
  );

const COMPLETE = { cards: [card("Cmdr", { commander: true, cmc: 3, types: "Legendary Creature" }), card("Rhystic Study", { cmc: 3, types: "Enchantment" })], identity: "U" };

beforeEach(async () => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
  combos.mockClear();
  facts.mockClear();
  await accountStore()!.replaceCardFacts([{ key: "rhystic study", fact: { tags: [], gameChanger: true, salt: 2.5 } }], Date.now());
});

describe("/api/rate", () => {
  it("refuses a request without the app's header", async () => {
    expect((await ask({ cards: [card("Bear")] }, null)).status).toBe(403);
    expect((await ask({ cards: [card("Bear")] }, "other")).status).toBe(403);
  });

  it("refuses a bad body", async () => {
    expect((await ask({ cards: [] })).status).toBe(400);
    expect((await ask({ cards: [card("")] })).status).toBe(400);
    expect((await ask({ cards: [card("Bear", { quantity: 0 })] })).status).toBe(400);
    expect((await ask({ cards: [card("Bear", { quantity: 100 })] })).status).toBe(400);
    expect((await ask({ cards: [card("x".repeat(201))] })).status).toBe(400);
    expect((await ask({ cards: Array.from({ length: 251 }, (_, i) => card(`Card ${i}`)) })).status).toBe(400);
  });

  it("says so, and rates nothing, while the card facts aren't there yet", async () => {
    resetMemoryStore();
    const r = await ask(COMPLETE);
    expect(r.status).toBe(503);
    expect(((await r.json()) as { error: string }).error).toBe("Card facts aren't ready yet.");
    expect(facts).not.toHaveBeenCalled();
    expect(combos).not.toHaveBeenCalled();
  });

  it("says so when Commander Spellbook doesn't answer", async () => {
    combos.mockRejectedValueOnce(new Error("Commander Spellbook didn't answer (HTTP 500)."));
    const r = await ask({ cards: [card("Bear")] });
    expect(r.status).toBe(503);
    expect(((await r.json()) as { error: string }).error).toBe("Commander Spellbook didn't answer (HTTP 500).");
  });

  it("rates a good body with fake facts and combos, and keeps the answer", async () => {
    const r = await ask(COMPLETE);
    expect(r.status).toBe(200);
    const { saltCard } = (await r.json()) as { saltCard: string };
    const rated = JSON.parse(saltCard) as { source: string; version: number; gameChangers: string[]; salt: number; bracket: number };
    expect(rated.source).toBe("app");
    expect(rated.version).toBe(4);
    expect(rated.gameChangers).toEqual(["Rhystic Study"]);
    expect(rated.salt).toBe(2.5);
    expect(rated.bracket).toBe(3);
    expect(facts).toHaveBeenCalledTimes(1);
    expect(combos).toHaveBeenCalledWith(["Cmdr"], [{ name: "Rhystic Study", quantity: 1 }]);
    // The same list again is answered from the kept answer: Commander Spellbook isn't asked twice.
    const again = await ask(COMPLETE);
    expect(((await again.json()) as { saltCard: string }).saltCard).toBe(saltCard);
    expect(facts).toHaveBeenCalledTimes(1);
    expect(combos).toHaveBeenCalledTimes(1);
  });
});
