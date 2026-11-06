import { errorResponse, json } from "@/lib/auth";
import { loggedOut, phoneAccount } from "@/lib/accountweb";
import { priceBlob } from "@/lib/pricestore";

export const dynamic = "force-dynamic";

/**
 * The nightly Cardmarket price file with the ECB rates, for phones logged in to an account (app 1.81): the stored
 * gzipped JSON as it is (about 2 MB instead of Cardmarket's 27 MB), or 304 when the phone already has this version
 * (If-None-Match). The app unzips it itself (PriceGuideRepository.readWebPrices).
 */
export async function GET(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    const sent = req.headers.get("if-none-match");
    const b = await priceBlob(sent);
    if (!b) return json({ error: "The website has no prices yet." }, 404);
    if (b === "same") return new Response(null, { status: 304, headers: { ETag: sent ?? "" } });
    return new Response(b.stream, {
      headers: { "Content-Type": "application/gzip", "Content-Length": String(b.size), ETag: b.etag, "Cache-Control": "private, no-store" },
    });
  } catch (e) {
    return errorResponse(e);
  }
}
