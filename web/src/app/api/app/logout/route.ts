import { json } from "@/lib/auth";
import { needStore, phoneAccount } from "@/lib/accountweb";

export const dynamic = "force-dynamic";

/** The app logs out: its token stops working. */
export async function POST(req: Request) {
  const p = await phoneAccount(req);
  if (p) await needStore().removeDevice(p.device);
  return json({ ok: true });
}
