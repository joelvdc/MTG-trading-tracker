// What accounts can do: create one (with an invite code, or the first from a Nextcloud login), log in on a device
// (with two-step login when it's on), reset a password (the owner, or the account itself with a link by email), make
// invite codes, and set up an authenticator app.
import {
  type AccountStore,
  checkPassword,
  cleanUsername,
  hashPassword,
  newCode,
  newId,
  newTempPassword,
  newToken,
  normalizeCode,
  passwordProblem,
  tokenHash,
  type Device,
  type TwoFactor,
  type UserAccount,
} from "./accounts";
import { mailReady, type Send } from "./mailer";
import { openAiKey, sealAiKey } from "./aiprimer";
import { newTotpSecret, otpauthUrl, qrSvg, verifyTotp } from "./totp";
import { cleanAddress, newEmailSettings } from "./weekly";
import { newEmailToken, resetMail } from "./weeklyjob";

export class AccountError extends Error {}

/** After this many wrong passwords in a row the account waits [LOCK_MINUTES]. */
export const MAX_FAILURES = 8;
export const LOCK_MINUTES = 15;

/** A "Forgot password?" link works once, for this long; an account gets at most [RESETS_PER_HOUR] an hour. */
export const RESET_AGE_MS = 3_600_000;
export const RESETS_PER_HOUR = 3;

/** The recovery codes a two-step login gets; each works once. */
export const RECOVERY_CODES = 10;

function checkNew(username: string, password: string): string {
  const name = cleanUsername(username);
  if (!name) throw new AccountError("A user name has 3 to 32 letters, digits, dots, dashes or underscores.");
  const p = passwordProblem(password);
  if (p) throw new AccountError(p);
  return name;
}

/**
 * A new account with an invite code (each code works once). [email] is checked first and kept unconfirmed (the sign-up
 * sends its confirmation link); the weekly email starts off: it is turned on in Settings.
 */
export async function register(store: AccountStore, invite: string, username: string, password: string, email: string, now: number): Promise<UserAccount> {
  if (!email.trim()) throw new AccountError("Enter your email address.");
  const address = cleanAddress(email);
  if (!address) throw new AccountError("That doesn't look like an email address.");
  const name = checkNew(username, password);
  if (await store.byName(name)) throw new AccountError("That user name is taken.");
  const code = normalizeCode(invite);
  if (!(await store.invites()).some((i) => i.code === code && i.usedBy === null)) throw new AccountError("That invite code isn't valid (or was used already).");
  const hash = await hashPassword(password);
  if (!(await store.useInvite(code, name, now))) throw new AccountError("That invite code isn't valid (or was used already).");
  const a = await store.create(name, hash, false, now);
  if (!a) throw new AccountError("That user name is taken.");
  await store.setEmailSettings(a.id, { ...newEmailSettings(address, 0, newEmailToken, null), enabled: false });
  return a;
}

/**
 * The account of someone logged in with an allowed Nextcloud login, made without an invite (how the owner starts).
 * The first account made is the admin, who makes invite codes and resets passwords.
 */
export async function createFromNextcloud(store: AccountStore, username: string, password: string, now: number): Promise<UserAccount> {
  const name = checkNew(username, password);
  if (await store.byName(name)) throw new AccountError("That user name is taken.");
  const admin = (await store.count()) === 0;
  const a = await store.create(name, await hashPassword(password), admin, now);
  if (!a) throw new AccountError("That user name is taken.");
  return a;
}

/** The account for a user name and password, counting wrong passwords. */
/** The account for a user name and password, counting wrong passwords. */
export async function checkLogin(store: AccountStore, username: string, password: string, now: number): Promise<UserAccount> {
  const wrong = new AccountError("Wrong user name or password.");
  const name = cleanUsername(username);
  if (!name) throw wrong;
  const a = await store.byName(name);
  if (!a) {
    await hashPassword(password); // About as slow as a real check, so names can't be guessed by timing.
    throw wrong;
  }
  if (a.lockedUntil > now) throw new AccountError(`Too many wrong passwords. Try again in ${Math.ceil((a.lockedUntil - now) / 60000)} minutes.`);
  if (!(await checkPassword(password, a.passwordHash))) {
    const failures = a.failures + 1;
    await store.setFailures(a.id, failures >= MAX_FAILURES ? 0 : failures, failures >= MAX_FAILURES ? now + LOCK_MINUTES * 60000 : 0);
    throw wrong;
  }
  // A right password clears the count; with two-step login on, a wrong code after it clears it again with its own count.
  return { id: a.id, username: a.username, admin: a.admin, createdAt: a.createdAt };
}

/**
 * Two-step login's code for an account whose password was right already: a code from the app, or an unused recovery
 * code (each works once). A wrong code counts as a wrong password and locks the account after [MAX_FAILURES].
 */
export async function checkCode(store: AccountStore, a: UserAccount, code: string, now: number): Promise<boolean> {
  const full = await store.byName(a.username);
  const tf = await store.twoFactor(a.id);
  if (!full) throw new AccountError("Wrong user name or password.");
  if (!tf || !tf.enabled) {
    if (full.failures > 0) await store.setFailures(a.id, 0, 0);
    return true;
  }
  const secret = openAiKey(tf.sealedSecret, now);
  const step = secret ? verifyTotp(secret, code, now, tf.lastStep) : null;
  const hash = tokenHash(normalizeCode(code));
  if (step !== null) {
    await store.setTwoFactor(a.id, { ...tf, lastStep: step });
  } else if (tf.recovery.includes(hash)) {
    // A recovery code (K7QD-M2XP) works once.
    await store.setTwoFactor(a.id, { ...tf, recovery: tf.recovery.filter((h) => h !== hash) });
  } else {
    const failures = full.failures + 1;
    await store.setFailures(a.id, failures >= MAX_FAILURES ? 0 : failures, failures >= MAX_FAILURES ? now + LOCK_MINUTES * 60000 : 0);
    throw new AccountError("Wrong code.");
  }
  if (full.failures > 0) await store.setFailures(a.id, 0, 0);
  return true;
}

/**
 * The account for a user name and password, counting wrong passwords. When the account has two-step login on: no
 * [code] gives "needCode" (nothing is logged in yet); otherwise [code] must be a code from the authenticator app or an
 * unused recovery code. A wrong code counts as a wrong password (same failures and lock).
 */
export async function login(store: AccountStore, username: string, password: string, code: string, now: number): Promise<UserAccount | "needCode"> {
  return (await loginStep(store, username, password, code, now)) ?? "needCode";
}

/**
 * The same for a page that asks for the code in a second try: the password with [checkLogin], then, when two-step login
 * is on, the code with [checkCode]; null when the password is right and the code is still asked for.
 */
export async function loginStep(store: AccountStore, username: string, password: string, code: string, now: number): Promise<UserAccount | null> {
  const a = await checkLogin(store, username, password, now);
  if ((await store.twoFactor(a.id))?.enabled && !code.trim()) return null;
  await checkCode(store, a, code, now);
  return a;
}

/** Logs [a] in on a device (a browser or a phone): the device's id and its secret token (shown once). */
export async function addDevice(store: AccountStore, a: UserAccount, name: string, now: number): Promise<{ id: string; token: string }> {
  const id = newId();
  const { token, hash } = newToken();
  await store.addDevice(id, a.id, hash, name.trim().slice(0, 80) || "Device", now);
  return { id, token };
}

/** The device and account for a phone's token, or null when it was logged out. */
export async function deviceFor(store: AccountStore, token: string, now: number): Promise<{ device: Device; account: UserAccount } | null> {
  const device = await store.deviceByToken(tokenHash(token));
  if (!device) return null;
  const account = await store.byId(device.accountId);
  if (!account) return null;
  await store.touchDevice(device.id, now);
  return { device, account };
}

/**
 * The owner resets someone's password: a one-time password to pass on; every device of that account is logged out and
 * its two-step login turned off (the way back for a lost phone).
 */
export async function resetPassword(store: AccountStore, admin: UserAccount, accountId: number): Promise<string> {
  if (!admin.admin) throw new AccountError("Only the owner can reset passwords.");
  if (!(await store.byId(accountId))) throw new AccountError("No such account.");
  const temp = newTempPassword();
  await store.setPassword(accountId, await hashPassword(temp));
  await store.removeDevices(accountId);
  await store.setTwoFactor(accountId, null);
  return temp;
}

/**
 * "Forgot password?": a link by email to every confirmed account with that address (nothing is said either way to
 * whoever asks). At most [RESETS_PER_HOUR] links an hour per account; each works once, for [RESET_AGE_MS]. [who] is
 * an email address, or a user name.
 */
export async function requestReset(store: AccountStore, who: string, now: number, send: Send): Promise<void> {
  if (!mailReady()) return;
  let ids: number[];
  if (who.includes("@")) ids = await store.accountsByEmail(cleanAddress(who) ?? who);
  else {
    const a = await store.byName(cleanUsername(who) ?? "");
    const s = a ? await store.emailSettings(a.id) : null;
    ids = a && s && s.confirmed ? [a.id] : [];
  }
  for (const id of ids) {
    if ((await store.passwordResetCount(id, now - RESET_AGE_MS)) >= RESETS_PER_HOUR) continue;
    const a = await store.byId(id);
    const s = await store.emailSettings(id);
    if (!a || !s || !s.confirmed) continue;
    const { token, hash } = newToken();
    await store.addPasswordReset(hash, id, now);
    try {
      await send({ to: s.address, ...resetMail(a.username, token) });
    } catch (e) {
      console.error(`Reset email: account ${id} failed:`, e);
    }
  }
}

/**
 * The "Forgot password?" link's page: a new password. The link works once and for an hour; every device is logged
 * out (the page then asks to log in again). Two-step login stays on.
 */
export async function finishReset(store: AccountStore, token: string, password: string, now: number): Promise<void> {
  const p = passwordProblem(password);
  if (p) throw new AccountError(p);
  const id = token ? await store.usePasswordReset(tokenHash(token), now, RESET_AGE_MS) : null;
  if (id === null) throw new AccountError("This link isn't valid any more. Ask for a new one.");
  await store.setPassword(id, await hashPassword(password));
  await store.removeDevices(id);
  await store.setFailures(id, 0, 0);
}

/** Settings → Two-step login, step 1: a new secret and its QR code (not on yet: [confirmTwoFactor] turns it on). */
export async function startTwoFactor(store: AccountStore, a: UserAccount, now: number): Promise<{ secret: string; otpauth: string; qr: string }> {
  const had = await store.twoFactor(a.id);
  if (had?.enabled) throw new AccountError("Two-step login is on already. Turn it off first to set it up again.");
  const secret = newTotpSecret();
  await store.setTwoFactor(a.id, { sealedSecret: sealAiKey(secret, now), enabled: false, recovery: [], lastStep: 0 });
  const otpauth = otpauthUrl(a.username, secret);
  return { secret, otpauth, qr: await qrSvg(otpauth) };
}

/** Step 2: the app's first code turns it on; the answer is the recovery codes, each to keep and use once. */
export async function confirmTwoFactor(store: AccountStore, a: UserAccount, code: string, now: number): Promise<string[]> {
  const tf = await store.twoFactor(a.id);
  const secret = tf ? openAiKey(tf.sealedSecret, now) : null;
  if (!tf || !secret) throw new AccountError("Start the set-up first.");
  const step = verifyTotp(secret, code, now, tf.lastStep);
  if (step === null) throw new AccountError("Wrong code.");
  const codes = Array.from({ length: RECOVERY_CODES }, () => newCode());
  await store.setTwoFactor(a.id, { ...tf, enabled: true, recovery: codes.map((c) => tokenHash(normalizeCode(c))), lastStep: step });
  return codes;
}

/** Settings → Turn two-step login off: the password, and a code from the app (or a recovery code). */
export async function stopTwoFactor(store: AccountStore, a: UserAccount, password: string, code: string, now: number): Promise<void> {
  const full = await store.byName(a.username);
  if (!full || !(await checkPassword(password, full.passwordHash))) throw new AccountError("The password is wrong.");
  const tf = await store.twoFactor(a.id);
  if (!tf || !tf.enabled) return;
  const secret = openAiKey(tf.sealedSecret, now);
  const step = secret ? verifyTotp(secret, code, now, tf.lastStep) : null;
  const hash = tokenHash(normalizeCode(code));
  const recovery = tf.recovery.includes(hash);
  if (step === null && !recovery) throw new AccountError("Wrong code.");
  await store.setTwoFactor(a.id, null);
}

export async function changePassword(store: AccountStore, a: UserAccount, current: string, next: string, keepDevice: string | null): Promise<void> {
  const full = await store.byName(a.username);
  if (!full || !(await checkPassword(current, full.passwordHash))) throw new AccountError("The current password is wrong.");
  const p = passwordProblem(next);
  if (p) throw new AccountError(p);
  await store.setPassword(a.id, await hashPassword(next));
  for (const d of await store.devices(a.id)) if (d.id !== keepDevice) await store.removeDevice(d.id);
}

export async function makeInvite(store: AccountStore, admin: UserAccount, now: number): Promise<string> {
  if (!admin.admin) throw new AccountError("Only the owner can make invite codes.");
  const code = newCode();
  await store.addInvite(code, admin.id, now);
  return code;
}
