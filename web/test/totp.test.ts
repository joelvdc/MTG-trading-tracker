import { describe, expect, it } from "vitest";
import { base32Decode, base32Encode, newTotpSecret, otpauthUrl, qrSvg, totpCode, verifyTotp } from "@/lib/totp";

// The codes an authenticator app shows (RFC 6238, SHA-1), with the secret the RFC uses: "12345678901234567890".
const SECRET = base32Encode(new TextEncoder().encode("12345678901234567890"));
const at = (seconds: number) => seconds * 1000;

describe("two-step codes (TOTP)", () => {
  it("gives the codes of RFC 6238", () => {
    expect(SECRET).toBe("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    expect(totpCode(SECRET, 59 / 30)).toBe("287082");
    expect(totpCode(SECRET, 1111111109 / 30)).toBe("081804");
    expect(totpCode(SECRET, 1234567890 / 30)).toBe("005924");
  });

  it("base32 goes both ways, ignoring spaces and case", () => {
    const bytes = new Uint8Array([0, 1, 2, 250, 255, 128]);
    expect([...base32Decode(base32Encode(bytes))]).toEqual([...bytes]);
    expect([...base32Decode(base32Encode(bytes).toLowerCase().replace(/(.{6})/g, "$1 "))]).toEqual([...bytes]);
    expect(newTotpSecret()).toMatch(/^[A-Z2-7]{32}$/);
    expect(base32Decode(newTotpSecret())).toHaveLength(20);
  });

  it("accepts the steps either side of the exact moment, and each code once", () => {
    const now = at(1111111109);
    const step = Math.floor(1111111109 / 30);
    expect(verifyTotp(SECRET, " 081804 ", now, 0)).toBe(step);
    // A code of the step before or after (the phone and the website a few seconds apart).
    expect(verifyTotp(SECRET, totpCode(SECRET, step - 1), now, 0)).toBe(step - 1);
    expect(verifyTotp(SECRET, totpCode(SECRET, step + 1), now, 0)).toBe(step + 1);
    // Used already: the same code, and the older ones, are refused.
    expect(verifyTotp(SECRET, "081804", now, step)).toBeNull();
    expect(verifyTotp(SECRET, totpCode(SECRET, step - 1), now, step)).toBeNull();
    // Two steps away, a wrong code, and not six digits.
    expect(verifyTotp(SECRET, totpCode(SECRET, step + 2), now, 0)).toBeNull();
    expect(verifyTotp(SECRET, "000000", now, 0)).toBeNull();
    expect(verifyTotp(SECRET, "8180", now, 0)).toBeNull();
    expect(verifyTotp(SECRET, "", now, 0)).toBeNull();
  });

  it("the link an app scans, and its QR code", async () => {
    const url = otpauthUrl("joel", "ABCDEFGH");
    expect(url).toBe("otpauth://totp/MTG Trader:joel?secret=ABCDEFGH&issuer=MTG%20Trader&digits=6&period=30");
    const q = new URL(url);
    expect(q.protocol).toBe("otpauth:");
    expect(q.host).toBe("totp");
    expect(decodeURIComponent(q.pathname)).toBe("/MTG Trader:joel");
    expect(Object.fromEntries(q.searchParams)).toEqual({ secret: "ABCDEFGH", issuer: "MTG Trader", digits: "6", period: "30" });
    expect(otpauthUrl("jo el", "AB")).toContain("MTG Trader:jo%20el");
    const svg = await qrSvg(url);
    expect(svg.startsWith("<svg")).toBe(true);
  });
});
