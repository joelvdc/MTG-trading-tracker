# To-do list for MTG Trader

Ideas the owner is collecting. **Nothing here is built until the owner gives the green light**; then everything on the
list is planned together and carried out (see `CLAUDE.md`).

How to use this file:
- Add new ideas under "Collecting", newest last, with the date and the owner's own wording.
- "Open questions" under an item are things to settle with the owner before building it.
- When an item is released, move it to "Done" as one line with the version it shipped in; the details go in
  `release-notes/<version>.md`.

(Trimmed on 2026-11-02 for sharing the app; the earlier, longer notes are in the git history.)

## Collecting

### 2026-11-05: website on Vercel (approved outline; each phase gets its own detailed plan and green light)
Owner: "a wishlist web page, where I can edit my wishlist in the app from a computer browser", growing into an
"archidekt lite website for editing/viewing the collection and decks", plus games.
- **Sync (decided): option A.** The website joins the Nextcloud sync as one more device: reads and writes
  `sync.json.gz` with ETag checks and the app's merge rules (ported to TypeScript, tested against example files from the
  app's own tests), keeps fields it doesn't know, turns read-only on a newer sync format, backs up the file before its
  first write in a session. Login = Nextcloud Login Flow v2 (the website stores no passwords). Prices: a daily Vercel job
  makes a price file from the phone's sources. The website never contacts Archidekt. Code in `web/` in this repository.
- **Order (decided): 1 → 3 → 2 → 4 → 5.**
  1. ~~Wishlist on the website~~ **Done 2026-11-05** (see Done).
  3. ~~App release: break free from Archidekt~~ **Done in 1.78** (see Done). Commander Salt for local decks moved
     to phase 4.
  2. ~~Collection on the website~~ **Done 2026-11-05** (see Done).
  4. ~~Decks on the website~~ **Done 2026-11-06** (4a–4d, see Done). Still open from it:
     - Commander Salt scoring for local decks (owner's decision: should the website ask Commander Salt to score
       local decks, as the app can?).
     - Deck categories on the website go by card type (or a pasted list's tags), because the website never contacts
       Archidekt. Does the owner want Archidekt's own categories for new decks (that would mean the website calling
       Archidekt)?
  5. Games on the website: log, stats, head to head, per deck; edit and add games by hand. Life tracker stays on the
     phone.
- Open questions:
  - Web address (decided 2026-11-05): mtgtrader.vercel.app (mtgtracker and mtg-trader were taken). Vercel account:
    Hobby plan, user jnccola-7915, no projects yet.
  - ~~Can Vercel's servers reach the owner's Nextcloud?~~ Yes, after Cloudflare changes on nocar.me (Bot Fight Mode
    off, a skip rule and a Geo filter exception for the website's pass header; details in `web/README.md`).
  - ~~Phase 3: does Archidekt read the app's CSV?~~ Decided 2026-11-05: CSV export (app in phase 3, website in phase
    2) asks for the format: the app's full format, or **"ManaBox – single folder"** as Archidekt's importer expects it
    (owner's screenshot: 18 columns; 1 name, 2 set code, 3 set name, 4 collector number, 5 foil/variant, 7 quantity,
    9 Scryfall ID, 14 condition, 15 language; the rest ignored; first line skipped). The app's current file is
    ManaBox's *whole collection* layout minus the ManaBox ID column, so its Scryfall ID sits one column early. The
    owner checks the first file with a small import on archidekt.com.
  - Phase 3 (decided 2026-11-05): Archidekt decklists are only ever read when the owner taps a refresh button
    ("Reload decklist", "Update all decks"); nothing automatic. Today the only automatic Archidekt calls are the
    collection sync (goes away) and `fillMissingUpdateDates` on opening the Decks tab (make it part of "Update all").
  - Commander Salt scores for local decks (owner's idea 2026-11-05: a copy of the decklist on a site Commander Salt
    accepts). Commander Salt takes links from Moxfield, Archidekt, TappedOut, ManaBox, MTGGoldfish, Scryfall,
    TopDecked, TopDeck.gg, Hareruya, CommanderView, MythicHub, untap.in and EDHREC deck previews. All of them need an
    account to create a deck, and none has an open way for an app to do it, so an automatic copy would mean the app
    logging in somewhere again. Proposed instead: a half-manual "Score on Commander Salt" for local decks: the app
    copies the list and opens Archidekt's new-deck page, the owner pastes and saves, then pastes the link back; the
    app keeps it as that local deck's scoring link (the local deck stays the main copy; after edits, "Copy list
    again"). Maybe also ask Commander Salt whether it could take a pasted list. Decide in phase 3 or 4.
  - Phase 4: porting the win-turn simulation is the largest single piece; plan it as its own step with the app's own
    test decks as the check that both give the same numbers.

### 2026-11-05: website: what the app can do that the website can't (yet)
Owner: "There are some features missing on the website, like merging a binder into another and creating/updating trade
binder. Please find and list what is missing on the website from the android app." Decks and games are phases 4 and 5;
the scanner, package check and life tracker stay on the phone.
- **Binders:** merge one into another; delete (keep the cards in Unsorted, or remove them); arrange the order; move all
  Unsorted cards to a binder.
- **Trade binder:** make it and fill it by its rules (most cards, keep at least 1 copy, keep the best copy for my decks or
  of every card, minimum value, suggest turned-down cards again), swap copies. Open question: the app keeps *which*
  binder is the trade binder only on the phone (`Settings.tradeBinderId`, not synced; its rules do sync), so the website
  first needs an app release that syncs it.
- **Collection:** sorting in several levels ("Add a level"); purchase price for several selected cards at once; "New
  trade" from selected cards; split a pile when only some copies are signed/altered; value over time (its history is
  only on the phone, it would have to sync); "Value by price source" in the stats; Import CSV (ManaBox…); import an order
  (CardTrader, Cardmarket), the orders list and purchase prices from orders; add a preconstructed deck; copy/share as a
  list; print cards as PDF.
- **Wishlist:** the received flow (mark received, pick the printing, add to the collection or just take it off; "Mark
  the cards I now own as received", "Mark all ordered cards as received", "Clear received cards", "Back on the
  wishlist"); alert history; share the wishlist; the "ordered cards in their own section" choice.
- **Trades:** make and edit trades, refresh prices, complete a trade and update the collection, undo, delete, export
  trades as CSV (today the website only shows them, as decided).
- **Settings:** price source, price type, currency, condition adjustment and other synced settings can only be changed in
  the app; backups and restore.
- **Status 2026-11-06 (approved plan: steps A wishlist in the collection + hover switch, B the website-only items,
  C website accounts, D nightly job for value over time, alert history and the trade binder page):**
  - A and B done (see Done). Print cards as PDF moved to decks (phase 4a): in the app it is the deck's bracket/power card.
  - Still open from this list: the trade binder (step D, needs the app to sync which binder it is: step C's release),
    value over time and alert history (step D), trades on the website (left out for now, as decided).
  - Step C shipped 2026-11-06: website accounts (commit 6405b5a) and app 1.79 (Settings → Sync → MTG Trader account,
    the trade binder's uid in the synced settings). The owner approved copying their data; they do it themselves on
    the website (Settings → Make my account and copy my data, while logged in with Nextcloud), then switch each phone
    the same day. Next: step D.
  - Automatic categories on the website: done 2026-11-06, from the website's own table built from Scryfall's tags
    (owner: "can we build our own database and not rely on archidekt?"). The app reads the website's table for its
    own "Auto categories for new cards" since 1.81.
  - Still to plan (owner agreed 2026-11-06): Commander Salt scores for local decks on the website: look for a deck
    link that needs no account, else the half-manual flow.
  - Step D shipped 2026-11-07 (owner's green light 2026-11-06): the website's nightly job, Value over time, Alert
    history, the trade binder page, and app 1.80 (trade binder rules and declined suggestions sync; the phone's value
    history sent once). Left out as planned: CardTrader in the website's alert check. The plan as approved:
    - Found while preparing: the trade binder's rules (`tradeMax`, `tradeKeepOne`… in Settings.tradeRules) and its
      declined suggestions (`trade_skips`) are NOT synced (only which binder it is, since 1.79). The app's value
      history and alert history live only on each phone and aren't in backups.
    - D1 nightly website job (`/api/cron/nightly`, 06:30 UTC, after the price jobs), for every account with a file:
      the day's collection value with the app's own keys (ValueHistory.snapshotValues: every price source and type,
      Card Kingdom's buylist, each binder by its uid) into a database table; and a check of wishlist alert levels
      against Cardmarket at the alert price type (PriceAlerts.step: once per drop below the level), written to an
      alert history table (the app's AlertEvent fields; a year, at most 500 lines). CardTrader prices left out (they
      need the owner's CardTrader token, which stays on the phone).
    - D2 website pages: Stats → Value over time (whole collection or a binder, the app's price source and type, 1
      month / 3 months / 1 year / all); Wishlist → Alert history.
    - D3 trade binder page on the website: the app's TradeBinderPlanner ported (cards to add and take out, with
      reasons), accept or decline, the binder made if needed; the rules editable there.
    - App 1.80 (needed by D3): trade binder rules and declined suggestions sync (as synced settings, no file format
      change); the phone's value history is sent once to the account so the website's chart starts from it.
    - Health check: the nightly job's last run and counts in /api/health.

### 2026-11-05: website accounts instead of the Nextcloud login
Owner: "Instead of logging into nextcloud for "opening" the website, could the website support users? Like create a
username/password and use nextcloud for backups only? That means both the android app and vercel will have the data
stored on an account created by the website, the android app would have to be able to create users as well."
- Possible. Sketch: the website gets a small database (e.g. Neon Postgres, free tier, via Vercel) with accounts and each
  account's sync file; the app syncs with the website (same file, same merge rules, read with a version number and save
  only if unchanged, like the ETag today) instead of with Nextcloud; the app gets "Create account / Log in"; Nextcloud
  becomes an optional nightly backup target. Existing data moves over once from the current Nextcloud file.
- Open questions: who may make an account (only people with an invite code, recommended, or anyone); forgotten passwords
  (no e-mail service today: the owner resets them, or add one); does Nextcloud backup stay per user and who sets it up;
  where the database lives (EU region); the "more places to sync" item below becomes "where to back up". It is a phase of
  its own (website and one app release); decide its place in the order (before phase 4 recommended, so decks and games
  are built on the final storage).
- **Step C design (prepared 2026-11-06; decided: Neon Postgres in the EU, invite codes, the owner resets passwords)**:
  - Database (Neon via Vercel's Storage tab, connected to the `mtgtrader` project): `accounts` (user name, password
    hash with scrypt, admin flag), `invites` (code, made by, used by, when), `devices` (one login token per phone or
    browser, only its hash stored, last used), `sync_files` (one per account: the same `sync.json.gz`, a version
    number, who wrote it last, when). Nothing else moves: prices and card details stay in Vercel Blob.
  - Website: log in with user name and password (the encrypted cookie stays), **Create account** needs an invite code
    (the owner makes codes in Settings), **Accounts** page for the owner to reset a password (shows a one-time
    password). Every page reads and writes the account's file instead of Nextcloud's, with the same read-edit-write
    and "someone wrote in between, redo" rule (the version number does what the ETag does now).
  - App release: Settings → Sync gets "MTG Trader account" (log in, or create one with an invite code) next to
    Nextcloud; the sync code keeps its merge rules and only swaps where the file is read and written
    (`GET/PUT /api/sync` with the version). The trade binder's uid joins the synced settings (needed by step D).
  - Nextcloud becomes a backup: the website copies each account's file to its Nextcloud folder at night when the
    account has connected one (Nextcloud's login flow, app password kept encrypted), keeping the last 10 days.
  - Moving the owner's data: once, from the current Nextcloud file into the owner's account (**asks the owner first**);
    the phones then switch to the account in Settings. Until a phone switches it keeps syncing with Nextcloud, so the
    switch happens on all phones the same day.
  - Health check: `/api/health` adds `database: true` and the account count; Neon is the one new outside service.

### 2026-11-05: more places to sync
Owner: "could we in the future expand the sync, so that the user can choose between nextcloud, google drive, dropbox or
onedrive?"
- Open questions: each service needs its own login (Google, Dropbox and Microsoft sign-in, with an app registration
  at each) and the website (see above) would need the same choice. Do it after the website phases, so the sync code
  is only reshaped once.

### 2026-11-06: website: second scan of what the app has and the website hasn't
Owner: "please scan for features in the app that are missing for the website. For example, I cannot delete a deck on
the website? The tokens section on the website is not collapsible." Also: "can you make a progress bar for the website
while it is playing the deck 10,000 times?"
- Done 2026-11-07 (owner's green light; full-screen pictures left out by the owner): see Done.
- **Still open, each needs its own plan** (owner: "then come back for the missing todo"):
  - **Trades on the website:** make, edit, complete (update the collection), delete and export trades; today they are
    only shown.
  - **Games on the website:** the game list, stats per deck and player, head to head, CSV export, the Games section on
    a deck page (the life tracker itself stays on the phone).
  - **Scan tab waiting list:** scans sync, but the website doesn't show them or send them to a binder, trade or deck.
  - **Commander Salt scores for local decks** (already planned above).
  - Archidekt on the website, the owned column and the tokens section: done 2026-11-07 (see Done). The "view on hover"
    switch for decks was dropped by the owner ("leave as it is now").

### 2026-11-07: ideas noted while building 1.81
- Prices from the website (owner: "can the website fetch the prices and the app just fetches the prices from the
  website instead?"): done in 1.81 for Cardmarket and the exchange rates. Card Kingdom (already a compact 5 MB copy,
  too big for one Vercel answer) and TCGplayer (fetched per owned card from Scryfall) stay as they were. The owner
  asked whether a database would be better than a file; decided: keep the file (offline, instant, no per-card lookups).
- The phone's "Unable to resolve host" error for every price site after logging in (owner's screenshot, VPN on): a
  DNS/VPN problem on the phone, not the app. If it comes back with the VPN off, look again.

## Done
- 1.81: card text and a Rulings tab (legalities, Scryfall's rulings) in the card windows; Cardmarket prices from the
  website's 2 MB nightly copy for phones with an account; auto categories from the website; signed and plain piles no
  longer merge at sync.
- 2026-11-07: website: Archidekt import, Reload decklist and Update all decks (with Commander Salt); win turns worked
  out by the website itself; owned icons with a Collection tracker box; tokens in their own section; the deck card
  window (card text, Rulings tab, in your collection, in your decks, Change printing on local decks, no per-card
  printing picker); card text and Rulings in the collection's card window too.
- 2026-11-07: website tweaks from the second scan: any deck can be deleted (also from the Decks list), each stats
  section and card category folds, a progress bar while the deck is played 10,000 times, Combos (with Commander
  Spellbook links, also in a card's window), a token's printing can be chosen, Cards in several decks, Rule-zero cards
  of all decks, power level source and Archidekt username in Settings, Stats → Value by set.
- 2026-11-07: step D: the website's nightly job (value per day, price alerts against Cardmarket), Stats → Value over
  time, Wishlist → Alert history, a trade binder page; app 1.80 syncs the trade binder's rules and declined
  suggestions and sends the phone's value history once.
- 2026-11-06: website auto categories from its own table, built weekly from Scryfall's card tags (owner: not
  Archidekt); a card's other categories go along; deleting a category moves cards to their next one.
- 2026-11-06: step C: website accounts (Neon), app 1.79 syncs with them; the trade binder syncs.
- 2026-11-06: website phase 4 (decks): decks list and deck pages (cards by category or type, owned/missing, stats,
  commander-casting odds, missing cards to the wishlist, bracket and power cards to print); editing local decks, new
  decks from a pasted list, a precon or empty, copy and "make it a local deck"; recommendations (EDHREC,
  recommander.cards); the win turn and the app's power level, compiled from the app's own `WinOdds.kt` (`web/winodds-kt`,
  the same numbers as the phone).
- 2026-11-06: website step B: binder tools (rename, merge, delete, arrange, move all Unsorted), sorting in up to three
  levels, purchase price for several cards, splitting signed/altered copies off a pile, "Value by price source" in the
  stats, copy selected cards as a list, the wishlist's received flow, the app's synced settings and the website's
  backups in Settings, Import CSV, and an Orders page: order history with today's worth, importing CardTrader and
  Cardmarket orders, adding a precon (MTGJSON), purchase prices from order files.
- 2026-11-05: website step A: the wishlist sits in the collection (its own chip), "Card on hover" switch.
- 2026-11-05: website phase 2: the collection (binders, search, filters, sort, edits, bulk changes, add cards, 25/50/100
  rows per page, card picture on hover), all collection stats, CSV export (full and Archidekt) and trades (view only),
  priced like the app (its source, type, currency and condition adjustment); two new daily stores (Card Kingdom copy,
  Scryfall card details); health check covers them. No app release needed.
- 1.78: break free from Archidekt (website phase 3): no login, collection sync or sending deck edits; Archidekt decks read-only with "Make it a local deck"; old unsent changes kept or discarded; Export CSV for Archidekt (everything or only what's new); Archidekt contacted only on a tap. Also Change printing on wishlist cards.
- 2026-11-05: website phase 1: the wishlist at mtgtrader.vercel.app (`web/`), editing the Nextcloud sync file as one more device; health check covers it.
- 2026-11-03: /delegate-mistral runs `vibe --prompt-file` with step and size limits (VIBE_APP_SERVER_CMD works around the blocked vibe-app-server.exe; no IT change needed).
- 1.77: home-screen widgets, value by set, price alert history, land drops (mana sinks, more "unless" lands, ramp basics).
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
