// What accounts can do: create one (with an invite code, or the first from a Nextcloud login), log in on a device,
// reset a password (the owner), make invite codes.
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
  type UserAccount,
} from "./accounts";

export class AccountError extends Error {}

/** After this many wrong passwords in a row the account waits [LOCK_MINUTES]. */
export const MAX_FAILURES = 8;
export const LOCK_MINUTES = 15;

function checkNew(username: string, password: string): string {
  const name = cleanUsername(username);
  if (!name) throw new AccountError("A user name has 3 to 32 letters, digits, dots, dashes or underscores.");
  const p = passwordProblem(password);
  if (p) throw new AccountError(p);
  return name;
}

/** A new account with an invite code (each code works once). */
export async function register(store: AccountStore, invite: string, username: string, password: string, now: number): Promise<UserAccount> {
  const name = checkNew(username, password);
  if (await store.byName(name)) throw new AccountError("That user name is taken.");
  const code = normalizeCode(invite);
  if (!(await store.invites()).some((i) => i.code === code && i.usedBy === null)) throw new AccountError("That invite code isn't valid (or was used already).");
  const hash = await hashPassword(password);
  if (!(await store.useInvite(code, name, now))) throw new AccountError("That invite code isn't valid (or was used already).");
  const a = await store.create(name, hash, false, now);
  if (!a) throw new AccountError("That user name is taken.");
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
  if (a.failures > 0) await store.setFailures(a.id, 0, 0);
  return { id: a.id, username: a.username, admin: a.admin, createdAt: a.createdAt };
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

/** The owner resets someone's password: a one-time password to pass on; every device of that account is logged out. */
export async function resetPassword(store: AccountStore, admin: UserAccount, accountId: number): Promise<string> {
  if (!admin.admin) throw new AccountError("Only the owner can reset passwords.");
  if (!(await store.byId(accountId))) throw new AccountError("No such account.");
  const temp = newTempPassword();
  await store.setPassword(accountId, await hashPassword(temp));
  await store.removeDevices(accountId);
  return temp;
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
