import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { needStore } from "@/lib/accountweb";
import { MailError, mailReady, NOT_READY, sendMail } from "@/lib/mailer";
import { isUser } from "@/lib/session";
import { cleanAddress, newEmailSettings } from "@/lib/weekly";
import { buildWeekly, confirmMail, defaultWeeklyDeps, newEmailToken, stopUrl } from "@/lib/weeklyjob";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

/** Settings → Weekly email: the address, whether it's confirmed, on or off, the weekday; and whether email works. */
export async function GET() {
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const s = await needStore().emailSettings(a.accountId);
    return json({ settings: s ? { address: s.address, confirmed: s.confirmed, enabled: s.enabled, day: s.day, lastSent: s.lastSent } : null, mailReady: mailReady() });
  } catch (e) {
    return errorResponse(e);
  }
}

/**
 * {action: "save", address, day, enabled}: keeps the settings; a new address gets a confirmation email first.
 * {action: "test"}: sends this week's email now (to a confirmed address). {action: "remove"}: forgets it all.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const store = needStore();
    const body = (await req.json().catch(() => ({}))) as { action?: string; address?: string; day?: number; enabled?: boolean };
    const had = await store.emailSettings(a.accountId);
    if (body.action === "remove") {
      await store.setEmailSettings(a.accountId, null);
      return json({ ok: true });
    }
    if (body.action === "save") {
      const address = cleanAddress(String(body.address ?? ""));
      if (!address) return json({ error: "That doesn't look like an email address." }, 400);
      const day = Number(body.day);
      if (!Number.isInteger(day) || day < 0 || day > 6) return json({ error: "Pick a day of the week." }, 400);
      const s = { ...newEmailSettings(address, day, newEmailToken, had), enabled: body.enabled !== false };
      await store.setEmailSettings(a.accountId, s);
      if (s.confirmed) return json({ ok: true, confirmed: true });
      if (!mailReady()) return json({ ok: true, confirmed: false, note: `Saved. ${NOT_READY} The confirmation email goes out once it is: save again then.` });
      await sendMail({ to: address, ...confirmMail(a.username, address, s.confirmToken!, day) });
      return json({ ok: true, confirmed: false, note: `A confirmation email went to ${address}. Click its link to start the weekly email.` });
    }
    if (body.action === "test") {
      if (!had) return json({ error: "Save an email address first." }, 400);
      if (!had.confirmed) return json({ error: "Confirm the address first (the link in the confirmation email)." }, 400);
      if (!mailReady()) return json({ error: NOT_READY }, 409);
      const built = await buildWeekly(store, a.accountId, had, defaultWeeklyDeps(), Date.now());
      if (!built) return json({ error: "There's no collection to report on yet." }, 404);
      // A test doesn't move "last week" on: the real email still compares with the last real one.
      await sendMail({ to: had.address, ...built.mail, subject: `[Test] ${built.mail.subject}`, unsubscribe: stopUrl(had) });
      return json({ ok: true, note: `Sent to ${had.address}.` });
    }
    return json({ error: "Unknown action." }, 400);
  } catch (e) {
    if (e instanceof MailError) return json({ error: e.message }, 502);
    return errorResponse(e);
  }
}
