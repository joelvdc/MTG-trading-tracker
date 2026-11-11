import { beforeEach, describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { rulesFor, RULES_MAX_AGE_MS } from "@/lib/legalstore";

const NOW = Date.UTC(2026, 10, 12, 10);

const scry = {
  "id-sol": { id: "id-sol", name: "Sol Ring", type_line: "Artifact", oracle_text: "{T}: Add {C}{C}.", color_identity: [], legalities: { commander: "legal" } },
  "id-lotus": { id: "id-lotus", name: "Black Lotus", type_line: "Artifact", color_identity: [], legalities: { commander: "banned" } },
};

function fakeScryfall() {
  const asked: string[][] = [];
  const f = async (_url: string, init?: RequestInit) => {
    const ids = (JSON.parse(String(init?.body)) as { identifiers: { id: string }[] }).identifiers.map((x) => x.id);
    asked.push(ids);
    return new Response(JSON.stringify({ data: ids.flatMap((id) => (id in scry ? [scry[id as keyof typeof scry]] : [])) }), { status: 200 });
  };
  return { f, asked };
}

beforeEach(() => {
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
});

describe("the cards' Commander rules", () => {
  const cards = [
    { name: "Sol Ring", scryfallId: "id-sol" },
    { name: "Black Lotus", scryfallId: "id-lotus" },
    { name: "Unknown", scryfallId: "id-none" },
  ];

  it("looks up what it lacks once, then keeps it for a week", async () => {
    const s = fakeScryfall();
    const first = await rulesFor(cards, true, NOW, NOW + 10_000, s.f);
    expect(s.asked).toEqual([["id-sol", "id-lotus", "id-none"]]);
    expect(first.get("black lotus")?.legal).toBe("banned");
    expect(first.has("unknown")).toBe(false);
    const again = await rulesFor(cards, true, NOW + 1000, NOW + 10_000, s.f);
    expect(s.asked).toHaveLength(1);
    expect(again.get("sol ring")?.copies).toBe(1);
    // A week later every card is asked again.
    await rulesFor(cards, true, NOW + RULES_MAX_AGE_MS + 1, NOW + RULES_MAX_AGE_MS + 10_000, s.f);
    expect(s.asked).toHaveLength(2);
  });

  it("only reads the table when told not to look up", async () => {
    const s = fakeScryfall();
    expect((await rulesFor(cards, false, NOW, NOW, s.f)).size).toBe(0);
    expect(s.asked).toEqual([]);
    await accountStore()!.putLegalRules([{ key: "sol ring", rule: JSON.stringify({ ci: "", type: "Artifact", legal: "legal", copies: 1, command: false, pair: [] }) }], NOW);
    expect([...(await rulesFor(cards, false, NOW, NOW, s.f)).keys()]).toEqual(["sol ring"]);
  });

  it("keeps the old rules when Scryfall doesn't answer", async () => {
    const s = fakeScryfall();
    await rulesFor(cards, true, NOW, NOW + 10_000, s.f);
    const down = async () => new Response("busy", { status: 503 });
    const later = await rulesFor(cards, true, NOW + RULES_MAX_AGE_MS + 1, NOW + RULES_MAX_AGE_MS + 10_000, down);
    expect(later.get("black lotus")?.legal).toBe("banned");
  });
});
