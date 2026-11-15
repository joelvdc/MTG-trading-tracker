// Small helpers for talking to the website's own API from the browser.

export interface ApiAnswer<T> {
  ok: boolean;
  status: number;
  data: T & { error?: string; loggedOut?: boolean };
}

// The last good answer per GET address, kept in memory while the website stays open (moving between pages with the
// top bar no longer reloads it). A page shows it at once and swaps in the fresh answer when that arrives. Any change
// (a POST) empties it, so nothing older than an edit is ever shown; the page then puts its own new data back.
const MAX_AGE_MS = 60 * 60_000;
const memo = new Map<string, { answer: ApiAnswer<unknown>; at: number }>();
const running = new Map<string, Promise<ApiAnswer<unknown>>>();
let generation = 0;

/** The last good answer for [path], if there is a recent one; null otherwise. */
export function peek<T>(path: string): ApiAnswer<T> | null {
  const m = memo.get(path);
  return m && Date.now() - m.at < MAX_AGE_MS ? (m.answer as ApiAnswer<T>) : null;
}

/** Keeps [data] as the latest answer for [path] (a page's own state after an edit). */
export function remember(path: string, data: unknown): void {
  memo.set(path, { answer: { ok: true, status: 200, data: data as ApiAnswer<unknown>["data"] }, at: Date.now() });
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
    if (!running.has(p) && !(m && Date.now() - m.at < 15_000)) void api(p);
  }
}

export async function api<T>(path: string, body?: unknown): Promise<ApiAnswer<T>> {
  if (typeof window === "undefined") return request<T>(path, body); // never share answers on the server
  if (body !== undefined) {
    forget();
    const r = await request<T>(path, body);
    forget();
    return r;
  }
  const already = running.get(path);
  if (already) return already as Promise<ApiAnswer<T>>;
  const gen = generation;
  const p = request<T>(path).then((r) => {
    if (running.get(path) === p) running.delete(path);
    const d = r.data as { loggedIn?: boolean; loggedOut?: boolean; noFolder?: boolean };
    if (r.ok && gen === generation && d.loggedIn !== false && !d.loggedOut && !d.noFolder) memo.set(path, { answer: r, at: Date.now() });
    return r;
  });
  running.set(path, p);
  return p;
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
