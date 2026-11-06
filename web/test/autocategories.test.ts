import { beforeEach, describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { autoCategories, categoryFromTags } from "@/lib/autocategories";

const NOW = 1_793_968_645_412;
const DAY = 86_400_000;

/** A fake Archidekt card search and Scryfall tag search; records every address asked. */
function fakeWeb(opts: { archidektDown?: boolean } = {}) {
  const asked: string[] = [];
  const archidekt: Record<string, object> = {
    "id-cultivate": { id: 1, uid: "id-cultivate", oracleCard: { defaultCategory: "Ramp", oTags: ["tutor-to-hand"], inheritedTags: ["ramp", "tutor"] } },
    "id-bolt": { id: 2, uid: "id-bolt", oracleCard: { defaultCategory: null, oTags: ["burn"], inheritedTags: ["removal"] } },
    "id-bears": { id: 3, uid: "id-bears", oracleCard: { defaultCategory: null, oTags: ["meme"], inheritedTags: [] } },
  };
  const f = async (input: string | URL | Request): Promise<Response> => {
    const url = String(input);
    asked.push(url);
    if (url.startsWith("https://archidekt.com/")) {
      if (opts.archidektDown) return new Response("down", { status: 503 });
      const uids = new URL(url).searchParams.get("uids")!.split(",");
      return Response.json({ results: uids.flatMap((u) => (archidekt[u] ? [archidekt[u]] : [])), next: null });
    }
    if (url.startsWith("https://api.scryfall.com/cards/search")) {
      const q = new URL(url).searchParams.get("q")!;
      // "Brand New Ramp" is a ramp card Archidekt doesn't know yet.
      if (q.includes("otag:ramp") && q.includes('!"Brand New Ramp"')) return Response.json({ data: [{ name: "Brand New Ramp" }] });
      return Response.json({ object: "error" }, { status: 404 });
    }
    return new Response("?", { status: 500 });
  };
  return { f: f as typeof fetch, asked };
}

const cards = [
  { scryfallId: "id-cultivate", name: "Cultivate" },
  { scryfallId: "id-bolt", name: "Lightning Bolt" },
  { scryfallId: "id-bears", name: "Grizzly Bears" },
  { scryfallId: "id-new", name: "Brand New Ramp" },
  { scryfallId: "id-plain", name: "Plain New Card" },
];

beforeEach(() => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
});

describe("auto categories", () => {
  it("tags pick the first matching group", () => {
    expect(categoryFromTags(["tutor", "ramp"])).toBe("Ramp");
    expect(categoryFromTags(["board-wipe"])).toBe("Removal");
    expect(categoryFromTags(["Card Advantage"])).toBe("Draw");
    expect(categoryFromTags(["meme"])).toBeNull();
  });

  it("Archidekt's category first, then its Scryfall tags, then a Scryfall search; kept for next time", async () => {
    const web = fakeWeb();
    const got = await autoCategories(cards, NOW, web.f);
    expect(Object.fromEntries([...got].map(([id, v]) => [id, v]))).toEqual({
      "id-cultivate": { cardId: 1, category: "Ramp" },
      "id-bolt": { cardId: 2, category: "Removal" },
      "id-bears": { cardId: 3, category: null },
      "id-new": { cardId: null, category: "Ramp" },
      "id-plain": { cardId: null, category: null },
    });
    expect(web.asked.filter((u) => u.includes("archidekt"))).toHaveLength(1);
    expect(await accountStore()!.countCardCategories()).toBe(5);

    // Next time from the database, without asking anyone.
    const again = fakeWeb();
    expect((await autoCategories(cards, NOW + DAY, again.f)).get("id-bolt")?.category).toBe("Removal");
    expect(again.asked).toEqual([]);

    // A week on, only the cards Archidekt didn't know are asked again.
    const later = fakeWeb();
    await autoCategories(cards, NOW + 8 * DAY, later.f);
    expect(later.asked[0]).toContain("uids=id-new,id-plain&");
  });

  it("when Archidekt is down, cards go by card type and nothing is kept", async () => {
    const web = fakeWeb({ archidektDown: true });
    const got = await autoCategories(cards, NOW, web.f);
    expect(got.size).toBe(0);
    expect(await accountStore()!.countCardCategories()).toBe(0);
  });
});
