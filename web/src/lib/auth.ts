// The logged-in account for a request, and the checks every API route makes.
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { allowedUsers, nextcloudServer } from "./config";
import { NextcloudError } from "./nextcloud";
import { type Account, SESSION_COOKIE, unseal } from "./session";
import { NoSyncFile, ReadOnlyFile } from "./store";
import { EditError } from "./wishlist";

export async function currentAccount(): Promise<Account | null> {
  const jar = await cookies();
  const a = unseal<Account>(jar.get(SESSION_COOKIE)?.value);
  if (!a) return null;
  // Still the server and a user allowed today (the settings may have changed since logging in).
  if (a.server !== nextcloudServer() || !allowedUsers().includes(a.userId)) return null;
  return a;
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
  if (e instanceof EditError) return json({ error: e.message }, 400);
  if (e instanceof ReadOnlyFile) return json({ error: e.message, readOnly: true }, 409);
  if (e instanceof NoSyncFile) return json({ error: e.message }, 404);
  if (e instanceof NextcloudError) return json({ error: e.message, loggedOut: e.status === 401 }, e.status === 401 ? 401 : 502);
  console.error(e);
  return json({ error: "Something went wrong on the website. Try again." }, 500);
}
