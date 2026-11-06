import { currentAccount, json } from "@/lib/auth";
import { accountStore } from "@/lib/accounts";
import { isUser } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Who is logged in (no secrets), for the pages. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ loggedIn: false, accounts: accountStore() !== null });
  // A website account has no folder to pick: "folder" is what the pages show at the top.
  if (isUser(a)) return json({ loggedIn: true, kind: "user", user: a.username, admin: a.admin, folder: `Account: ${a.username}`, accounts: true });
  return json({ loggedIn: true, kind: "nextcloud", user: a.userId, folder: a.folder, server: a.server, accounts: accountStore() !== null });
}
