import { cookies } from "next/headers";
import { currentAccount, fromOurPages, json } from "@/lib/auth";
import { accountStore } from "@/lib/accounts";
import { isUser, SESSION_COOKIE } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Logs out here: the account's device is forgotten (an old Nextcloud login cookie is just dropped). */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (a && isUser(a)) await accountStore()?.removeDevice(a.device);
  (await cookies()).delete(SESSION_COOKIE);
  return json({ ok: true });
}
