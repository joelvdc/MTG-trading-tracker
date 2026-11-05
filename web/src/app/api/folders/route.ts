import { currentAccount, errorResponse, json } from "@/lib/auth";
import { cleanFolder } from "@/lib/config";
import { browse } from "@/lib/nextcloud";
import { SYNC_FILE } from "@/lib/store";

export const dynamic = "force-dynamic";

/** The folders inside ?path= (empty: the top of the owner's Nextcloud), and whether it holds the app's sync file. */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  const raw = new URL(req.url).searchParams.get("path") ?? "";
  const path = raw.trim() === "" ? "" : cleanFolder(raw);
  if (path === null) return json({ error: "That folder name can't be used." }, 400);
  try {
    const { folders, files } = await browse(a, path);
    return json({ path, folders, hasSync: path !== "" && files.includes(SYNC_FILE) });
  } catch (e) {
    return errorResponse(e);
  }
}
