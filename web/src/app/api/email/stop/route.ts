import { needStore } from "@/lib/accountweb";
import { emailPage } from "@/lib/emailpage";

export const dynamic = "force-dynamic";

const token = (req: Request) => new URL(req.url).searchParams.get("t") ?? "";

/**
 * Every weekly email's "Stop these emails" link: a page with one button. (Mail programs and virus scanners open links
 * by themselves; only the button, or a mail program's one-click unsubscribe, stops the emails.)
 */
export async function GET(req: Request) {
  const t = token(req);
  try {
    const found = t ? await needStore().emailByToken("stop", t) : null;
    if (!found) return emailPage("Link not valid", "This link isn't valid any more; no weekly email is going out for it.", 404);
    if (!found.settings.enabled) return emailPage("Already stopped", "No weekly emails are being sent. Switch them on again any time in Settings → Weekly email.");
    return emailPage("Stop the weekly email?", `No more weekly emails will go to ${found.settings.address}.`, 200, {
      action: `/api/email/stop?t=${encodeURIComponent(t)}`,
      label: "Stop these emails",
    });
  } catch (e) {
    console.error(e);
    return emailPage("Something went wrong", "Try the link again in a moment.", 500);
  }
}

/** The page's button, and mail programs' one-click unsubscribe (List-Unsubscribe-Post). */
export async function POST(req: Request) {
  const t = token(req);
  try {
    const store = needStore();
    const found = t ? await store.emailByToken("stop", t) : null;
    if (!found) return emailPage("Link not valid", "This link isn't valid any more; no weekly email is going out for it.", 404);
    await store.setEmailSettings(found.accountId, { ...found.settings, enabled: false });
    return emailPage("Stopped", "No more weekly emails will be sent. Switch them on again any time in Settings → Weekly email.");
  } catch (e) {
    console.error(e);
    return emailPage("Something went wrong", "Try again in a moment.", 500);
  }
}
