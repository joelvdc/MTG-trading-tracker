# MTG Trader website

The website at [mtgtrader.vercel.app](https://mtgtrader.vercel.app): the wishlist, collection (with stats and CSV
export) and trades of the MTG Trader app, from a computer. Next.js (App Router) on Vercel, project `mtgtrader`, root folder `web/`, region `arn1` (Stockholm).
Pushes to `main` that touch `web/` deploy to production; other branches get a preview address. Whether `web/` changed
is judged against the last deployed commit (`vercel.json` → `ignoreCommand`, since 2026-11-13; before, only the push's
last commit counted, so a push whose last commit didn't touch `web/` skipped the deploy).

## How it works
- **Two ways to hold the data**, both the app's sync file (`sync.json.gz`), edited as one more sync device
  (`writtenBy: "MTG Trader website"`). Every change: read the file with its version (ETag), apply the edit, write it
  back only over that version; if a phone wrote in between, read again and redo the edit (up to 4 times). Unknown
  fields are kept as they are. If the file's `format` is newer than `WEB_FORMAT`, the website only shows the data.
  - **Website accounts** (since step C, `lib/accounts.ts`, `lib/users.ts`): the file lives in the website's own
    database (Neon Postgres, EU, added through Vercel's Storage tab; tables are made on first use). The phones sync
    with it through `/api/sync` (below).
  - **Nextcloud** is only a backup place since 2026-11-08 (no longer a way to log in; an old Nextcloud login cookie
    counts as logged out). The code that read the file straight from Nextcloud is still in `lib/store.ts` but unused.
- **Accounts:** user name and password (scrypt), 15 minutes' wait after 8 wrong passwords. The owner's account was made
  from the old Nextcloud login (2026-11-06; that way in is gone). Anyone else needs an invite code from the owner (each
  works once). Forgotten password: the owner resets it under Settings and passes the one-time password on; that logs out every
  device of that account. Each browser and phone is a device with its own random token (only its SHA-256 is stored);
  Settings lists them and can log them out.
- **Phone API** (Bearer token, no cookies): `POST /api/app/login` and `/api/app/register` (`{username, password,
  device[, invite]}` → `{token, username}`), `POST /api/app/logout`. `GET /api/sync` answers the gzip file with
  `ETag: "v<n>"` (304 for a matching `If-None-Match`, 404 when the account has none yet); `PUT /api/sync` saves it with
  `If-Match: "v<n>"` or `If-None-Match: *` (412 if it changed meanwhile, as on Nextcloud; 428 with neither header), 201/204 with the new ETag.
  Uploads must be a readable sync file, at most 20 MB.
- **Backups:** before the first change of a day a copy is kept in the database (the newest 10, also before a phone's
  first save of the day). A nightly cron (`/api/cron/backup`, 02:30 UTC) also copies each account's file to
  `<folder>/web-backups/sync-YYYY-MM-DD.json.gz` on Nextcloud (the newest 10) while that backup is on. Settings →
  Nextcloud backup → **Connect Nextcloud** (`/api/account/backup`): Nextcloud's Login Flow v2 in a new tab gives the
  website an app password, kept sealed for the nightly job only (a user in `ALLOWED_USERS`); the folder is made if
  needed and today's copy is written at once. **Stop the Nextcloud copy** forgets it and revokes the app password.
- **Login:** an account's user name and password only. The session lives in an encrypted cookie (AES-256-GCM with
  `SESSION_SECRET`, 30 days). Logging out removes the device.
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
- **Group printings** (website only, `lib/collgroup.ts`, remembered per browser): the collection table shows one line per
  card name after search and filters. One value where every pile agrees, otherwise a summary ("3 printings", "2
  binders", "Mixed", cheapest – dearest price); Qty, Total and Profit are added up, Change only shows when every pile
  has the same. Lines sort by their totals (copies added up, dearest copy, newest date). Clicking a line opens its piles
  under it; its tick box ticks them all. Pages count lines. The picture view keeps one tile per pile.
- **Auto categories** (`lib/autocategories.ts`): cards added to local decks, new decks and "Put every card in its auto
  category" use the website's own table `card_tags` (card name → categories). A daily job (`/api/cron/categories`,
  04:00 UTC) rebuilds it from Scryfall's card tags when it is a week old: one tag search per category (paged), about
  16,000 cards in about 2½ minutes; if a page fails the old table stays. The categories and their order: Ramp (ramp),
  Removal (removal, sweeper, counterspell), Draw (draw), Tutor (tutor), Recursion (recursion), Protection
  (protection). The first decides the card's section, the others go on the deck line as extra categories; deleting a
  category moves its cards to their next one. Cards without a tag go by card type. The website doesn't contact
  Archidekt (owner's decision, 2026-11-06).
- **Nightly job** (`lib/nightly.ts`, `/api/cron/nightly`, 06:30 UTC after the price jobs), for every account with a
  sync file: the day's collection value with the app's own keys (ValueHistory.snapshotValues: every source and price
  type, `pays@cardkingdom`, per binder `b:<uid>:…`, Unsorted `b:~:…`) into `value_history`; and the wishlist's alert
  levels against Cardmarket at the alert price type (PriceAlerts.step; "any printing" via Scryfall's prints search),
  the levels reached in `alert_levels`, the lines in `alert_events` (a year, at most 500). Its last run and counts go to
  `jobs` and `/api/health`. CardTrader is left to the phones. The app (1.80) sends the phone's value history once with
  `POST /api/app/valuehistory` (`{days:[{day, cards, values}]}`; days the website has are kept). Pages: Stats → Value
  over time (`/api/collection/history`), Wishlist → Alert history (`/api/wishlist/alerts`).
- **Trade binder** (`lib/tradebinder.ts`, `/tradebinder`, `/api/tradebinder`): the app's TradeBinderPlanner and
  TradeBinder.apply ported. Its rules and declined suggestions are synced settings (`tradeMax`, `tradeKeepOne`,
  `tradeMinValue`, `tradeKeepBestDecks`, `tradeKeepBestAll`, `tradeSkips` newline-joined; the binder is `tradeBinder`).
- **Scanned cards** (`lib/scans.ts`, `/api/scans`, the collection's **Scanned** chip next to Wishlist, shown while scans
  wait): the app's Scan tab list (the synced `scans`). Fix a scan's printing, finish, condition, language and copies;
  send selected scans to a binder (Unsorted, or a new one; MtgRepository.scansToCollection) or a local deck (or a new
  one; DeckEditor.addScans / fromScans, auto categories), each with "Keep them in the scan list"; discard with Undo
  (the scans come back under new uids). **Check an order** (`lib/packagecheck.ts`, a copy of the app's PackageCheck.kt,
  `/api/scans/check`; the file is read by `/api/orders/read`) compares the scans with an order's file and adds them with
  the order's details (MtgRepository.addCheckedPackage), recording the order. The camera and "To trade" stay in the app.
- **Games** (`/games`, `lib/games.ts` with `lib/gameshared.ts`, `/api/games`): the games the app's life tracker
  recorded (the synced `games`, `players`, `friendDecks`, `pausedGames`). **Games**: the list (search, filter by player
  or deck, 25/50/100 per page), the game window (places, draw, how it was won, note, minutes, turns, who played which
  deck; the app's GameDetailScreen and changeSeat rules), **Add a past game** (20:00 on the day, like the app), delete
  with Undo, drop a paused game, **Export (CSV)** (GameStats.csv). **Stats**: the app's GameStats (decks table with its
  sort orders and "My decks", players, "You against…") plus website extras: tiles (games, your win rate, average game
  and turns), games per month, how games were won, the starting player's win rate against an equal chance, and a
  period choice (all time, 12 months, 3 months). **Players**: add, rename, delete friends (their decks go, their games
  keep their names) and their decks (commander looked up with Scryfall's fuzzy search). Each of your decks' pages has
  a **Games** section (the app's DeckGamesCard). The life tracker itself stays on the phone.
- `/api/health` answers `syncFormat`, price freshness, the card details and Card Kingdom stores (`cardDetails`,
  `cardKingdomRows`, `cardKingdomListDate`, `cardKingdomFresh`), whether the accounts database answers (`database`,
  `accounts`: how many, no names), the auto categories table (`categoryCards`, `categoriesAgeHours`, `categoriesFresh`), the nightly job (`nightly`
  counts, `nightlyAgeHours`, `nightlyFresh`) and whether the settings are complete (no secrets).

- **Shared links** (`lib/shares.ts`, `lib/shareweb.ts`, since app 1.89): Share on the deck page, the game window and
  the Collection page (whole collection, or the binder picked), and in the app (`POST /api/app/share` with
  `{kind, target}`: deck id, game uid, binder uid or `~` for Unsorted, `""` for the collection or the wishlist; the
  wishlist page shows the still-wanted cards with prices, never notes, alerts or orders). One link per item
  (table `share_links`, a random 22-character code); Settings → Shared links switches a link off and on (the same
  address comes back) or deletes it. `/s/<code>` reads `GET /api/share/<code>` (no login; `noindex`): the item as it is
  now, priced in the owner's source and currency with the source stated, never purchase prices, notes or a game's
  note; "off", "gone" or "unknown" otherwise. Each opening is counted.
- **Commander legality** (`lib/legality.ts`, the same rules as the app's `data/DeckLegality.kt`; both are tested against
  `test/fixtures/legality-cases.json`): banned or not legal (Scryfall's `legalities.commander`), outside the
  commanders' colour identity, more copies than allowed (basics and "a deck can have any number / up to seven…"), not
  exactly 100 cards, a commander that can't lead or two that can't pair (partner, partner with, partner groups,
  friends forever, Background, Doctor's companion). The maybeboard doesn't count. The cards' rules are kept in the
  table `legal_rules` by front-face name and asked from Scryfall's `/cards/collection` (by Scryfall id) when missing
  or a week old (`lib/legalstore.ts`): the deck page looks up for 6 seconds at most, the Decks page only reads the
  table ("⚠ Not legal" when all of a deck's cards are known), a shared deck page for 8.
- **Trades** (`lib/tradeedit.ts`, `/api/trades` POST, `components/TradeDialogs.tsx`, since 2026-11-13): the app's
  addToTrade, snapshot, applyTrade (`takeCopies` in `lib/collection.ts`, the app's removeFromCollection order) and
  revertTrade ported; trades travel whole in the sync file (newest `updatedAt` wins), deletions get a record and an
  Undo puts the trade back newer than it. CSV export (`lib/tradecsv.ts`) has the app's columns. The shared sample
  files carry a trade each way.
- **Saved views** (`lib/savedviews.ts`, `/api/collection/views`, `components/ViewsMenu.tsx`): up to 30 per account in
  the table `saved_views`; **Group printings** (`lib/collgroup.ts`) folds the list view by card name; the Decks
  filter has SPARE (owned more than the decks use, `lib/colquery.ts`).
- **Themes** (`lib/themes.ts`, `components/ThemePanel.tsx`, Settings → Theme): every theme colour lives in
  `lib/themes.ts` (the layout writes them as CSS variables, `globals.css` only uses `var(--...)`; the colours that mean
  something, such as verdict green and red, foil and mana, are fixed in `:root`). The choice is kept in this browser
  (`localStorage` `mtg.theme`); none = System (Dark, or Light when the computer asks for light). A small script in
  `<head>` applies it before the page paints. `test/themes.test.ts` checks every theme gives every colour, text
  contrast at least 4.5:1, and that every `var(--...)` the site uses exists: a new colour goes into `THEME_TOKENS` and
  every theme.
- **Footer and pages** (`components/SiteFooter.tsx`, `/privacy`, `/legal`): data sources, the Fan Content Policy
  wording, Contact = the GitHub issues page.
- **Print** (`lib/printlist.ts`, `components/PrintLists.tsx`): an A4 list without pictures, drawn under `<body>` and
  printed with `body.printing-list` (the "Printing a list" rules in `globals.css`); the collection prints what the page
  shows (all pages), optionally with purchase prices; the deck prints its main deck by category or card type.
- **Weekly email** (`lib/weekly.ts`, `lib/weeklyjob.ts`, `lib/mailer.ts`, Settings → Weekly email,
  `/api/account/email`): an address confirmed by a link (`/api/email/confirm?t=`), a weekday and on/off, in the table
  `email_settings`. The nightly job also keeps every pile's price (one copy, in euros, owner's source) in
  `card_price_days` (`lib/pricehistory.ts`): every day for 92 days, then one day a week (the Monday) up to 366 days;
  the card window's price chart (`components/PriceChart.tsx`) reads it through `/api/collection/prices`. `/api/cron/weekly` (07:30 UTC every day) sends the email to accounts whose weekday it is in
  Copenhagen (not twice within 20 hours): collection value against the value-history day a week back, the 5 piles up
  and down most against their price a week ago (from 5 cents), wishlist alerts reached in the last 7 days, and per
  deck game changers added or removed (Scryfall's `is:gamechanger`) and new legality problems against the state kept
  from the last email (the first email lists the problems there are). Sent through Resend (`RESEND_API_KEY`,
  `EMAIL_FROM`); without them nothing is sent, Settings says so, and `/api/health` has `emailReady: false`. Every email
  has a stop link (`/api/email/stop?t=`: a page with a button, plus `List-Unsubscribe` one-click). "Send a test email
  now" sends this week's email without moving "last week" on.

## Settings (Vercel environment variables)
| Name | What |
|---|---|
| `SESSION_SECRET` | 32 random bytes, base64. Changing it logs everyone out. |
| `NEXTCLOUD_SERVER` | The only Nextcloud the nightly backup goes to (`https://cloud.nocar.me`). |
| `ALLOWED_USERS` | Nextcloud user ids that may be connected as a backup place, comma separated. Empty: nobody. |
| `NEXTCLOUD_PASS` | Sent as header `X-MTG-Trader-Pass` to Nextcloud, so Cloudflare rules can let the website through. |
| `CRON_SECRET` | Vercel sends it to the crons. |
| `RESEND_API_KEY` | Resend's API key for the weekly email (a secret; the owner adds it). Unset: no email is sent. |
| `EMAIL_FROM` | The sender, e.g. `MTG Trader <mtgtrader@mail.nocar.me>`, on a domain verified at Resend. |
| `SITE_URL` | Optional: the address used in emails (default `https://mtgtrader.vercel.app`). |
| `BLOB_READ_WRITE_TOKEN` | Set by Vercel for the Blob store. |
| `DATABASE_URL` (and the other `PG*`/`POSTGRES_*`) | Set by Vercel for the Neon database (preview and production only). `memory` keeps accounts in memory, for tests and local runs. |

## Cloudflare in front of the Nextcloud
The owner's Nextcloud is behind Cloudflare, which challenges or blocks servers. What makes the website work:
- Bot Fight Mode **off** (on the free plan no rule can skip it).
- Custom rule "MTG Trader website" (first in the list): host is `cloud.nocar.me` and header `x-mtg-trader-pass` equals
  the secret → **Skip** all remaining custom rules and rate limiting (since 2026-11-11 not the managed rules, so the
  php2shell protection still checks these requests).
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
until its cards, colours, combos or `WinOddsCalc.VERSION` change. For the progress bar the build adds one call at the
start of each game (`WebProgress.game()` in `Website.kt`); the worker posts the games started every 100, which doesn't
change the result.

**When the app's sync format changes** (`Sync.FORMAT` in `app/.../data/Sync.kt`): update `web/src/lib/syncfile.ts`
(`WEB_FORMAT` and the types) in the same release, and refresh the shared sample files:
- `WRITE_WEB_FIXTURES=1 ./gradlew testDebugUnitTest --tests '*WebSyncFixtureTest*'` writes `web/test/fixtures/app-sync.json`;
- `WRITE_WEB_FIXTURES=1 npm test` (in `web/`) writes `web-edited-sync.json`, which the Kotlin test merges like a phone.
