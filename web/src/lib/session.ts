// The login, kept in an encrypted cookie in the owner's browser: the website itself stores no passwords.
// AES-256-GCM with SESSION_SECRET (32 random bytes, base64, a Vercel environment variable).
import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

export interface Account {
  /** "https://cloud.example.com", no trailing slash. */
  server: string;
  /** What the owner logs in with (may be an e-mail address); used for Basic auth. */
  loginName: string;
  /** Nextcloud's user id, used in WebDAV paths (/remote.php/dav/files/<userId>). */
  userId: string;
  /** The app password Nextcloud made for this website (revoked on logout). */
  appPassword: string;
  /** The sync folder, as in the app ("MTG Trader"). */
  folder: string;
}

export interface PendingLogin {
  pollToken: string;
  pollEndpoint: string;
  folder: string;
}

export const SESSION_COOKIE = "mtgt_session";
export const LOGIN_COOKIE = "mtgt_login";
export const SESSION_DAYS = 30;
export const LOGIN_MINUTES = 20;

function key(secret = process.env.SESSION_SECRET): Buffer {
  const k = secret ? Buffer.from(secret, "base64") : Buffer.alloc(0);
  if (k.length !== 32) throw new Error("SESSION_SECRET must be 32 random bytes in base64.");
  return k;
}

/** Encrypts [value] with an expiry; the result is safe to put in a cookie. */
export function seal(value: unknown, ttlMs: number, now = Date.now(), secret?: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(secret), iv);
  const plain = Buffer.from(JSON.stringify({ v: value, exp: now + ttlMs }), "utf8");
  const body = Buffer.concat([cipher.update(plain), cipher.final()]);
  return [iv, cipher.getAuthTag(), body].map((b) => b.toString("base64url")).join(".");
}

/** The value [seal] encrypted, or null if it was changed, made with another key, or has expired. */
export function unseal<T>(token: string | undefined, now = Date.now(), secret?: string): T | null {
  if (!token) return null;
  try {
    const [iv, tag, body] = token.split(".").map((p) => Buffer.from(p, "base64url"));
    if (!iv || !tag || !body || iv.length !== 12 || tag.length !== 16) return null;
    const decipher = createDecipheriv("aes-256-gcm", key(secret), iv);
    decipher.setAuthTag(tag);
    const plain = Buffer.concat([decipher.update(body), decipher.final()]).toString("utf8");
    const { v, exp } = JSON.parse(plain) as { v: T; exp: number };
    return typeof exp === "number" && exp > now ? v : null;
  } catch {
    return null;
  }
}

export const cookieOptions = (maxAgeSeconds: number) => ({
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "lax" as const,
  path: "/",
  maxAge: maxAgeSeconds,
});
