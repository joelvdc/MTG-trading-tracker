import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { accountStore, checkPassword, cleanUsername, hashPassword, memoryStore, normalizeCode, resetMemoryStore, type AccountStore } from "@/lib/accounts";
import { openAiKey } from "@/lib/aiprimer";
import { type Mail as MailBody } from "@/lib/mailer";
import type { UserSession } from "@/lib/session";
import { editSync, KEEP_DB_BACKUPS, loadSync } from "@/lib/store";
import { encode, parse, wishlist } from "@/lib/syncfile";
import { totpCode } from "@/lib/totp";
import {
  AccountError,
  addDevice,
  changePassword,
  checkLogin,
  confirmTwoFactor,
  createFromNextcloud,
  deviceFor,
  finishReset,
  login,
  loginStep,
  makeInvite,
  MAX_FAILURES,
  register,
  requestReset,
  resetPassword,
  startTwoFactor,
  stopTwoFactor,
} from "@/lib/users";

const sample = () => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));

/** A time that isn't on a 30-second edge, so a code's step is well inside the verify window. */
const NOW = 1_794_300_001_000;
const stepOf = (ms: number) => Math.floor(ms / 1000 / 30);
/** The secret behind an account's two-step login (only readable through SESSION_SECRET, like the AI keys). */
async function secretOf(s: AccountStore, id: number): Promise<string> {
  return openAiKey((await s.twoFactor(id))!.sealedSecret)!;
}
/** A six-digit code that is wrong both at [now] and a second later ("000000" may be a real one meanwhile). */
function notTheCode(secret: string, now: number, used: number, tried: string[] = []): string {
  const steps = [now, now + 1000].flatMap((t) => [0, 1, 2].map((i) => stepOf(t) + i)).filter((st) => st > used);
  const real = [...steps.map((st) => totpCode(secret, st)), ...tried];
  return Array.from({ length: 10 }, (_, i) => String(i).repeat(6)).find((c) => !real.includes(c))!;
}
/** An owner, and an account with an invite code and an email address (with two-step login on when [two]). */
async function withAccount(s: AccountStore, two = false) {
  const owner = await createFromNextcloud(s, "joel", "a long password", 1);
  const kid = await register(s, await makeInvite(s, owner, 2), "kid", "another long one", "Kid@Example.com", 3);
  if (!two) return { owner, kid, codes: [] as string[] };
  await startTwoFactor(s, kid, NOW);
  const codes = await confirmTwoFactor(s, kid, totpCode(await secretOf(s, kid.id), stepOf(NOW)), NOW);
  return { owner, kid, codes };
}

type Sent = MailBody & { to: string };
/** A stand-in for sendMail that keeps what would be sent. */
const fakeSend = () => {
  const sent: Sent[] = [];
  const send = async (m: MailBody) => void sent.push(m as Sent);
  return { sent, send };
};
/** Both Vercel settings, so the website would send email (no test ever sends one). */
const mailOn = () => {
  process.env.RESEND_API_KEY = "re_test";
  process.env.EMAIL_FROM = "MTG Trader <t@example.com>";
};
const mailOff = () => {
  delete process.env.RESEND_API_KEY;
  delete process.env.EMAIL_FROM;
};

describe("passwords", () => {
  it("check the right one only", async () => {
    const h = await hashPassword("correct horse battery");
    expect(await checkPassword("correct horse battery", h)).toBe(true);
    expect(await checkPassword("correct horse batterY", h)).toBe(false);
    expect(h).not.toContain("correct");
  });
  it("user names and invite codes are tidied", () => {
    expect(cleanUsername("  Joel ")).toBe("joel");
    expect(cleanUsername("a")).toBeNull();
    expect(cleanUsername("no spaces")).toBeNull();
    expect(normalizeCode(" k7qd m2xp ")).toBe("K7QD-M2XP");
  });
});

describe("accounts", () => {
  it("the first account from a Nextcloud login is the owner; later ones need an invite, used once", async () => {
    const s = memoryStore();
    const owner = await createFromNextcloud(s, "Joel", "a long password", 1);
    expect(owner).toMatchObject({ username: "joel", admin: true });
    await expect(register(s, "NOPE-NOPE", "kid", "another long one", "kid@example.com", 2)).rejects.toThrow(AccountError);
    const code = await makeInvite(s, owner, 3);
    const kid = await register(s, code.toLowerCase(), "Kid", "another long one", "kid@example.com", 4);
    expect(kid.admin).toBe(false);
    await expect(register(s, code, "kid2", "another long one", "kid@example.com", 5)).rejects.toThrow(/invite/);
    await expect(makeInvite(s, kid, 6)).rejects.toThrow(/owner/);
    await expect(createFromNextcloud(s, "joel", "a long password", 7)).rejects.toThrow(/taken/);
  });

  it("rejects short passwords", async () => {
    await expect(createFromNextcloud(memoryStore(), "joel", "short", 1)).rejects.toThrow(/10 characters/);
  });

  it("waits after too many wrong passwords", async () => {
    const s = memoryStore();
    await createFromNextcloud(s, "joel", "a long password", 1);
    for (let i = 0; i < MAX_FAILURES; i++) await expect(checkLogin(s, "joel", "wrong password", 1000)).rejects.toThrow(/Wrong/);
    await expect(checkLogin(s, "joel", "a long password", 2000)).rejects.toThrow(/Too many/);
    expect((await checkLogin(s, "JOEL", "a long password", 1000 + 16 * 60000)).username).toBe("joel");
    await expect(checkLogin(s, "nobody", "a long password", 1)).rejects.toThrow(/Wrong/);
  });

  it("devices log in with a token; a reset logs every device out", async () => {
    const s = memoryStore();
    const owner = await createFromNextcloud(s, "joel", "a long password", 1);
    const kid = await register(s, await makeInvite(s, owner, 2), "kid", "another long one", "kid@example.com", 3);
    const phone = await addDevice(s, kid, "App · Pixel", 4);
    expect((await deviceFor(s, phone.token, 5))?.account.username).toBe("kid");
    expect(await deviceFor(s, "not-a-real-token-at-all-xxxxxxxx", 5)).toBeNull();
    const temp = await resetPassword(s, owner, kid.id);
    expect(await deviceFor(s, phone.token, 6)).toBeNull();
    expect((await checkLogin(s, "kid", temp, 7)).id).toBe(kid.id);
    await expect(resetPassword(s, kid, owner.id)).rejects.toThrow(/owner/);
  });

  it("changing the password keeps this device and logs out the others", async () => {
    const s = memoryStore();
    const a = await createFromNextcloud(s, "joel", "a long password", 1);
    const here = await addDevice(s, a, "Website", 2);
    const there = await addDevice(s, a, "App", 3);
    await expect(changePassword(s, a, "wrong", "a new long password", here.id)).rejects.toThrow(/current/);
    await changePassword(s, a, "a long password", "a new long password", here.id);
    expect(await deviceFor(s, here.token, 4)).not.toBeNull();
    expect(await deviceFor(s, there.token, 4)).toBeNull();
  });
});

describe("the email asked at sign-up", () => {
  it("is required and saved unconfirmed, with the weekly email off", async () => {
    const s = memoryStore();
    const owner = await createFromNextcloud(s, "joel", "a long password", 1);
    const code = await makeInvite(s, owner, 2);
    // Checked first: nothing else can be wrong enough to hide it.
    await expect(register(s, code, "a", "short", "", 3)).rejects.toThrow("Enter your email address.");
    await expect(register(s, code, "kid", "another long one", "   ", 3)).rejects.toThrow("Enter your email address.");
    await expect(register(s, code, "kid", "another long one", "kid at example", 3)).rejects.toThrow("That doesn't look like an email address.");
    // A rejected sign-up doesn't use up the invite code.
    expect((await s.invites())[0].usedBy).toBeNull();
    const kid = await register(s, code, "kid", "another long one", " Kid@Example.COM ", 4);
    const mail = (await s.emailSettings(kid.id))!;
    expect(mail).toMatchObject({ address: "Kid@Example.COM", confirmed: false, enabled: false, day: 0 });
    expect(mail.confirmToken).toBeTruthy();
    expect(await s.emailAccounts()).toEqual([]);
    // The owner's account (from Nextcloud) has no address until one is saved.
    expect(await s.emailSettings(owner.id)).toBeNull();
  });

  it("is the weekly email's address: a confirmed one is found, without case", async () => {
    const s = memoryStore();
    const { kid } = await withAccount(s);
    expect(await s.accountsByEmail("kid@example.com")).toEqual([]);
    const mail = (await s.emailSettings(kid.id))!;
    await s.setEmailSettings(kid.id, { ...mail, confirmed: true });
    expect(await s.accountsByEmail("kid@EXAMPLE.com")).toEqual([kid.id]);
    expect(await s.accountsByEmail("other@example.com")).toEqual([]);
  });
});

describe("two-step login", () => {
  it("is off until the app's first code confirms it; then 10 recovery codes", async () => {
    const s = memoryStore();
    const owner = await createFromNextcloud(s, "joel", "a long password", 1);
    const kid = await register(s, await makeInvite(s, owner, 2), "kid", "another long one", "kid@example.com", 3);
    const set = await startTwoFactor(s, kid, NOW);
    expect(set.secret).toMatch(/^[A-Z2-7]{32}$/);
    expect(set.otpauth).toContain(`secret=${set.secret}`);
    expect(set.otpauth).toContain("issuer=MTG%20Trader");
    expect(set.qr.startsWith("<svg")).toBe(true);
    expect(await s.twoFactor(kid.id)).toMatchObject({ enabled: false, recovery: [], lastStep: 0 });
    // Not on yet: the password is enough.
    expect(await login(s, "kid", "another long one", "", NOW)).toMatchObject({ username: "kid" });
    await expect(confirmTwoFactor(s, kid, notTheCode(set.secret, NOW, stepOf(NOW)), NOW)).rejects.toThrow("Wrong code.");
    const codes = await confirmTwoFactor(s, kid, totpCode(set.secret, stepOf(NOW)), NOW);
    expect(codes).toHaveLength(10);
    expect(new Set(codes).size).toBe(10);
    const tf = (await s.twoFactor(kid.id))!;
    expect(tf).toMatchObject({ enabled: true, lastStep: stepOf(NOW) });
    expect(tf.recovery).toHaveLength(10);
    // Neither the secret nor a recovery code is kept in the clear.
    expect(tf.sealedSecret).not.toContain(set.secret);
    expect(tf.recovery).not.toContain(normalizeCode(codes[0]));
    expect(await login(s, "kid", "another long one", "", NOW)).toBe("needCode");
    await expect(startTwoFactor(s, kid, NOW)).rejects.toThrow(/on already/);
  });

  it("can't be confirmed without the set-up", async () => {
    const s = memoryStore();
    const owner = await createFromNextcloud(s, "joel", "a long password", 1);
    const kid = await register(s, await makeInvite(s, owner, 2), "kid", "another long one", "kid@example.com", 3);
    await expect(confirmTwoFactor(s, kid, "123456", NOW)).rejects.toThrow(/Start the set-up/);
    expect(await s.twoFactor(kid.id)).toBeNull();
  });

  it("asks for the code at login; the app's code works once, and so does each recovery code", async () => {
    const s = memoryStore();
    const { kid, codes } = await withAccount(s, true);
    const secret = await secretOf(s, kid.id);
    expect(await login(s, "kid", "another long one", "", NOW)).toBe("needCode");
    // The set-up's code was used up by the confirmation.
    await expect(login(s, "kid", "another long one", totpCode(secret, stepOf(NOW)), NOW)).rejects.toThrow("Wrong code.");
    // The next one works, once.
    const now2 = NOW + 30_000;
    const code = totpCode(secret, stepOf(now2));
    expect(await login(s, "kid", "another long one", code, now2)).toMatchObject({ id: kid.id, username: "kid" });
    await expect(login(s, "kid", "another long one", code, now2)).rejects.toThrow("Wrong code.");
    // A recovery code, typed loosely (spaces, lower case), works once.
    const recovered = await login(s, "kid", "another long one", codes[0].toLowerCase().replace("-", " "), now2 + 30_000);
    expect(recovered).not.toBe("needCode");
    expect(recovered).toMatchObject({ id: kid.id });
    await expect(login(s, "kid", "another long one", codes[0], now2 + 60_000)).rejects.toThrow("Wrong code.");
    expect((await s.twoFactor(kid.id))!.recovery).toHaveLength(9);
    // A page that asks for the code in a second try (loginStep).
    expect(await loginStep(s, "kid", "another long one", "", now2 + 120_000)).toBeNull();
    expect(await loginStep(s, "kid", "another long one", totpCode(secret, stepOf(now2 + 120_000)), now2 + 120_000)).toMatchObject({ id: kid.id });
  });

  it("a wrong code counts as a wrong password, and locks the account like one", async () => {
    const s = memoryStore();
    const { kid } = await withAccount(s, true);
    const secret = await secretOf(s, kid.id);
    // As the routes do it: the password first, then the code, so each wrong code is counted.
    const tried: string[] = [];
    for (let i = 0; i < MAX_FAILURES - 1; i++) {
      const wrong = notTheCode(secret, NOW, stepOf(NOW), tried);
      tried.push(wrong);
      const err = await loginStep(s, "kid", "another long one", wrong, NOW).catch((e) => String(e));
      expect(err).toBe("Error: Wrong code.");
      expect((await s.byName("kid"))!.failures).toBe(i + 1);
    }
    const last = await loginStep(s, "kid", "another long one", notTheCode(secret, NOW, stepOf(NOW), tried), NOW).catch((e) => String(e));
    expect(last).toBe("Error: Wrong code.");
    expect((await s.byName("kid"))!.lockedUntil).toBe(NOW + 15 * 60_000);
    // Locked: even a right code doesn't get in.
    await expect(loginStep(s, "kid", "another long one", totpCode(secret, stepOf(NOW + 60_000)), NOW + 60_000)).rejects.toThrow(/Too many/);
    expect(kid.id).toBeGreaterThan(0);
  });

  it("turns off with the password and a code (from the app or a recovery code)", async () => {
    const s = memoryStore();
    const { kid, codes } = await withAccount(s, true);
    const secret = await secretOf(s, kid.id);
    await expect(stopTwoFactor(s, kid, "wrong password", codes[0], NOW)).rejects.toThrow("The password is wrong.");
    await expect(stopTwoFactor(s, kid, "another long one", notTheCode(secret, NOW, stepOf(NOW)), NOW)).rejects.toThrow("Wrong code.");
    expect((await s.twoFactor(kid.id))!.enabled).toBe(true);
    await stopTwoFactor(s, kid, "another long one", codes[0], NOW);
    expect(await s.twoFactor(kid.id)).toBeNull();
    expect(await login(s, "kid", "another long one", "", NOW)).not.toBe("needCode");
  });
});

describe("forgotten passwords", () => {
  const confirmed = async (s: AccountStore, id: number) => {
    const mail = (await s.emailSettings(id))!;
    await s.setEmailSettings(id, { ...mail, confirmed: true });
  };
  const tokenOf = (m: Sent) => /\/reset\?t=([^\s]+)/.exec(m.text)![1];

  it("sends a link to confirmed accounts only, never says which, and 3 an hour", async () => {
    const s = memoryStore();
    const { kid } = await withAccount(s);
    const { sent, send } = fakeSend();
    mailOn();
    try {
      await requestReset(s, "nobody@example.com", NOW, send);
      // Unconfirmed (a sign-up's address) until its link is clicked; an unknown user name or address: nothing.
      await requestReset(s, "kid@example.com", NOW, send);
      await requestReset(s, "nobody", NOW, send);
      expect(sent).toEqual([]);
      await confirmed(s, kid.id);
      // By email address (any case) and by user name.
      await requestReset(s, "KID@example.com", NOW, send);
      await requestReset(s, "kid", NOW + 1000, send);
      expect(sent).toHaveLength(2);
      expect(sent[0]).toMatchObject({ to: "Kid@Example.com", subject: "Reset your MTG Trader password" });
      expect(sent[0].text).toMatch(/works once, for one hour/);
      expect(sent[0].text).toMatch(/ignore this email/);
      expect(tokenOf(sent[0])).toMatch(/^[A-Za-z0-9_-]{20,}$/);
      // At most 3 an hour per account.
      await requestReset(s, "kid@example.com", NOW + 2000, send);
      await requestReset(s, "kid@example.com", NOW + 3000, send);
      expect(sent).toHaveLength(3);
      // An hour later is a new hour.
      await requestReset(s, "kid@example.com", NOW + 3_600_001, send);
      expect(sent).toHaveLength(4);
    } finally {
      mailOff();
    }
  });

  it("does nothing while email isn't set up (the answer stays the same)", async () => {
    const s = memoryStore();
    const { kid } = await withAccount(s);
    await confirmed(s, kid.id);
    const { sent, send } = fakeSend();
    await requestReset(s, "kid@example.com", NOW, send);
    expect(sent).toEqual([]);
    expect(await s.passwordResetCount(kid.id, 0)).toBe(0);
  });

  it("a mail the service refuses is logged, not thrown", async () => {
    const s = memoryStore();
    const { kid } = await withAccount(s);
    await confirmed(s, kid.id);
    mailOn();
    const errors = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      await requestReset(s, "kid@example.com", NOW, async () => {
        throw new Error("refused");
      });
      expect(errors).toHaveBeenCalled();
    } finally {
      mailOff();
      errors.mockRestore();
    }
  });

  it("sets the new password once, within the hour, logs every device out and keeps two-step on", async () => {
    const s = memoryStore();
    const { kid } = await withAccount(s, true);
    await confirmed(s, kid.id);
    const phone = await addDevice(s, kid, "App · Pixel", NOW - 5000);
    const { sent, send } = fakeSend();
    mailOn();
    try {
      await requestReset(s, "kid@example.com", NOW, send);
      await requestReset(s, "kid@example.com", NOW + 1000, send);
      const [first, second] = [tokenOf(sent[0]), tokenOf(sent[1])];
      await expect(finishReset(s, "no-such-token-at-all-xxxxxxxxxx", "a brand new password", NOW + 2000)).rejects.toThrow(/isn't valid/);
      await expect(finishReset(s, first, "short", NOW + 2000)).rejects.toThrow(/10 characters/);
      await finishReset(s, first, "a brand new password", NOW + 2000);
      expect(await deviceFor(s, phone.token, NOW + 3000)).toBeNull();
      await expect(checkLogin(s, "kid", "another long one", NOW + 3000)).rejects.toThrow(/Wrong/);
      // Two-step login stays on: the new password alone is "needCode".
      expect(await login(s, "kid", "a brand new password", "", NOW + 3000)).toBe("needCode");
      // The link works once…
      await expect(finishReset(s, first, "another brand new one", NOW + 4000)).rejects.toThrow(/isn't valid/);
      // …and for an hour (an hour exactly still works).
      await expect(finishReset(s, second, "another brand new one", NOW + 1000 + 3_600_001)).rejects.toThrow(/isn't valid/);
      await finishReset(s, second, "another brand new one", NOW + 1000 + 3_600_000);
      expect((await s.byName("kid"))!.failures).toBe(0);
    } finally {
      mailOff();
    }
  });

  it("the owner's reset turns two-step login off (the way back for a lost phone)", async () => {
    const s = memoryStore();
    const { owner, kid, codes } = await withAccount(s, true);
    expect(codes).toHaveLength(10);
    const temp = await resetPassword(s, owner, kid.id);
    expect(await s.twoFactor(kid.id)).toBeNull();
    expect(await login(s, "kid", temp, "", NOW)).not.toBe("needCode");
  });
});

describe("an account's sync file", () => {
  it("saves only over the version it read (like Nextcloud's ETag)", async () => {
    const s = memoryStore();
    const bytes = new Uint8Array([1, 2, 3]);
    expect(await s.putSync(1, bytes, null, 1)).toBe(1);
    expect(await s.putSync(1, bytes, null, 2)).toBeNull();
    expect(await s.putSync(1, bytes, 1, 3)).toBe(2);
    expect(await s.putSync(1, bytes, 1, 4)).toBeNull();
  });

  it("keeps one backup a day, the newest ten", async () => {
    const s = memoryStore();
    for (let d = 1; d <= 12; d++) await s.backupOnce(1, `2026-11-${String(d).padStart(2, "0")}`, new Uint8Array([d]), 10);
    await s.backupOnce(1, "2026-11-12", new Uint8Array([99]), 10);
    const days = await s.backups(1);
    expect(days).toHaveLength(10);
    expect(days[0]).toBe("2026-11-12");
    expect((await s.backup(1, "2026-11-12"))?.[0]).toBe(12);
  });

  it("the website keeps only the newest three database backups", async () => {
    process.env.DATABASE_URL = "memory";
    resetMemoryStore();
    const store = accountStore()!;
    const a = await createFromNextcloud(store, "joel", "a long password", 1);
    await store.putSync(a.id, encode(sample()), null, 1);
    const session: UserSession = { kind: "user", accountId: a.id, username: "joel", admin: true, device: "x" };
    for (let d = 1; d <= 4; d++) await editSync(session, () => undefined, () => Date.UTC(2026, 10, d));
    expect(await store.backups(a.id)).toEqual(["2026-11-04", "2026-11-03", "2026-11-02"]);
    expect(KEEP_DB_BACKUPS).toBe(3);
  });

  it("the website's edits go to the account's file, redone when a phone saved in between", async () => {
    process.env.DATABASE_URL = "memory";
    resetMemoryStore();
    const store = accountStore()!;
    const a = await createFromNextcloud(store, "joel", "a long password", 1);
    await store.putSync(a.id, encode(sample()), null, 1);
    const session: UserSession = { kind: "user", accountId: a.id, username: "joel", admin: true, device: "x" };
    const before = wishlist((await loadSync(session)).file).length;
    let tries = 0;
    // A phone saves while the website is editing (only the first time): the website's save must fail and be redone.
    const put = store.putSync.bind(store);
    let phoneSaved = false;
    store.putSync = async (id, bytes, ifVersion, now) => {
      if (!phoneSaved) {
        phoneSaved = true;
        const cur = (await store.getSync(id))!;
        await put(id, cur.bytes, cur.version, 2);
      }
      return put(id, bytes, ifVersion, now);
    };
    await editSync(
      session,
      (file) => {
        tries++;
        const items = wishlist(file);
        items.splice(0, 1);
        (file.data as Record<string, unknown>).wishlist = items;
      },
      () => 3,
    );
    expect(tries).toBe(2);
    expect(wishlist((await loadSync(session)).file).length).toBe(before - 1);
    expect(await store.backups(a.id)).toEqual(["1970-01-01"]);
  });
});

beforeEach(() => {
  delete process.env.DATABASE_URL;
  // Sealing the two-step secret (like the AI keys) needs the website's key.
  process.env.SESSION_SECRET = Buffer.alloc(32, 7).toString("base64");
  mailOff();
  resetMemoryStore();
});
