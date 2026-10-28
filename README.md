# MTG Trader (Android)

Track Magic: The Gathering trades, check they're fair using Cardmarket prices, and keep your collection up to date.
Keep your Commander decks at hand too, with their power level, brackets and rule-zero cards, organise the collection in
binders, scan piles of cards before deciding where they go, and sync it all between your phones through your own
Nextcloud. Your collection can also be kept the same as your Archidekt collection, both ways.

| Trade | Search | Collection | Compact view |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/trade.png" width="200" alt="A trade with the value of both sides compared"> | <img src="docs/screenshots/search.png" width="200" alt="Search results listing every printing with Cardmarket prices"> | <img src="docs/screenshots/collection.png" width="200" alt="The collection as a list with binder filters"> | <img src="docs/screenshots/compact.png" width="200" alt="The collection as one text line per card"> |

| Scan tab | Commander decks | Deck page | Settings |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/scan.png" width="200" alt="Scanned cards waiting to be sent to a binder, trade or deck"> | <img src="docs/screenshots/decks.png" width="200" alt="Imported Commander decks with power level and brackets"> | <img src="docs/screenshots/deck.png" width="200" alt="A deck's power level, brackets and the Refresh bracket and power level button"> | <img src="docs/screenshots/settings.png" width="200" alt="Choosing the power level source and connecting to Nextcloud"> |

| Bracket card | Power card | Card page | Collection stats |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/bracket.png" width="200" alt="The app's bracket rule-zero card: brackets, criteria, how the deck plays and its combos"> | <img src="docs/screenshots/power.png" width="200" alt="The app's power level card with ScrollVault's power level, win turns and line for the pod"> | <img src="docs/screenshots/card.png" width="200" alt="A card's page: price, quantity, finish, binder, condition, language, paid price and notes, and the other copies you own"> | <img src="docs/screenshots/stats.png" width="200" alt="Collection stats: totals, value and the color pie"> |

## Contents
- [Install](#install)
- [Where the data comes from](#where-the-data-comes-from)
- [Prices and currencies](#prices-and-currencies)
- [Trades](#trades)
- [Collection and binders](#collection-and-binders)
- [Orders and purchase prices](#orders-and-purchase-prices)
- [Trade binder](#trade-binder)
- [Wishlist](#wishlist)
- [Scan tab](#scan-tab)
- [Commander decks](#commander-decks)
  - [Deck stats, win turn and power level](#deck-stats-win-turn-and-power-level)
  - [Rule-zero cards](#rule-zero-cards)
  - [Editing decks and local decks](#editing-decks-and-local-decks)
- [Games: life tracker and game records](#games-life-tracker-and-game-records)
- [Sync between phones](#sync-between-phones)
- [Archidekt collection sync](#archidekt-collection-sync)
- [Backups and restore points](#backups-and-restore-points)
- [Rebuilding](#rebuilding)
- [License and disclaimer](#license-and-disclaimer)

## Install
[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/joelvdc/MTG-trading-tracker)

**With [Obtainium](https://github.com/ImranR98/Obtainium)** (recommended): tap the button above on your phone, or add
`https://github.com/joelvdc/MTG-trading-tracker` as an app in Obtainium. It installs the app from this page's releases
and tells you when there's a new version. Pick `MTG-Trader-<version>.apk` (or the `-universal` one if that doesn't
install).

**By hand:** download `MTG-Trader-<version>.apk` from the [latest release](https://github.com/joelvdc/MTG-trading-tracker/releases/latest)
and open it on your phone (allow "install unknown apps" when asked). It's built for 64-bit ARM phones (practically every
phone from the last ~6 years); `MTG-Trader-<version>-universal.apk` runs on any device. A new version installs over
the old one and keeps your data.

## Where the data comes from
Everything is looked up live, so new sets show up without an app update.
- **Cards and pictures:** Scryfall. Pictures you've seen stay on the phone; on Wi-Fi the app also saves your decks' and
  wishlist's pictures for offline use (Settings, on by default, at most 600 MB).
- **Prices:** Cardmarket's public daily price guide (~26 MB a day), plus TCGplayer's market prices (with Scryfall's
  card data) and Card Kingdom's public price list (~10 MB a day). Cardmarket's weekly product list fills in the
  printings Scryfall doesn't link (The List, some surge foils, special foils' separate products).
- **Decks:** Archidekt (decklists, and changes to your own decks), [Commander Salt](https://www.commandersalt.com/)
  (brackets, scores and rule-zero card data), and optionally [EDH Power Level](https://edhpowerlevel.com/) or
  [ScrollVault](https://scrollvault.net/tools/commander-bracket/) for the power level. Mana symbols use the
  [Mana font](https://mana.andrewgioia.com) by Andrew Gioia (SIL Open Font License; symbols © Wizards of the Coast).
- **Recommendations:** [EDHREC](https://edhrec.com/) and [recommander.cards](https://recommander.cards/).
- **Preconstructed decks:** [MTGJSON](https://mtgjson.com/). **Exchange rates:** the European Central Bank.

Prices download once a day when the app opens and in the background (Settings can limit this to Wi-Fi or turn it off;
"Update prices now" always runs), with a progress bar above the tabs. The collection stays usable meanwhile. Card
Kingdom refuses some phones (HTTP 403, e.g. in Denmark); then the app uses a copy that a daily GitHub job
(`.github/workflows/card-kingdom.yml`) keeps on this repository's `card-kingdom-prices` branch.

## Prices and currencies
- **Price source** (Settings): Cardmarket, TCGplayer or Card Kingdom values your cards everywhere (totals, sorting,
  filters, trades). A card the source has no price for gets Cardmarket's, shown with "≈". Card Kingdom follows each
  copy's condition. Each card's page and the stats compare all three.
- **Condition-adjusted values** (Settings, off by default): a copy counts for a share of the price by condition
  (Near Mint 100%, Excellent 85%, Good 70%, Light Played 60%, Played 45%, Poor 30%, each adjustable), marked e.g.
  "EX 85%".
- **Currency** (Settings): show prices in euros, Danish kroner or US dollars, at the ECB's daily rates.
- **Typing a price in the currency you paid in:** every price field has a **€ ▾ / kr. ▾ / $ ▾** button. The amount is
  kept as typed (75 kr. stays 75,00 kr.) and in euros for totals, Archidekt and CSV files. When copies paid in
  different currencies join one stack, the app asks which currency the average is kept in.

## Trades
A trade has two sides, **You give** and **You get**, each with its cards, their value and the difference; add cards by
search or the scanner. The sides can show one above the other or side by side. Wishlist cards on the "You get" side get
a ★. **Complete** the trade to update the collection: given cards are taken from Unsorted first, and you choose the
binder for the received ones. Trades can be shared as text, and an agreed price can be set per card.

## Collection and binders
- **Binders:** the bar above the list shows **All**, **Unsorted** and each binder with its card count. Create, rename,
  delete, **merge** and **arrange** binders from the ⋮ menu; the order syncs.
- **Views:** list, compact (one line per card) or a grid of card pictures.
- **Sort** in up to three layers (e.g. colour, then name), by name, colour, type, mana value, rarity, set, number,
  value, date added, copies, or the latest price change (in % or money).
- **Filter** by colours, type, rarity, sets, finish, signed or altered, condition, language, value and whether the card
  is in a deck. The search field finds names, sets and foil types.
- **Selecting:** press and hold a card, tap more, then move them to a binder, add them to a trade, delete them, edit
  condition, language or price, or share, copy or export them. **Swiping** a card moves it or adds it to a trade (each
  direction can be changed in Settings). Everything can be undone right after.
- **Card page:** picture, price and trend, quantity, finish, binder, condition, language, **purchase price**, notes,
  **Signed** and **Altered**, the decks it's in and the other copies you own. Tap a picture to see it full screen;
  double-faced cards flip.
- **Value over time:** tap the total above the list for a chart per day (per binder too), the biggest risers and
  fallers, and **Paid and worth** (what you paid against today's value).
- **Stats:** totals, the colour pie, colour identities, rarity, types, mana curve, sets, release years, finish,
  condition, language, value per binder, your most valuable and most played cards and your Game Changers. Tap a slice
  to see those cards.
- **CSV import and export** with binders, purchase prices (as typed), signed and altered (ManaBox's columns are read).

## Orders and purchase prices
- **Import an order** (⋮ menu): a CardTrader order (Excel) or a Cardmarket shipment or monthly CSV. The app finds each
  card on Scryfall and shows the order first; cards it isn't sure about say **Check the printing**. The cards keep the
  order's finish, condition, language and price paid. Afterwards, wishlist cards in the order can be marked received.
- **Purchase prices from orders:** fills in what you paid for cards already in the collection (the average per card),
  showing every change first, with a choice where the file can't tell which copy it was.
- **Orders:** the history of your orders, checked packages and precons, with what you paid and what they're worth now.
  Older order files can be added to the list without changing the collection.
- **Add a preconstructed deck:** any Commander precon, its cards in their own printing, the price split over them by
  value.

## Trade binder
⋮ → **Make a trade binder** suggests spare copies to trade (what your decks use, wishlist cards and basic lands stay
home), ranked by value and by how much Commander players want them, with a nudge for rising prices. Set the maximum
number of cards, a minimum value and whether to keep one copy of each card; by default your decks keep their best copy.
**Swap** picks another copy of a card. Later, **Update trade binder** shows what to put in and take out, with the reason
for each. Signed and altered copies are never suggested. Nothing changes until you apply.

## Wishlist
The **★ Wishlist** in the binder bar holds the cards you want: any printing, or one printing and finish. It's in three
sections, **To buy**, **Ordered** and **Received**.
- **Swipe right** moves a card on: To buy → Ordered → Received → into the collection (asking printing, finish,
  condition, language, binder and price). **Swipe left** deletes. Every swipe can be undone.
- The ⋮ menu marks all ordered cards, or the cards you now own, as received, and clears received cards (also
  automatically after 1–12 months).
- **Price alerts:** set "Notify me at or below" on a card; you get a notification when it costs that much or less, once
  per drop. Compares the chosen price source and, with your own CardTrader API token, CardTrader's cheapest offer
  (optionally CardTrader Zero only).
- **Offline:** "Add card" suggests names from Cardmarket's list on the phone; the details follow once online.

## Scan tab
Scan a pile of cards (or add them by name) into a waiting list, then send some or all of them **to a binder**, **to a
trade**, **to a deck** or discard them.
- The newest card is on top with its price. When only the name was readable, **Choose printing** lists all printings
  with pictures and prices, and sets the finish.
- Cards from before 2015 are matched by their **set symbol**; when two symbols look alike, you choose.
- **Language:** "auto" (as printed) or one you choose for all scanned cards.
- **Check an order:** compares the scanned cards with the order's file: missing cards, wrong language or printing,
  extra cards and what to check by hand, with a problem report to copy for the shop and **Add to collection…** with
  the order's prices, condition and finish.

## Commander decks
**New deck** (Decks tab): paste or share an Archidekt link, pick decks from someone's Archidekt profile, or make a
**local deck** (empty, from a precon, from scanned cards, a copy of one of yours, or a pasted Moxfield list). The tab
has three sections: **Archidekt** (your own decks, editable), **Archidekt (read only)** (other people's) and **Local**.

The deck page shows the list by your Archidekt categories (or card type) with mana costs and prices, as cards with
pictures or one line per card. Labels mark game changers, combos, extra turns and land denial (red: they count for
the bracket) and tutors and fast mana (grey). Sections fold away. Each card is shown in a printing you own when you
have one (tap it to choose). Also on the deck page and its ⋮ menu:
- **Maybeboard**, **Cards I'm missing** (with their cost; to the wishlist or as a list), **Add to collection**.
- **Recommendations** from EDHREC or recommander.cards, filtered by type, owned and new cards; also for all decks at
  once. **Cards in several decks** shows which cards your decks share.
- **Combos:** every combo Commander Salt finds is flagged (COMBO 2 / COMBO 3+, red when it counts for the bracket),
  with what it does and a link to Commander Spellbook.
- **Refresh bracket and power level**, **Reload decklist from Archidekt**, and on the Decks tab **Update all decks from
  Archidekt** and **Re-score all decks**. The deck list sorts by name, power level, bracket or last change.

### Deck stats, win turn and power level
Folding **Stats** and **Tokens** sections on the deck page:
- **Mana curve**, **mana cost** by colour and **mana production** (each card counted once per colour it can make; fetch
  lands for what they can find in the deck).
- **Tokens** the deck makes, with the printing of your choice.
- **Casting the commander:** the chance per turn (1–7) that the commander can be cast and each colour is there, from
  10,000 games played alone (Frank Karsten's method with the deck's own cards: Commander mulligans, a land a turn, mana
  rocks, mana creatures and ramp).
- **Winning the game:** the first turn the deck wins (green), the turn it has won by in 4 of 10 games (gold) and in 9
  of 10, and the share of games won each turn. The app plays the deck alone 10,000 times for up to 15 turns against
  three players at 40 life, nobody blocking: it casts its cards (most expensive first), attacks with everything, and
  reads from the cards' text what they do: damage, drain, tokens, pumps, counters, equipment, card draw, ramp,
  Treasures, the graveyard, reanimation, copies, polymorph, cascade, planeswalkers, sacrifice payoffs, poison and
  Commander Salt's **combos** (with tutors fetching a missing piece). It wins when all three opponents are out (life,
  21 commander damage, poison, a combo, Maze's End). **How?** in the app lists what was counted. No removal or blockers,
  so it's a best case to compare decks by. Worked out in the background and kept until the deck changes.

**Power level source** (Settings): Commander Salt, EDH Power Level, ScrollVault or **This app**. It's used everywhere:
the Decks tab, sorting, the deck page, the power card and the printed cards. Brackets always come from Commander Salt.
- **EDH Power Level and ScrollVault** have no API, so the app opens their calculator out of sight and reads the result
  (a few seconds per deck). ScrollVault adds a margin (e.g. 6.2 ±0.5), its typical winning turn and a line for your pod.
- **This app:** a whole number from 1 to 10, worked out on the phone. **Speed** is the turn by which half the
  simulated games are won (with decimals: 38% by turn 8 and 62% by turn 9 is turn 8.5), on the turn scale of the
  r/EDH "Guide to Power Levels in EDH": turn 1 is 10, turn 3 is 9, turn 6 is 8, turn 8 is 7, turn 10.5 is 6, turn 13
  is 5, turn 15 is 4.5 (in between, it slides). Decks winning under half their games by turn 15 get 4 (35% or more),
  3 (15% or more), 2 (any) or 1. Then **what the win turn can't see**, from Commander Salt's 0–1 ratings, at most one
  point either way: (interaction − 0.25) × 2 + (consistency − 0.45) + (efficiency − 0.30) × 1.5 + (mana base − 0.90).
  The deck page shows how the number came about.

### Rule-zero cards
The **bracket** and **power level cards**, drawn by the app from Commander Salt's analysis: the brackets and their
criteria (game changers, combos, extra turns, land denial), the speed (the win turn), how the deck plays, saltiness,
mana base, interaction and win conditions. They show full screen to show your table, can be shared as pictures and
work offline. Decks tab ⋮ → **Bracket cards of all decks** / **Power level cards of all decks** shows every deck's card;
the 🖨 button there prints them as an **A4 PDF** at the size of a Magic card (nine per page, with cut marks, and an
ink saver option).

### Editing decks and local decks
Your own Archidekt decks (of the account logged in under Settings → Archidekt) and local decks can be changed in the
app: drag cards between categories, select several to move, put in the maybeboard or remove, add cards (search,
scanner, Scan tab or recommendations), and manage categories (add, rename, delete, hide). New cards go into Archidekt's
auto category (or by card type). Every edit can be undone, shows at once and is sent to Archidekt the way its own
editor does; offline, edits wait on the phone and go once online. On other people's decks, cards are added in the app
only. A local deck can be **sent to Archidekt** as a new deck (folder and visibility of your choice) or into one of
your decks; any Archidekt deck can be **detached** into a local deck. Deleting a deck in the app never deletes it on
Archidekt.

Commander Salt has no official API: the app makes the same requests as its website, so this part may break if the site
changes. Importing a deck adds it to commandersalt.com.

## Games: life tracker and game records
The **Games** tab keeps score in Commander games and remembers them. The tracker looks and works like the Lifetap app.
- **New game:** 2 to 6 players, their seats and decks (yours, friends' with a commander from Scryfall, or a guest's
  commander), and who starts (picked or at random). **Rematch** sets up the last table again.
- **The table:** each player's panel faces them, in a colour or their commander's picture, with life, − and +, and a
  **commander damage mini-grid** (partners split in two). Tap for ±1, hold for ±10. At 0 life, 10 poison or 21
  commander damage the app asks **Dead?**. A player's sheet has commander damage, poison, commander tax, the monarch,
  the initiative, day/night, energy and other counters.
- **The middle bar:** end the game, game settings, the round and game clock, history (with undo), the mana pool and
  **Dice & random** (dice, a coin, a random player or opponent; hold the die for a quick-roll ring).
- **Turns** (optional) with a gold frame for the active player; **pause** a game to continue later, on either phone.
- **Ending a game:** who won and how, then save and rematch. **Stats:** wins, win rates and lengths per deck and
  player, head to head, and a **Games** section on each deck page. Games can be edited, added by hand and exported as
  CSV.
- **Game settings** (starting life, layout, commander damage, controls, vibration, look) are kept for the next games
  and sync between phones.

## Sync between phones
Settings → **Sync with Nextcloud** keeps the collection, binders, wishlist, trades, decks, scans, games and preferences
the same on all your phones, through a file (`sync.json.gz`) in a folder of your own
[Nextcloud](https://nextcloud.com/). Prices and pictures aren't synced; each phone downloads those itself.
- **Connect:** enter your server address and log in in the browser; the app gets its own app password (revocable in
  Nextcloud). Then pick the folder; use the same one on every phone.
- **First sync:** when both sides hold data, choose: merge both, use the Nextcloud copy, or start from this phone.
- **After that** changes are merged item by item (the most recent change wins, deletions carry over). Automatic sync
  runs when you open the app, shortly after a change, when you leave it and every hour (optionally only on Wi-Fi).
- When the sync file's format changes with an update, older versions stop syncing until they're updated, so no data is
  lost.

The password is stored encrypted with a key kept in the phone's keystore.

## Archidekt collection sync
Settings → **Archidekt collection** keeps your [Archidekt](https://archidekt.com/) collection the same as the app's,
both ways. Archidekt holds the whole collection (it has no binders or wishlist).
- **Log in** with your Archidekt user name and password (if you use Google to log in there, set a password first). The
  app keeps only Archidekt's login token, encrypted.
- **First sync:** if one side is empty it gets the other's cards; if they differ, choose: the app replaces Archidekt,
  Archidekt replaces the app, or decide card by card.
- **After that** the app compares both sides with the last sync, so each change goes the right way: added, removed and
  changed cards, counts and purchase prices. Cards from Archidekt go to Unsorted or a "From Archidekt" binder.
- **Careful by design:** a card changed differently on both sides waits for you, a sync removing more than 20 copies
  waits for "Go ahead", and a backup is made before every sync that changes the app. Each sync has a report.
- **Labels:** binders can become Archidekt labels; signed and altered copies are separate entries labelled "Signed" or
  "Altered". Conditions are matched by grade (NM, LP, MP, HP, Damaged).
- **Several phones:** with Nextcloud sync on, every phone can sync with Archidekt (one at a time); without it, use one
  phone.
- **Automatic sync** (optional): when you open the app (at most once an hour) and after you leave it.

This uses the same connection as Archidekt's website, which isn't an official interface: if Archidekt changes it,
syncing may stop until the app is updated.

## Backups and restore points
Settings → **Backups** keeps restore points of everything the app syncs.
- Made **every day** when something changed, and before big changes (the first Nextcloud sync, Archidekt syncs, CSV and
  order imports, restoring). **Back up now** makes one you keep.
- Stored next to the Nextcloud sync file (for all your phones), or on the phone or in a folder you choose; **Save to a
  file…** and **Restore from a file…** work with any file.
- Kept: the latest 10 automatic backups and one per week for 8 weeks.
- **Restoring** shows what differs, backs up the current state first, then replaces the app's data.

Settings → **Appearance**: same as the phone, light or dark.

## Rebuilding
Requires JDK 17+ and the Android SDK (installed at `%USERPROFILE%\Android\sdk`, see `local.properties`).
```
set JAVA_HOME=C:\Program Files\Java\jdk-18.0.2
gradlew assembleRelease
```
APKs land in `%LOCALAPPDATA%\mtgtrader-build\app\outputs\apk\release\` (kept out of OneDrive on purpose).

**Keep `keystore/` and `keystore.properties` safe and private.** Android only installs an update over the existing app
if it is signed with the same key; losing it means uninstalling (and losing app data) to install a new version.

- **Tests:** `gradlew testDebugUnitTest` (logic) and `gradlew connectedDebugAndroidTest` (scanner, needs a device).
- **Screenshot tests:** `ScreenshotTest.kt` draws parts of screens with sample data (Robolectric and Roborazzi) and
  compares them with `app/src/test/screenshots`. `gradlew verifyRoborazziDebug` compares, `gradlew
  recordRoborazziDebug` redraws after an intended change.
- **Releases:** `.github/workflows/release.yml` builds, signs and publishes a release with the keystore in the
  repository's Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`): Actions → Release
  → Run workflow releases the `versionName` in `app/build.gradle.kts` with `release-notes/<version>.md` as the notes.
  Every pull request runs the unit tests (`tests.yml`).

## License and disclaimer
The code is released under the [MIT License](LICENSE). That covers this app's code only, not the card data, names or
images it shows.

MTG Trader is unofficial Fan Content permitted under the Fan Content Policy. Not approved/endorsed by Wizards. Portions
of the materials used are property of Wizards of the Coast. ©Wizards of the Coast LLC.

The app isn't affiliated with or endorsed by Scryfall, Cardmarket, CardTrader, Archidekt, Commander Salt, EDH Power
Level or ScrollVault; it uses their public data. Prices are for guidance only.
