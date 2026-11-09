// Scryfall's rate limits (https://scryfall.com/docs/api/rate-limits): /cards/search, /cards/named, /cards/random and
// /cards/collection 2 per second, everything else 10 per second. A 429 (too many requests) shuts the caller out for
// 30 seconds, and ignoring 429s can get the website banned. Every request to api.scryfall.com goes through
// scryFetch: it keeps the spacing per server instance, and while shut out it answers 429 itself without asking.

const SLOW = /^https:\/\/api\.scryfall\.com\/cards\/(search|named|random|collection)\b/;
const SLOW_MS = 550;
const FAST_MS = 120;
const SHUT_OUT_MS = 30_000;

let next = { slow: 0, fast: 0 };
let shutOutUntil = 0;
// Requests wait their turn one after the other (several requests of one instance may run at the same time).
let queue: Promise<void> = Promise.resolve();

const testing = () => process.env.NODE_ENV === "test";
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/** Until when Scryfall has shut this instance out (0 when it hasn't). */
export const scryfallShutOutUntil = () => (shutOutUntil > Date.now() ? shutOutUntil : 0);

/** fetch for api.scryfall.com, keeping to Scryfall's rate limits. */
export async function scryFetch(input: string | URL | Request, init?: RequestInit): Promise<Response> {
  const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
  if (scryfallShutOutUntil()) return busy();
  if (!testing()) {
    const slow = SLOW.test(url);
    const turn = queue.then(async () => {
      const wait = (slow ? next.slow : next.fast) - Date.now();
      if (wait > 0) await sleep(wait);
      const now = Date.now();
      if (slow) next = { slow: now + SLOW_MS, fast: Math.max(next.fast, now + FAST_MS) };
      else next = { ...next, fast: now + FAST_MS };
    });
    queue = turn.catch(() => undefined);
    await turn;
    if (scryfallShutOutUntil()) return busy();
  }
  const r = await globalThis.fetch(input, init);
  if (r.status === 429 && !testing()) {
    const after = Number(r.headers.get("retry-after"));
    shutOutUntil = Date.now() + (Number.isFinite(after) && after > 0 ? after * 1000 : SHUT_OUT_MS);
  }
  return r;
}

function busy(): Response {
  const seconds = Math.max(1, Math.ceil((shutOutUntil - Date.now()) / 1000));
  return new Response(JSON.stringify({ object: "error", status: 429, details: "Too many requests (waiting before asking Scryfall again)" }), {
    status: 429,
    headers: { "Content-Type": "application/json", "Retry-After": String(seconds) },
  });
}

/** For the tests: forget the spacing and any shut-out. */
export function resetScryfallLimit(): void {
  next = { slow: 0, fast: 0 };
  shutOutUntil = 0;
  queue = Promise.resolve();
}
