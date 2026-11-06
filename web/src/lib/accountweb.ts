// Shared bits of the account routes: logging a browser in, and the phones' token check.
import { cookies } from "next/headers";
import { accountStore, type AccountStore, type UserAccount } from "./accounts";
import { json } from "./auth";
import { cookieOptions, SESSION_COOKIE, SESSION_DAYS, seal, type UserSession } from "./session";
import { addDevice, deviceFor } from "./users";

export function needStore(): AccountStore {
  const s = accountStore();
  if (!s) throw new Error("The website has no database (DATABASE_URL).");
  return s;
}

/** "Firefox on Windows" from a browser's User-Agent, for the list of logged-in devices. */
export function browserName(ua: string | null): string {
  const u = ua ?? "";
  const b = /Edg\//.test(u) ? "Edge" : /Firefox\//.test(u) ? "Firefox" : /Chrome\//.test(u) ? "Chrome" : /Safari\//.test(u) ? "Safari" : "A browser";
  const os = /Windows/.test(u) ? "Windows" : /Android/.test(u) ? "Android" : /iPhone|iPad/.test(u) ? "iOS" : /Mac OS/.test(u) ? "macOS" : /Linux/.test(u) ? "Linux" : "";
  return os ? `${b} on ${os}` : b;
}

/** Logs this browser in with [a]: a device row, and the cookie that points to it. */
export async function logBrowserIn(store: AccountStore, a: UserAccount, req: Request, now: number): Promise<void> {
  const { id } = await addDevice(store, a, `Website · ${browserName(req.headers.get("user-agent"))}`, now);
  const session: UserSession = { kind: "user", accountId: a.id, username: a.username, admin: a.admin, device: id };
  (await cookies()).set(SESSION_COOKIE, seal(session, SESSION_DAYS * 86_400_000), cookieOptions(SESSION_DAYS * 86_400));
}

/** The account behind a phone's "Authorization: Bearer <token>", or null. */
export async function phoneAccount(req: Request): Promise<{ account: UserAccount; device: string } | null> {
  const m = /^Bearer ([A-Za-z0-9_-]{20,100})$/.exec(req.headers.get("authorization") ?? "");
  const store = accountStore();
  if (!m || !store) return null;
  const found = await deviceFor(store, m[1], Date.now());
  return found ? { account: found.account, device: found.device.id } : null;
}

export const loggedOut = () => json({ error: "Log in again (Settings → Sync).", loggedOut: true }, 401);

/** Reads a JSON body with string fields (anything else becomes ""). */
export async function fields(req: Request, names: string[]): Promise<Record<string, string>> {
  const body = (await req.json().catch(() => ({}))) as Record<string, unknown>;
  return Object.fromEntries(names.map((n) => [n, typeof body[n] === "string" ? (body[n] as string).slice(0, 300) : ""]));
}
