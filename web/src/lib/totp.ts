// Two-step login's codes (RFC 6238, called TOTP): an authenticator app (Google Authenticator, Authy, 1Password…)
// shows a new 6-digit code every 30 seconds from a secret shared once, as a QR code. node:crypto and the qrcode
// package only; the secret and the codes never leave the page and the database.
import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";
import QRCode from "qrcode";

/** Seconds one code is good for, and the issuer the apps show next to the account. */
export const TOTP_PERIOD = 30;
export const TOTP_DIGITS = 6;
export const TOTP_ISSUER = "MTG Trader";

const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

/** RFC 4648 base32, no padding (what authenticator apps expect). */
export function base32Encode(bytes: Uint8Array): string {
  let bits = 0;
  let value = 0;
  let out = "";
  for (const b of bytes) {
    value = (value << 8) | b;
    bits += 8;
    while (bits >= 5) {
      out += ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

/** The bytes of a base32 secret; spaces and case don't matter. Throws on a character outside the alphabet. */
export function base32Decode(s: string): Uint8Array {
  const clean = s.toUpperCase().replace(/\s+/g, "").replace(/=+$/, "");
  const out: number[] = [];
  let bits = 0;
  let value = 0;
  for (const c of clean) {
    const i = ALPHABET.indexOf(c);
    if (i < 0) throw new Error("Not a base32 secret.");
    value = (value << 5) | i;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }
  return new Uint8Array(out);
}

/** 20 random bytes in base32 (160 bits, what apps expect). */
export function newTotpSecret(): string {
  return base32Encode(randomBytes(20));
}

/** The 6-digit code for [step] (the number of 30-second periods since 1 January 1970). */
export function totpCode(secret: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(step)));
  const mac = createHmac("sha1", base32Decode(secret)).update(counter).digest();
  const offset = mac[mac.length - 1] & 0x0f;
  const n = ((mac[offset] & 0x7f) << 24) | ((mac[offset + 1] & 0xff) << 16) | ((mac[offset + 2] & 0xff) << 8) | (mac[offset + 3] & 0xff);
  return String(n % 10 ** TOTP_DIGITS).padStart(TOTP_DIGITS, "0");
}

const stepAt = (now: number) => Math.floor(now / 1000 / TOTP_PERIOD);

/**
 * The time step [code] belongs to, or null when it is wrong or was used already: the steps clock-1, clock, clock+1,
 * but only those newer than [lastStep] (so each code works once, even if two arrive in the same step).
 */
export function verifyTotp(secret: string, code: string, now: number, lastStep: number): number | null {
  const entered = code.replace(/\s+/g, "");
  if (!new RegExp(`^\\d{${TOTP_DIGITS}}$`).test(entered)) return null;
  const clock = stepAt(now);
  const got = Buffer.from(entered, "utf8");
  for (const step of [clock - 1, clock, clock + 1]) {
    if (step <= lastStep) continue;
    const want = Buffer.from(totpCode(secret, step), "utf8");
    if (got.length === want.length && timingSafeEqual(got, want)) return step;
  }
  return null;
}

/** What the QR code holds: the secret and the account's name, for the app to add. */
export function otpauthUrl(username: string, secret: string): string {
  return `otpauth://totp/${TOTP_ISSUER}:${encodeURIComponent(username)}?secret=${encodeURIComponent(secret)}&issuer=${encodeURIComponent(TOTP_ISSUER)}&digits=${TOTP_DIGITS}&period=${TOTP_PERIOD}`;
}

/** The otpauth link as an SVG picture (the QR code the phone scans). */
export async function qrSvg(text: string): Promise<string> {
  return QRCode.toString(text, { type: "svg", margin: 1 });
}
