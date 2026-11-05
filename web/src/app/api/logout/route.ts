import { cookies } from "next/headers";
import { currentAccount, fromOurPages, json } from "@/lib/auth";
import { revoke } from "@/lib/nextcloud";
import { SESSION_COOKIE } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Logs out here and cancels the website's app password on Nextcloud. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (a) await revoke(a);
  (await cookies()).delete(SESSION_COOKIE);
  return json({ ok: true });
}
