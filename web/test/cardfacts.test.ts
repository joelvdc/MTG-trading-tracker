import { gzipSync } from "node:zlib";
import { beforeEach, describe, expect, it } from "vitest";
import { PGlite } from "@electric-sql/pglite";
import { REBUILD_AFTER_MS } from "@/lib/autocategories";
import { accountStore, memoryStore, resetMemoryStore, sqlStore, type Row } from "@/lib/accounts";
import { cardFactsByName, fetchCardFacts, rebuildCardFacts, SALT_URL } from "@/lib/cardfacts";

const NOW = 1_793_968_645_412;

const saltGz = (doc: Record<string, number>) => new Response(new Uint8Array(gzipSync(JSON.stringify(doc))), { status: 200 });

/**
 * A fake Scryfall and card-salt branch: Cultivate is ramp and a tutor (two searches), Sink into Stupor is removal and a
 * modal spell//land, Evolving Wilds is a tutor and a land (kept here), Fire // Ice is removal and a counterspell, and
 * the salt has Fire // Ice under its full name and Rhystic Study under its own.
 */
function fakeScryfall(opts: { fail?: string; saltFails?: boolean } = {}) {
  const asked: string[] = [];
  const f = async (input: string | URL | Request): Promise<Response> => {
    const url = String(input);
    asked.push(url);
    if (url === SALT_URL) return opts.saltFails ? new Response("gone", { status: 404 }) : saltGz({ "Fire // Ice": 0.75, "Rhystic Study": 2.25, "Sol Ring": 1.5 });
    if (opts.fail && decodeURIComponent(url).includes(`otag:${opts.fail}`)) return new Response("down", { status: 503 });
    const q = new URL(url).searchParams.get("q") ?? "";
    const page = new URL(url).searchParams.get("page");
    if (q === "otag:ramp") return Response.json({ data: [{ name: "Cultivate" }, { name: "Sol Ring" }], has_more: false });
    if (q === "otag:tutor") return Response.json({ data: [{ name: "Cultivate" }, { name: "Demonic Tutor" }], has_more: false });
    if (q === "otag:removal") {
      if (page === "2") return Response.json({ data: [{ name: "Fire // Ice" }], has_more: false });
      return Response.json({
        data: [{ name: "Sink into Stupor // Soporific Springs" }, { name: "Rhystic Study" }],
        has_more: true,
        next_page: `${url}&page=2`,
      });
    }
    if (q === "otag:counterspell") return Response.json({ data: [{ name: "Fire // Ice" }], has_more: false });
    if (q === "otag:draw") return Response.json({ data: [{ name: "Rhystic Study" }], has_more: false });
    if (q === "is:gamechanger") return Response.json({ data: [{ name: "Rhystic Study" }], has_more: false });
    return Response.json({ object: "error" }, { status: 404 });
  };
  return { f: f as typeof fetch, asked };
}

beforeEach(() => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
});

describe("card facts (the website's own table, for rating decks)", () => {
  it("merges a card's tags across the searches, keeps lands, and flags Game Changers", async () => {
    const rows = await fetchCardFacts(fakeScryfall().f, Date.now() + 60_000);
    const by = Object.fromEntries(rows.map((r) => [r.name, { tags: r.fact.tags, gameChanger: r.fact.gameChanger, salt: r.fact.salt }]));
    expect(by).toEqual({
      // Two searches, in fact-tag order (tutor comes before ramp in that list).
      Cultivate: { tags: ["tutor", "ramp"], gameChanger: false, salt: null },
      "Sol Ring": { tags: ["ramp"], gameChanger: false, salt: 1.5 },
      "Demonic Tutor": { tags: ["tutor"], gameChanger: false, salt: null },
      // A modal card with a land on the back is kept, and so is a land tagged tutor (unlike the auto categories).
      "Sink into Stupor // Soporific Springs": { tags: ["removal"], gameChanger: false, salt: null },
      "Rhystic Study": { tags: ["removal", "draw"], gameChanger: true, salt: 2.25 },
      "Fire // Ice": { tags: ["removal", "counterspell"], gameChanger: false, salt: 0.75 },
    });
  });

  it("joins the salt by full name, and by front face when it is only there", async () => {
    const rows = await fetchCardFacts(
      (async (input: string | URL | Request) => (String(input) === SALT_URL ? saltGz({ Fire: 3 }) : fakeScryfall().f(input))) as typeof fetch,
      Date.now() + 60_000,
    );
    const by = Object.fromEntries(rows.map((r) => [r.name, r.fact.salt]));
    expect(by).toMatchObject({ "Fire // Ice": 3 });
  });

  it("builds the table without salt when the salt can't be read", async () => {
    const rows = await fetchCardFacts(fakeScryfall({ saltFails: true }).f, Date.now() + 60_000);
    expect(rows).toHaveLength(6);
    expect(rows.every((r) => r.fact.salt === null)).toBe(true);
  });

  it("rebuilds weekly, gives a double-faced card its front-face row, and answers by name", async () => {
    const first = await rebuildCardFacts(NOW, fakeScryfall().f, Date.now() + 60_000);
    // Seven cards, plus "fire" for Fire // Ice.
    expect(first).toMatchObject({ rebuilt: true, cards: 8, updatedAt: NOW });
    const soon = fakeScryfall();
    expect((await rebuildCardFacts(NOW + 86_400_000, soon.f)).rebuilt).toBe(false);
    expect(soon.asked).toEqual([]);
    // A failed search keeps the old table.
    await expect(rebuildCardFacts(NOW + REBUILD_AFTER_MS + 1, fakeScryfall({ fail: "tutor" }).f, Date.now() + 60_000)).rejects.toThrow(/failed/);
    expect((await accountStore()!.cardFactStats()).cards).toBe(8);

    const got = await cardFactsByName(["Fire", "Fire // Ice", "Sol Ring", "Grizzly Bears"]);
    expect(Object.fromEntries(got)).toEqual({
      Fire: { tags: ["removal", "counterspell"], gameChanger: false, salt: 0.75 },
      "Fire // Ice": { tags: ["removal", "counterspell"], gameChanger: false, salt: 0.75 },
      "Sol Ring": { tags: ["ramp"], gameChanger: false, salt: 1.5 },
    });
  });

  it("the memory store keeps the whole table and replaces it in one go", async () => {
    const store = memoryStore();
    expect(await store.cardFactStats()).toEqual({ cards: 0, updatedAt: null });
    expect((await store.cardFacts([])).size).toBe(0);
    await store.replaceCardFacts(
      [
        { key: "cultivate", fact: { tags: ["ramp", "tutor"], gameChanger: false, salt: 1.5 } },
        { key: "old card", fact: { tags: ["draw"], gameChanger: false, salt: null } },
      ],
      NOW,
    );
    await store.replaceCardFacts(
      [
        { key: "cultivate", fact: { tags: ["ramp", "tutor"], gameChanger: false, salt: 1.5 } },
        { key: "rhystic study", fact: { tags: ["draw"], gameChanger: true, salt: 2.25 } },
      ],
      NOW + 1,
    );
    expect(Object.fromEntries(await store.cardFacts(["cultivate", "old card", "rhystic study", "nope"]))).toEqual({
      cultivate: { tags: ["ramp", "tutor"], gameChanger: false, salt: 1.5 },
      "rhystic study": { tags: ["draw"], gameChanger: true, salt: 2.25 },
    });
    expect(await store.cardFactStats()).toEqual({ cards: 2, updatedAt: NOW + 1 });
  });

  it("the Postgres store does both too (PGlite)", async () => {
    const db = new PGlite();
    const s = sqlStore(async (text, params) => (await db.query<Row>(text, params)).rows);
    await s.replaceCardFacts(
      [
        { key: "cultivate", fact: { tags: ["ramp", "tutor"], gameChanger: false, salt: 1.5 } },
        { key: "old card", fact: { tags: ["draw"], gameChanger: false, salt: null } },
      ],
      NOW,
    );
    await s.replaceCardFacts([{ key: "rhystic study", fact: { tags: ["draw"], gameChanger: true, salt: 2.25 } }], NOW + 1);
    expect(Object.fromEntries(await s.cardFacts(["cultivate", "old card", "rhystic study"]))).toEqual({
      "rhystic study": { tags: ["draw"], gameChanger: true, salt: 2.25 },
    });
    expect(await s.cardFactStats()).toEqual({ cards: 1, updatedAt: NOW + 1 });
  }, 30_000);
});
