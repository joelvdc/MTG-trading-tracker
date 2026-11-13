import { errorResponse, json } from "@/lib/auth";
import { loggedOut, phoneAccount } from "@/lib/accountweb";
import { cardKingdomBlob } from "@/lib/cardkingdom";

export const dynamic = "force-dynamic";

/**
 * The website's Card Kingdom file (lib/cardkingdom.ts CkFile, gzipped JSON) for phones logged in to an account
 * (app 1.93): 304 when the phone already has this version (If-None-Match), 503 when the daily job hasn't refreshed
 * it for two days or there's none yet (the phone then asks Card Kingdom itself). The app reads it with
 * PriceSources.kt readCardKingdomWeb.
 */
export async function GET(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    const sent = req.headers.get("if-none-match");
    const b = await cardKingdomBlob(sent);
    if (!b) return json({ error: "The website has no Card Kingdom prices yet." }, 503);
    if (b === "stale") return json({ error: "The website's Card Kingdom prices are out of date." }, 503);
    if (b === "same") return new Response(null, { status: 304, headers: { ETag: sent ?? "" } });
    return new Response(b.stream, {
      headers: { "Content-Type": "application/gzip", "Content-Length": String(b.size), ETag: b.etag, "Cache-Control": "private, no-store" },
    });
  } catch (e) {
    return errorResponse(e);
  }
}
