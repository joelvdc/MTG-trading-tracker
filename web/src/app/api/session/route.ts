import { currentAccount, json } from "@/lib/auth";

export const dynamic = "force-dynamic";

/** Who is logged in (no secrets), for the pages. */
export async function GET() {
  const a = await currentAccount();
  return json(a ? { loggedIn: true, user: a.userId, folder: a.folder, server: a.server } : { loggedIn: false });
}
