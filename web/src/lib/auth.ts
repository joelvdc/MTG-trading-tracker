// The logged-in account for a request, and the checks every API route makes.
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { NextcloudError } from "./nextcloud";
import { accountStore } from "./accounts";
import { type Account, isUser, SESSION_COOKIE, unseal } from "./session";
import { AccountError } from "./users";
import { NoAccountFile, NoFolder, NoSyncFile, ReadOnlyFile } from "./store";
import { EditError } from "./wishlist";

export async function currentAccount(): Promise<Account | null> {
  const jar = await cookies();
  const a = unseal<Account>(jar.get(SESSION_COOKIE)?.value);
  if (!a) return null;
  if (isUser(a)) {
    // Still logged in on this device (a password reset or "log out everywhere" removes it), and still that account.
    const store = accountStore();
    const d = store ? await store.device(a.device) : null;
    if (!store || !d || d.accountId !== a.accountId) return null;
    const acc = await store.byId(a.accountId);
    return acc ? { ...a, username: acc.username, admin: acc.admin } : null;
  }
  // An old Nextcloud login (before 2026-11-08): no longer a way in; Nextcloud is only the backup place now.
  return null;
}

/** Requests that change something must come from the website's own pages (a header other sites can't send). */
export function fromOurPages(req: Request): boolean {
  return req.headers.get("x-requested-with") === "mtgtrader";
}

export function json(body: unknown, status = 200): NextResponse {
  return NextResponse.json(body, { status, headers: { "Cache-Control": "no-store" } });
}

/** A short message for the page; details of unexpected errors stay in Vercel's log. */
export function errorResponse(e: unknown): NextResponse {
  if (e instanceof EditError || e instanceof AccountError) return json({ error: e.message }, 400);
  if (e instanceof ReadOnlyFile) return json({ error: e.message, readOnly: true }, 409);
  if (e instanceof NoFolder) return json({ error: e.message, noFolder: true }, 409);
  if (e instanceof NoSyncFile || e instanceof NoAccountFile) return json({ error: e.message, noFile: true }, 404);
  if (e instanceof NextcloudError) return json({ error: e.message, loggedOut: e.status === 401 }, e.status === 401 ? 401 : 502);
  console.error(e);
  return json({ error: "Something went wrong on the website. Try again." }, 500);
}
