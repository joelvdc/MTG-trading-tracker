import { cookies } from "next/headers";
import { currentAccount, fromOurPages, json } from "@/lib/auth";
import { revoke } from "@/lib/nextcloud";
import { accountStore } from "@/lib/accounts";
import { isUser, SESSION_COOKIE } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Logs out here: an account's device is forgotten; a Nextcloud login's app password is cancelled. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (a && isUser(a)) await accountStore()?.removeDevice(a.device);
  else if (a) await revoke(a);
  (await cookies()).delete(SESSION_COOKIE);
  return json({ ok: true });
}
