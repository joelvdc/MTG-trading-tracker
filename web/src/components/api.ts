// Small helpers for talking to the website's own API from the browser.

export interface ApiAnswer<T> {
  ok: boolean;
  status: number;
  data: T & { error?: string; loggedOut?: boolean };
}

// The last good answer per GET address, kept in memory while the website stays open (moving between pages with the
// top bar no longer reloads it). A page shows it at once and swaps in the fresh answer when that arrives. An edit
// (a POST) doesn't throw those answers away: it marks them old, so pages still open instantly with what they had and
// the fresh answer takes their place a moment later.
const MAX_AGE_MS = 60 * 60_000;
const memo = new Map<string, { answer: ApiAnswer<unknown>; at: number; stale: boolean }>();
const running = new Map<string, Promise<ApiAnswer<unknown>>>();
let generation = 0;

/** The last good answer for [path], if there is a recent one (even one an edit marked old); null otherwise. */
export function peek<T>(path: string): ApiAnswer<T> | null {
  const m = memo.get(path);
  return m && Date.now() - m.at < MAX_AGE_MS ? (m.answer as ApiAnswer<T>) : null;
}

/** Keeps [data] as the latest answer for [path] (a page's own state after an edit). */
export function remember(path: string, data: unknown): void {
  memo.set(path, { answer: { ok: true, status: 200, data: data as ApiAnswer<unknown>["data"] }, at: Date.now(), stale: false });
}

/** Marks every kept answer old, without dropping it (a page still shows it at once and the fresh answer replaces it). */
function markStale(): void {
  generation++;
  running.clear();
  for (const m of memo.values()) m.stale = true;
}

/** Drops every kept answer, and lets loads that are still running finish without being kept or shared. */
export function forget(): void {
  generation++;
  memo.clear();
  running.clear();
}

/** Starts loading [paths] in the background (when a link is about to be pressed), unless they're fresh or loading. */
export function prefetch(paths: string[]): void {
  for (const p of paths) {
    const m = memo.get(p);
    if (!running.has(p) && !(m && !m.stale && Date.now() - m.at < 15_000)) void api(p);
  }
}

export async function api<T>(path: string, body?: unknown): Promise<ApiAnswer<T>> {
  if (typeof window === "undefined") return request<T>(path, body); // never share answers on the server
  if (body !== undefined) {
    markStale();
    const r = await request<T>(path, body);
    markStale();
    return r;
  }
  const already = running.get(path);
  if (already) return already as Promise<ApiAnswer<T>>;
  const gen = generation;
  const p = request<T>(path).then((r) => {
    if (running.get(path) === p) running.delete(path);
    const d = r.data as { loggedIn?: boolean; loggedOut?: boolean; noFolder?: boolean };
    if (r.ok && gen === generation && d.loggedIn !== false && !d.loggedOut && !d.noFolder) memo.set(path, { answer: r, at: Date.now(), stale: false });
    return r;
  });
  running.set(path, p);
  return p;
}

/** Every GET the section pages make when they open, loaded one after another once the website is idle. */
const WARM = ["/api/session", "/api/collection", "/api/collection/views", "/api/decks", "/api/wishlist", "/api/scans",
  "/api/games", "/api/orders", "/api/trades", "/api/tradebinder"];
let warmed = false;

/** Once per visit, after the page has loaded: fetches the other sections' answers so opening them is instant. */
export function warmUp(): void {
  if (typeof window === "undefined" || warmed) return;
  warmed = true;
  // Let the page finish what it is doing first: when the browser is idle, or after a moment anyway.
  const w = window as unknown as { requestIdleCallback?: (run: () => void, opts?: { timeout: number }) => void };
  const start = () => void warmSections();
  if (typeof w.requestIdleCallback === "function") w.requestIdleCallback(start, { timeout: 3000 });
  else setTimeout(start, 1500);
}

/** Fetches WARM one path after another, waiting for the page's own loads first and stopping if the session is gone. */
async function warmSections(): Promise<void> {
  const deadline = Date.now() + 10_000;
  while (running.size > 0 && Date.now() < deadline) await wait(300);
  for (const path of WARM) {
    const m = memo.get(path);
    if (m && loggedOut(m.answer)) return;
    if (running.has(path) || (m && !m.stale && Date.now() - m.at < 60_000)) continue;
    try {
      const r = await api<{ loggedIn?: boolean }>(path);
      if (loggedOut(r)) return;
    } catch {
      // A section that can't be loaded now is loaded again when it is opened.
    }
  }
}

function loggedOut(answer: ApiAnswer<unknown>): boolean {
  const d = answer.data as { loggedIn?: boolean } | null | undefined;
  return !!d && d.loggedIn === false;
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function request<T>(path: string, body?: unknown): Promise<ApiAnswer<T>> {
  const init: RequestInit =
    body === undefined
      ? {}
      : {
          method: "POST",
          headers: { "Content-Type": "application/json", "X-Requested-With": "mtgtrader" },
          body: JSON.stringify(body),
        };
  try {
    const res = await fetch(path, init);
    let data: unknown = {};
    try {
      data = await res.json();
    } catch {
      data = {};
    }
    return { ok: res.ok, status: res.status, data: data as ApiAnswer<T>["data"] };
  } catch {
    return { ok: false, status: 0, data: { error: "No connection to the website." } as ApiAnswer<T>["data"] };
  }
}

const dateFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

export function formatDate(ms: number): string {
  return dateFormat.format(new Date(ms));
}
