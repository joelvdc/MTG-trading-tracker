import { beforeEach, describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { autoCategories, fetchTagCategories, rebuildTagCategories, REBUILD_AFTER_MS } from "@/lib/autocategories";
import { autoFor, type Lookups } from "@/lib/deckedit";

const NOW = 1_793_968_645_412;

/** A fake Scryfall tag search: Cultivate is ramp and a tutor, Counterspell a counterspell, removal in two pages. */
function fakeScryfall(opts: { fail?: boolean } = {}) {
  const asked: string[] = [];
  const f = async (input: string | URL | Request): Promise<Response> => {
    const url = String(input);
    asked.push(url);
    if (opts.fail) return new Response("down", { status: 503 });
    const q = new URL(url).searchParams.get("q") ?? "";
    const page = new URL(url).searchParams.get("page");
    if (q.includes("otag:ramp")) return Response.json({ data: [{ name: "Cultivate" }, { name: "Sol Ring" }], has_more: false });
    if (q.includes("otag:counterspell")) {
      if (page === "2") return Response.json({ data: [{ name: "Fire // Ice" }], has_more: false });
      return Response.json({ data: [{ name: "Counterspell" }], has_more: true, next_page: `${url}&page=2` });
    }
    if (q.includes("otag:tutor")) return Response.json({ data: [{ name: "Cultivate" }, { name: "Demonic Tutor" }], has_more: false });
    return Response.json({ object: "error" }, { status: 404 });
  };
  return { f: f as typeof fetch, asked };
}

beforeEach(() => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
});

describe("auto categories (the website's own table, from Scryfall's tags)", () => {
  it("collects every category of a card, in the fixed order", async () => {
    const rows = await fetchTagCategories(fakeScryfall().f, Date.now() + 60_000);
    const by = Object.fromEntries(rows.map((r) => [r.name, r.categories]));
    expect(by).toEqual({ Cultivate: ["Ramp", "Tutor"], "Sol Ring": ["Ramp"], Counterspell: ["Removal"], "Fire // Ice": ["Removal"], "Demonic Tutor": ["Tutor"] });
  });

  it("rebuilds weekly, keeps the old table when Scryfall fails, and answers by name", async () => {
    const first = await rebuildTagCategories(NOW, fakeScryfall().f, Date.now() + 60_000);
    expect(first).toMatchObject({ rebuilt: true, cards: 6, updatedAt: NOW }); // Fire // Ice also as "fire"
    const soon = fakeScryfall();
    expect((await rebuildTagCategories(NOW + 86_400_000, soon.f)).rebuilt).toBe(false);
    expect(soon.asked).toEqual([]);
    await expect(rebuildTagCategories(NOW + REBUILD_AFTER_MS + 1, fakeScryfall({ fail: true }).f, Date.now() + 60_000)).rejects.toThrow(/failed/);
    expect((await accountStore()!.tagCategoryStats()).cards).toBe(6);

    const got = await autoCategories([
      { scryfallId: "p1", name: "Cultivate" },
      { scryfallId: "p2", name: "Fire" },
      { scryfallId: "p3", name: "Grizzly Bears" },
    ]);
    expect(got.get("p1")).toEqual({ cardId: null, category: "Ramp", extra: ["Tutor"] });
    expect(got.get("p2")?.category).toBe("Removal");
    expect(got.has("p3")).toBe(false);
  });

  it("a deck line gets its first category, the others along; deleting the first moves it to the next", () => {
    const look: Lookups = { identity: () => null, auto: new Map([["p1", { cardId: null, category: "Ramp", extra: ["Tutor"] }]]) };
    expect(autoFor(look, "p1", "Sorcery")).toEqual({ to: "Ramp", extra: ["Tutor"] });
    expect(autoFor(look, "p1", "Sorcery", "ramp")).toEqual({ to: "Tutor", extra: [] });
    expect(autoFor(look, "p9", "Creature — Bear")).toEqual({ to: "Creature", extra: [] });
  });
});
