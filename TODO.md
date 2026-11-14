# To-do list for MTG Trader

Ideas the owner is collecting. **Nothing here is built until the owner gives the green light**; then everything on the
list is planned together and carried out (see `CLAUDE.md`).

How to use this file:
- **Right now** is a short note for the next session: where things stand, what waits on the owner, what is expected to
  look odd. Rewrite it (at most about 15 lines) after every release or website change.
- Add new ideas under "Collecting", newest last, with the date and the owner's own wording.
- "Open questions" under an item are things to settle with the owner before building it.
- When an item is released, move it to "Done" as one line with the version it shipped in; the details go in
  `release-notes/<version>.md`.

(Trimmed on 2026-11-02 and tidied on 2026-11-12: finished plans and design notes were removed; they are in the git
history, the release notes and `web/README.md`.)

## Right now (2026-11-14, after 1.94)
- **Latest:** app 1.94 (2026-11-14): the life tracker's menus (Game settings, Dice, Mana, History, End game, player
  sheet, dice result) and Settings → Games in the app's colours and theme; much less text on every settings page;
  Backups explains Nextcloud without the website (a chosen folder plus the Nextcloud app's auto-upload). 1.93 the day
  before: TCGplayer and Card Kingdom prices from the website.
- **GitHub checks:** the Tests check runs the app and the website on pull requests and on every push to `main`; a red
  mark after a push means fix that first. The Release workflow compares the certificate with the previous release.
- **Expected to look odd:** Settings → Prices says Card Kingdom came "from the MTG Trader website". The website's Card
  Kingdom list comes from the GitHub copy, so it can be a few hours behind Card Kingdom's own. The price chart has
  only the nights since 2026-11-12 ("not enough history" for a few days). Trades completed on the website reach the
  phones at their next sync.
- **Weekly email:** working (confirmed 2026-11-13).
- **Waiting on the owner:** nothing. Review follow-ups for later under "From the code reviews". Ideas still
  collecting: Commander Salt scores for local decks, more backup places, Group printings in the app.

## Collecting

### Commander Salt scores for local decks (2026-11-05)
Owner's idea: a copy of the decklist on a site Commander Salt accepts. Every site it accepts (Moxfield, Archidekt,
TappedOut, ManaBox, MTGGoldfish, Scryfall, …) needs an account to create a deck, with no open way for an app to do it.
- Proposed: a half-manual "Score on Commander Salt": copy the list and open Archidekt's new-deck page, the owner pastes
  and saves, then pastes the link back; it is kept as that local deck's scoring link ("Copy list again" after edits).
  First look for a site that takes a deck link without an account; maybe ask Commander Salt whether it could take a
  pasted list.
- Open question: on the website too, or only in the app?

### More places to back up (2026-11-05)
Owner: "could we in the future expand the sync, so that the user can choose between nextcloud, google drive, dropbox or
onedrive?" Since 1.82 Nextcloud is only a nightly backup (syncing goes through the MTG Trader account), so this is now
about where to back up.
- Open questions: each service needs its own sign-in and an app registration there; which ones the owner wants first.

### Group printings in the app (2026-11-13)
Owner: "not needed for now, add it to the todo as a potential future task". The website's Group printings (one line
per card name, opens to its piles) for the app's collection list.

### Smaller loose ends
- The website's price alerts leave CardTrader out (they need the owner's CardTrader token, which stays on the phone).
- Scanned page on the website: "To trade" for scans, and sending scans to an Archidekt deck (the website only adds to
  local decks). Small; left from the 2026-11-05 list.
- The phone's "Unable to resolve host" for every price site (2026-11-07, VPN on) was a DNS/VPN problem on the phone,
  not the app. Look again only if it comes back with the VPN off.

### From the code reviews (2026-11-13)
GLM and DeepSeek reviewed the code; Claude checked their findings. The urgent ones shipped in 1.92 (sync fixes, tests
in GitHub for the website, the release certificate check, the website's deploy rule). The owner asked to keep these
for later:
- **Speed with big collections (app):** the hourly sync reads every table before asking the website "changed?" (check
  the change count and ask first, read only when needed); some lookups load the whole collection to count one card,
  once per wishlist card too (`MtgRepository.kt`, `PriceSources.kt` `ownedIds`: targeted DAO queries); the Collection
  screen redoes filtering, sorting and counts on every letter typed (wait ~150 ms, do it off the main thread).
- **Website card-details file** (`web/src/lib/carddetails.ts`): read whole, changed and written back with no version
  check, so two saves at once can drop one's additions (they're looked up again later). Rows in the database like
  `legal_rules`, or retry on the ETag.
- **Website login lock** (`web/src/lib/users.ts`): two wrong passwords at the same moment can count once (atomic SQL
  counter), and a locked account is reported before the password is checked.
- **Website route tests:** `test/syncroute.test.ts` (1.92) covers `/api/sync`; the login, lockout and other routes
  have none yet.
- **Content-Security-Policy** for the website, starting in report-only mode.
- Smaller: the 7 cron routes each copy the secret check (one shared helper); failed price/detail loads on the website
  are silent (a `console.warn`); `currentAccount()` makes two database calls where one would do; the unused
  Nextcloud read path in `web/src/lib/store.ts`; ESLint with the React hooks rules; `CollectionScreen.kt`,
  `DeckScreen.kt` and `web/src/app/collection/page.tsx` are large (split a piece off when changing it anyway);
  `targetSdk` 36 as its own release with new screenshots.

### Decided, keep in mind
- Website address mtgtrader.vercel.app; Cloudflare rules for it on nocar.me are described in `web/README.md`.
- Archidekt is contacted only on a tap ("Reload decklist", "Update all decks"); decks from Archidekt are read-only.
- Prices from the website come as one file, not per-card database lookups (offline, instant). Since 1.93 phones with an
  account get all three sources from the website first (Cardmarket's file, Card Kingdom's file, TCGplayer for the
  phone's cards in one request), falling back to Scryfall, Card Kingdom and the GitHub copy. CardTrader stays on the
  phone (the owner's key never leaves it).
- Android's own Google backup is off (1.93): the app's own backups cover it.
- Dropped by the owner: the ideas offered on 2026-11-11 (trade matching between friends, "what should we play
  tonight?", a game-night planner website, a website for Poké Trader; dropped 2026-11-12), Archidekt's own categories for new decks (2026-11-08), the "view on hover" switch for decks,
  full-screen pictures. The life tracker stays on the phone.

## Done
- 1.94 (2026-11-14): tracker menus and Settings → Games in the app's colours, shorter settings texts, Backups and
  Nextcloud without the website.
- 1.93 (2026-11-13): TCGplayer and Card Kingdom prices from the website (`/api/app/tcgplayer`,
  `/api/app/cardkingdom`), Android's own Google backup off.
- 1.92 (2026-11-13): sync fixes from the code reviews, sync tests, website checks in GitHub, release certificate
  check, `/api/sync` 428, Vercel deploy rule.
- Website (2026-11-13): themes (System, Dark, Light, Plains, Island, Swamp, Mountain, Forest), per browser.
- 1.91 (2026-11-13): "Share wishlist link" in the app, About text with every source and the Fan Content Policy;
  website: trades (make, edit, complete, undo, delete with Undo, CSV export, the app's rules), saved views, Spare
  filter, price chart (prices kept a year: daily 92 days, then weekly), wishlist share link, footer with Privacy and
  Legal & attribution pages (Contact = GitHub issues).
- Website (2026-11-12): Group printings on the collection table (one line per card name, opens to its piles).
- 1.90 (2026-11-12): Faces filter (MDFC, Transforming, Split / Adventure; Scryfall's layout now kept in the app's card
  details, database 29, and the website's details blob), Retry on the app's failed-price notes, and the website's
  Columns dialog with Decks, In decks, Spare, profit, every price source and more (sortable by Decks, Spare, EDHREC);
  the Ordered tick box in the website wishlist's picture view.
- 1.89 (2026-11-12): share links for a deck, game, binder or the whole collection (app and website; Settings → Shared
  links switches them off), the Commander legality check (app and website, shared test cases), Print to PDF on the
  website's Collection and deck pages, and the weekly email (Settings → Weekly email; value against a week ago, top 5
  gainers and losers, wishlist alerts, deck changes). Resend set up by the owner the same day.
- 2026-11-11: website: "Import ▾" (CSV file… / Preconstructed deck…); the trade binder page catches up with the app
  (price per suggestion, Swap, card window, quick minimum values); drop-down menus close on a click elsewhere or Esc.
- 1.87 (2026-11-10): new "Card Swap" logo in the app and on the website. Drafts of the four other directions:
  https://claude.ai/artifact/CtAVMkn2uwvQMyHDL5Aisj (private).
- 2026-11-09: website: dragging cards and categories on the deck page done by the page itself (the mouse wheel and the
  window's edges scroll while dragging, Esc cancels).
- 2026-11-09: website: Recommendations for all decks (/decks/recommendations) with a deck picker and "Up to date for";
  every Scryfall request keeps to its limits, with retries and "Try these again"; recommendation filters.
- 1.86 (2026-11-09): recommendations to a deck's maybeboard and the wishlist; website deck page with fixed category
  columns, dragging categories into order (sync format 14) and cards between categories.
- 1.85 (2026-11-08): decks from Archidekt read only in the app too; the deck page groups and sorts like the website.
- 2026-11-08: website: the deck page's Collection tracker lists other decks using the card; Retire / Bring back on the
  Decks page; Check an order on the Scanned page; the collection's tabs; the Decks list groups fold.
- 1.83 (2026-11-08): retired decks (app and website); Sync.FORMAT 13.
- 1.82 (2026-11-08): game stats Overview and period choice; lending decks to friends and guests; Nextcloud only for
  backups; Settings in categories.
- 2026-11-08: website: Games section (games, stats, players, a Games section on each deck page); the Scanned chip in the
  collection; accounts only (Nextcloud login gone; Settings → Nextcloud backup).
- 2026-11-08: website deck page: maybeboard in three columns below the deck, "Split into groups", mana costs and
  collection info on maybeboard lines.
- 2026-11-07: website deck page redesign and second round (compact lines, view options, Sort by / Group by, syntax
  filter with Show only / Highlight, Stats and Tokens at the bottom).
- 1.81 (2026-11-07): card text and Rulings in the card windows; Cardmarket prices from the website's nightly copy; auto
  categories from the website; signed and plain piles no longer merge at sync.
- 2026-11-07: website: Archidekt import, Reload decklist, Update all decks (with Commander Salt); win turns on the
  website; Collection tracker; tokens section; deck card window; deleting any deck; progress bar for the 10,000 games;
  Combos; Cards in several decks; Rule-zero cards; Stats → Value by set.
- 2026-11-07: step D: the website's nightly job (value per day, price alerts), Value over time, Alert history, the
  trade binder page; app 1.80 syncs the trade binder's rules and declined suggestions and sends the value history once.
- 2026-11-06: website auto categories from its own table built weekly from Scryfall's card tags.
- 2026-11-06: step C: website accounts (Neon), app 1.79 syncs with them; the trade binder syncs.
- 2026-11-06: website phase 4 (decks): decks list and deck pages, editing local decks, new decks, recommendations, win
  turn and power level from the app's own `WinOdds.kt` (`web/winodds-kt`).
- 2026-11-06: website step B: binder tools, multi-level sorting, bulk purchase price, splitting piles, value by price
  source, copy as a list, the wishlist's received flow, synced settings and backups in Settings, Import CSV, Orders.
- 2026-11-05: website step A: the wishlist in the collection, "Card on hover".
- 2026-11-05: website phase 2: the collection, its stats, CSV export (full and Archidekt), trades (view only).
- 1.78: break free from Archidekt (no login or collection sync; Archidekt decks read-only with "Make it a local deck";
  Export CSV for Archidekt). Also Change printing on wishlist cards.
- 2026-11-05: website phase 1: the wishlist at mtgtrader.vercel.app (`web/`).
- 2026-11-03: /delegate-mistral (since cancelled by the owner, 2026-11-10).
- 1.77: home-screen widgets, value by set, price alert history, land drops.
- 1.76: Archidekt requests at a safe pace, automatic sync every 6 hours, a clearer login notice, remote switches.
- 1.75: the app tells every site its version and GitHub page; README and code tidied for sharing.
- 1.74: the same starter after 4 → 5 → 4 players in Game settings.
- 1.73: who starts is picked after Game settings is closed.
- 1.72: arrange the table by dragging seats.
- 1.71: land drops in the commander and win-turn odds.
- 1.70: win turn: Currency Converter, Renewed Solidarity, spree, death triggers.
- 1.69: win turn: lands, ability costs, finishers, Sagas, Vehicles, toxic.
- 1.68: neater tiles on the printed bracket cards.
- 1.67: tidier bracket and power level cards.
- 1.66: the app's own power level; loading circle during price downloads.
- 1.65: print the bracket and power level cards as PDF.
- 1.64: win turn: deaths, sacrifice outlets, -1/-1 counters.
- 1.63: win turn: battles, planeswalker ultimates, proliferate and poison.
- 1.62: win turn: trigger timing, mana, land drops, modes.
- 2026-10-26: compared the win turn with ScrollVault's (a report, no app change).
- 1.61: loading circle during Card Kingdom downloads; win turn sacrifice payoffs and token fixes.
- 1.60: purchase prices keep the currency they were typed in.
- 2026-10-24: random test failures on the PC fixed (no release).
- 1.59: dice (Dicefont), quick-roll ring and "I'm …" for the random opponent.
- 1.58: life tracker feedback (vibration, "1st" badge, guests' commanders and more).
- 1.57: hold − or + for 10 commander damage.
- 1.56: simulator: one attacker hits only one opponent.
- 1.55: the simulator reads ramp, Treasures, the graveyard, copies, planeswalkers, Clues and counters.
- 1.54: the win turn at 4 in 10 games, the first win highlighted, and a better simulator.
- 1.53: combos and tutors in the win turn.
- 1.52: when the deck can win (also on the bracket card), the turn badge in the name label, the scanner list showing
  the newest card.
- 1.51: pausing games, and the table on narrower phones.
- 1.50: the low-life ghost no longer floats over the +.
- 1.49: random pick timing, commander damage from your own commander, the mini-grid in a frame.
- 1.48: the life tracker's new layout (menu bar in the middle, player sheets, dice, mana pool, history).
- 1.47: life tracker and game records for Commander; deck page and card window fixes, Manage categories.
- 1.46: changing decks on Archidekt, local decks, combos that don't count for the bracket, all decks' bracket or power
  level cards, when the deck can cast its commander.
- 1.45: choosing a token's printing per deck.
- 1.44: deck stats (mana curve, mana cost, mana production, tokens).
- 1.43: compact deck view with mana costs.
- 1.42: compact deck view, folding sections, the maybeboard, adding cards from the deck page.
- 1.33: order history, profit and loss, add a precon, condition-adjusted values.
- 1.32: sort by value change, Cardmarket orders, package check against an order, purchase prices from orders.
- One-off (no release): purchase prices of the whole collection from all past orders.
