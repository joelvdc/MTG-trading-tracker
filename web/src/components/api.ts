// Small helpers for talking to the website's own API from the browser.

export interface ApiAnswer<T> {
  ok: boolean;
  status: number;
  data: T & { error?: string; loggedOut?: boolean };
}

export async function api<T>(path: string, body?: unknown): Promise<ApiAnswer<T>> {
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
