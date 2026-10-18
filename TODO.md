# To-do list for MTG Trader

Ideas the owner (joelvdc) is collecting. **Nothing here is built until the owner gives the green light**; then
everything on the list is planned together and carried out (see `CLAUDE.md`).

How to use this file:
- Add new ideas under "Collecting", newest last, with the date and the owner's own wording.
- "Open questions" under an item are things to settle with the owner before building it.
- When an item is released, move it to "Done" with the version it shipped in.

## Collecting

### Release plan
*Agreed with the owner on 2026-10-13, when the green light for the whole list was given.*

1. ~~MTG Trader 1.32: sort by value change, Cardmarket orders, package check, purchase prices from orders.~~ Released.
2. ~~MTG Trader 1.33: order history, profit and loss, add a precon, condition-adjusted values.~~ Released.
3. **Poké Trader 1.20:** sort by value change (item 1 on Poké Trader's list; the code is nearly the same as MTG's
   1.32). The owner said on 2026-10-13 to wait with this one: don't start it until they say so.

4. ~~MTG Trader 1.42: compact deck view, folding sections, the maybeboard (stored on the phone, as the owner chose on
   2026-10-16), adding cards from the deck page.~~ Released.
5. ~~MTG Trader 1.43: the compact deck view on one line per card, with mana costs (Mana font), and labels that
   give way to long names (picked by the owner from mockups on 2026-10-16).~~ Released.
6. **MTG Trader 1.46: changing decks on Archidekt** (below). Planned with the owner on 2026-10-16, after 1.42; moved
   to 1.44 when the compact view's redo became 1.43, to 1.45 when the deck stats became 1.44, and to 1.46 when choosing
   token printings became 1.45 (2026-10-17). Not started: the owner settled all its questions on 2026-10-17 and
   added local decks, auto categories, Moxfield text import, detach/link, folders and the Decks tab's sections.
   Also in 1.46 (owner, 2026-10-17): the combo flags for combos that don't count for the bracket, and all decks'
   bracket or power level cards from the Decks tab (both below), and when the deck can cast its commander and has its
   colours (owner, 2026-10-18, below).
   Green light given 2026-10-18; the full plan with 16 mockups was sent the same day. The owner chose (2026-10-18):
   **one release**, the bracket cards of all decks as small cards (option A), the commander odds as a table (option
   B), and new Archidekt decks **unlisted** by default (private decks can't be scored by Commander Salt).
   Being built.
7. ~~MTG Trader 1.44: deck stats (mana curve, mana cost, mana production as sources, the deck's tokens), asked for and
   planned with the owner on 2026-10-17.~~ Released.
8. ~~MTG Trader 1.45: choosing which printing of a token a deck shows (an "Other printings" button on the token,
   remembered per deck and synced), asked for and approved by the owner on 2026-10-17.~~ Released.
9. **MTG Trader 1.47: life tracker and game records for Commander** (below). Asked for by the owner on 2026-10-17;
   a draft plan is below; the owner answered its questions the same day. Comes after 1.46.

### Changing decks on Archidekt (planned as MTG Trader 1.46)
*Added 2026-10-16. MTG Trader only.*

The owner's words: "I would also like to push the added cards in the app to archidekts maybeboard. And lastly I would
like to be able to just edit the deck in the app [...] like being able to add cards and dragging them between
categories, add or remove categories, etc. I think that it is too much to have all the functionality that archidekt
has, but at least provide some basic edit capabilities."

What was proposed (only for decks owned by the Archidekt account logged in under Settings; other people's decks stay
read-only apart from "Added in app"):
1. **Push "Added in app" cards to Archidekt's maybeboard**: one button on the deck page. Once Archidekt has them,
   they leave "Added in app" and show in the maybeboard section (from 1.42).
2. **Basic editing**: change a card's quantity, remove a card, move a card to another category, add a card straight
   into a category, add / rename / delete a category (deleting moves its cards to another one). Needs internet; every
   change can be undone.

How big: about as much work as the four 1.42 items together. The app already logs in to Archidekt (collection sync),
but Archidekt has no official API for changing decks: the app would use the one Archidekt's own website uses
(`PATCH /api/decks/<id>/modifyCards/`, now `.../modifyCards/v2/`, which answered "please log in" when checked on 2026-10-16). It can't be tested
from a cloud session, so the owner tries it on a spare deck first.

Answered by the owner on 2026-10-17:
- **Where edits are saved:** asked whether saving to Archidekt and reloading would slow the app down. Claude's answer:
  not noticeably, because an edit shows on the phone at once and goes to Archidekt in the background (one small
  request). Only the changed card goes, with no reload of the whole deck (the full reload, with the Scryfall lookup
  and the scoring, takes a few seconds, as today's "refresh" does). Recommended, still to confirm: Archidekt stays the
  real decklist.
- **Moving cards between categories:** both. Select one or several cards and move them, and also drag them.
- **Offline:** "save edits locally and then push them to Archidekt when online."
- **Other people's decks are not editable.** Only decks of the Archidekt account logged in to the app, and the new
  local decks (below).
- **New idea, local decks:** "Could we allow for local decks, that could eventually be pushed to a deck on archidekt
  (or create a new deck on the user's account that is logged in the app)? That deck could also just live locally if
  the user decides so." So: a deck made in the app (empty, or from a precon, or from scans?) that can stay on the phone,
  or later be sent to Archidekt as a new deck on the logged-in account, or into one of its existing decks.

Settled with the owner on 2026-10-17:
- **Waiting edits when Archidekt changed meanwhile:** sent one by one on top of Archidekt's list; an edit that no longer
  fits (a card already removed there) is skipped and reported.
- **Both phones:** only the phone that made an edit sends it to Archidekt; the other phone sees it at the next sync.
- **Local decks' database change:** OK (both phones must run the new version before they sync).
- **Pushing a local deck into an existing Archidekt deck:** ask each time: add its cards, or replace that deck's list.
- **How a local deck starts:** empty, from a precon, or from scanned cards. The Scan tab can already send cards to a
  deck; it gets the option to make a new deck from them.
- **One release** for all of it, for now (on 2026-10-17 the owner said: one release or split is decided later).

New question from the owner (2026-10-17): "Archidekt has an option to auto categorize cards, could that function be
mirrored in the app? I actually love their auto categories and really use them a lot when drafting a decklist."
- What Archidekt does: when a card is added, it goes into a category chosen from about 30 broad ones (Ramp, Draw,
  Removal, Protection…), picked from how Archidekt's users most often categorise that card. Not every card has one
  (newer or rarely played cards). It's a setting on the Archidekt account; existing decks can be re-sorted by
  dragging all cards onto "Auto".
- Checked 2026-10-17: Archidekt's public card data has this category per card (`oracleCard.defaultCategory`, from
  `/api/cards/v2/?uids=...`, no login needed; the app already reads this for the collection sync). Sol Ring → Ramp,
  Cultivate → Ramp, Swords to Plowshares → Removal, Wrath of God → Removal, Rhystic Study → Draw, Lightning Greaves
  → Protection. So the app can use exactly Archidekt's categories, and they stay the same as on the website.

Settled with the owner on 2026-10-17:
- **Auto categories:** every card added in the app (to Archidekt decks and local decks) goes into its auto category,
  with a switch in Settings to turn it off; a deck also gets "Auto-categorise all cards", which asks first.
- **Cards without an auto category** go into a category by card type (Creature, Instant…); the owner says that is
  what Archidekt does too.
- **A new deck can also start as a copy** of an existing deck in the app (so: empty, a precon, scanned cards, or a copy).
- **The card page opened from a deck** shows the card's category and lets the owner change it (on decks that can be
  edited).
- **Health check:** this release adds Archidekt's deck-editing addresses and its card data's `defaultCategory`, so the
  weekly health check's instructions get these when it is built.

Added by the owner on 2026-10-17, for 1.46: "When creating a new deck in the app, I would like to be able to import
a decklist from moxfield too."
- Moxfield has no connection the app can use (see "Moxfield connection" below), so not by link. Instead the decklist
  as text: Moxfield's Export gives the list as plain text ("1 Sol Ring (CMR) 263", foils marked "*F*", sections such
  as SIDEBOARD/MAYBEBOARD). The app reads that pasted text or a .txt file into a new local deck, with the exact
  printings where the text has set and number, and auto categories for the cards. The same reader takes most other
  sites' text lists (Archidekt, MTG Arena, EDHREC). No new outside service, so no health check change.

Other people's Archidekt decks (asked 2026-10-17): today a deck imported from anyone's Archidekt link stays linked
to it. "Refresh" and "Update all decks" reload it from Archidekt (so it follows its owner's changes), keeping the
cards "Added in app"; if the owner makes it private or deletes it, the update says it failed and the app keeps the
last version. In 1.46 such decks stay read-only.

Settled with the owner on 2026-10-17:
- **Moxfield import:** choosing "Import from Moxfield" when making a new deck shows a field to paste the decklist
  into (paste only). If the pasted list has Moxfield tags, the app asks: use those as categories, or Archidekt's auto
  categories.
- **Which decks can be edited:** a deck imported from another Archidekt user is read-only (and keeps following its
  owner's changes). A deck of the logged-in user can be read and edited.
- **Detach:** any deck from an Archidekt link (own or someone else's) can be "detached": it becomes a local deck,
  editable, no longer linked or reloaded. (So no separate "copied from" link is needed.)
- **Link a local deck:** a local deck can be linked to an existing Archidekt deck of the logged-in user (asking each
  time whether to add its cards or replace that deck's list, as settled above) or create a new deck on that account.
- **Folders:** the owner asks whether the app can see and change the folders the decks are in on Archidekt.

Checked 2026-10-17 (Archidekt, read without logging in):
- Every deck tells its folder (`parentFolder` on /api/decks/<id>/, `parentFolderId`/`parentFolderName` in the deck
  list). Folders can be read at /api/decks/folders/<id>/ with their name, parent, subfolders and decks: the owner's
  "Home" holds 00. Retired, 01. Ready to Play (19 decks), 02. Brewing, 03. May Brew, 04. Inspiration, 99. MTGO.
- Archidekt's website code creates a deck with POST /api/decks/v2/ (with `parent_folder`, `private`, a name and the
  format), copies one with POST /api/decks/copy/, changes a deck's details with PATCH /api/decks/<id>/update/,
  changes its cards with PATCH /api/decks/<id>/modifyCards/v2/ (the address moved to "v2" since the 2026-10-16
  check) and makes a category with POST /api/decks/createCategory/.
- Not found yet: the addresses for creating, renaming, moving or deleting a folder (that part of the website's code
  wasn't loaded on its home page). Likely there, to be checked when building.

Settled with the owner on 2026-10-17:
- **The Decks tab has three sections, not Archidekt's folders:** "Archidekt" (the logged-in user's decks),
  "Archidekt (read only)" (other people's decks) and "Local". Each section can be folded away.
- **New decks on Archidekt are private** by default. The app can also change whether one of the owner's decks is
  private, unlisted or public (Archidekt's website does this through PATCH /api/decks/<id>/update/; to confirm the
  field names when building).
- **Importing from Archidekt:** the import screen gets a switch "Make it a local deck", for importing a deck straight
  as a local deck (the same as importing it and then detaching it).
- **Size:** the owner wants everything on the list first; whether 1.46 is one big release or is split is decided
  later.

- **Archidekt folders:** pick the folder when sending a local deck to Archidekt as a new deck; a deck can be moved to
  another folder from the app. Making, renaming and deleting folders stays on the website.
- **Deleting a deck in the app** only removes it from the app, never from Archidekt. Asked by the owner on 2026-10-17;
  the delete question already says so today ("The deck is only removed from this app; it stays on Archidekt and
  Commander Salt."). In 1.46 the message fits the kind of deck: a local deck exists nowhere else, so deleting it is
  for good (it says so, plus an undo); a deck with edits still waiting to be sent warns that those edits are lost.

- **Deleting on Archidekt:** never from the app (owner, 2026-10-17: "deleting archidekt decks is done through
  archidekt").

No open questions left. Still waiting for the owner's green light. Mockups (selecting, dragging, the card page's
category, making a new deck) come with the plan.

### Combos that don't count for the bracket (planned for MTG Trader 1.46)
*Added 2026-10-17. MTG Trader only. The owner said on 2026-10-17: "I want this for release 1.46".*

The owner's words: "add a flag for not bracket relevant combos. Maybe the same combo flag but in another color?"

Background (asked about the Auntie Ool deck, archidekt.com/decks/20263351, on 2026-10-17): the app's "COMBO" flag
shows only the combos Commander Salt counts for the bracket (`details.brackets.categories.twoCardCombos` and
`earlyGameInfiniteCombos`, read in `data/SaltCard.kt` SaltCardParser). Commander Salt's full combo list
(`details.combos.list`: each with `cards`, `categories`, `spellbookUri`, `complexity.requiresCardsOnBoard`) has more:
8 for Auntie Ool, of which 2 count (Devoted Druid + Mikaeus, the Unhallowed; Flourishing Defenses + High Perfect
Morcant). Tree of Perdition + Soul Immolation and All Will Be One + Everlasting Torment are in the list but not
counted: every two-card combo there with `requiresCardsOnBoard: true` (needs more than its two cards on the
battlefield) is left out. Salt doesn't publish its rule; this is read from its data.

Proposed:
- Read the whole combo list too, and flag the cards of combos that aren't counted with the same "COMBO" label in
  another colour, so the bracket ones still stand out (settled below).
- The card's "Combo with …" line says which kind it is ("counts for the bracket" or "doesn't count for the bracket"),
  with a link to the combo on Commander Spellbook (`spellbookUri`).
- No new service (the data is already in Commander Salt's answer); the health check gets the `details.combos.list`
  fields added.

Settled with the owner on 2026-10-17 ("Just choose a color that does not clash with other flags. Idk, blue or
something." / "3 card combos should get flagged" / "The deck should count total number of combos and bracket
relevant combos."):
- **Colour says whether it counts, the text says how many cards.** Today's flags: red for WotC's bracket rules (GAME
  CHANGER, COMBO, EXTRA TURN, LAND DENIAL), grey for TUTOR and FAST MANA, purple for the foil mark. So blue is free:
  - the label stays "COMBO" with the number of cards added as a small digit: "COMBO 2" for two cards, "COMBO 3+"
    for three or more (owner, 2026-10-17). Red = Commander Salt counts it for the bracket (two-card and early-game combos; an
    early-game one can have three cards), blue = it doesn't.
  - The owner found "2-CARD COMBO" / "3-CARD COMBO" too big (2026-10-17) and left the design to Claude: so the flag is
    barely wider than today's "COMBO". In the compact view, when space runs short, it shortens to "C2" / "C3+" and then
    to a red or blue dot, like the other flags (`DeckMarks` in `ui/DeckScreen.kt`). Shown as mockups when planned.
  A card in several combos shows one flag: red before blue, then the fewest cards; its "Combo with …" lines list them
  all, each saying whether it counts for the bracket.
- **The deck counts both:** the total number of combos and how many count for the bracket (e.g. "8 combos, 2 count for
  the bracket" for Auntie Ool). The bracket summary's "combo pieces" stays bracket combos only.

No open questions left; the colours and short forms are shown as mockups when it is planned.

### All decks' bracket or power level cards (planned for MTG Trader 1.46)
*Added 2026-10-17. MTG Trader only.*

The owner's words: "Another thing for 1.46 release: View all bracket cards or power level card under the decks page,
maybe an option under the 3 dots."

What exists today: each deck's page has two buttons, "Bracket" and "Power level" (`RuleZeroCard` in `data/Decks.kt`,
`RuleZeroDialog` in `ui/RuleZeroCards.kt`), which show Commander Salt's rule-zero card for that one deck. The Decks
tab's ⋮ menu has "Update all decks from Archidekt", "Re-score all decks on Commander Salt", "Recommendations for
all decks" and "Cards in several decks".

Proposed:
- Two new items in the Decks tab's ⋮ menu: "Bracket cards of all decks" and "Power level cards of all decks".
- They open a scrolling list of every scored deck's card, one below the other, in a compact version of the card (so
  several fit on the screen; worked out with mockups). Tapping a card opens it full size; from there swipe left and
  right to the other decks' cards, or go back to the list.
- Only the owner's own decks: the "Archidekt" and "Local" sections, in the Decks tab's order; not "Archidekt (read
  only)". Decks Commander Salt hasn't scored (e.g. local decks never sent to Archidekt) are left out, with a line
  saying how many.
- No new service (the same Commander Salt data the deck page uses), so no health check change.

Settled with the owner on 2026-10-17: "All cards in one scrolling list (maybe make a compacted version for this),
when clicking on a card allow for swiping or go back to the scrolling list. Only my own decks, archidekt and local."
No open questions left.

### Life tracker and game records for Commander (planned for MTG Trader 1.47)
*Added 2026-10-17. MTG Trader only.*

The owner's words: "Life tracker for commander games. New tab in the app. UI and usage design like the lifetap app.
Track commander decks games, wins and losses just like the mythic tools or playgroup.gg app. Track games length,
average length for each deck, etc, whatever stats are relevant to track. Track turns like the playgroup.gg APP, I
think this could be optional in the app. Can you work out a plan?"

Draft plan (Claude, 2026-10-17; to be worked out with mockups when the list gets the green light):
1. **A new "Games" tab** next to Decks. It opens on "New game", the game in progress (if any) and the game history
   with the stats.
2. **Setting up a game:** 2 to 6 players (4 by default), starting life 40, and per player a name and a deck. The
   owner's own decks are picked from the app's decks (the "Archidekt" and "Local" sections, so the commander and its
   picture come with it); friends are saved as players with their own decks (a name and a commander, found by name on
   Scryfall, or an Archidekt link). The last table setup is remembered, so a rematch is one tap. Who starts: picked by
   hand, or at random with a short animation.
3. **The life tracker, like Lifetap:** the whole screen split in one coloured panel per player, each turned to face
   its player around the table (layouts for 2 to 6 players, the phone lying flat in the middle). The commander's
   picture or a colour as the panel's background (see the design notes). Tap for −1 / +1 (left/right or above/below,
   see the design notes), hold for ±10 (see the design notes); the change since the last pause shows next to the life total. The screen stays on.
   Per player, from a small button on the panel: commander damage from each opponent (both commanders for partners;
   commander damage also takes life), poison, commander tax, and optional counters (energy, experience, rad). Table
   items: monarch, the initiative, day/night, a dice roll and a coin flip. A player at 0 life, 21 commander damage
   from one commander or 10 poison is asked whether they're out (see the design notes); undo for the last changes.
4. **Turns (optional, a switch in the game setup and a default in Settings), like playgroup.gg:** the active player's
   panel is marked, a "next turn" tap passes the turn in seating order, and the app counts the rounds and times each
   turn (pause button for breaks). Turned off, the game still records its start, end and length.
5. **Ending a game:** the last player standing wins (or the owner picks the winner, or a draw); optionally how it was
   won (combat, commander damage, combo, poison, alternative win, everyone conceded) and a note. Recorded by itself:
   date, length, players, decks, seats and who started, the order players went out (and on which turn, with turns on),
   final life totals. A past game can also be added by hand (without the tracker), and a recorded game can be changed
   or deleted.
6. **Stats** (in the Games tab, and a "Games" section on each deck's page):
   - per deck: games, wins, losses, win rate, average game length, average length of its wins, average number of turns
     and the turn it usually wins on (with turns on), average place, how its games ended, its last games;
   - per player and against each opponent (head to head), wins when starting first or by seat, games per month;
   - the deck's bracket and power level (from Commander Salt) next to its results, so it's easy to see whether a deck
     is too strong or too weak for the group;
   - all decks in one table that can be sorted (most played, best win rate, longest games…).
7. **Storage and both phones:** games are stored in the app's database (new tables: players, games, the players of a
   game, turns) and go into backups and the Nextcloud sync, so both phones see the same history. That is a database
   change: both phones must run 1.47 before they sync (as for 1.46).
- **Health check:** no new service, but one new Scryfall address (see the design notes).
- **Size:** about as big as the deck stats (1.44); one release if the owner agrees, otherwise the tracker first and
  the stats right after.

Settled with the owner on 2026-10-17:
- **The tab bar:** a sixth tab "Games" is fine for the owner, but only decided after seeing the mockups (so the plan
  shows the six-tab bar on a small and a large phone, next to the alternative with Settings behind a gear button).
- **Friends:** yes, friends can be added with their names and commanders ("would be neat"). Their decks are a name
  plus a commander found on Scryfall (an Archidekt link optional), so head-to-head stats work.
- **One phone** in the middle of the table, like Lifetap. No connection between phones during a game.
- **Counters:** the most common ones can be switched on per game (commander damage, poison and commander tax always
  there; energy, experience, rad, storm, monarch, the initiative, day/night as options), plus "Custom counter": the
  owner names it, and it works like the others.
- **Turns:** off by default (switch in the game setup, default in Settings). An option shows a clock during the game:
  the game's time, and with turns on the current turn's time on the active player's panel.
- **No import of old games.** Instead **export**: the game history as a spreadsheet file (one line per player per
  game, with date, deck, commander, place, length, turns), saved or shared like the app's other exports.
- **Formats:** Commander first, but the starting life can be set (e.g. 20 for two-player games); commander damage can
  be switched off for non-Commander games.

Design notes from the owner (2026-10-17):
- **Backgrounds:** one setting in Settings for all games (owner, 2026-10-17: a general setting, not a choice per
  game), never mixed at one table ("I don't like the idea of mixing a background commander picture with plain
  colors"):
  - **Commander pictures:** every panel shows a commander's art (Scryfall's art crop). A player without a commander
    set (a friend added by name only, or no deck picked) gets a random commander's art instead, picked when the game
    is set up and kept for the whole game; tapping it during setup picks another. Random pictures come from Scryfall
    (`/cards/random?q=is:commander`, a new address for the app). For when the phone is offline, the app keeps a small
    store of random commander pictures on the phone (about 20, refreshed now and then when online), just for
    backgrounds, so they aren't limited to the owner's own decks and show other commanders by default.
  - **Plain colours:** a different colour for every player, from a set of six that look good together and stay easy
    to tell apart (also for colour-blindness), with the life total in white on top. A player can tap their panel's
    colour to pick another.
  Shown as mockups (commander pictures, plain colours).
- **Low life animations:** when a player's life is below 25% of the starting life (below 10 at 40), their panel gets
  an animation, like Mythic Tools' ghost. A few to choose from in Settings (for example a faint ghost drifting up
  through the panel, a slowly pulsing dark edge, a fading or cracking life total), each with a preview, or off.
- **Changing life:** two ways, chosen in Settings: tap to the **left / right** of the life total, or **above / below**
  it (always as seen by the player the panel faces). Holding: −10 / +10 (as Lifetap does, owner 2026-10-17), and
  keeping the finger down repeats it; a setting can make holding count by 1 instead. A setting also shows
  **− and + symbols** on the panels, for those who like to see where to tap. Shown as mockups (both ways, with and
  without the symbols).
- **A player who is out:** when a player's life reaches 0 or below (or 21 commander damage from one commander, or 10
  poison), a button appears in their panel: "Dead?" (owner, 2026-10-17, like Lifetap and Mythic Tools). Yes: the
  panel turns dark with a big skull, the player counts as out (their place and the turn are recorded) and their panel
  stops reacting to taps, except a small "Undo" to bring them back. No (e.g. life gain is coming): the button goes
  away until the life changes again. The low life animation stops once a player is out. Shown as a mockup.
- **Health check:** the random commander pictures add Scryfall's `/cards/random` (the art crops are already used for
  the decks' pictures), so the weekly health check gets it when this is built.

Open questions: none for now; the mockups come with the plan (tab bar, table layouts for 2–6 players, backgrounds,
counters, the low life animation, the end-of-game screen, the stats).

### When the deck can cast its commander and has its colours (planned for MTG Trader 1.46)
*Added 2026-10-18. MTG Trader only. The owner said on 2026-10-18: "Add it to 1.46".*

The owner's words: "Regarding mana production, I base myself on some rules I found online while ago. Like, if you
want white by turn 2, you need at least 21 pips to have that reliable. By turn 3, then 20 pips, and so on." Then:
"try to give an estimation of the probability when the deck is able to cast the commander. Based on lands and ramp
spells and commander's mana cost. Instead of focusing on the most demanding cards for each color. Also add when the
different deck colors are likely to be available, which turn number."

Background (researched 2026-10-18):
- The rule is Frank Karsten's "How Many Sources Do You Need to Consistently Cast Your Spells? A 2022 Update"
  (tcgplayer.com). It counts sources (cards that make the colour), not pips. For 99-card decks, about 90% on curve:
  1C on turn 2 needs 19, 2C 18, CC 30, 1CC 28, 2CC 26, CCC 36 sources; the owner's 21/20 are the older 2018 numbers
  (the 2022 update counts Commander's free mulligan and the first player's draw). It assumes 41 lands; mana rocks
  count ¾, mana creatures ½. Claude's own simulation of Karsten's model gave the same numbers within one source.
- Instead of a table, the app can simulate the deck itself: shuffle the real 99, mulligan like Karsten (free
  mulligan, then London), draw (also on turn 1), play a land each turn (enters-tapped lands come in tapped), cast
  mana rocks, mana creatures (usable the next turn) and land ramp spells (Cultivate, Farseek, Three Visits…) when it
  can, and note the first turn the commander's cost can be paid with the right colours, and the turn each colour
  is first available. Thousands of games take about a second on a phone. It plays "goldfish" (no opponents, no
  removal), so the numbers are a best case for comparing decks and changes, not a promise.
- Tried on Auntie Plague (archidekt.com/decks/20263351, Auntie Ool {1}{B}{R}{G}, 38 lands, 2 rocks, 4 mana
  creatures, 3 ramp spells; 30,000 games): commander out by turn 3 in 39%, turn 4 79%, turn 5 91%, turn 6 95%.
  With only the lands it would be 0% / 59% / 81% / 88%, so the ramp adds about 20 points on turn 4. Colours: black
  96%, red 92%, green 94% by turn 3; all three colours 66% by turn 2, 84% by turn 3, 90% by turn 4.

Proposed (in the deck page's Stats section, under mana production; worked out with mockups):
- **Casting the commander:** one line, e.g. "Auntie Ool (4 mana): turn 4 in 79% of games, turn 5 in 91%", with the
  chance per turn as a small row of bars (turns 2 to 8). For partners: each commander, and both. The commander's
  cost as printed (commander tax isn't counted: it's the first cast).
- **Colours:** per colour of the deck the turn it's usually there and the turn it's there in 9 of 10 games
  ("Red: usually by turn 1, 9 in 10 by turn 3"), and the same for all colours together.
- **Counted:** lands (also double-faced cards with a land side and fetch lands, which find the colours they can
  fetch), mana rocks and creatures with "{T}: Add" costing up to 4, and land ramp spells costing up to 4. Treasure
  makers, rituals and cost reducers aren't counted (said in the ⓘ, which also explains the method and links to
  Karsten's article). Hybrid costs can be paid with either colour; Phyrexian mana is left out.
- The result is worked out when the stats are opened (and again after edits), not stored or synced.
- No new service: it uses the mana costs, mana production and card text the app already has (since 1.43/1.44), so no
  health check change.
- **Karsten's table stays out** (owner, 2026-10-18): the ⓘ explains the method and links to his article, without
  the table.

No open questions left.

### Moxfield connection (some day, not planned)
*Added 2026-10-17. MTG Trader only. The owner said on 2026-10-17: keep it for the future, not planned for a release;
the idea and its open questions can be picked up again when wished.*

The owner's words: "I would also be interest in a moxfield connection to the app. Connecting decks and collection to
moxfield. Syncing between app, archidekt and moxfield, if both are logged in and connected to the app. The deck
categories work differently on moxfield, I think it uses tags instead and has no automatic categories or tags. But
if we do this I would still be interested in using the archidekt auto categories for moxfield decks."

What was found (2026-10-17):
- Moxfield has **no official API** for other apps. Its website uses a private one (`api2.moxfield.com`), which sits
  behind Cloudflare's bot check: from a cloud session every address answered 403, the website itself too. Moxfield
  support can put an app's name (its "user agent") on an allow-list; one developer reported that even then logging in
  through it was blocked by a captcha (github.com/moxfield/moxfield-public issue 143). So the first step is the owner
  asking Moxfield support for access for MTG Trader, and nothing can be promised before their answer.
- Moxfield sorts a deck into boards (main, sideboard, maybeboard, "considering") and the owner's own tags per card;
  it groups by card type by default. Archidekt's auto categories (from 1.46) can be given as Moxfield tags.

Draft plan, in steps (each its own release):
1. **Moxfield decks, read only:** add a deck by its Moxfield link or from the logged-in account's list; it shows like
   an Archidekt deck (cards, maybeboard, stats, tokens, value, what the collection is missing). Scores: Commander Salt
   also reads Moxfield links (to check when building).
2. **Moxfield collection sync:** the same two-way sync the app has with Archidekt's collection (quantities, finish,
   condition, language, purchase price), for the logged-in Moxfield account.
3. **Changing Moxfield decks:** the 1.46 editing (quantities, boards, moving cards, tags as categories, auto
   categories as tags), sending a local deck to Moxfield as a new deck.
- Health check: each step adds Moxfield addresses to the weekly health check.

Open questions:
- **Asking Moxfield for access:** OK for the owner to write to Moxfield support (Claude drafts the message)?
- **How the owner logs in to Moxfield:** email and password, or Google / Apple? The app can only log in with a
  password; Google/Apple sign-in would need a password set on the Moxfield account first.
- **One deck on both sites:** should a deck be able to live on Archidekt and Moxfield at the same time and be kept
  equal (every change sent to both), or does each deck have one home site (recommended: much simpler, no
  conflicts), with "copy this deck to Moxfield / Archidekt" as a one-off?
- **Collections:** keep the app, Archidekt and Moxfield collections all equal (the app in the middle), or is one site
  enough for the collection?
- **Categories on Moxfield decks:** Archidekt's auto category as one tag per card. Also keep Moxfield's own tags as
  the deck's categories in the app (they then show like Archidekt's categories)? A card can have several tags on
  Moxfield; the app shows a card under its first category today.
- **Which step first,** or all three together?

## Done

### Order history
*Released in MTG Trader 1.33 (2026-10-13). Built as settled: ⋮ → Orders in the collection, in `data/Orders.kt` and `ui/OrdersScreen.kt`. An order is one row with its cards as JSON (database version 14, sync and backup file format 3). "+" adds past order files without touching the collection; a Cardmarket month becomes one order per shipment. The owner's 17 precons of 13 October are in `precon-orders.json` in their order-files folder, in the app's own orders format, to add with "+". Restoring a backup from before 1.33 leaves the orders as they are.*

*Added 2026-10-12. MTG Trader only. Suggested by Claude, picked by the owner.*

Once the app reads CardTrader and Cardmarket orders (items 2 and 3), remember each order: date, shop, what was paid
in total, and what those cards are worth now. Today the price paid is stored per card (`purchasePrice`) and the order
itself is forgotten after import.

Settled with the owner on 2026-10-13 (no open questions left):
- Its own screen, opened from the three-dot menu of the collection.
- The order's date is read from the file where it has one (CardTrader's file name, each line of Cardmarket's monthly
  file); otherwise the app asks at import, with today filled in.
- The history syncs between phones and is part of backups. The owner said yes to the database change this needs
  (a new table; both phones must run the new version before they sync, so raise the sync file's format).
- Past orders are loaded too: a one-time import the owner starts in the app by picking the files kept on their PC
  (43 Cardmarket monthly files, 15 CardTrader orders).
- Confirmed by the owner on 2026-10-13: each of the 17 preconstructed decks priced that day becomes an order in the
  history (deck name, date, price paid), so profit and loss per deck works. The decks, prices and per-card shares
  are in the owner's folder (`dry-run/7-precon-prices-per-deck.csv` and `-per-card.csv`).

### Profit and loss
*Released in MTG Trader 1.33 (2026-10-13). Built as settled: "Paid and worth" with best and worst buys on the value screen (`data/ProfitLoss.kt`), and paid against worth per order in the order history. An order's cards without a price today are left out of both sides of its gain or loss.*

*Added 2026-10-12. MTG Trader only. Suggested by Claude, picked by the owner.*

The app stores what was paid per card and shows it on the card page. Total it: paid versus current value for the
whole collection and per binder, and the best and worst buys. Only cards with a purchase price count; say how many
cards have none.

Settled with the owner on 2026-10-13 (no open questions left):
- Part of the value screen, next to "Rising lately" and "Falling lately".
- Per order as well, in the order history: what was paid and what those cards are worth now.

### Condition-adjusted values
*Released in MTG Trader 1.33 (2026-10-13). Built as settled, in `Pricing.conditionPct` (`data/PriceSources.kt`): off by default, the six percentages in Settings (synced), applied wherever a copy's value is worked out, marked "EX 85%". Card Kingdom's own per-condition prices are left alone. The price change the collection sorts by (item 1) is not scaled.*

*Added 2026-10-12. MTG Trader only. Suggested by Claude, picked by the owner.*

Cardmarket's guide has one price per card whatever its condition, so a Played card counts as much as a Near Mint one
in the collection total. An optional percentage per condition would make totals more realistic (Card Kingdom already
has a price per condition, `PriceSources.ckCondition`).

Settled with the owner on 2026-10-13 (no open questions left):
- Default percentages NM 100%, EX 85%, GD 70%, LP 60%, PL 45%, PO 30%, adjustable in Settings.
- It applies everywhere a value is shown, trades and the trade binder included.
- Off until switched on; an adjusted price carries a small condition tag so it can be told apart.

### Add a preconstructed deck to the collection, with the price paid
*Released in MTG Trader 1.33 (2026-10-13). Built as settled, in `data/Precons.kt` and `ui/PreconScreen.kt`: Commander decks from MTGJSON, binder only, the price split by today's Scryfall euro prices with a 0.05 floor per copy, the shares adding up to the price exactly. The weekly health check watches MTGJSON since 2026-10-13. Still open for later: "also add it to the Decks tab".*

*Added 2026-10-13. MTG Trader only.*

Owner's wording: "if in the future you can create a function in the app to import a precon into a binder (or even
as a deck) and define the price paid."

How it could work (Claude's view, from doing it by script on 2026-10-13):
- Pick the deck from a list (search by name or set), choose the binder, enter the price paid (in the app's currency)
  and the date. The app adds the deck's cards to the collection, each with its share of the price: the price is
  split in proportion to today's Cardmarket price, so the cards add up to what the deck cost. The deck also becomes
  an order in the order history (item 4).
- Deck lists: MTGJSON publishes every preconstructed product with Scryfall ids and which cards are foil
  (`https://mtgjson.com/api/v5/DeckList.json`, about 650 KB, then one small file per deck). Scryfall has no deck
  lists. A new source for the app, so the weekly health check should watch it.
- Basic lands and tokens are left out (the owner doesn't track them); they get no share of the price.
- A card already in the binder gets one more copy, and its price becomes the average weighted by copies (item 7's rule).
- "As a deck": the Decks tab holds Archidekt decks (each is tied to an Archidekt deck number). Archidekt publishes
  the official precon lists, so the deck could be added through the existing Archidekt import; a deck that exists
  only in the app would be a bigger change to the Decks tab.

Settled with the owner on 2026-10-13 (no open questions left):
- Binder only at first; "also add it to the Decks tab" is a later step.
- The price is split by today's card prices.
- Commander decks only to start with.

### Sort the collection by value change
*Released in MTG Trader 1.32 (2026-10-13). Poké Trader still has this item open (planned as Poké Trader 1.20).*

*Added 2026-10-12. Also on the Poké Trader to-do list.*

Owner's wording: "being able to sort by value change in the collection view (% or just raw value). Similar to your
rising lately or falling lately in the collection value overview."

What exists today: the value screen's "Rising lately" / "Falling lately" lists compare Cardmarket's trend price with
its 30-day average (`ValueHistory.biggestMoves`), and rank by the change in money over all copies owned. The collection
already shows that change per card as a percentage arrow. The collection's sort dialog (`SortField` in
`data/CollectionQuery.kt`) has no such order yet.

Decided with the owner (2026-10-12):
- **Two new sort orders:** "Change in %" and "Change in money", each with the biggest risers or the biggest fallers
  first.
- **The change is per card**, not over all copies owned.
- **The value screen changes too:** "Rising lately" and "Falling lately" should show and rank the change per card
  (today they use the change over all copies), so both screens agree.
- **Cards without a 30-day average** have no change: always last, in both directions.
- **While TCGplayer or Card Kingdom is the price source:** still sort by Cardmarket's change, and say so in the sort
  dialog.

### Check a scanned package against its CardTrader order
*Released in MTG Trader 1.32 (2026-10-13). Built as planned. Started with "Check an order" above the scanned list; works for Cardmarket files too. Not built: fixing a line by picking its printing on the check screen (an unmatched line is compared by name).*

*Added 2026-10-12. MTG Trader only. The owner expects a CardTrader package around 2026-10-23.*

Owner's wording: "I experience often that cardtrader makes mistakes and around 1% of the cards are missing or have the
wrong language. I think it would be great that, after a scan session, after receiving the cards from cardtrader, the
app is able to tell me if I receive all the cards or not, or if there were mistakes in the order, by linking the excel
file from the cardtrader order. The app already has a way to import a cardtrader excel file to the collection, but
this is a bit different, where during a scan session, the user can attach a cardtrader excel and the app could verify
if all the cards are there or not."

What exists today: the order import (`data/CardTraderImport.kt`: `CardTraderOrder.parse` and `match`,
`ui/OrderImportScreen.kt`) reads the Excel file and finds each line's printing; the Scan tab's list
(`ScannedCard`, `ui/ScansScreen.kt`) holds scanned cards with printing, language, quantity and `exactPrinting`.

Proposed (2026-10-12):
- On the Scan tab: "Check against an order" → pick the Excel file → compare the order's lines with the scan list.
- Per order line: received; wrong language (same printing); wrong printing (same name, other set/number); missing
  (not scanned, or fewer copies); plus extra cards (scanned, not ordered). A summary line on top.
- Fix scanner mistakes before complaining: tap a problem line to rescan it or mark it "it's here, the scan was wrong".
- Copy a problem report (missing and wrong cards with set, number, language, price paid) to send to CardTrader.
- Add the received cards to the collection with the order's details (price paid, condition, language, foil), then
  clear the scan list.
- What the camera can't check: foil, condition, signed, altered → list those lines as "check by hand". Older cards
  print no language code → "language not checked". Scans with `exactPrinting = false` → "printing unsure", not wrong.

Decided with the owner (2026-10-12):
- **After-scan check only**, started from the scanned cards list (the Scan tab's list), not while scanning. No live
  ticking.
- **Adding to the collection is included**, and works like a regular scan: the checked cards go from the scan list
  to a binder the usual way.
- **Matched cards take the order's details** where the camera can't know them: price paid, condition, foil,
  signed/altered. Cards that matched no order line stay as scanned.
- **Not ahead of the list:** wait for the owner's green light for the whole to-do list.

### Cardmarket orders: import to the collection, and check against scanned cards
*Released in MTG Trader 1.32 (2026-10-13). Built as planned, in `data/OrderFiles.kt` (one reader for all three files). Assumptions to confirm with a real file: a playset's `price` is taken as the article's price, so a quarter per card (Cardmarket's monthly file showed a playset sold as one article at the full price); the language numbers are Cardmarket's API list.*

*Added 2026-10-12. MTG Trader only.*

Owner's wording: "In the same way that we have these functionalities for a cardtrader order, could you make the same
for cardmarket orders (sample csv file attached)? Both for the existing import to collection and planned comparison
between scanned cards and an order."

The file (Cardmarket: a shipment's "ArticlesFromShipment<number>.csv"): semicolon-separated, one line per article,
with the columns
`idProduct;groupCount;price;idLanguage;condition;isFoil;isSigned;isAltered;isPlayset;isReverseHolo;isFirstEd;isFullArt;isUberRare;isWithDie`
(flags are "1" or empty). It has no card names or sets, only Cardmarket's product number. Example lines:
`315288;1;6.98;1;3;;;;;;;;;` and `788181;1;2.98;1;3;1;;;;;;;;` (a foil).

Checked on 2026-10-12:
- Scryfall finds the exact printing from the product number: `api.scryfall.com/cards/cardmarket/<idProduct>`
  (315288 → Journey to Eternity, RIX 160; 788181 → Marvin, Murderous Mimic, DSK 253). One request per product, so an
  order of 200 lines takes about half a minute at Scryfall's pace: show progress.
- The app's own Cardmarket product list (`cm_products`, from products_singles_1.json) has the name and expansion of
  every product in the sample: the fallback for products Scryfall doesn't know (show the name, let the owner pick the
  printing, as the CardTrader import does for guessed lines).

Proposed (2026-10-12):
- One "order file" reader for both shops: recognise the file (CardTrader's Excel or Cardmarket's CSV) and turn either
  into the same order lines, so the existing import (`ui/OrderImportScreen.kt`, `MtgRepository.importOrder`) and the
  planned check against scanned cards (item 2) work for both. Show which shop the order is from.
- `isPlayset` = four copies per group. `isFoil`, `isSigned`, `isAltered` as in the CardTrader import.
- Tests use a made-up file in the same format; the owner's own order file is not committed (public repository).

Decided with the owner (2026-10-12):
- **Conditions confirmed** on the sample order: 2 = Near Mint (Obsessive Pursuit), 3 = Excellent (Journey to
  Eternity). So Cardmarket's scale: 1 Mint, 2 Near Mint, 3 Excellent, 4 Good, 5 Light Played, 6 Played, 7 Poor.
- **`price` is per card.**
- **MTG Trader only:** no order import or check for Poké Trader for now.

Still an assumption (only English, idLanguage 1, was in the sample): 2 French, 3 German, 4 Spanish, 5 Italian,
6 Simplified Chinese, 7 Japanese, 8 Portuguese, 9 Russian, 10 Korean, 11 Traditional Chinese. When building, show an
unknown number as "language ?" rather than guessing, and check the list against Cardmarket's documentation.

### Update purchase prices of cards already in the collection, from an order file
*Released in MTG Trader 1.32 (2026-10-13). Built as planned, in `data/PurchasePrices.kt`. On the owner's real files it gives the same cards and prices as the one-off script of 13 October (3,021 stacks against 3,019; the two extra were decided by hand that day). Not built: spotting a playset in the monthly file (it isn't marked there).*

*Added 2026-10-12, confirmed as an app feature 2026-10-13. MTG Trader only.*

Owner's wording: "I would also be interested in having a way to read a cardtrader excel or cardmarket csv, have the
app find the cards in my collection and update the purchase price accordingly." And on 2026-10-13: "I still want the
app to be able to update prices of the existing cards in the collection based on cardmarket or cardtrader purchases.
[...] I know that we will do a bulk update for this outside the app, but I still want this functionality
implemented."

What it is: the same order reader as items 2 and 3, but instead of adding cards it finds them in the collection and
sets what was paid. It reads all three kinds of file: CardTrader's Excel, Cardmarket's per-shipment CSV (item 3), and
Cardmarket's monthly "Purchased Articles" CSV (format below).

Proposed (2026-10-12):
- Pick one file or several at once.
- Match an order line to collection cards by printing, finish and language; condition as a tie-breaker.
  Cardmarket's monthly file has no foil, language or condition: match by printing (product ID) only.
- Show a preview before changing anything: would update N cards; lines with no card in the collection (sold or
  traded since); cards that already have a price; ambiguous lines for the owner to choose (the same printing owned
  as foil and non-foil, or in two languages, when the file can't say which).
- A restore point (backup) is made first, as before other bulk changes.

Decided with the owner (2026-10-12):
- **Bought several times at different prices:** the stack gets the average paid (weighted by copies).
- **Cards that already have a purchase price:** kept, unless the owner chooses "overwrite".
- **More copies in the collection than the orders cover:** all copies get the price.
- **A stack that already has a price and has more copies than the orders cover:** average them, weighted by copies
  (4 copies at €2 with an order for 1 copy at €4 → €2.50). So "keep the existing price" only applies when the orders
  cover no more copies than the price already stands for; "overwrite" replaces instead of averaging.

Cardmarket's monthly file ("Purchased Articles-byPurchaseDate-2024-12-01_2024-12-31.csv", Account → Statistics →
Purchased Articles), semicolon-separated, one line per article:
`Shipment nr.;Date of purchase;Article;Product ID;Localized Product Name;Expansion;Category;Amount;Article Value;Total;Currency;Comments`
e.g. `1185249514;2024-12-02 16:38:27;Essence Flux;289154;Essence Flux;Shadows over Innistrad;Magic Single;1;2.38;2.38;EUR;289154`.
"Article Value" is the price per card, "Amount" the copies, "Comments" the seller's free text (ignore). It has the
shipment number and purchase date, which also suit the order history (item 4).

### One-off: purchase prices of the whole collection from all past orders (was item 8)
*Done 2026-10-13 on the owner's PC, outside the app (no release).*

4,512 of 5,210 stacks got a purchase price (EUR 7,580 in total) from 43 Cardmarket monthly files, 15 CardTrader
orders and 17 preconstructed Commander decks; 698 stacks have no known purchase. The new `sync.json.gz` went into
the owner's Nextcloud folder; the phones take it over at their next sync.

How it was done, as the reference for item 7 (the app's version should give the same results):
- Cardmarket lines match by product ID (`cardmarketId` / `cardmarketFoilId`); CardTrader lines by set code and
  collector number with the name as a check, then finish and language. CardTrader's own set codes are one letter
  longer than Scryfall's (CRVR, LTRH, FDNS, DCLB) or "PRE" + set for prerelease cards; The List and Mystery
  Booster codes (MB1, PLIST, PLIST2) all mean Scryfall's `plst`/`mb1`/`mb2`.
- A stack's price is the average of all its purchases weighted by copies; all copies get it.
- 175 lines were not sure matches and were settled with the owner in chat: another printing owned (143), the same
  printing owned in two finishes or languages (16), finish or language differing from the order (15), one playset
  sold as a single article (Cardmarket's monthly file doesn't mark playsets; found by its price).
- The monthly file's "Localized Product Name" differs from "Article" for non-English cards: a usable language hint.
- Cardmarket's old double-faced names use " / " instead of " // ".
- Precons: found by comparing MTGJSON's deck lists with the collection (17 decks, each 96% or more complete). The
  owner gave the price paid per deck (DKK, 7.46 to the euro); it was split over the deck's cards in proportion to
  today's Cardmarket price. Idea for later: the app could do the same for a precon the owner adds.
- Also corrected at the owner's request: 8 cards set to foil, 2 to Japanese, 8 to the printing actually bought.
- Checked before writing: the Nextcloud merge takes the newer `updatedAt` per stack, so the phones take the prices
  over; the Archidekt sync sends prices changed in the app to Archidekt (it doesn't overwrite them).
- The order files, the report, the decisions and the scripts are in the owner's folder `cardmarket and cardtrader`
  inside the project folder on their PC (hidden from git through `.git/info/exclude`; never commit it). Keep it for
  item 4 (order history).

Getting the files out of Cardmarket (looked at in the owner's account, 2026-10-12):
- The account has 300 arrived shipments. Per shipment there is the "ArticlesFromShipment" CSV of item 3.
- **Better: Account → Statistics → "Purchased Articles"** exports every article bought in one month as CSV or Excel
  (choose month, year, and purchase or payment date). One file per month instead of one per shipment. Cardmarket's
  note on it (2022) says its 4th and 5th columns are the product ID and the product name.
- The monthly file (sample shared by the owner 2026-10-12, "Purchased Articles-byPurchaseDate-2024-12-01_2024-12-31.csv"),
  semicolon-separated, one line per article:
  `Shipment nr.;Date of purchase;Article;Product ID;Localized Product Name;Expansion;Category;Amount;Article Value;Total;Currency;Comments`
  e.g. `1185249514;2024-12-02 16:38:27;Essence Flux;289154;Essence Flux;Shadows over Innistrad;Magic Single;1;2.38;2.38;EUR;289154`.
  "Article Value" is the price per card, "Amount" the copies, "Comments" the seller's free text (ignore).
- It has **no condition, language, foil, signed or altered**. Enough for purchase prices (product ID → printing) and
  it gives the order history (item 4) real dates and shipment numbers. When the owner has the same printing in
  several versions (foil and non-foil, two languages), the file can't say which one the price belongs to: list
  those in the preview for the owner to choose.
- So: three readers in the end. CardTrader's Excel and Cardmarket's per-shipment CSV for importing and checking new
  orders (items 2 and 3); Cardmarket's monthly file for past purchase prices and order history.
- Collecting the monthly files (done partly on 2026-10-12/13, in the owner's Chrome, with the owner's OK): the
  export is not a direct download. "Export" asks Cardmarket to prepare a report (and emails the owner when a month
  has purchases; empty months produce no report and no email); the report then appears under Account → Downloads,
  where each file is one click. Chrome blocks more than one scripted download per page, so the owner clicks the
  files. Every month from January 2014 to October 2026 has now been requested (finished 2026-10-13). Months
  with purchases: August to December 2014; March to October 2018; October 2022 to March 2024; August 2024 to
  February 2025; June and September 2025; March, May and June 2026 (43 files). No purchases in 2015 to 2017, or
  between November 2018 and September 2022. The owner keeps the files on their PC; they are not in the repository.
- CardTrader (about 15 orders) by hand is fine.

