import { describe, expect, it } from "vitest";
import { findCombos, parseCombos } from "@/lib/spellbook";

// Modelled on backend.commanderspellbook.com/find-my-combos: only the fields the rating reads.
const answer = {
  results: {
    identity: "WUBG",
    included: [
      {
        id: "1234-5678",
        uses: [{ card: { name: "Thopter Foundry" } }, { card: { name: "Sword of the Meek" } }],
        produces: [{ feature: { name: "Win the game" } }, { feature: { name: "Infinite creature tokens" } }],
        notablePrerequisites: "All permanents on the battlefield.",
        easyPrerequisites: "3 mana available. Thopter Foundry does not have summoning sickness.",
        bracketTag: "B",
        salt: 4.2,
        description: "…",
      },
      {
        id: "90ab-cdef",
        uses: [{ card: { name: "Dockside Extortionist" } }],
        produces: [{ feature: { name: "Infinite colorless mana" } }],
        notablePrerequisites: "",
        easyPrerequisites: "",
        bracketTag: null,
        salt: null,
      },
    ],
    almostIncluded: [],
    includedByAddingCards: [],
  },
};

function fakeFetch(status: number, body: unknown, seen: { url?: string; init?: RequestInit } = {}): typeof fetch {
  return (async (url: string, init?: RequestInit) => {
    seen.url = url;
    seen.init = init;
    return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
  }) as unknown as typeof fetch;
}

describe("Commander Spellbook's combos", () => {
  it("posts the deck and maps the answer", async () => {
    const seen: { url?: string; init?: RequestInit } = {};
    const combos = await findCombos(["Saffi Eriksdotter"], [{ name: "Thopter Foundry", quantity: 1 }, { name: "Sword of the Meek", quantity: 1 }], fakeFetch(200, answer, seen));
    expect(seen.url).toBe("https://backend.commanderspellbook.com/find-my-combos");
    expect(seen.init?.method).toBe("POST");
    expect(seen.init?.cache).toBe("no-store");
    const headers = seen.init?.headers as Record<string, string>;
    expect(headers["Content-Type"]).toBe("application/json");
    expect(headers.Accept).toBe("application/json");
    expect(headers["User-Agent"]).toBe("MTGTrader/1.0 (+https://mtgtrader.vercel.app)");
    expect(JSON.parse(String(seen.init?.body))).toEqual({
      commanders: [{ card: "Saffi Eriksdotter" }],
      main: [
        { card: "Thopter Foundry", quantity: 1 },
        { card: "Sword of the Meek", quantity: 1 },
      ],
    });
    expect(combos).toEqual([
      {
        id: "1234-5678",
        cards: ["Thopter Foundry", "Sword of the Meek"],
        features: ["Win the game", "Infinite creature tokens"],
        notablePrerequisites: "All permanents on the battlefield.",
        easyPrerequisites: "3 mana available. Thopter Foundry does not have summoning sickness.",
        bracketTag: "B",
        salt: 4.2,
      },
      {
        id: "90ab-cdef",
        cards: ["Dockside Extortionist"],
        features: ["Infinite colorless mana"],
        notablePrerequisites: "",
        easyPrerequisites: "",
        bracketTag: null,
        salt: null,
      },
    ]);
  });

  it("has nothing for an empty answer", () => {
    expect(parseCombos({ results: { included: [] } })).toEqual([]);
    expect(parseCombos({})).toEqual([]);
  });

  it("throws when Commander Spellbook fails", async () => {
    await expect(findCombos(["X"], [], fakeFetch(500, {}))).rejects.toThrow("Commander Spellbook didn't answer (HTTP 500).");
  });
});
