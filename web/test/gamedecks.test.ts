import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { applySettings } from "@/lib/appsettings";
import { syncDecks } from "@/lib/decks";
import { myDecks } from "@/lib/games";
import { parse } from "@/lib/syncfile";

const NOW = Date.UTC(2026, 10, 22, 12);
const file = () => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));

describe("game seat decks (app 1.108)", () => {
  it("marks other users' Archidekt decks as followed, so new seats can't pick them", () => {
    const f = file();
    const owner = syncDecks(f)[0].deck.owner ?? "";
    applySettings(f, { ownArchidektUser: owner.toUpperCase() }, NOW);
    expect(myDecks(f)[0].followed).toBe(false);
    applySettings(f, { ownArchidektUser: "someone-else" }, NOW + 1);
    expect(myDecks(f)[0].followed).toBe(true);
    // Without a username only local decks are yours.
    applySettings(f, { ownArchidektUser: "" }, NOW + 2);
    expect(myDecks(f)[0].followed).toBe(true);
  });
});
