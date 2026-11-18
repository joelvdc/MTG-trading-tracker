import { readFileSync } from "node:fs";
import path from "node:path";
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// The server side of AI-written deck primers (release 5): the prompt and the cleaner the dialog shares, OpenCode Go's
// model list and one chat request, the deck data an answer is written from, the daily limit on the route, and the
// health check's AI part. No test talks to the network: every fetch is a fake.

// The routes want a logged-in account.
vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return {
    ...actual,
    currentAccount: async () => ({ kind: "user", accountId: 1, username: "joel", admin: true, device: "test" }),
  };
});

import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { aiConfigured, deckPrimerInput, listModels, websiteAccess, writePrimer, type AiAccess } from "@/lib/aiprimer";
import { buildPrompt, cleanPrimer, DEFAULT_MODEL, DEFAULT_SECTIONS, MAX_PER_DAY, type AiOptions, type PrimerInput } from "@/lib/aiprimershared";
import { USER_AGENT } from "@/lib/config";
import { syncDecks } from "@/lib/decks";
import type { DeckCard } from "@/lib/deckshared";
import { encode, type SyncFile } from "@/lib/syncfile";
import { GET as healthGet } from "@/app/api/health/route";
import { POST as aiPrimerPost } from "@/app/api/decks/[id]/aiprimer/route";

const NOW = 1_794_300_000_000;
const ID = 10710772;
const KEY = "test-key-not-real";
/** The website's own key, which only the owner's account may use. */
const OWNER = (): AiAccess => ({ provider: "opencode-go", key: KEY, shared: true });
const fixture = path.join(__dirname, "fixtures");
const appFile = (): SyncFile => JSON.parse(readFileSync(path.join(fixture, "app-sync.json"), "utf8")) as SyncFile;
const deckOf = (f: SyncFile, id = ID) => syncDecks(f).find((d) => d.deck.archidektId === id)!;

const originalKey = process.env.OPENCODE_API_KEY;

type Account = { kind: "user"; accountId: number; username: string; admin: boolean; device: string };
const ACCOUNT: Account = { kind: "user", accountId: 1, username: "joel", admin: true, device: "test" };
let store: NonNullable<ReturnType<typeof accountStore>>;

const jsonBody = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const chatAnswer = (content: unknown, status = 200) => jsonBody({ choices: [{ message: { content } }] }, status);

type Call = { url: string; init: RequestInit };
/** A fake fetch that records its calls; the handler gives each answer. */
function fakeFetch(handler: (url: string, init: RequestInit) => Response | Promise<Response>): { fn: typeof fetch; calls: Call[]; spy: ReturnType<typeof vi.fn> } {
  const calls: Call[] = [];
  const spy = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(url), init: init ?? {} });
    return handler(String(url), init ?? {});
  });
  return { fn: spy as unknown as typeof fetch, calls, spy };
}

beforeEach(async () => {
  process.env.OPENCODE_API_KEY = KEY;
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
  store = accountStore()!;
  await store.create("joel", "x", true, NOW);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

afterAll(() => {
  if (originalKey === undefined) delete process.env.OPENCODE_API_KEY;
  else process.env.OPENCODE_API_KEY = originalKey;
});

// ---- The health check's AI part -----------------------------------------------------------------------------------

describe("the health check's AI part", () => {
  it("says not set up when there is no key", async () => {
    delete process.env.OPENCODE_API_KEY;
    const body = (await (await healthGet()).json()) as { ai: unknown };
    expect(body.ai).toEqual({ configured: false, reachable: false, hasDefault: false, checkedAgeHours: null });
  });

  it("keeps the last check for an hour and asks Go only when it is older", async () => {
    await store.setJob("ai-check", Date.now(), { reachable: true, hasDefault: true });
    const seen: string[] = [];
    const spy = vi.fn(async (url: RequestInfo | URL) => {
      seen.push(String(url));
      throw new Error("Go must not be asked");
    });
    vi.stubGlobal("fetch", spy);

    const fresh = (await (await healthGet()).json()) as { ai: { configured: boolean; reachable: boolean; hasDefault: boolean; checkedAgeHours: number | null } };
    expect(fresh.ai).toEqual({ configured: true, reachable: true, hasDefault: true, checkedAgeHours: 0 });

    // Older than an hour: the model list is asked again, and a failure becomes the new (kept) answer.
    await store.setJob("ai-check", Date.now() - 2 * 3_600_000, { reachable: true, hasDefault: true });
    const stale = (await (await healthGet()).json()) as { ai: { configured: boolean; reachable: boolean; hasDefault: boolean; checkedAgeHours: number | null } };
    expect(seen.filter((u) => u.includes("opencode.ai"))).toEqual(["https://opencode.ai/inference/go/v1/models"]);
    expect(stale.ai).toEqual({ configured: true, reachable: false, hasDefault: false, checkedAgeHours: 0 });
    expect((await store.job("ai-check"))!.info).toEqual({ reachable: false, hasDefault: false });
  });
});

// ---- The prompt and the cleaner -----------------------------------------------------------------------------------

const input: PrimerInput = {
  deckName: "Vohar Danse",
  commanders: ["Vohar, Vodalian Desecrator"],
  sections: [
    { title: "Lands", cards: [{ name: "Forest", quantity: 2 }] },
    { title: "Ramp", cards: [{ name: "Sol Ring", quantity: 1 }] },
  ],
  roles: "Lands 37 · Ramp 11 · Draw 9",
  bracket: "Bracket 3 (Upgraded)",
  power: "Power 7.5 (Commander Salt)",
  combos: ["Sol Ring + Basalt Monolith: infinite mana"],
  notes: [{ name: "Sol Ring", note: "Tutor target" }],
};

const options = (over: Partial<AiOptions> = {}): AiOptions => ({
  focus: "HIGH",
  length: "SHORT",
  sections: ["plan", "weak", "upgrades"],
  startFrom: null,
  ...over,
});

describe("buildPrompt", () => {
  it("asks for the format, the focus, the length, the sections in order and the deck", () => {
    const p = buildPrompt(input, options());
    expect(p.system).toMatch(/plain text/i);
    expect(p.system).toContain("No tables");
    expect(p.system).toContain("no HTML");
    expect(p.system).toContain("code blocks");
    expect(p.system).toContain("English");
    // The primer's card links: deck cards go in double square brackets, upgrade ideas don't.
    expect(p.system).toContain("[[Sol Ring]]");
    expect(p.system).toContain("exactly as it is written in the list");
    expect(p.system).toContain("go without brackets");
    expect(p.system).toMatch(/don't invent cards/i);

    expect(p.user).toContain("High power");
    expect(p.user).toContain("strong and efficient");
    expect(p.user).toContain("250–400 words");
    const plan = p.user.indexOf("## Game plan");
    const weak = p.user.indexOf("## Weak spots");
    const upgrades = p.user.indexOf("## Upgrade ideas");
    expect(plan).toBeGreaterThanOrEqual(0);
    expect(weak).toBeGreaterThan(plan);
    expect(upgrades).toBeGreaterThan(weak);

    expect(p.user).toContain("Deck: Vohar Danse");
    expect(p.user).toContain("Commanders: Vohar, Vodalian Desecrator");
    expect(p.user).toContain("Bracket 3 (Upgraded)");
    expect(p.user).toContain("Power 7.5 (Commander Salt)");
    expect(p.user).toContain("Roles: Lands 37 · Ramp 11 · Draw 9");
    expect(p.user).toContain("Sol Ring + Basalt Monolith: infinite mana");
    expect(p.user).toContain("Sol Ring: Tutor target");
    expect(p.user).toContain("2 Forest");
    expect(p.user).toContain("1 Sol Ring");
    // Prices and the collection never go into a prompt.
    expect(p.user).not.toContain("€");
    expect(p.user).not.toContain("purchase");
    // The upgrade ideas only come when they are asked for.
    expect(DEFAULT_SECTIONS).not.toContain("upgrades");
  });

  it("writes the full length and the other focuses", () => {
    expect(buildPrompt(input, options({ length: "FULL" })).user).toContain("700–1200 words");
    expect(buildPrompt(input, options({ focus: "CASUAL" })).user).toContain("fun and fair");
    expect(buildPrompt(input, options({ focus: "UPGRADED" })).user).toContain("tuned but social");
    expect(buildPrompt(input, options({ focus: "CEDH" })).user).toContain("fastest wins and interaction");
  });

  it("starts from the owner's current primer", () => {
    const p = buildPrompt(input, options({ startFrom: "My plan\n\n- Sol Ring" }));
    expect(p.user).toContain("Start from the owner's current primer below: keep what's right, improve and fill in the rest");
    expect(p.user).toContain("My plan\n\n- Sol Ring");
  });
});

describe("cleanPrimer", () => {
  it("removes fences, fixes bullets and headings, and drops HTML", () => {
    expect(cleanPrimer("```markdown\n# Plan\n- one\n```")).toBe("# Plan\n- one");
    expect(cleanPrimer("* one\n+ two")).toBe("- one\n- two");
    expect(cleanPrimer("#### Deep\n##### Deeper\n# Fine")).toBe("### Deep\n### Deeper\n# Fine");
    expect(cleanPrimer('<b>bold</b> and <a href="x">a link</a>')).toBe("bold and a link");
  });

  it("keeps the primer's card links untouched", () => {
    expect(cleanPrimer("- [[Sol Ring]] first\n**[[Arcane Signet]]**")).toBe("- [[Sol Ring]] first\n**[[Arcane Signet]]**");
    // A link isn't HTML, so the tag strip leaves it as it is.
    expect(cleanPrimer("Play [[Atraxa, Praetors' Voice]]")).toBe("Play [[Atraxa, Praetors' Voice]]");
  });

  it("trims, collapses blank runs, cuts to 20,000 characters and answers null for nothing", () => {
    expect(cleanPrimer("a   \nb  ")).toBe("a\nb");
    expect(cleanPrimer("a\n\n\n\nb")).toBe("a\n\nb");
    expect(cleanPrimer("  \n text \n\n")).toBe("text");
    expect(cleanPrimer("x".repeat(25_000))).toHaveLength(20_000);
    expect(cleanPrimer("   \n\n```\n")).toBeNull();
    expect(cleanPrimer("<p></p>")).toBeNull();
    expect(cleanPrimer("")).toBeNull();
  });
});

// ---- OpenCode Go: the model list and one chat request -------------------------------------------------------------

describe("listModels", () => {
  it("lists Go's models, the default first, and keeps them for an hour", async () => {
    const first = fakeFetch(() => jsonBody({ data: [{ id: "zeta" }, { id: DEFAULT_MODEL }, { id: "alpha" }, { id: DEFAULT_MODEL }] }));
    expect(await listModels(OWNER(), first.fn)).toEqual([DEFAULT_MODEL, "alpha", "zeta"]);
    expect(first.calls).toHaveLength(1);
    expect(first.calls[0].url).toBe("https://opencode.ai/inference/go/v1/models");
    const headers = first.calls[0].init.headers as Record<string, string>;
    expect(headers.Authorization).toBe(`Bearer ${KEY}`);
    expect(headers["User-Agent"]).toBe(USER_AGENT);

    // An hour has not passed: the memory keeps the list, so the second look isn't asked again.
    const second = fakeFetch(() => {
      throw new Error("must not be asked");
    });
    expect(await listModels(OWNER(), second.fn)).toEqual([DEFAULT_MODEL, "alpha", "zeta"]);
    expect(second.spy).not.toHaveBeenCalled();
  });
});

describe("writePrimer", () => {
  it("sends one chat request to the right address with the key, the model and the token budget", async () => {
    const full = fakeFetch(() => chatAnswer("```\n# Plan\n- Sol Ring\n```"));
    expect((await writePrimer(OWNER(), input, options({ length: "FULL" }), "some-other-model", full.fn)).text).toBe("# Plan\n- Sol Ring");
    expect(full.calls).toHaveLength(1);
    expect(full.calls[0].url).toBe("https://opencode.ai/inference/go/openai/v1/chat/completions");
    const headers = full.calls[0].init.headers as Record<string, string>;
    expect(headers.Authorization).toBe(`Bearer ${KEY}`);
    expect(headers["User-Agent"]).toBe(USER_AGENT);
    const body = JSON.parse(String(full.calls[0].init.body)) as { model: string; max_tokens: number; temperature: number; stream: boolean; messages: { role: string; content: string }[] };
    expect(body.model).toBe("some-other-model");
    expect(body.max_tokens).toBe(16000);
    expect(body.temperature).toBe(0.7);
    // The whole answer, not a stream: reading chunks is only a fallback.
    expect(body.stream).toBe(false);
    expect(body.messages.map((m) => m.role)).toEqual(["system", "user"]);
    expect(body.messages[1].content).toContain("## Game plan");

    const short = fakeFetch(() => chatAnswer("ok"));
    await writePrimer(OWNER(), input, options({ length: "SHORT" }), "m", short.fn);
    expect((JSON.parse(String(short.calls[0].init.body)) as { max_tokens: number }).max_tokens).toBe(8000);
  });

  it("takes a content that comes as parts", async () => {
    const parts = fakeFetch(() =>
      jsonBody({ choices: [{ message: { content: [{ type: "text", text: "## Plan\n" }, { text: "- Sol Ring" }] } }] }),
    );
    expect((await writePrimer(OWNER(), input, options(), "m", parts.fn)).text).toBe("## Plan\n- Sol Ring");

    // Nothing but the other parts left: still an empty answer, never a crash.
    const images = fakeFetch(() => jsonBody({ choices: [{ message: { content: [{ type: "image_url", image_url: { url: "x" } }] } }] }));
    const quiet = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      await expect(writePrimer(OWNER(), input, options(), "m", images.fn)).rejects.toThrow("The AI's answer was empty.");
    } finally {
      quiet.mockRestore();
    }
  });

  it("reads an answer that came as server-sent events", async () => {
    const lines = [
      `data: ${JSON.stringify({ model: "m", choices: [{ delta: { role: "assistant", content: "## Plan\n" } }] })}`,
      `data: ${JSON.stringify({ choices: [{ delta: { content: "- Sol Ring" }, finish_reason: null }] })}`,
      `data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: "stop" }] })}`,
      "data: [DONE]",
    ];
    const streamed = fakeFetch(
      () => new Response(`${lines.join("\n\n")}\n\n`, { status: 200, headers: { "content-type": "text/event-stream" } }),
    );
    expect((await writePrimer(OWNER(), input, options(), "m", streamed.fn)).text).toBe("## Plan\n- Sol Ring");
  });

  it("reports the seconds it took and the tokens the answer used", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000_000);
    const usage = fakeFetch(() => {
      // The answer comes back 2.34 s after the request left, and its body is read straight after: one decimal.
      vi.setSystemTime(1_002_340);
      return jsonBody({
        choices: [{ message: { content: "ok" } }],
        usage: { prompt_tokens: 10, completion_tokens: 42, total_tokens: 52, reasoning_tokens: 7 },
      });
    });
    expect(await writePrimer(OWNER(), input, options(), "m", usage.fn)).toEqual({ text: "ok", seconds: 2.3, outputTokens: 42, reasoningTokens: 7 });
  });

  it("says nothing about tokens without usage, and reads reasoning under either name", async () => {
    const none = await writePrimer(OWNER(), input, options(), "m", fakeFetch(() => chatAnswer("ok")).fn);
    expect(none.outputTokens).toBeNull();
    expect(none.reasoningTokens).toBeNull();

    // OpenAI's nested detail wins; a plain reasoning_tokens beside completion_tokens still counts when it's missing.
    const nested = fakeFetch(() =>
      jsonBody({ choices: [{ message: { content: "ok" } }], usage: { completion_tokens: 900, completion_tokens_details: { reasoning_tokens: 800 } } }),
    );
    expect(await writePrimer(OWNER(), input, options(), "m", nested.fn)).toMatchObject({ outputTokens: 900, reasoningTokens: 800 });

    // A streamed answer carries its usage in the last chunk, which is the one that counts.
    const lines = [
      `data: ${JSON.stringify({ model: "m", choices: [{ delta: { content: "## Plan" } }] })}`,
      `data: ${JSON.stringify({ choices: [{ delta: { content: " ok" }, finish_reason: "stop" }], usage: { completion_tokens: 120, completion_tokens_details: { reasoning_tokens: 100 } } })}`,
      "data: [DONE]",
    ];
    const streamed = fakeFetch(() => new Response(`${lines.join("\n\n")}\n\n`, { status: 200, headers: { "content-type": "text/event-stream" } }));
    expect(await writePrimer(OWNER(), input, options(), "m", streamed.fn)).toMatchObject({ text: "## Plan ok", outputTokens: 120, reasoningTokens: 100 });
  });

  it("says when thinking used up the answer's room, and warns with the numbers only", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      const thinking = fakeFetch(() =>
        jsonBody({
          model: "deepseek-v4.1-flash",
          choices: [{ message: { role: "assistant", content: "", reasoning_content: "Reasoning nobody may read" }, finish_reason: "length" }],
          usage: { prompt_tokens: 1200, completion_tokens: 8000, total_tokens: 9200 },
        }),
      );
      await expect(writePrimer(OWNER(), input, options(), "deepseek-v4.1-flash", thinking.fn)).rejects.toThrow(
        "The AI used all its room for thinking and wrote nothing; try again, or pick Short or fewer sections.",
      );

      expect(warn).toHaveBeenCalledTimes(1);
      const line = String(warn.mock.calls[0][0]);
      expect(line.startsWith("aiprimer empty: ")).toBe(true);
      // Nothing of the answer, and never the key.
      expect(line).not.toContain("Reasoning nobody may read");
      expect(line).not.toContain(KEY);
      const report = JSON.parse(line.slice("aiprimer empty: ".length)) as Record<string, unknown>;
      expect(Object.keys(report).sort()).toEqual(["bodyLength", "contentType", "finishReason", "hasReasoning", "messageKeys", "model", "status", "usage"]);
      expect(report.status).toBe(200);
      expect(report.contentType).toBe("application/json");
      expect(report.bodyLength).toBeGreaterThan(0);
      expect(report.finishReason).toBe("length");
      expect(report.messageKeys).toEqual(["role", "content", "reasoning_content"]);
      expect(report.hasReasoning).toBe(true);
      expect(report.usage).toEqual({ prompt_tokens: 1200, completion_tokens: 8000, total_tokens: 9200 });
      expect(report.model).toBe("deepseek-v4.1-flash");

      // A short answer that simply said nothing keeps the old wording.
      const empty = fakeFetch(() => chatAnswer("   "));
      await expect(writePrimer(OWNER(), input, options(), "m", empty.fn)).rejects.toThrow("The AI's answer was empty.");
      expect(warn).toHaveBeenCalledTimes(2);
      expect(String(warn.mock.calls[1][0])).toContain('"finishReason":null');
    } finally {
      warn.mockRestore();
    }
  });

  it("says what went wrong, without the key in the message", async () => {
    const cases: [number, string][] = [
      [401, "The AI key isn't accepted."],
      [403, "The AI key isn't accepted."],
      [429, "OpenCode Go's usage limit is reached; try again later."],
      [500, "The AI didn't answer (500)."],
    ];
    for (const [status, message] of cases) {
      const { fn } = fakeFetch(() => new Response("", { status }));
      await expect(writePrimer(OWNER(), input, options(), "m", fn)).rejects.toThrow(message);
    }

    const quiet = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      const empty = fakeFetch(() => chatAnswer("   "));
      await expect(writePrimer(OWNER(), input, options(), "m", empty.fn)).rejects.toThrow("The AI's answer was empty.");
    } finally {
      quiet.mockRestore();
    }

    const offline = fakeFetch(() => {
      throw new Error("offline");
    });
    const error = await writePrimer(OWNER(), input, options(), "m", offline.fn).then(
      () => null,
      (e: unknown) => e as Error,
    );
    expect(error?.message).toBe("The AI didn't answer.");
    expect(error?.message).not.toContain(KEY);
  });

  it("gives each primer its own conversation id, which Go insists on", async () => {
    const { fn, calls } = fakeFetch(() => chatAnswer("ok"));
    await writePrimer(OWNER(), input, options(), "m", fn);
    await writePrimer(OWNER(), input, options(), "m", fn);

    const ids = calls.map((c) => (c.init.headers as Record<string, string>)["x-opencode-session"]);
    expect(ids).toHaveLength(2);
    expect(ids[0]).toMatch(/^primer-/);
    expect(ids[1]).toMatch(/^primer-/);
    expect(ids[0]).not.toBe(ids[1]);
    // Nothing about the account, the deck or the owner in it.
    for (const id of ids) expect(id).toMatch(/^primer-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });

  it("has no website key and no owner access without a key", () => {
    delete process.env.OPENCODE_API_KEY;
    expect(aiConfigured()).toBe(false);
    expect(websiteAccess()).toBeNull();
  });

  it("gives up after 100 seconds", async () => {
    vi.useFakeTimers();
    const { fn } = fakeFetch(
      (_url, init) =>
        new Promise<Response>((_resolve, reject) => {
          init.signal?.addEventListener("abort", () => reject(new Error("aborted")));
        }),
    );
    const answer = writePrimer(OWNER(), input, options(), "m", fn);
    const failed = expect(answer).rejects.toThrow("The AI took too long; try again.");
    await vi.advanceTimersByTimeAsync(100_000);
    await failed;
  });
});

// ---- A deck as the prompt sees it ---------------------------------------------------------------------------------

function deckCard(name: string, quantity: number, opts: { id: string; category?: string; types?: string; commander?: boolean; maybeboard?: boolean }): DeckCard {
  return {
    id: 0,
    deckId: ID,
    card: {
      scryfallId: opts.id,
      name,
      setCode: "tst",
      setName: "Test Set",
      collectorNumber: "1",
      rarity: "common",
      imageUrl: null,
      cardmarketId: null,
      fallbackEur: null,
      fallbackEurFoil: null,
      hasNonFoil: true,
      hasFoil: false,
      foilType: null,
      hasEtched: false,
      flavorName: null,
      cardmarketFoilId: null,
    },
    quantity,
    category: opts.category ?? "Other",
    types: opts.types ?? "Artifact",
    cmc: 0,
    commander: opts.commander ?? false,
    foil: false,
    etched: false,
    gameChanger: false,
    addedInApp: false,
    maybeboard: opts.maybeboard ?? false,
    categories: "",
    archidektCardId: null,
    relationId: null,
    label: null,
    lineUid: `u-${name}`,
  };
}

describe("deckPrimerInput", () => {
  it("takes the commanders, sections, roles, bracket, power, combos and notes; never prices", () => {
    const f = appFile();
    const sd = deckOf(f);
    sd.deck.name = "Vohar Danse";
    sd.deck.commanders = "Vohar, Vodalian Desecrator";
    sd.deck.bracketRealistic = 3;
    sd.deck.powerLevel = 7.5;
    sd.deck.cardNotes = JSON.stringify({ "sol ring": "Tutor target", forest: "Never cut", "maybe rock": "Not in the deck" });
    sd.deck.saltCard = JSON.stringify({
      combos: [{ cards: ["Sol Ring", "Basalt Monolith"], results: ["infinite-mana"] }],
      twoCardCombos: [["Exquisite Blood", "Sanguine Bond"]],
    });
    sd.cards = [
      deckCard("Vohar, Vodalian Desecrator", 1, { id: "a", category: "Commander", types: "Legendary Creature — Zombie Wizard", commander: true }),
      deckCard("Sol Ring", 1, { id: "b", category: "Ramp", types: "Artifact" }),
      deckCard("Forest", 37, { id: "c", category: "Lands", types: "Basic Land — Forest" }),
      deckCard("Maybe Rock", 2, { id: "d", category: "Ramp", types: "Artifact", maybeboard: true }),
    ];

    const got = deckPrimerInput(f, ID, {})!;
    expect(got).not.toBeNull();
    expect(got.deckName).toBe("Vohar Danse");
    expect(got.commanders).toEqual(["Vohar, Vodalian Desecrator"]);
    expect(got.sections.find((s) => s.title === "Lands")!.cards).toEqual([{ name: "Forest", quantity: 37 }]);
    expect(got.sections.find((s) => s.title === "Ramp")!.cards).toEqual([{ name: "Sol Ring", quantity: 1 }]);
    expect(got.roles).toContain("Lands 37");
    expect(got.roles).toContain("Ramp 1");
    expect(got.bracket).toBe("Bracket 3 (Upgraded)");
    expect(got.power).toBe("Power 7.5 (Commander Salt)");
    expect(got.combos).toContain("Sol Ring + Basalt Monolith: infinite mana");
    expect(got.combos).toContain("Exquisite Blood + Sanguine Bond");
    expect(got.notes).toContainEqual({ name: "Sol Ring", note: "Tutor target" });
    expect(got.notes).toContainEqual({ name: "Forest", note: "Never cut" });
    // The maybeboard is not the list the primer is about.
    expect(got.sections.some((s) => s.cards.some((c) => c.name === "Maybe Rock"))).toBe(false);
    expect(got.notes.some((n) => n.name === "Maybe Rock")).toBe(false);

    const text = JSON.stringify(got);
    expect(text).not.toContain("€");
    expect(text).not.toContain("purchase");
    expect(text).not.toContain("fallbackEur");
  });

  it("answers null for a deck that isn't there", () => {
    expect(deckPrimerInput(appFile(), 999, {})).toBeNull();
  });
});

// ---- The route: one answer, the daily limit, defaults -------------------------------------------------------------

const dayKey = (now = Date.now()) => `aiprimer:1:${new Date(now).toISOString().slice(0, 10)}`;

const callRoute = (body: unknown, id = ID) =>
  aiPrimerPost(
    new Request(`https://mtgtrader.vercel.app/api/decks/${id}/aiprimer`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-requested-with": "mtgtrader" },
      body: JSON.stringify(body),
    }),
    { params: Promise.resolve({ id: String(id) }) },
  );

describe("the AI primer route", () => {
  it("writes from the deck and counts the answer for the day", async () => {
    await store.putSync(ACCOUNT.accountId, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch(() => chatAnswer("```\n# Plan\n- Sol Ring\n```"));
    vi.stubGlobal("fetch", fn);

    const answer = await callRoute({ model: "my-model", focus: "CEDH", length: "SHORT", sections: ["plan", "weak"], startFrom: "Old words" });
    expect(answer.status).toBe(200);
    const body = (await answer.json()) as { text: string; model: string; left: number; seconds: number; outputTokens: number | null; reasoningTokens: number | null };
    // The text, the model and the day's count; the numbers are this request's own clock and Go's usage (none sent here).
    expect({ ...body, seconds: 0 }).toEqual({ text: "# Plan\n- Sol Ring", model: "my-model", left: MAX_PER_DAY - 1, seconds: 0, outputTokens: null, reasoningTokens: null });
    expect(typeof body.seconds).toBe("number");
    expect(body.seconds).toBeGreaterThanOrEqual(0);
    expect(calls).toHaveLength(1);
    const sent = JSON.parse(String(calls[0].init.body)) as { model: string; max_tokens: number; messages: { content: string }[] };
    expect(sent.model).toBe("my-model");
    expect(sent.max_tokens).toBe(8000);
    expect(sent.messages[1].content).toContain("Focus: cEDH");
    expect(sent.messages[1].content).toContain("## Game plan");
    expect(sent.messages[1].content).toContain("## Weak spots");
    expect(sent.messages[1].content).not.toContain("## Upgrade ideas");
    expect(sent.messages[1].content).toContain("Old words");
    expect((await store.job(dayKey()))!.info).toEqual({ count: 1 });
  });

  it("counts only answers and refuses after 20", async () => {
    await store.putSync(ACCOUNT.accountId, encode(appFile()), null, NOW);
    await store.setJob(dayKey(), Date.now(), { count: 19 });
    const { fn, spy } = fakeFetch(() => new Response("", { status: 429 }));
    vi.stubGlobal("fetch", fn);

    const failed = await callRoute({});
    expect(failed.status).toBe(400);
    expect(((await failed.json()) as { error: string }).error).toBe("OpenCode Go's usage limit is reached; try again later.");
    expect((await store.job(dayKey()))!.info).toEqual({ count: 19 });

    await store.setJob(dayKey(), Date.now(), { count: MAX_PER_DAY });
    const refused = await callRoute({});
    expect(refused.status).toBe(400);
    expect(((await refused.json()) as { error: string }).error).toBe("You've written 20 primers with AI today; more tomorrow.");
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it("uses the defaults for unknown options", async () => {
    await store.putSync(ACCOUNT.accountId, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch(() => chatAnswer("ok"));
    vi.stubGlobal("fetch", fn);

    const answer = await callRoute({ model: "not a model!", focus: "NOPE", length: "HUGE", sections: ["plan", "bogus", "plan"], startFrom: 7 });
    expect(answer.status).toBe(200);
    expect(((await answer.json()) as { model: string }).model).toBe(DEFAULT_MODEL);
    const sent = JSON.parse(String(calls[0].init.body)) as { model: string; max_tokens: number; messages: { content: string }[] };
    expect(sent.model).toBe(DEFAULT_MODEL);
    expect(sent.max_tokens).toBe(16000);
    expect(sent.messages[1].content).toContain("Focus: Casual");
    expect(sent.messages[1].content).toContain("700–1200 words");
    expect(sent.messages[1].content.match(/## Game plan/g)).toHaveLength(1);
    expect(sent.messages[1].content).not.toContain("## Weak spots");
    expect(sent.messages[1].content).not.toContain("Start from the owner's current primer");
  });
});
