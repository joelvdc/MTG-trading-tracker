import { afterEach, describe, expect, it, vi } from "vitest";
import { resetScryfallLimit, scryFetch, scryfallShutOutUntil } from "@/lib/scrylimit";

// Scryfall's rate limits: card searches and lookups 2 a second, a 429 shuts the website out for 30 seconds.
describe("Scryfall rate limits (scryFetch)", () => {
  const env = process.env as Record<string, string | undefined>;
  const was = env.NODE_ENV;
  afterEach(() => {
    env.NODE_ENV = was;
    vi.unstubAllGlobals();
    resetScryfallLimit();
  });

  it("spaces card lookups half a second apart", async () => {
    env.NODE_ENV = "production";
    const at: number[] = [];
    vi.stubGlobal("fetch", async () => {
      at.push(Date.now());
      return new Response("{}");
    });
    await Promise.all([1, 2, 3].map(() => scryFetch("https://api.scryfall.com/cards/collection", { method: "POST" })));
    expect(at).toHaveLength(3);
    expect(at[1] - at[0]).toBeGreaterThanOrEqual(500);
    expect(at[2] - at[1]).toBeGreaterThanOrEqual(500);
  });

  it("after a 429 waits as asked without asking Scryfall again", async () => {
    env.NODE_ENV = "production";
    let calls = 0;
    vi.stubGlobal("fetch", async () => {
      calls++;
      return new Response("{}", { status: 429, headers: { "Retry-After": "20" } });
    });
    expect((await scryFetch("https://api.scryfall.com/cards/search?q=x")).status).toBe(429);
    expect(scryfallShutOutUntil() - Date.now()).toBeGreaterThan(15_000);
    const again = await scryFetch("https://api.scryfall.com/cards/abc");
    expect(again.status).toBe(429);
    expect(Number(again.headers.get("retry-after"))).toBeGreaterThan(15);
    expect(calls).toBe(1);
  });
});
