import { beforeEach, describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { autoCategories, fetchTagCategories, rebuildTagCategories, REBUILD_AFTER_MS, RULES_SINCE } from "@/lib/autocategories";
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
    if (q.includes("otag:ramp")) return Response.json({ data: [{ name: "Cultivate", type_line: "Sorcery" }, { name: "Sol Ring", type_line: "Artifact" }], has_more: false });
    if (q.includes("otag:counterspell")) {
      if (page === "2") return Response.json({ data: [{ name: "Fire // Ice", type_line: "Instant // Sorcery" }], has_more: false });
      return Response.json({ data: [{ name: "Counterspell", type_line: "Instant" }], has_more: true, next_page: `${url}&page=2` });
    }
    if (q.includes("otag:tutor")) return Response.json({ data: [{ name: "Cultivate", type_line: "Sorcery" }, { name: "Demonic Tutor", type_line: "Sorcery" }], has_more: false });
    return Response.json({ object: "error" }, { status: 404 });
  };
  return { f: f as typeof fetch, asked };
}

/** A fake Scryfall tag search with a land tagged tutor and a modal card whose back face is a land. */
function fakeLands() {
  const asked: string[] = [];
  const f = async (input: string | URL | Request): Promise<Response> => {
    const url = String(input);
    asked.push(url);
    const q = new URL(url).searchParams.get("q") ?? "";
    if (q.includes("otag:tutor")) return Response.json({ data: [{ name: "Evolving Wilds", type_line: "Land" }, { name: "Demonic Tutor", type_line: "Sorcery" }], has_more: false });
    if (q.includes("otag:removal")) return Response.json({ data: [{ name: "Sink into Stupor // Soporific Springs", type_line: "Instant // Land" }], has_more: false });
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

  it("leaves lands out of the table, but keeps a modal card with a spell on the front", async () => {
    const rows = await fetchTagCategories(fakeLands().f, Date.now() + 60_000);
    const by = Object.fromEntries(rows.map((r) => [r.name, r.categories]));
    // Evolving Wilds is tagged "tutor" but is a land: no row, so it goes by card type (Land).
    expect(by["Evolving Wilds"]).toBeUndefined();
    expect(by).toEqual({ "Demonic Tutor": ["Tutor"], "Sink into Stupor // Soporific Springs": ["Removal"] });
  });

  it("rebuilds a table built before RULES_SINCE, even when it is young", async () => {
    await accountStore()!.replaceTagCategories([{ key: "evolving wilds", categories: ["Tutor"] }], RULES_SINCE - 86_400_000);
    const build = fakeLands();
    const r = await rebuildTagCategories(RULES_SINCE + 86_400_000, build.f, Date.now() + 60_000);
    expect(r).toMatchObject({ rebuilt: true, updatedAt: RULES_SINCE + 86_400_000 });
    expect(build.asked.length).toBeGreaterThan(0);
    expect(await accountStore()!.tagCategories(["evolving wilds"])).toEqual(new Map());
    // Once rebuilt under the new rules, the usual weekly skip is back.
    const soon = fakeLands();
    expect((await rebuildTagCategories(RULES_SINCE + 2 * 86_400_000, soon.f)).rebuilt).toBe(false);
    expect(soon.asked).toEqual([]);
  });

  it("a deck line gets its first category, the others along; deleting the first moves it to the next", () => {
    const look: Lookups = { identity: () => null, auto: new Map([["p1", { cardId: null, category: "Ramp", extra: ["Tutor"] }]]) };
    expect(autoFor(look, "p1", "Sorcery")).toEqual({ to: "Ramp", extra: ["Tutor"] });
    expect(autoFor(look, "p1", "Sorcery", "ramp")).toEqual({ to: "Tutor", extra: [] });
    expect(autoFor(look, "p9", "Creature — Bear")).toEqual({ to: "Creature", extra: [] });
  });

  it("a land goes by its card type, whatever the table says", () => {
    // Evolving Wilds is tagged "tutor", but a land never gets a tag category.
    const land: Lookups = { identity: () => null, auto: new Map([["wilds", { cardId: null, category: "Tutor" }]]) };
    expect(autoFor(land, "wilds", "Land")).toEqual({ to: "Land", extra: [] });
    expect(autoFor(land, "wilds", "Basic Land")).toEqual({ to: "Land", extra: [] });
    expect(autoFor(land, "wilds", "Artifact Land — Urza's")).toEqual({ to: "Land", extra: [] });
    // A modal card with a spell on the front keeps its tag categories.
    const mdfc: Lookups = { identity: () => null, auto: new Map([["sink", { cardId: null, category: "Removal" }]]) };
    expect(autoFor(mdfc, "sink", "Instant // Land")).toEqual({ to: "Removal", extra: [] });
  });
});
