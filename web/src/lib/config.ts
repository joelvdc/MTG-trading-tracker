// Settings from Vercel's environment variables.

/** The only Nextcloud server the website logs in to, e.g. "https://cloud.example.com". */
export function nextcloudServer(): string {
  const s = (process.env.NEXTCLOUD_SERVER ?? "").trim().replace(/\/+$/, "");
  if (!/^https:\/\/[^/\s]+$/.test(s)) throw new Error("NEXTCLOUD_SERVER must be set to https://<host>.");
  return s;
}

/** Nextcloud user ids allowed in (comma separated). Nobody gets in while it's empty. */
export function allowedUsers(): string[] {
  return (process.env.ALLOWED_USERS ?? "")
    .split(",")
    .map((u) => u.trim())
    .filter(Boolean);
}

/** How the website names itself to other sites (Nextcloud shows it as the device; Scryfall asks for one). */
export const USER_AGENT = "MTG Trader website (+https://github.com/joelvdc/MTG-trading-tracker)";

/** The app's default sync folder. */
export const DEFAULT_FOLDER = "MTG Trader";

/** A folder path inside Nextcloud: words separated by "/", no "." or ".." parts. Null if not acceptable. */
export function cleanFolder(folder: string | null | undefined): string | null {
  const parts = (folder ?? "").split("/").map((p) => p.trim()).filter(Boolean);
  if (parts.length === 0 || parts.length > 5) return null;
  if (parts.some((p) => p === "." || p === ".." || p.length > 100 || /[\\\u0000-\u001f]/.test(p))) return null;
  return parts.join("/");
}
