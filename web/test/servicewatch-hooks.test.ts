// What the website's notes to /api/health say after its own requests to Archidekt and EDHREC (lib/servicewatch.ts).
// The notes go to a fake here, so these check which note a request leaves and how many times.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const notes = vi.hoisted(() => ({ noteService: vi.fn(async (_service: string, _problem: string | null) => {}) }));

vi.mock("@/lib/servicewatch", () => ({
  noteService: notes.noteService,
  serviceNotes: vi.fn(),
  WATCHED: ["edhrec", "archidekt"],
}));

import { ArchidektError, fetchDeck, resetArchidekt, userDecks } from "@/lib/archidekt";
import { recommendations, RecError } from "@/lib/recommend";

const SWITCHES = { archidektDecks: true };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

/** A fake fetch: the remote switches answer first (cached for an hour), then [answer] for every other address. */
function fake(answer: () => Response | Promise<Response>) {
  const urls: string[] = [];
  const f = (async (input: string | URL | Request) => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    urls.push(url);
    return url.includes("remote-switches.json") ? json(SWITCHES) : answer();
  }) as typeof fetch;
  return { f, urls };
}

const deckJson = { id: 42, name: "A deck", owner: { username: "joelvdc" }, cards: [], categories: [] };

const calls = () => notes.noteService.mock.calls.map(([service, problem]) => [service, problem]);

beforeEach(() => {
  notes.noteService.mockClear();
  resetArchidekt();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Archidekt's notes for the health check", () => {
  it("a deck that isn't public (404) isn't Archidekt's problem: no note", async () => {
    const { f } = fake(() => new Response("", { status: 404 }));
    await expect(fetchDeck(42, f)).rejects.toBeInstanceOf(ArchidektError);
    expect(notes.noteService).not.toHaveBeenCalled();
  });

  it("a 403 on a user's decks isn't noted either", async () => {
    const { f } = fake(() => new Response("", { status: 403 }));
    await expect(userDecks("joelvdc", f)).rejects.toBeInstanceOf(ArchidektError);
    expect(notes.noteService).not.toHaveBeenCalled();
  });

  it("a 500 is noted once, as answered 500 (not as a changed answer)", async () => {
    const { f } = fake(() => new Response("", { status: 500 }));
    await expect(fetchDeck(42, f)).rejects.toThrow(/answered 500/);
    expect(calls()).toEqual([["archidekt", "answered 500"]]);
  });

  it("an unreadable answer (not JSON) is noted once, by get()", async () => {
    const { f } = fake(() => new Response("<html>", { status: 200 }));
    await expect(fetchDeck(42, f)).rejects.toThrow();
    expect(calls()).toHaveLength(1);
    expect(calls()[0][0]).toBe("archidekt");
    expect(calls()[0][1]).toMatch(/^answer changed/);
  });

  it("a deck that reads but has the wrong shape still reads: a missing id shows as 0", async () => {
    const { f } = fake(() => json({ unexpected: true }));
    const deck = await fetchDeck(42, f);
    // parseDeck reads anything as a deck (missing fields count as empty), so what a changed answer shows is an empty deck.
    expect(deck).toMatchObject({ id: 0, name: "", cards: [], maybeboard: [] });
    expect(calls()).toEqual([["archidekt", null]]);
  });

  it("a deck that reads is noted (null) exactly once", async () => {
    const { f } = fake(() => json(deckJson));
    await expect(fetchDeck(42, f)).resolves.toMatchObject({ id: 42, name: "A deck" });
    expect(calls()).toEqual([["archidekt", null]]);
  });

  it("a deck list of two pages is noted (null) exactly once", async () => {
    const pages = [{ results: [{ id: 1, name: "A", size: 100 }], next: "http://archidekt.com/api/decks/v3/?page=2" }, { results: [{ id: 2, name: "B", size: 99 }], next: null }];
    let page = 0;
    const { f, urls } = fake(() => json(pages[Math.min(page++, pages.length - 1)]));
    await expect(userDecks("joelvdc", f)).resolves.toHaveLength(2);
    expect(urls.filter((u) => u.includes("/api/decks/v3/"))).toHaveLength(2);
    expect(calls()).toEqual([["archidekt", null]]);
  });

  it("a user whose second page fails (500) is noted once, no success, no changed answer", async () => {
    let page = 0;
    const { f } = fake(() => (page++ === 0 ? json({ results: [{ id: 1, name: "A" }], next: "https://archidekt.com/api/decks/v3/?page=2" }) : new Response("", { status: 500 })));
    await expect(userDecks("joelvdc", f)).rejects.toThrow(/answered 500/);
    expect(calls()).toEqual([["archidekt", "answered 500"]]);
  });
});

describe("EDHREC's notes for the health check (through recommendations)", () => {
  const lines = [{ name: "Baba Lysaga, Night Witch", commander: true }];
  const edhrecUrl = (url: string) => url.includes("json.edhrec.com");

  it("a page that reads is noted (null) exactly once", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request) => {
        const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
        if (edhrecUrl(url)) return json({ container: { json_dict: { cardlists: [{ header: "New Cards", tag: "newcards", cardviews: [] }] } } });
        return new Response("", { status: 404 });
      }),
    );
    const result = await recommendations("edhrec", lines, "Baba Lysaga, Night Witch", "BG");
    expect(result.sections).toEqual([]);
    expect(calls()).toEqual([["edhrec", null]]);
  });

  it("no page for the commander (404) isn't noted; a 500 is noted as answered 500", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 404 })));
    await expect(recommendations("edhrec", lines, "", "BG")).rejects.toBeInstanceOf(RecError);
    expect(calls()).toEqual([]);
    notes.noteService.mockClear();
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 500 })));
    await expect(recommendations("edhrec", lines, "", "BG")).rejects.toThrow(/answered 500/);
    expect(calls()).toEqual([["edhrec", "answered 500"]]);
  });

  it("a changed answer (no card lists) is noted once as a changed answer", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => json({ container: { json_dict: {} } })));
    await expect(recommendations("edhrec", lines, "", "BG")).rejects.toThrow();
    expect(calls()).toHaveLength(1);
    expect(calls()[0][0]).toBe("edhrec");
    expect(calls()[0][1]).toMatch(/^answer changed/);
  });

  it("an unreachable EDHREC is noted as couldn't be reached", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("fetch failed");
      }),
    );
    await expect(recommendations("edhrec", lines, "", "BG")).rejects.toThrow("fetch failed");
    expect(calls()).toEqual([["edhrec", "couldn't be reached"]]);
  });
});
