import { needStore } from "@/lib/accountweb";
import { emailPage } from "@/lib/emailpage";
import { DAY_NAMES } from "@/lib/weekly";

export const dynamic = "force-dynamic";

/** The confirmation email's link: the address is confirmed and the weekly email starts. */
export async function GET(req: Request) {
  const t = new URL(req.url).searchParams.get("t") ?? "";
  try {
    const store = needStore();
    const found = t ? await store.emailByToken("confirm", t) : null;
    if (!found) return emailPage("Link not valid", "This confirmation link isn't valid any more. It may have been used already, or a newer address was saved since.", 404);
    const s = { ...found.settings, confirmed: true, confirmToken: null };
    await store.setEmailSettings(found.accountId, s);
    return emailPage("Confirmed", `The weekly email will go to ${s.address} every ${DAY_NAMES[s.day]} morning. Change or stop it any time in Settings, or with the link in every email.`);
  } catch (e) {
    console.error(e);
    return emailPage("Something went wrong", "Try the link again in a moment.", 500);
  }
}
