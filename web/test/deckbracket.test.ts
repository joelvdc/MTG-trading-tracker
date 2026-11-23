import { describe, expect, it } from "vitest";
import { deckBracket } from "@/lib/deckshared";

const card = (over: Record<string, unknown>): string => JSON.stringify({ gameChangers: [], ...over });

describe("deckBracket", () => {
  it("gives the bracket of the website's own rating", () => {
    expect(deckBracket({ saltCard: card({ source: "app", bracket: 3 }), bracketBaseline: 2 })).toBe(3);
  });

  it("falls back to the bracket kept on the deck", () => {
    expect(deckBracket({ saltCard: card({ source: "app", bracket: null }), bracketBaseline: 2 })).toBe(2);
    expect(deckBracket({ saltCard: card({ source: "app" }), bracketBaseline: null })).toBeNull();
  });

  it("has no bracket for a deck the website hasn't rated", () => {
    expect(deckBracket({ saltCard: null, bracketBaseline: 4 })).toBeNull();
    // Data of an older app, from before the website rated decks itself.
    expect(deckBracket({ saltCard: card({}), bracketBaseline: 4 })).toBeNull();
    expect(deckBracket({ saltCard: "not json", bracketBaseline: 4 })).toBeNull();
  });
});
