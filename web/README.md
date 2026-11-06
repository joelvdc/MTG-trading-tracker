# MTG Trader website

The website at [mtgtrader.vercel.app](https://mtgtrader.vercel.app): the wishlist, collection (with stats and CSV
export) and trades (view only) of the MTG Trader app, from a computer. Next.js (App Router) on Vercel, project `mtgtrader`, root folder `web/`, region `arn1` (Stockholm).
Pushes to `main` that touch `web/` deploy to production; other branches get a preview address.

## How it works
- **Two ways to hold the data**, both the app's sync file (`sync.json.gz`), edited as one more sync device
  (`writtenBy: "MTG Trader website"`). Every change: read the file with its version (ETag), apply the edit, write it
  back only over that version; if a phone wrote in between, read again and redo the edit (up to 4 times). Unknown
  fields are kept as they are. If the file's `format` is newer than `WEB_FORMAT`, the website only shows the data.
  - **Website accounts** (since step C, `lib/accounts.ts`, `lib/users.ts`): the file lives in the website's own
    database (Neon Postgres, EU, added through Vercel's Storage tab; tables are made on first use). The phones sync
    with it through `/api/sync` (below).
  - **Nextcloud login** (the old way, still there): the website reads and writes the file on the owner's Nextcloud.
- **Accounts:** user name and password (scrypt), 15 minutes' wait after 8 wrong passwords. The first account is made
  from a Nextcloud login under Settings (**Make my account and copy my data**): it copies that sync file in, becomes the
  owner, and keeps the Nextcloud login for a nightly backup. Anyone else needs an invite code from the owner (each works
  once). Forgotten password: the owner resets it under Settings and passes the one-time password on; that logs out every
  device of that account. Each browser and phone is a device with its own random token (only its SHA-256 is stored);
  Settings lists them and can log them out.
- **Phone API** (Bearer token, no cookies): `POST /api/app/login` and `/api/app/register` (`{username, password,
  device[, invite]}` → `{token, username}`), `POST /api/app/logout`. `GET /api/sync` answers the gzip file with
  `ETag: "v<n>"` (304 for a matching `If-None-Match`, 404 when the account has none yet); `PUT /api/sync` saves it with
  `If-Match: "v<n>"` or `If-None-Match: *` (412 if it changed meanwhile, as on Nextcloud), 201/204 with the new ETag.
  Uploads must be a readable sync file, at most 20 MB.
- **Backups:** before the first change of a day a copy is kept: in the database for accounts (the newest 10, also before
  a phone's first save of the day), in `web-backups/sync-YYYY-MM-DD.json.gz` in the sync folder for Nextcloud logins
  (the newest 10). A nightly cron (`/api/cron/backup`, 02:30 UTC) also copies each account's file to its Nextcloud
  `web-backups/` while that backup is on (Settings can stop it).
- **Login:** an account's session, or Nextcloud Login Flow v2, which gives the website its own app password. Both live
  only in an encrypted cookie (AES-256-GCM with `SESSION_SECRET`, 30 days). Logging out removes the device or revokes
  the app password. With Nextcloud, the sync folder is picked after logging in (Settings) and must hold `sync.json.gz`.
- **Prices:** a daily cron (`/api/cron/prices`, 05:30 UTC) shrinks Cardmarket's `price_guide_1.json` and the ECB rates
  into a private Vercel Blob (`prices/latest.json.gz`). Card search uses Scryfall.
- **Same numbers as the app:** the collection is priced like the app's `Pricing` (data/PriceSources.kt): the app's price
  source (Cardmarket, TCGplayer or Card Kingdom, falling back to Cardmarket with "≈"), price type, currency and
  condition adjustment, all read from the synced settings. A second daily cron (`/api/cron/sources`, 06:00 UTC) keeps
  - `prices/cardkingdom.json.gz`: the Card Kingdom copy on the `card-kingdom-prices` branch (the same file the app
    falls back to), all grades;
  - `cards/details.json.gz`: per printing, from Scryfall's `/cards/collection`: colors, color identity, type line, mana
    value, EDHREC rank, release date, Game Changer and the TCGplayer prices (`prices.usd*`, `purchase_uris.tcgplayer`).
    New printings are looked up when the collection page opens (75 at a time, `/api/cards/details`); the cron refreshes
    the oldest entries first for 4 minutes a day.
- **Collection edits** (`lib/collection.ts`) follow the app's rules: a pile that becomes identical to another (card,
  finish, condition, language, binder, signed, altered) is merged into it, with a deletion record for the other; new
  uids are 32 hex characters; prices typed in DKK or USD are kept as typed and converted to euros with the ECB rate.
  Binders can be created and renamed here; deleting, merging and arranging them stays in the app.
- Stats, filters, sorting and the two CSV exports (`lib/colquery.ts`, `lib/csvexport.ts`) copy the app's
  `CollectionStats`, filter sheet, `SortSpec` and `exportCollectionCsv`. Value over time is not shown (its history lives
  on the phone); risers and fallers use Cardmarket's trend against its 30-day average, like the app.
- **Auto categories** (`lib/autocategories.ts`, since 2026-11-06): cards added to local decks, new decks and "Put
  every card in its auto category" use Archidekt's category for the card (its public card search
  `archidekt.com/api/cards/v2/?uids=…`, no login, 60 printings per request, like the app's
  `ArchidektCollectionClient.printings`); without one, the Scryfall tagger tags Archidekt sends along
  (`oracleCard.oTags`/`inheritedTags`: ramp, removal/sweeper/counterspell, draw, tutor, recursion, protection);
  for cards Archidekt doesn't know yet, a Scryfall `otag:` search by name; else the card type. Each printing is kept
  in the database table `card_categories` (60 days; 7 for cards Archidekt didn't know). This is the website's only
  contact with Archidekt (approved by the owner on 2026-11-06).
- `/api/health` answers `syncFormat`, price freshness, the card details and Card Kingdom stores (`cardDetails`,
  `cardKingdomRows`, `cardKingdomListDate`, `cardKingdomFresh`), whether the accounts database answers (`database`,
  `accounts`: how many, no names) and whether the settings are complete (no secrets).

## Settings (Vercel environment variables)
| Name | What |
|---|---|
| `SESSION_SECRET` | 32 random bytes, base64. Changing it logs everyone out. |
| `NEXTCLOUD_SERVER` | The only Nextcloud the website logs in to (`https://cloud.nocar.me`). |
| `ALLOWED_USERS` | Nextcloud user ids allowed in, comma separated. Empty: nobody. |
| `NEXTCLOUD_PASS` | Sent as header `X-MTG-Trader-Pass` to Nextcloud, so Cloudflare rules can let the website through. |
| `CRON_SECRET` | Vercel sends it to the crons. |
| `BLOB_READ_WRITE_TOKEN` | Set by Vercel for the Blob store. |
| `DATABASE_URL` (and the other `PG*`/`POSTGRES_*`) | Set by Vercel for the Neon database (preview and production only). `memory` keeps accounts in memory, for tests and local runs. |

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

**The win turn** (deck page, "Winning the game") is the app's own `WinOdds.kt` compiled to JavaScript, so its numbers
are the phone's. `web/winodds-kt/` is a small Kotlin/JS build: it copies `DeckStats.kt`, `ManaOdds.kt`, `SaltCard.kt`,
`WinOdds.kt` and `AppPowerLevel` from `app/src/main/java/com/mtgtrader/data`, swaps out the few JVM-only parts (threads
become the same four batches one after another, file storage is left out; each swap fails loudly if the app's text
moved) and writes the result to `src/lib/winodds-js/` (committed). **After changing the app's win turn, rebuild it**
from the repository root with JDK 18:
`./gradlew -p web/winodds-kt copyToWeb`, then `npm test` in `web/` (`test/winodds.test.ts` compares it with the app's
answer for a small deck, `test/fixtures/winodds-small.json`; refresh that answer when the model changes on purpose).
The browser runs it in a Web Worker (`src/lib/winodds.worker.ts`) and keeps each deck's result in `localStorage`
until its cards, colours, combos or `WinOddsCalc.VERSION` change.

**When the app's sync format changes** (`Sync.FORMAT` in `app/.../data/Sync.kt`): update `web/src/lib/syncfile.ts`
(`WEB_FORMAT` and the types) in the same release, and refresh the shared sample files:
- `WRITE_WEB_FIXTURES=1 ./gradlew testDebugUnitTest --tests '*WebSyncFixtureTest*'` writes `web/test/fixtures/app-sync.json`;
- `WRITE_WEB_FIXTURES=1 npm test` (in `web/`) writes `web-edited-sync.json`, which the Kotlin test merges like a phone.
