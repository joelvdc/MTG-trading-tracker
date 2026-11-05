// Nextcloud: Login Flow v2 (as the app's Nextcloud.kt) and the WebDAV calls for the sync folder.
import { USER_AGENT } from "./config";
import type { Account } from "./session";

const TIMEOUT_MS = 20_000;

export class NextcloudError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

async function call(url: string, init: RequestInit = {}): Promise<Response> {
  try {
    return await fetch(url, {
      ...init,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
      headers: { "User-Agent": USER_AGENT, ...(init.headers ?? {}) },
    });
  } catch (e) {
    throw new NextcloudError(`Nextcloud didn't answer (${(e as Error).name === "TimeoutError" ? "timed out" : "no connection"}).`);
  }
}

function basic(a: Pick<Account, "loginName" | "appPassword">): string {
  return "Basic " + Buffer.from(`${a.loginName}:${a.appPassword}`, "utf8").toString("base64");
}

// ---- Login Flow v2 ----

export interface LoginStart {
  /** The page the owner opens to approve the website. */
  loginUrl: string;
  pollToken: string;
  pollEndpoint: string;
}

export async function startLogin(server: string): Promise<LoginStart> {
  const r = await call(`${server}/index.php/login/v2`, { method: "POST" });
  if (!r.ok) throw new NextcloudError(`Nextcloud refused to start a login (HTTP ${r.status}).`, r.status);
  const j = (await r.json()) as { poll?: { token?: string; endpoint?: string }; login?: string };
  const start = { loginUrl: j.login ?? "", pollToken: j.poll?.token ?? "", pollEndpoint: j.poll?.endpoint ?? "" };
  // Only ever talk to the server we asked (the answer names the addresses to use).
  if (!sameOrigin(start.loginUrl, server) || !sameOrigin(start.pollEndpoint, server) || !start.pollToken) {
    throw new NextcloudError("Nextcloud's login answer pointed somewhere else.");
  }
  return start;
}

export function sameOrigin(url: string, server: string): boolean {
  try {
    return new URL(url).origin === new URL(server).origin;
  } catch {
    return false;
  }
}

/** The login once the owner has approved it, or null while waiting. */
export async function pollLogin(endpoint: string, token: string): Promise<{ server: string; loginName: string; appPassword: string } | null> {
  const r = await call(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ token }).toString(),
  });
  if (r.status === 404) return null;
  if (!r.ok) throw new NextcloudError(`Nextcloud login check failed (HTTP ${r.status}).`, r.status);
  const j = (await r.json()) as { server?: string; loginName?: string; appPassword?: string };
  if (!j.loginName || !j.appPassword) throw new NextcloudError("Nextcloud's login answer was incomplete.");
  return { server: (j.server ?? "").replace(/\/+$/, ""), loginName: j.loginName, appPassword: j.appPassword };
}

/** The user id for WebDAV paths (the login name may be an e-mail address). */
export async function userId(server: string, a: Pick<Account, "loginName" | "appPassword">): Promise<string> {
  const r = await call(`${server}/ocs/v2.php/cloud/user?format=json`, {
    headers: { Authorization: basic(a), "OCS-APIRequest": "true", Accept: "application/json" },
  });
  if (!r.ok) throw new NextcloudError(`Nextcloud didn't accept the login (HTTP ${r.status}).`, r.status);
  const j = (await r.json()) as { ocs?: { data?: { id?: string } } };
  const id = j.ocs?.data?.id;
  if (!id) throw new NextcloudError("Nextcloud didn't say who is logged in.");
  return id;
}

/** Cancels the website's app password on Nextcloud (logging out). */
export async function revoke(a: Account): Promise<void> {
  await call(`${a.server}/ocs/v2.php/core/apppassword`, {
    method: "DELETE",
    headers: { Authorization: basic(a), "OCS-APIRequest": "true" },
  }).catch(() => undefined);
}

// ---- WebDAV in the sync folder ----

/** The address of [name] (a file or folder, may contain "/") inside the account's sync folder. */
export function davUrl(a: Account, name = ""): string {
  const parts = [...a.folder.split("/"), ...name.split("/")].filter(Boolean).map(encodeURIComponent);
  return `${a.server}/remote.php/dav/files/${encodeURIComponent(a.userId)}/${parts.join("/")}`;
}

function auth(a: Account, extra: Record<string, string> = {}): Record<string, string> {
  return { Authorization: basic(a), ...extra };
}

function failed(what: string, r: Response): NextcloudError {
  if (r.status === 401) return new NextcloudError("Nextcloud no longer accepts this login. Log in again.", 401);
  return new NextcloudError(`${what} failed (HTTP ${r.status}).`, r.status);
}

export interface Remote {
  bytes: Uint8Array;
  etag: string | null;
}

/** The file, or null if it isn't there. */
export async function getFile(a: Account, name: string): Promise<Remote | null> {
  const r = await call(davUrl(a, name), { headers: auth(a) });
  if (r.status === 404) return null;
  if (!r.ok) throw failed(`Reading ${name}`, r);
  return { bytes: new Uint8Array(await r.arrayBuffer()), etag: r.headers.get("etag") };
}

export type PutResult = { ok: true; etag: string | null } | { ok: false; conflict: true };

/**
 * Writes the file only if it's still the version with [ifMatch] (or, with null, only if there's no file yet), so
 * a phone's write in the meantime is never overwritten.
 */
export async function putFile(a: Account, name: string, bytes: Uint8Array, ifMatch: string | null): Promise<PutResult> {
  const r = await call(davUrl(a, name), {
    method: "PUT",
    headers: auth(a, { "Content-Type": "application/octet-stream", ...(ifMatch ? { "If-Match": ifMatch } : { "If-None-Match": "*" }) }),
    body: bytes as BodyInit,
  });
  if (r.status === 412) return { ok: false, conflict: true };
  if (!r.ok) throw failed(`Saving ${name}`, r);
  return { ok: true, etag: r.headers.get("etag") };
}

/** Writes a file whatever is there (backups). */
export async function putPlain(a: Account, name: string, bytes: Uint8Array): Promise<void> {
  const r = await call(davUrl(a, name), { method: "PUT", headers: auth(a, { "Content-Type": "application/octet-stream" }), body: bytes as BodyInit });
  if (!r.ok) throw failed(`Saving ${name}`, r);
}

export async function makeFolder(a: Account, name: string): Promise<void> {
  const r = await call(davUrl(a, name), { method: "MKCOL", headers: auth(a) });
  // 405: it's already there.
  if (!r.ok && r.status !== 405) throw failed(`Making the folder ${name}`, r);
}

export async function deleteFile(a: Account, name: string): Promise<void> {
  const r = await call(davUrl(a, name), { method: "DELETE", headers: auth(a) });
  if (!r.ok && r.status !== 404) throw failed(`Deleting ${name}`, r);
}

/** The names of the files directly in [folder] (empty if the folder isn't there). */
export async function listFiles(a: Account, folder: string): Promise<string[]> {
  const r = await call(davUrl(a, folder), {
    method: "PROPFIND",
    headers: auth(a, { Depth: "1", "Content-Type": "application/xml" }),
    body: '<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>',
  });
  if (r.status === 404) return [];
  if (!r.ok) throw failed(`Listing ${folder}`, r);
  return parseListing(await r.text());
}

/** File names from a PROPFIND answer (folders, including the listed one itself, left out). */
export function parseListing(xml: string): string[] {
  const out: string[] = [];
  for (const m of xml.matchAll(/<(?:\w+:)?response\b[\s\S]*?<\/(?:\w+:)?response>/g)) {
    const block = m[0];
    if (/<(?:\w+:)?collection\s*\/>/.test(block)) continue;
    const href = /<(?:\w+:)?href>([^<]+)<\/(?:\w+:)?href>/.exec(block)?.[1];
    if (!href) continue;
    const name = decodeURIComponent(href.replace(/\/+$/, "").split("/").pop() ?? "");
    if (name) out.push(name);
  }
  return out;
}
