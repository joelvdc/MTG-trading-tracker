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

## Right now (2026-11-17, after app 1.100)
- **Latest (website only, 2026-11-17):** the deck page's win turn runs the app's 4 batches in 4 Web Workers and
  merges them (`winBatchJson`/`winMergeJson`, exactly the app's result); a first estimate after 1,000 games ("≈"
  power, table) within a few seconds. On the owner's 4-core laptop 26 s → 13 s. Optional step 2 (leaner simulation,
  also for the app) not started.
- **Latest: app 1.100 (2026-11-17, no sync change):** the deck page's Land heading counts MDFC lands outside it,
  "Land (37, 40 with MDFCs)" (`MdfcLands` in data/DeckStats.kt, tests in Release1100Test). Same day, website (no app
  needed): precon pages work out the win turn and show Power (from the
  win turn) and Missing, no Bracket; one result per precon kept for every account (`precon_win_turns`,
  `POST /api/precons/winturn`). Lands always go in Land (categories table leaves front-face lands out, rebuilt once by
  the 04:00 UTC job on 2026-11-18; `autoFor` places lands by type at once). The Land heading counts MDFC lands:
  "Land · 37 (40 with MDFCs)" (`lib/mdfclands.ts`; not on shared links).
- **Expected to look odd:** until the 2026-11-18 rebuild, the phones and the role line may still count Evolving
  Wilds and other lands as Tutor/Ramp; existing decks keep old placements until "Put every card in its auto category".
- **Before that (website, 2026-11-17):** precons pages and the Explore top bar:
  new top bar (Collection, Decks, Games, Trades
  (Wishlist link removed again on request; it stays inside Collection), Explore ▾ panel, account menu with Orders/Settings/Log out) and Commander precons: `/precons` grid
  from a daily index (`lib/preconindex.ts`, Blob `precons/index.json.gz`, filled by `/api/cron/sources`) and
  `/precons/<fileName>`, the deck page in read-only precon mode (`DeckScreen.tsx`, `/api/precons/view`, `deckanswer.ts`)
  with Make it my deck, Add its cards to my collection (`/orders?precon=`), Copy decklist. Details: `web/README.md`.
- **Expected to look odd:** a precon tile has no picture, commander or colours until the index has read that deck;
  opening /precons starts reading the missing ones (the page refreshes itself), the daily 06:00 UTC job too.
- **Before that (website only, 2026-11-17):** playtester feedback round (corner preview, zoom, battlefield menu,
  View card, custom tokens, real card back; Primer folds). At a high zoom, cards placed close together overlap.
- **Before that:** app 1.99 + website (2026-11-16, no sync change): primer card links `[[Card name]]` (shared rules
  `DeckNotes.linkSegments/linkMatches` and `web/src/lib/decknotes.ts`, cases in
  `web/test/fixtures/primerlinks-cases.json`). Website: `/` card list in the primer editor, links open the card window
  and show the hover picture; the AI writes links and reports seconds, tokens, thinking tokens and tokens/s. App: a
  link on the Primer page opens the deck page's card window (hand-over through the deck entry's saved state
  "primerCard") and returns to the Primer page on close.
- **Earlier:** app 1.98 (Primer page, website Write with AI on OpenCode Go) and Next.js 16.4.6 (2026-11-16).
- Harmless noise: `npm test` prints "Failed to load source map" for `web/src/lib/winodds-js/*.mjs` (the `.map` files aren't committed).
- **Expected to look odd:** AI primers use the owner's OpenCode Go plan (DeepSeek's $100 a month is shared with the
  coding sessions; Go's 5-hour and weekly limits apply). Go is meant for coding agents; if OpenCode objects, switch
  provider. The app's primer editor has no `/` list (type `[[…]]` by hand).
- **Earlier:** app 1.97 (2026-11-16, sync format 15): primer, card notes, own tags with role line, packages, change
  history. 1.96 the same day: deck search, copy/paste deck text.
- **Before that:** app 1.95 (2026-11-15): a calmer deck page (most help text gone, shorter scores/legality/stats lines, no
  big Maybeboard title) and a "Modified <date>" line under the deck's cards and value, the same date as the website
  (the Decks tab list deliberately unchanged). 1.94 the day before: tracker menus in the app's colours, shorter
  settings texts.
- **Website speed-up (2026-11-15, website only):** internal links no longer reload the site (`next/link`); every page
  and panel (collection, decks, deck pages, wishlist, scans, stats, games, orders, trades, trade binder, settings
  panels) shows its last answer at once and refreshes in the background (`web/src/components/api.ts`: `peek`,
  `remember`; an edit only marks answers stale; a kept answer is only used when the page is still empty); after the
  first page loads, `warmUp()` fetches every section once in the background; hovering a deck tile or a top-bar link
  preloads it; the login check is one query (`deviceAccount`). **Expected to look odd:** right after switching or an
  edit, a page can show the previous numbers for a moment. Opening the site from scratch still takes the 1-2 s first
  load. Later options: keep answers across reloads, pre-built answers in the database, ETag/304, slimmer JSON.
- **Collection toolbar tidy-up (2026-11-15, website only):** Display ▾ / Collection ▾ / Binders ▾ menus, Add card on
  the right, Views button there from the start, Moxfield-style Filter and Sort windows, one button text size site-wide.
  Decks list the same way: search, Sort, one Decks ▾ menu (the three deck pages, Archidekt update/import, retired
  toggle) and New deck on the right; form controls never take the 13px `.small` text.
  Possible next step the owner may ask for: more filters like Moxfield's (rules text, mana value, power/toughness).
- **GitHub checks:** the Tests check runs the app and the website on pull requests and on every push to `main`; a red
  mark after a push means fix that first. The Release workflow compares the certificate with the previous release.
- **Expected to look odd:** Settings → Prices says Card Kingdom came "from the MTG Trader website". The website's Card
  Kingdom list comes from the GitHub copy, so it can be a few hours behind Card Kingdom's own. The price chart has
  only the nights since 2026-11-12 ("not enough history" for a few days). Trades completed on the website reach the
  phones at their next sync.
- **Weekly email:** working (confirmed 2026-11-13).
- **Waiting on the owner:** playtester feedback (now due) and a working AI primer on the website. Review follow-ups for later under "From the code reviews". Ideas still
  collecting: Commander Salt scores for local decks, more backup places, Group printings in the app.

## Finished: deck builder parity (green light 2026-11-15, all five releases done 2026-11-16)
Owner: "keep improving" local and imported decks against Archidekt/Moxfield. Green light for everything; DeepSeek
(OpenCode Go) writes all code, Opus plans, reviews, tests and releases (`/deepseek`).
- **Release 1, website:** DONE 2026-11-16 (tag/package bulk actions moved to release 4). Deck page buttons cut down (Bracket/Power/Missing numbers clickable; Playtest,
  Recommendations, Share ▾, Edit ▾); drop "(This app)" after Power; Undo bar for every deck edit incl. category and
  deck delete; Select mode with bulk actions (category, maybeboard, copies, printing cheapest/newest/oldest/owned,
  foil, tag, package, copy, remove; Archidekt decks only tag/package/copy); Copy deck as text (Moxfield, Archidekt,
  Arena, plain); Paste cards into an existing deck with a preview.
- **Release 2, website:** playtester at `/decks/<id>/playtest` (zones, London mulligan + free first, library actions
  incl. scry/surveil/mill/tutor, tap, flip, face down, copies, counters on cards, tokens from the deck + Scryfall
  search, life/poison/energy/experience/commander tax, turns, undo, log, dice/coin, shortcuts, land/draw odds). DONE
  (live 2026-11-16). Owner tests it after release 4. No playtest in the Android app.
- **Release 3, app 1.96:** DONE 2026-11-16 (deck search, copy/paste deck text, menu headings + search icon).
- **Release 4, app 1.97 + website, sync format 15:** DONE 2026-11-16. Deck primer + per-card
  notes (share page too, kept on Archidekt reload, Archidekt description fills an empty primer once); own tags that
  apply in every deck + role counts line; packages; change history per deck (same-day merge, last 100 entries).
- **Release 5, app 1.98 + website:** DONE 2026-11-16. Primer page in the app; website primer at the bottom of
  the deck page with in-page editor and Write with AI (OpenCode Go, 20 a day); health check `ai`.

## Done
- Website (2026-11-17): playtester corner preview, zoom, battlefield menu (tap all, proliferate), View card,
  custom tokens, real card back; deck page primer folds, tighter Stats.
- Website (2026-11-16): Next.js 16.4.6 security update.
- 1.99 (2026-11-16): clickable card links in primers (`/` list on the website), AI timing note.
- 1.98 (2026-11-16): Primer page in the app; website primer editor at the bottom of the deck page with Write
  with AI (OpenCode Go).
- 1.97 (2026-11-16): deck primer and card notes, own tags in every deck with a role line, packages, change history
  (app and website, sync format 15); website Select mode gains Tag… and Save as package….
- 1.96 (2026-11-16): search inside a deck, copy deck as text, paste cards, the deck menu under headings.
- Website (2026-11-16): fewer deck buttons, Undo bar, Select mode, copy/paste deck text, playtester.
- 1.95 (2026-11-15): deck page with much less help text, and its last modified date (not on the Decks tab).
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
