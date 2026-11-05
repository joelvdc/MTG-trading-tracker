# MTG Trader website

The website at [mtgtrader.vercel.app](https://mtgtrader.vercel.app): the wishlist of the MTG Trader app, edited from a
computer. Next.js (App Router) on Vercel, project `mtgtrader`, root folder `web/`, region `arn1` (Stockholm).
Pushes to `main` that touch `web/` deploy to production; other branches get a preview address.

## How it works
- **No database.** The website reads and writes the app's Nextcloud sync file (`sync.json.gz`) directly, as one more
  sync device (`writtenBy: "MTG Trader website"`). Every change: read the file with its ETag, apply the edit, write it
  back with `If-Match`; if a phone wrote in between, read again and redo the edit (up to 4 times). Unknown fields are
  kept as they are. If the file's `format` is newer than `WEB_FORMAT`, the website only shows the data.
- **Backups:** before the first change of a day, a copy goes to `web-backups/sync-YYYY-MM-DD.json.gz` in the sync
  folder; the newest 10 are kept.
- **Login:** Nextcloud Login Flow v2 gives the website its own app password, kept only in an encrypted cookie
  (AES-256-GCM with `SESSION_SECRET`, 30 days). Logging out revokes it. The sync folder is picked after logging in
  (Settings) and must hold `sync.json.gz`.
- **Prices:** a daily cron (`/api/cron/prices`, 05:30 UTC) shrinks Cardmarket's `price_guide_1.json` and the ECB rates
  into a private Vercel Blob (`prices/latest.json.gz`). Card search uses Scryfall.
- `/api/health` answers `syncFormat`, price freshness and whether the settings are complete (no secrets).

## Settings (Vercel environment variables)
| Name | What |
|---|---|
| `SESSION_SECRET` | 32 random bytes, base64. Changing it logs everyone out. |
| `NEXTCLOUD_SERVER` | The only Nextcloud the website logs in to (`https://cloud.nocar.me`). |
| `ALLOWED_USERS` | Nextcloud user ids allowed in, comma separated. Empty: nobody. |
| `NEXTCLOUD_PASS` | Sent as header `X-MTG-Trader-Pass` to Nextcloud, so Cloudflare rules can let the website through. |
| `CRON_SECRET` | Vercel sends it to the price cron. |
| `BLOB_READ_WRITE_TOKEN` | Set by Vercel for the Blob store. |

## Cloudflare in front of the Nextcloud
The owner's Nextcloud is behind Cloudflare, which challenges or blocks servers. What makes the website work:
- Bot Fight Mode **off** (on the free plan no rule can skip it).
- Custom rule "MTG Trader website" (first in the list): host is `cloud.nocar.me` and header `x-mtg-trader-pass` equals
  the secret → **Skip** managed rules, Super Bot Fight Mode, rate limiting, Security Level, Browser Integrity Check.
  ("All remaining custom rules" can't be ticked together with those.)
- The "Geo filter" rule (blocks outside Denmark; Vercel's servers are in Sweden) has the exception
  `and not any(http.request.headers["x-mtg-trader-pass"][*] eq "<secret>")`.
When Cloudflare still blocks, the website says "Cloudflare … blocked the website" and logs `Cloudflare blocked <method>
<path>: cf-mitigated=…, title=…` (Vercel runtime logs); it retries such a call twice first.

## Working on it
Node 22. In `web/`: `npm ci`, `npm test` (vitest), `npm run typecheck`, `npm run build` (it rewrites `tsconfig.json`;
undo that). `.npmrc` has `legacy-peer-deps=true` because of a vitest peer-dependency clash in npm.

**When the app's sync format changes** (`Sync.FORMAT` in `app/.../data/Sync.kt`): update `web/src/lib/syncfile.ts`
(`WEB_FORMAT` and the types) in the same release, and refresh the shared sample files:
- `WRITE_WEB_FIXTURES=1 ./gradlew testDebugUnitTest --tests '*WebSyncFixtureTest*'` writes `web/test/fixtures/app-sync.json`;
- `WRITE_WEB_FIXTURES=1 npm test` (in `web/`) writes `web-edited-sync.json`, which the Kotlin test merges like a phone.
