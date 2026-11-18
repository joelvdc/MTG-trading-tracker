import { readFileSync } from "node:fs";
import path from "node:path";
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Each account's own AI key (2026-11-18): the owner's account writes with the website's key, every other account only
// with its own saved one (OpenCode Go, Mistral or OpenRouter), and an account with no key can't write primers at all. No
// test talks to the network (every fetch is a fake), and a key must never appear in an answer, a message or a log line.

type Session = { kind: "user"; accountId: number; username: string; admin: boolean; device: string };
/** Who the routes think is logged in; a test sets it to the owner or to another account. */
const who = vi.hoisted(() => ({ current: { kind: "user", accountId: 1, username: "owner", admin: true, device: "test" } as Session | null }));

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return { ...actual, currentAccount: async () => who.current };
});

import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { accessFor, cleanAiKey, forgetModelLists, listModels, openAiKey, sealAiKey, websiteAccess, writePrimer, type AiAccess } from "@/lib/aiprimer";
import { AI_PROVIDERS, DEFAULT_MODEL, type AiOptions, type PrimerInput } from "@/lib/aiprimershared";
import { seal } from "@/lib/session";
import { encode, type SyncFile } from "@/lib/syncfile";
import { GET as keyGet, POST as keyPost } from "@/app/api/account/aikey/route";
import { GET as modelsGet } from "@/app/api/ai/models/route";
import { POST as aiPrimerPost } from "@/app/api/decks/[id]/aiprimer/route";

const NOW = 1_794_300_000_000;
const ID = 10710772;
const OWNER_ID = 1;
const USER_ID = 2;
/** The website's own key, as OPENCODE_API_KEY holds it. */
const WEBSITE_KEY = "website-key-not-real-000";
const GO_KEY = "go-key-00000000000000001";
const OR_KEY = "sk-or-v1-0000000000000000000002";
/** A Mistral key, as MISTRAL_API_KEY holds the website's own. */
const MISTRAL_KEY = "mistral-key-not-real-0000001";
const FIXTURE = path.join(__dirname, "fixtures");
const appFile = (): SyncFile => JSON.parse(readFileSync(path.join(FIXTURE, "app-sync.json"), "utf8")) as SyncFile;

const env = {
  SESSION_SECRET: process.env.SESSION_SECRET,
  OPENCODE_API_KEY: process.env.OPENCODE_API_KEY,
  MISTRAL_API_KEY: process.env.MISTRAL_API_KEY,
  DATABASE_URL: process.env.DATABASE_URL,
};

let store: NonNullable<ReturnType<typeof accountStore>>;

const asOwner = () => {
  who.current = { kind: "user", accountId: OWNER_ID, username: "owner", admin: true, device: "test" };
};
const asUser = (accountId = USER_ID, username = "kid") => {
  who.current = { kind: "user", accountId, username, admin: false, device: "test" };
};

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

const headerOf = (c: Call, name: string) => (c.init.headers as Record<string, string>)[name];
const sentModel = (c: Call) => (JSON.parse(String(c.init.body)) as { model: string }).model;

const primerRequest = () =>
  aiPrimerPost(
    new Request(`https://mtgtrader.vercel.app/api/decks/${ID}/aiprimer`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-requested-with": "mtgtrader" },
      body: JSON.stringify({}),
    }),
    { params: Promise.resolve({ id: String(ID) }) },
  );

const primerWith = (body: unknown) =>
  aiPrimerPost(
    new Request(`https://mtgtrader.vercel.app/api/decks/${ID}/aiprimer`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-requested-with": "mtgtrader" },
      body: JSON.stringify(body),
    }),
    { params: Promise.resolve({ id: String(ID) }) },
  );

const keyRequest = (body: unknown, headers: Record<string, string> = { "content-type": "application/json", "x-requested-with": "mtgtrader" }) =>
  keyPost(new Request("https://mtgtrader.vercel.app/api/account/aikey", { method: "POST", headers, body: JSON.stringify(body) }));

type ModelsAnswer = {
  configured: boolean;
  needsKey: boolean;
  provider?: string;
  models: string[];
  defaultModel: string;
  service?: string;
  services?: { id: string; label: string }[];
  error?: string;
};
type KeyAnswer = {
  usesWebsiteKey: boolean;
  websiteKeySet: boolean;
  websiteProvider: string | null;
  websiteProviders: string[];
  providers: { id: string; label: string }[];
  saved: { provider: string; label: string; last4: string; savedAt: number } | null;
  unreadable: boolean;
};

const models = async () => (await (await modelsGet(new Request("https://mtgtrader.vercel.app/api/ai/models"))).json()) as ModelsAnswer;
/** The list for one service (?provider=), as the owner's dialog asks when it switches. */
const modelsFor = async (provider: string) =>
  (await (await modelsGet(new Request(`https://mtgtrader.vercel.app/api/ai/models?provider=${encodeURIComponent(provider)}`))).json()) as ModelsAnswer;
const keyInfo = async () => (await (await keyGet()).json()) as KeyAnswer;

beforeEach(async () => {
  process.env.SESSION_SECRET = Buffer.alloc(32, 7).toString("base64");
  process.env.OPENCODE_API_KEY = WEBSITE_KEY;
  // The website's own key is Mistral's only when this is set: no test starts with one.
  delete process.env.MISTRAL_API_KEY;
  process.env.DATABASE_URL = "memory";
  resetMemoryStore();
  store = accountStore()!;
  await store.create("owner", "x", true, NOW);
  await store.create("kid", "x", false, NOW);
  asUser();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

afterAll(() => {
  for (const [name, value] of Object.entries(env)) {
    if (value === undefined) delete process.env[name];
    else process.env[name] = value;
  }
});

// ---- The lists are kept for the website's own key only ------------------------------------------------------------

// This test runs first: the website's model list is kept in memory for the whole file, so it must be filled here.
describe("whose list is kept", () => {
  it("never hands the website's cached list, or one account's list, to another account", async () => {
    asOwner();
    const site = fakeFetch(() => jsonBody({ data: [{ id: "shared-only-model" }] }));
    vi.stubGlobal("fetch", site.fn);
    const owner = await models();
    expect(owner.models).toEqual(["shared-only-model"]);
    expect(site.calls).toHaveLength(1);

    // Another account's list is its own: the website's kept list is neither used nor replaced.
    asUser();
    await store.setAiKey(USER_ID, { provider: "openrouter", sealedKey: sealAiKey(OR_KEY, NOW), savedAt: NOW });
    const mine = fakeFetch(() => jsonBody({ data: [{ id: "own-only-model" }] }));
    vi.stubGlobal("fetch", mine.fn);
    const other = await models();
    expect(other.models).toEqual(["own-only-model"]);
    expect(other.models).not.toEqual(owner.models);
    expect(mine.calls).toHaveLength(1);
    expect(mine.calls[0].url).toBe("https://openrouter.ai/api/v1/models");
    expect(headerOf(mine.calls[0], "Authorization")).toBe(`Bearer ${OR_KEY}`);

    // Not kept either: a second look asks the service again, so one account's list can't reach the next.
    const again = fakeFetch(() => jsonBody({ data: [{ id: "second-look" }] }));
    vi.stubGlobal("fetch", again.fn);
    expect((await models()).models).toEqual(["second-look"]);
    expect(again.calls).toHaveLength(1);

    const third = (await store.create("third", "x", false, NOW))!;
    asUser(third.id, "third");
    await store.setAiKey(third.id, { provider: "opencode-go", sealedKey: sealAiKey(GO_KEY, NOW), savedAt: NOW });
    const theirs = fakeFetch(() => jsonBody({ data: [{ id: "third-model" }] }));
    vi.stubGlobal("fetch", theirs.fn);
    expect((await models()).models).toEqual(["third-model"]);
    expect(theirs.calls[0].url).toBe("https://opencode.ai/inference/go/v1/models");
    expect(headerOf(theirs.calls[0], "Authorization")).toBe(`Bearer ${GO_KEY}`);

    // The owner still gets the website's own list, kept as it was.
    asOwner();
    const back = fakeFetch(() => {
      throw new Error("the website's list is kept");
    });
    vi.stubGlobal("fetch", back.fn);
    expect((await models()).models).toEqual(["shared-only-model"]);
    expect(back.spy).not.toHaveBeenCalled();
  });
});

// ---- 1. An account with no key can't write a primer ---------------------------------------------------------------

describe("an account with no key", () => {
  it("is asked for one, and nothing is sent to any AI service", async () => {
    const never = fakeFetch(() => {
      throw new Error("must not be asked");
    });
    vi.stubGlobal("fetch", never.fn);
    await store.putSync(USER_ID, encode(appFile()), null, NOW);

    expect(await models()).toEqual({ configured: false, needsKey: true, models: [], defaultModel: DEFAULT_MODEL });

    const answer = await primerRequest();
    expect(answer.status).toBe(400);
    expect(((await answer.json()) as { error: string }).error).toBe("Add your own AI key under Settings → AI key first.");
    expect(never.spy).not.toHaveBeenCalled();
  });
});

// ---- 2. The owner's account uses the website's key, and nothing else ----------------------------------------------

describe("the owner's account", () => {
  it("writes with the website's key even when it has a saved key of its own", async () => {
    asOwner();
    await store.setAiKey(OWNER_ID, { provider: "openrouter", sealedKey: sealAiKey(OR_KEY, NOW), savedAt: NOW });
    await store.putSync(OWNER_ID, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch(() => chatAnswer("ok"));
    vi.stubGlobal("fetch", fn);

    expect(await primerWith({ model: "my-model" })).toHaveProperty("status", 200);
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("https://opencode.ai/inference/go/openai/v1/chat/completions");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${WEBSITE_KEY}`);
    expect(sentModel(calls[0])).toBe("my-model");
    // The key it saved is never used, and never sent anywhere.
    for (const c of calls) expect(JSON.stringify(c.init)).not.toContain(OR_KEY);

    // GET says whose key it uses, and shows no saved key for the owner's account.
    const info = await keyInfo();
    expect(info).toMatchObject({ usesWebsiteKey: true, websiteKeySet: true, saved: null, unreadable: false });
  });
});

// ---- 3. An OpenRouter key: the account's own key, and no Go session header -----------------------------------------

describe("an account with an OpenRouter key", () => {
  it("asks OpenRouter with its own key, and never with the website's", async () => {
    await store.setAiKey(USER_ID, { provider: "openrouter", sealedKey: sealAiKey(OR_KEY, NOW), savedAt: NOW });
    await store.putSync(USER_ID, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch((url) =>
      url.endsWith("/models") ? jsonBody({ data: [{ id: "other/model" }, { id: AI_PROVIDERS.openrouter.defaultModel }] }) : chatAnswer("ok"),
    );
    vi.stubGlobal("fetch", fn);

    const list = await models();
    expect(list).toMatchObject({ configured: true, needsKey: false, provider: "OpenRouter", defaultModel: AI_PROVIDERS.openrouter.defaultModel });
    expect(list.models).toEqual([AI_PROVIDERS.openrouter.defaultModel, "other/model"]);
    expect(calls[0].url).toBe("https://openrouter.ai/api/v1/models");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${OR_KEY}`);

    expect(await primerWith({ model: "other/model" })).toHaveProperty("status", 200);
    const chat = calls.find((c) => c.url.includes("/chat/completions"))!;
    expect(chat.url).toBe("https://openrouter.ai/api/v1/chat/completions");
    expect(headerOf(chat, "Authorization")).toBe(`Bearer ${OR_KEY}`);
    // OpenRouter needs no session header; only Go insists on one.
    expect(headerOf(chat, "x-opencode-session")).toBeUndefined();
    expect(sentModel(chat)).toBe("other/model");
    for (const c of calls) expect(JSON.stringify(c.init)).not.toContain(WEBSITE_KEY);
  });
});

// ---- 4. An OpenCode Go key: the account's own key, with Go's session header ---------------------------------------

describe("an account with an OpenCode Go key", () => {
  it("asks Go with its own key and its conversation id", async () => {
    await store.setAiKey(USER_ID, { provider: "opencode-go", sealedKey: sealAiKey(GO_KEY, NOW), savedAt: NOW });
    await store.putSync(USER_ID, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch(() => chatAnswer("ok"));
    vi.stubGlobal("fetch", fn);

    expect(await primerRequest()).toHaveProperty("status", 200);
    expect(calls[0].url).toBe("https://opencode.ai/inference/go/openai/v1/chat/completions");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${GO_KEY}`);
    expect(headerOf(calls[0], "x-opencode-session")).toMatch(/^primer-/);
    // The model is the one the account's service starts on.
    expect(sentModel(calls[0])).toBe(AI_PROVIDERS["opencode-go"].defaultModel);
    for (const c of calls) expect(JSON.stringify(c.init)).not.toContain(WEBSITE_KEY);
  });
});

// ---- 5. Saving and removing a key ----------------------------------------------------------------------------------

describe("saving a key", () => {
  it("keeps only a key the service accepts, sealed, and answers only its last four characters", async () => {
    // Go's model list answers 401 to a wrong key, so nothing is stored.
    const refused = fakeFetch(() => jsonBody({ error: { message: "nope" } }, 401));
    vi.stubGlobal("fetch", refused.fn);
    const bad = await keyRequest({ action: "save", provider: "opencode-go", key: GO_KEY });
    expect(bad.status).toBe(400);
    expect(((await bad.json()) as { error: string }).error).toBe("OpenCode Go didn't accept this key.");
    expect(refused.calls[0].url).toBe("https://opencode.ai/inference/go/v1/models");
    expect(await store.aiKey(USER_ID)).toBeNull();

    // OpenRouter's model list is public, so its key is checked at its own address.
    const accepted = fakeFetch(() => jsonBody({ data: { label: "test" } }));
    vi.stubGlobal("fetch", accepted.fn);
    const before = Date.now();
    const saved = await keyRequest({ action: "save", provider: "openrouter", key: OR_KEY });
    expect(saved.status).toBe(200);
    expect(((await saved.json()) as { note: string }).note).toBe("Saved. OpenRouter accepted the key.");
    expect(accepted.calls[0].url).toBe("https://openrouter.ai/api/v1/key");
    expect(headerOf(accepted.calls[0], "Authorization")).toBe(`Bearer ${OR_KEY}`);

    const row = (await store.aiKey(USER_ID))!;
    expect(row.provider).toBe("openrouter");
    expect(row.sealedKey).not.toContain(OR_KEY);
    expect(row.savedAt).toBeGreaterThanOrEqual(before);
    expect(openAiKey(row.sealedKey)).toBe(OR_KEY);

    // GET shows the last four characters and where it came from, never the key.
    const info = await keyInfo();
    expect(info).toMatchObject({
      usesWebsiteKey: false,
      websiteKeySet: true,
      providers: [
        { id: "opencode-go", label: "OpenCode Go" },
        { id: "mistral", label: "Mistral" },
        { id: "openrouter", label: "OpenRouter" },
      ],
      saved: { provider: "openrouter", label: "OpenRouter", last4: OR_KEY.slice(-4) },
      unreadable: false,
    });
    expect(info.saved!.savedAt).toBe(row.savedAt);
    expect(JSON.stringify(info)).not.toContain(OR_KEY);

    // Saving again replaces the key and its service.
    const replaced = fakeFetch(() => jsonBody({}));
    vi.stubGlobal("fetch", replaced.fn);
    expect(await keyRequest({ action: "save", provider: "opencode-go", key: GO_KEY })).toHaveProperty("status", 200);
    expect(replaced.calls[0].url).toBe("https://opencode.ai/inference/go/v1/models");
    expect(openAiKey((await store.aiKey(USER_ID))!.sealedKey)).toBe(GO_KEY);
    expect((await store.aiKey(USER_ID))!.provider).toBe("opencode-go");

    // Removing it leaves nothing.
    const removed = await keyRequest({ action: "remove" });
    expect(((await removed.json()) as { note: string }).note).toBe("Your key is removed.");
    expect(await store.aiKey(USER_ID)).toBeNull();
    expect((await keyInfo()).saved).toBeNull();
  });

  it("refuses the owner's account, an unknown service and a key that can't be one, without asking anyone", async () => {
    const never = fakeFetch(() => {
      throw new Error("must not be asked");
    });
    vi.stubGlobal("fetch", never.fn);
    asOwner();
    const owner = await keyRequest({ action: "save", provider: "openrouter", key: OR_KEY });
    expect(((await owner.json()) as { error: string }).error).toBe("Your account uses the website's own key.");

    asUser();
    const unknown = await keyRequest({ action: "save", provider: "together", key: OR_KEY });
    expect(((await unknown.json()) as { error: string }).error).toBe("Pick OpenCode Go, Mistral or OpenRouter.");

    const space = await keyRequest({ action: "save", provider: "opencode-go", key: "abcdefghij klmnopqrstuvwxyz" });
    expect(((await space.json()) as { error: string }).error).toBe("That doesn't look like an API key.");

    const short = await keyRequest({ action: "save", provider: "opencode-go", key: "short-key" });
    expect(((await short.json()) as { error: string }).error).toBe("That doesn't look like an API key.");

    const unknownAction = await keyRequest({ action: "nope" });
    expect(unknownAction.status).toBe(400);
    expect(((await unknownAction.json()) as { error: string }).error).toBe("Unknown action.");
    expect(never.spy).not.toHaveBeenCalled();
    expect(await store.aiKey(USER_ID)).toBeNull();
  });
});

// ---- 6. A key that can't be opened is no key at all ----------------------------------------------------------------

describe("a key sealed with another secret", () => {
  it("is unreadable, and the account never falls back to the website's key", async () => {
    const otherSecret = Buffer.alloc(32, 9).toString("base64");
    await store.setAiKey(USER_ID, { provider: "opencode-go", sealedKey: seal(GO_KEY, 100 * 365 * 86_400_000, NOW, otherSecret), savedAt: NOW });
    await store.putSync(USER_ID, encode(appFile()), null, NOW);

    const info = await keyInfo();
    expect(info).toMatchObject({ unreadable: true, saved: null });
    expect(await accessFor({ accountId: USER_ID, admin: false }, store)).toBeNull();

    const never = fakeFetch(() => {
      throw new Error("must not be asked");
    });
    vi.stubGlobal("fetch", never.fn);
    expect(await models()).toMatchObject({ configured: false, needsKey: true });
    const answer = await primerRequest();
    expect(answer.status).toBe(400);
    expect(((await answer.json()) as { error: string }).error).toBe("Add your own AI key under Settings → AI key first.");
    expect(never.spy).not.toHaveBeenCalled();
  });
});

// ---- 7. OpenRouter's own errors, and a 200 that says there is a problem ---------------------------------------------

const input: PrimerInput = {
  deckName: "Vohar Danse",
  commanders: ["Vohar, Vodalian Desecrator"],
  sections: [{ title: "Ramp", cards: [{ name: "Sol Ring", quantity: 1 }] }],
  roles: "Lands 37 · Ramp 11",
  bracket: null,
  power: null,
  combos: [],
  notes: [],
};
const options = (): AiOptions => ({ focus: "CASUAL", length: "SHORT", sections: ["plan"], startFrom: null });

describe("OpenRouter's errors", () => {
  it("says what is wrong without the service's own words", async () => {
    const access: AiAccess = { provider: "openrouter", key: OR_KEY, shared: false };
    const credit = fakeFetch(() => new Response("", { status: 402 }));
    await expect(writePrimer(access, input, options(), "", credit.fn)).rejects.toThrow("OpenRouter says there is no credit left on this key.");

    const refused = fakeFetch(() => new Response("", { status: 403 }));
    await expect(writePrimer(access, input, options(), "", refused.fn)).rejects.toThrow("OpenRouter refused this request; check your key's budget and permissions.");

    // A 200 with an error field and no text: a problem with the model, never the service's message.
    const quiet = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      const problem = fakeFetch(() => jsonBody({ error: { message: "SECRET-TEXT" } }));
      const error = await writePrimer(access, input, options(), "m", problem.fn).then(
        () => null,
        (e: unknown) => e as Error,
      );
      expect(error?.message).toBe("OpenRouter reported a problem with this model; try again or pick another.");
      expect(error?.message).not.toContain("SECRET-TEXT");
      // The diagnostic line keeps to the numbers, and never the service's message either.
      const line = String(quiet.mock.calls[0]?.[0] ?? "");
      expect(line).not.toContain("SECRET-TEXT");
      expect(line).not.toContain(OR_KEY);
    } finally {
      quiet.mockRestore();
    }
  });
});

// ---- 8. OpenRouter's list, and a model id that starts with ~ -------------------------------------------------------

describe("OpenRouter's model list", () => {
  it("drops batch and non-text models, and puts its default first", async () => {
    const access: AiAccess = { provider: "openrouter", key: OR_KEY, shared: false };
    const { fn } = fakeFetch(() =>
      jsonBody({
        data: [
          { id: "zeta/vision", architecture: { output_modalities: ["image"] } },
          { id: "alpha/text", architecture: { output_modalities: ["text"] } },
          { id: "batch/thing:batch" },
          { id: AI_PROVIDERS.openrouter.defaultModel },
          { id: "no-architecture" },
        ],
      }),
    );
    expect(await listModels(access, fn)).toEqual([AI_PROVIDERS.openrouter.defaultModel, "alpha/text", "no-architecture"]);
  });

  it("takes a model id that starts with a tilde on the primer route", async () => {
    await store.setAiKey(USER_ID, { provider: "openrouter", sealedKey: sealAiKey(OR_KEY, NOW), savedAt: NOW });
    await store.putSync(USER_ID, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch(() => chatAnswer("ok"));
    vi.stubGlobal("fetch", fn);

    const answer = await primerWith({ model: "~deepseek/deepseek-v4.1-flash" });
    expect(answer.status).toBe(200);
    expect(((await answer.json()) as { model: string }).model).toBe("~deepseek/deepseek-v4.1-flash");
    expect(sentModel(calls[0])).toBe("~deepseek/deepseek-v4.1-flash");
  });
});

// ---- 9. The route's own checks, and the key helpers -----------------------------------------------------------------

describe("the AI key route", () => {
  it("answers 403 without the website's own header, and 401 without a login", async () => {
    const bare = await keyPost(
      new Request("https://mtgtrader.vercel.app/api/account/aikey", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ action: "remove" }) }),
    );
    expect(bare.status).toBe(403);
    expect(((await bare.json()) as { error: string }).error).toBe("Not allowed.");

    who.current = null;
    const out = await keyRequest({ action: "remove" });
    expect(out.status).toBe(401);
    expect(((await out.json()) as { loggedOut: boolean }).loggedOut).toBe(true);

    const get = await keyGet();
    expect(get.status).toBe(401);
    expect(((await get.json()) as { loggedOut: boolean }).loggedOut).toBe(true);
  });
});

describe("the key helpers", () => {
  it("takes only a key that could be one, and seals it so only this website can read it", () => {
    expect(cleanAiKey(`  ${GO_KEY}  `)).toBe(GO_KEY);
    expect(cleanAiKey("abcdefghij klmnopqrstuvwxyz")).toBeNull();
    expect(cleanAiKey("short")).toBeNull();
    expect(cleanAiKey(42)).toBeNull();
    expect(cleanAiKey("x".repeat(300))).toBe("x".repeat(300));
    expect(cleanAiKey("x".repeat(301))).toBeNull();

    const sealed = sealAiKey(GO_KEY, NOW);
    expect(sealed).not.toContain(GO_KEY);
    expect(openAiKey(sealed, NOW)).toBe(GO_KEY);
    expect(openAiKey(sealed, NOW + 101 * 365 * 86_400_000)).toBeNull();
    // Not a sealed value at all.
    expect(openAiKey("garbage", NOW)).toBeNull();

    expect(websiteAccess()).toEqual({ provider: "opencode-go", key: WEBSITE_KEY, shared: true });
  });
});

// ---- 10. Mistral: the website's own key, an account's own key, and the service's list --------------------------------

describe("the website's own key, when MISTRAL_API_KEY is set", () => {
  it("is Mistral's, and OpenCode Go's only when it is blank", () => {
    process.env.MISTRAL_API_KEY = MISTRAL_KEY;
    expect(websiteAccess()).toEqual({ provider: "mistral", key: MISTRAL_KEY, shared: true });

    // Spaces only: no Mistral key, so the website falls back to its OpenCode Go one.
    process.env.MISTRAL_API_KEY = "   ";
    expect(websiteAccess()).toEqual({ provider: "opencode-go", key: WEBSITE_KEY, shared: true });
  });

  it("writes the owner's primer at Mistral's address, with Mistral's key and no Go session header", async () => {
    process.env.MISTRAL_API_KEY = MISTRAL_KEY;
    asOwner();
    await store.putSync(OWNER_ID, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch(() => chatAnswer("ok"));
    vi.stubGlobal("fetch", fn);

    expect(await primerRequest()).toHaveProperty("status", 200);
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("https://api.mistral.ai/v1/chat/completions");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${MISTRAL_KEY}`);
    // Only Go insists on a session header.
    expect(headerOf(calls[0], "x-opencode-session")).toBeUndefined();
    // The model is the one Mistral starts on; the website's other key never leaves for it.
    expect(sentModel(calls[0])).toBe(AI_PROVIDERS.mistral.defaultModel);
    for (const c of calls) expect(JSON.stringify(c.init)).not.toContain(WEBSITE_KEY);
  });

  it("is still only the owner's: another account with no key can't write a primer", async () => {
    process.env.MISTRAL_API_KEY = MISTRAL_KEY;
    asUser();
    await store.putSync(USER_ID, encode(appFile()), null, NOW);
    const never = fakeFetch(() => {
      throw new Error("must not be asked");
    });
    vi.stubGlobal("fetch", never.fn);

    const answer = await primerRequest();
    expect(answer.status).toBe(400);
    expect(((await answer.json()) as { error: string }).error).toBe("Add your own AI key under Settings → AI key first.");
    expect(never.spy).not.toHaveBeenCalled();
  });
});

describe("an account with a Mistral key", () => {
  it("writes with its own key at Mistral's chat address", async () => {
    await store.setAiKey(USER_ID, { provider: "mistral", sealedKey: sealAiKey(MISTRAL_KEY, NOW), savedAt: NOW });
    await store.putSync(USER_ID, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch(() => chatAnswer("ok"));
    vi.stubGlobal("fetch", fn);

    expect(await primerRequest()).toHaveProperty("status", 200);
    expect(calls[0].url).toBe("https://api.mistral.ai/v1/chat/completions");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${MISTRAL_KEY}`);
    expect(headerOf(calls[0], "x-opencode-session")).toBeUndefined();
    expect(sentModel(calls[0])).toBe(AI_PROVIDERS.mistral.defaultModel);
    for (const c of calls) expect(JSON.stringify(c.init)).not.toContain(WEBSITE_KEY);
  });
});

describe("saving a Mistral key", () => {
  it("checks it with Mistral's model list, and keeps only a key Mistral accepts", async () => {
    // Mistral's model list answers 401 to a wrong key, so nothing is stored.
    const refused = fakeFetch(() => jsonBody({ error: { message: "nope" } }, 401));
    vi.stubGlobal("fetch", refused.fn);
    const bad = await keyRequest({ action: "save", provider: "mistral", key: MISTRAL_KEY });
    expect(bad.status).toBe(400);
    expect(((await bad.json()) as { error: string }).error).toBe("Mistral didn't accept this key.");
    expect(refused.calls[0].url).toBe("https://api.mistral.ai/v1/models");
    expect(headerOf(refused.calls[0], "Authorization")).toBe(`Bearer ${MISTRAL_KEY}`);
    expect(await store.aiKey(USER_ID)).toBeNull();

    const accepted = fakeFetch(() => jsonBody({ data: [{ id: AI_PROVIDERS.mistral.defaultModel }] }));
    vi.stubGlobal("fetch", accepted.fn);
    const saved = await keyRequest({ action: "save", provider: "mistral", key: MISTRAL_KEY });
    expect(saved.status).toBe(200);
    expect(((await saved.json()) as { note: string }).note).toBe("Saved. Mistral accepted the key.");
    expect(accepted.calls[0].url).toBe("https://api.mistral.ai/v1/models");
    expect(headerOf(accepted.calls[0], "Authorization")).toBe(`Bearer ${MISTRAL_KEY}`);
    expect(openAiKey((await store.aiKey(USER_ID))!.sealedKey)).toBe(MISTRAL_KEY);
  });
});

describe("Mistral's model list", () => {
  it("drops the models that can't chat, keeps the rest and puts the default first", async () => {
    const access: AiAccess = { provider: "mistral", key: MISTRAL_KEY, shared: false };
    const { fn, calls } = fakeFetch(() =>
      jsonBody({
        data: [
          { id: "mistral-embed", capabilities: { completion_chat: false } },
          { id: "zeta-chat", capabilities: { completion_chat: true } },
          { id: "no-capabilities" },
          { id: AI_PROVIDERS.mistral.defaultModel, capabilities: { completion_chat: true } },
        ],
      }),
    );
    expect(await listModels(access, fn)).toEqual([AI_PROVIDERS.mistral.defaultModel, "no-capabilities", "zeta-chat"]);
    expect(calls[0].url).toBe("https://api.mistral.ai/v1/models");
  });
});

describe("GET /api/account/aikey with the website's own key", () => {
  it("names the service of that key", async () => {
    process.env.MISTRAL_API_KEY = MISTRAL_KEY;
    expect((await keyInfo()).websiteProvider).toBe("Mistral");

    delete process.env.MISTRAL_API_KEY;
    expect((await keyInfo()).websiteProvider).toBe("OpenCode Go");
  });
});

// ---- 11. The owner's two keys: the service is chosen per primer, and only the owner can choose it ---------------------

/** Both of the website's keys, as the owner's account has them from now on. */
const bothKeys = () => {
  process.env.MISTRAL_API_KEY = MISTRAL_KEY;
  process.env.OPENCODE_API_KEY = WEBSITE_KEY;
};

const bothServices = [
  { id: "mistral", label: "Mistral" },
  { id: "opencode-go", label: "OpenCode Go" },
];

describe("the owner's two services", () => {
  beforeEach(() => {
    // A list kept by another test would answer these instead of the fake fetch.
    forgetModelLists();
  });

  it("starts on Mistral, with every service the owner can pick between", async () => {
    bothKeys();
    asOwner();
    const { fn, calls } = fakeFetch(() => jsonBody({ data: [{ id: "mistral-model" }] }));
    vi.stubGlobal("fetch", fn);

    const list = await models();
    expect(list.service).toBe("mistral");
    expect(list.services).toEqual(bothServices);
    expect(list.models).toEqual(["mistral-model"]);
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("https://api.mistral.ai/v1/models");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${MISTRAL_KEY}`);
  });

  it("asks OpenCode Go's list for ?provider=opencode-go, and falls back to Mistral for a service it doesn't have", async () => {
    bothKeys();
    asOwner();
    const { fn, calls } = fakeFetch((url) =>
      url.includes("opencode.ai") ? jsonBody({ data: [{ id: "go-model" }] }) : jsonBody({ data: [{ id: "mistral-model" }] }),
    );
    vi.stubGlobal("fetch", fn);

    const go = await modelsFor("opencode-go");
    expect(go.service).toBe("opencode-go");
    expect(go.defaultModel).toBe(AI_PROVIDERS["opencode-go"].defaultModel);
    expect(go.models).toContain("go-model");
    expect(go.services).toEqual(bothServices);
    expect(calls[0].url).toBe("https://opencode.ai/inference/go/v1/models");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${WEBSITE_KEY}`);

    // An unknown service is the website's first key's, never a key it doesn't have.
    const unknown = await modelsFor("together");
    expect(unknown.service).toBe("mistral");
    expect(calls[1].url).toBe("https://api.mistral.ai/v1/models");
    expect(headerOf(calls[1], "Authorization")).toBe(`Bearer ${MISTRAL_KEY}`);
  });

  it("keeps each service's list apart, so a second look asks nobody again", async () => {
    bothKeys();
    asOwner();
    const { fn, calls } = fakeFetch((url) =>
      url.includes("opencode.ai") ? jsonBody({ data: [{ id: "go-model" }] }) : jsonBody({ data: [{ id: "mistral-model" }] }),
    );
    vi.stubGlobal("fetch", fn);

    await modelsFor("mistral");
    await modelsFor("opencode-go");
    expect(calls).toHaveLength(2);

    const again = fakeFetch(() => {
      throw new Error("both lists are kept");
    });
    vi.stubGlobal("fetch", again.fn);
    const mistral = await modelsFor("mistral");
    const go = await modelsFor("opencode-go");
    expect(mistral.models).toEqual(["mistral-model"]);
    expect(mistral.service).toBe("mistral");
    expect(go.models).toContain("go-model");
    expect(go.models).not.toContain("mistral-model");
    expect(go.service).toBe("opencode-go");
    expect(again.spy).not.toHaveBeenCalled();
  });

  it("writes a primer at the service the dialog names, with that service's key", async () => {
    bothKeys();
    asOwner();
    await store.putSync(OWNER_ID, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch(() => chatAnswer("ok"));
    vi.stubGlobal("fetch", fn);

    const go = await primerWith({ provider: "opencode-go", model: AI_PROVIDERS["opencode-go"].defaultModel });
    expect(go.status).toBe(200);
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("https://opencode.ai/inference/go/openai/v1/chat/completions");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${WEBSITE_KEY}`);
    expect(headerOf(calls[0], "x-opencode-session")).toMatch(/^primer-/);
    expect(sentModel(calls[0])).toBe(AI_PROVIDERS["opencode-go"].defaultModel);
    // Mistral's key never leaves for Go.
    for (const c of calls) expect(JSON.stringify(c.init)).not.toContain(MISTRAL_KEY);

    const mistral = await primerWith({ provider: "mistral" });
    expect(mistral.status).toBe(200);
    expect(calls).toHaveLength(2);
    expect(calls[1].url).toBe("https://api.mistral.ai/v1/chat/completions");
    expect(headerOf(calls[1], "Authorization")).toBe(`Bearer ${MISTRAL_KEY}`);
    expect(headerOf(calls[1], "x-opencode-session")).toBeUndefined();
    expect(sentModel(calls[1])).toBe(AI_PROVIDERS.mistral.defaultModel);
    // Go's own request never carried Mistral's key, and Mistral's never carries Go's.
    expect(JSON.stringify(calls[0].init)).not.toContain(MISTRAL_KEY);
    expect(JSON.stringify(calls[1].init)).not.toContain(WEBSITE_KEY);
  });

  it("refuses a service the website has no key for, and asks nobody", async () => {
    delete process.env.MISTRAL_API_KEY;
    process.env.OPENCODE_API_KEY = WEBSITE_KEY;
    asOwner();
    await store.putSync(OWNER_ID, encode(appFile()), null, NOW);
    const never = fakeFetch(() => {
      throw new Error("must not be asked");
    });
    vi.stubGlobal("fetch", never.fn);

    const answer = await primerWith({ provider: "mistral" });
    expect(answer.status).toBe(400);
    expect(((await answer.json()) as { error: string }).error).toBe("That AI service isn't set up on this website.");
    expect(never.spy).not.toHaveBeenCalled();
  });

  it("gives another account no website key, whatever service it names", async () => {
    bothKeys();
    asUser();
    await store.putSync(USER_ID, encode(appFile()), null, NOW);
    const never = fakeFetch(() => {
      throw new Error("must not be asked");
    });
    vi.stubGlobal("fetch", never.fn);

    for (const service of ["mistral", "opencode-go"]) {
      const answer = await primerWith({ provider: service });
      expect(answer.status).toBe(400);
      expect(((await answer.json()) as { error: string }).error).toBe("Add your own AI key under Settings → AI key first.");
    }
    expect((await modelsFor("mistral")).configured).toBe(false);
    expect(never.spy).not.toHaveBeenCalled();
  });

  it("keeps another account on its own key when it names a service", async () => {
    bothKeys();
    asUser();
    await store.setAiKey(USER_ID, { provider: "openrouter", sealedKey: sealAiKey(OR_KEY, NOW), savedAt: NOW });
    await store.putSync(USER_ID, encode(appFile()), null, NOW);
    const { fn, calls } = fakeFetch((url) => (url.endsWith("/models") ? jsonBody({ data: [{ id: "other/model" }] }) : chatAnswer("ok")));
    vi.stubGlobal("fetch", fn);

    const list = await modelsFor("mistral");
    expect(list.service).toBe("openrouter");
    expect(list.services).toEqual([]);
    expect(calls[0].url).toBe("https://openrouter.ai/api/v1/models");
    expect(headerOf(calls[0], "Authorization")).toBe(`Bearer ${OR_KEY}`);

    const answer = await primerWith({ provider: "mistral", model: "other/model" });
    expect(answer.status).toBe(200);
    const chat = calls.find((c) => c.url.includes("/chat/completions"))!;
    expect(chat.url).toBe("https://openrouter.ai/api/v1/chat/completions");
    expect(headerOf(chat, "Authorization")).toBe(`Bearer ${OR_KEY}`);
    for (const c of calls) {
      expect(JSON.stringify(c.init)).not.toContain(MISTRAL_KEY);
      expect(JSON.stringify(c.init)).not.toContain(WEBSITE_KEY);
    }
  });

  it("names both of the website's services on the AI key page", async () => {
    bothKeys();
    expect((await keyInfo()).websiteProviders).toEqual(["Mistral", "OpenCode Go"]);
  });
});
