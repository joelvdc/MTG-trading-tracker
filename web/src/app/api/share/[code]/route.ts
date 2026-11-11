import { errorResponse, json } from "@/lib/auth";
import { needStore } from "@/lib/accountweb";
import { validCode } from "@/lib/shares";
import { shareAnswer } from "@/lib/shareweb";

export const dynamic = "force-dynamic";

type Params = { params: Promise<{ code: string }> };

/** A shared page's data, for anyone with the address (no login): the view, or "off", "gone" or "unknown". */
export async function GET(_req: Request, { params }: Params) {
  const { code } = await params;
  if (!validCode(code)) return json({ state: "unknown" }, 404);
  try {
    const a = await shareAnswer(needStore(), code, Date.now());
    const res = json(a, a.state === "ok" ? 200 : a.state === "unknown" ? 404 : 410);
    res.headers.set("X-Robots-Tag", "noindex, nofollow");
    return res;
  } catch (e) {
    return errorResponse(e);
  }
}
