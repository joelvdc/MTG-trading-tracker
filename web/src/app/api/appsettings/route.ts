import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { appSettings, applySettings, type AppSettings } from "@/lib/appsettings";
import { editSync, loadSync } from "@/lib/store";
import { canWrite } from "@/lib/syncfile";

export const dynamic = "force-dynamic";

/** The app's synced settings, as the app reads them. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const { file } = await loadSync(a);
    return json({ settings: appSettings(file), readOnly: !canWrite(file) });
  } catch (e) {
    return errorResponse(e);
  }
}

/** Changes some of them ({ changes: Partial<AppSettings> }); the phones take them at their next sync. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { changes?: Partial<AppSettings> };
    if (!body?.changes || typeof body.changes !== "object") return json({ error: "No change sent." }, 400);
    const file = await editSync(a, (f, now) => {
      applySettings(f, body.changes!, now);
      return f;
    });
    return json({ settings: appSettings(file), readOnly: !canWrite(file) });
  } catch (e) {
    return errorResponse(e);
  }
}
