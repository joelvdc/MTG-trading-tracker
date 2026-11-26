import { describe, expect, it } from "vitest";
import { findCombos, parseCombos, reentering } from "@/lib/spellbook";

// Modelled on backend.commanderspellbook.com/find-my-combos: only the fields the rating reads.
const answer = {
  results: {
    identity: "WUBG",
    included: [
      {
        id: "1234-5678",
        uses: [
          { card: { name: "Thopter Foundry" }, zoneLocations: ["B"], exileCardState: "" },
          { card: { name: "Sword of the Meek" }, zoneLocations: ["B"], exileCardState: "" },
        ],
        produces: [{ feature: { name: "Win the game" } }, { feature: { name: "Infinite creature tokens" } }],
        notablePrerequisites: "All permanents on the battlefield.",
        easyPrerequisites: "3 mana available. Thopter Foundry does not have summoning sickness.",
        bracketTag: "B",
        manaNeeded: "{3}",
        salt: 4.2,
        description:
          "Cast Thopter Foundry by paying {3}.\nActivate Thopter Foundry by sacrificing Sword of the Meek.\nRepeat step 2 using the Thopter token.",
      },
      {
        id: "90ab-cdef",
        uses: [{ card: { name: "Dockside Extortionist" }, zoneLocations: ["H", "B"] }],
        requires: [{ template: { name: "Man-Land that Enters Untapped" }, quantity: 1 }, { template: { name: "Card that Earthbends" }, quantity: 2 }],
        produces: [{ feature: { name: "Infinite colorless mana" } }],
        notablePrerequisites: "",
        easyPrerequisites: "",
        bracketTag: null,
        manaNeeded: "",
        salt: null,
      },
      {
        id: "aa11-bb22",
        uses: [
          { card: { name: "Isochron Scepter" }, zoneLocations: ["B"], exileCardState: "" },
          { card: { name: "Dramatic Reversal" }, zoneLocations: ["H", "B", "E"], exileCardState: "exiled by Isochron Scepter." },
        ],
        produces: [{ feature: { name: "Infinite mana" } }],
        notablePrerequisites: "",
        easyPrerequisites: "",
        bracketTag: "P",
        manaNeeded: "{2}",
        salt: 1.5,
        description: "Cast Isochron Scepter by paying {2}.\nTap Isochron Scepter to copy Dramatic Reversal.",
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
        zones: ["B", "B"],
        templates: [],
        features: ["Win the game", "Infinite creature tokens"],
        notablePrerequisites: "All permanents on the battlefield.",
        easyPrerequisites: "3 mana available. Thopter Foundry does not have summoning sickness.",
        bracketTag: "B",
        manaNeeded: "{3}",
        steps: "Cast Thopter Foundry by paying {3}.\nActivate Thopter Foundry by sacrificing Sword of the Meek.\nRepeat step 2 using the Thopter token.",
        exiledBy: ["", ""],
        salt: 4.2,
      },
      {
        id: "90ab-cdef",
        cards: ["Dockside Extortionist"],
        // Several zones: any of them works.
        zones: ["HB"],
        templates: ["Man-Land that Enters Untapped", "Card that Earthbends", "Card that Earthbends"],
        features: ["Infinite colorless mana"],
        notablePrerequisites: "",
        easyPrerequisites: "",
        bracketTag: null,
        manaNeeded: "",
        // No description and no exile state to read.
        steps: "",
        exiledBy: [""],
        salt: null,
      },
      {
        id: "aa11-bb22",
        cards: ["Isochron Scepter", "Dramatic Reversal"],
        zones: ["B", "HBE"],
        templates: [],
        features: ["Infinite mana"],
        notablePrerequisites: "",
        easyPrerequisites: "",
        bracketTag: "P",
        manaNeeded: "{2}",
        steps: "Cast Isochron Scepter by paying {2}.\nTap Isochron Scepter to copy Dramatic Reversal.",
        // "exiled by Isochron Scepter." without its full stop, and nothing for the Scepter itself.
        exiledBy: ["", "Isochron Scepter"],
        salt: 1.5,
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

// The steps, one per line, of the combos whose cards must enter: making those leave and enter again starts them again.
describe("the cards whose entering starts a combo", () => {
  const ondu =
    "Cast Ondu Spiritdancer by paying {4}{W}.\n" +
    "When Ondu Spiritdancer enters the battlefield as an enchantment due to Secret Arcade, Ondu Spiritdancer " +
    "triggers, creating a token copy of itself.\nRepeat step 2 using the Ondu Spiritdancer token.";

  it("takes a card that must come from hand when a step says it enters", () => {
    // One zone per card, so its own is "H" here.
    expect(reentering(["Ondu Spiritdancer"], ["H", "B"], ondu)).toEqual(["Ondu Spiritdancer"]);
  });

  it("never takes a card that may start on the battlefield", () => {
    // "HB" means either works: it may be on the battlefield already, so blinking isn't what starts the combo.
    expect(reentering(["Ondu Spiritdancer"], ["HB"], ondu)).toEqual([]);
    expect(reentering(["Ondu Spiritdancer"], ["B"], ondu)).toEqual([]);
    // No zone at all: not known, so not trusted either.
    expect(reentering(["Ondu Spiritdancer"], [""], ondu)).toEqual([]);
  });

  it("takes a card that is cast and then enters in the next step", () => {
    const steps = "Cast Goblin Recruiter by paying {1}{R}.\nWhen it enters, it triggers, putting your library in any order.";
    expect(reentering(["Goblin Recruiter"], ["H"], steps)).toEqual(["Goblin Recruiter"]);
  });

  it("needs a step that says it enters", () => {
    const steps = "Cast Demonic Consultation by paying {B}.\nName a card, then exile the top six cards of your library.";
    expect(reentering(["Demonic Consultation"], ["H"], steps)).toEqual([]);
    // Another card's "it enters" further on doesn't count.
    const later = `${steps}\nCast Laboratory Maniac.\nWhen it enters, draw a card.`;
    expect(reentering(["Demonic Consultation"], ["H"], later)).toEqual([]);
  });

  it("takes a card whose zone has other letters too", () => {
    const steps = "When High Perfect Morcant enters, its first ability triggers.";
    expect(reentering(["High Perfect Morcant"], ["HC"], steps)).toEqual(["High Perfect Morcant"]);
  });

  it("matches a legendary card by its short name", () => {
    expect(reentering(["Kiki-Jiki, Mirror Breaker"], ["H"], "When Kiki-Jiki enters, untap it.")).toEqual(["Kiki-Jiki, Mirror Breaker"]);
  });

  it("keeps the cards in their order", () => {
    const steps = "Cast Bear by paying {1}{G}.\nWhen Bear enters, draw a card.\nCast Elf by paying {G}.";
    expect(reentering(["Bear", "Elf"], ["H", "G"], steps)).toEqual(["Bear"]);
    expect(reentering(["Elf", "Bear"], ["G", "H"], steps)).toEqual(["Bear"]);
  });
});
