import { errorResponse, json } from "@/lib/auth";
import type { ValueDay } from "@/lib/accounts";
import { loggedOut, needStore, phoneAccount } from "@/lib/accountweb";
import { cleanDay } from "@/lib/nightly";

export const dynamic = "force-dynamic";

/** At most this many days (about 27 years) and bytes are taken. */
const MAX_DAYS = 10_000;
const MAX_BYTES = 8 * 1024 * 1024;

/**
 * The phone's value history, sent once after logging in with an account (app 1.80), so the website's chart
 * (Stats → Value over time) starts where the phone's does. Days the website saved itself are kept. Binder entries
 * come by binder uid ("b:<uid>:trend"; Unsorted "b:~:trend").
 */
export async function POST(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    const text = await req.text();
    if (text.length > MAX_BYTES) return json({ error: "Too much value history." }, 413);
    const body = JSON.parse(text) as { days?: unknown };
    if (!Array.isArray(body?.days) || body.days.length > MAX_DAYS) return json({ error: "No value history sent." }, 400);
    const days = body.days.map(cleanDay).filter((d): d is ValueDay => d !== null);
    const store = needStore();
    await store.putValueDays(p.account.id, days, false);
    return json({ ok: true, days: days.length, stored: await store.valueDayCount(p.account.id) });
  } catch (e) {
    if (e instanceof SyntaxError) return json({ error: "That isn't value history." }, 400);
    return errorResponse(e);
  }
}
