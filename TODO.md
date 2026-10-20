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
6. ~~MTG Trader 1.46: changing decks on Archidekt, local decks, the combo flags, all decks' bracket or power level
   cards and when the deck can cast its commander (one release, as the owner chose on 2026-10-18).~~ Released.
7. ~~MTG Trader 1.44: deck stats (mana curve, mana cost, mana production as sources, the deck's tokens), asked for and
   planned with the owner on 2026-10-17.~~ Released.
8. ~~MTG Trader 1.45: choosing which printing of a token a deck shows (an "Other printings" button on the token,
   remembered per deck and synced), asked for and approved by the owner on 2026-10-17.~~ Released.
- **Open since 1.46 (2026-10-18):** the owner tries the Archidekt editing on a spare deck first and reports what
  happens.
- **Since 1.47 (2026-10-18):** the weekly health check's full new text (Scryfall's random commanders, Archidekt's
  category addresses, and a new part C for the Commander rules) is in `docs/health-check-instructions.txt`; it was
  given to the owner to paste, and the owner pastes it before clearing the conversation.

9. ~~MTG Trader 1.47: life tracker and game records for Commander, with the deck page fixes and Manage categories
   (green light and choices from the owner on 2026-10-18: five tabs with Settings behind a gear, commander pictures
   and the ghost by default).~~ Released.
10. ~~MTG Trader 1.48: the life tracker redesigned as Lifetap (the owner's screenshots of Lifetap, 2026-10-18): soft
   colours, a menu bar across the middle, each player's sheet with commander damage on a seating grid (hold a box for
   − and + and partner commanders) and counter tiles, game settings, dice, mana pool, history and ending a game all
   inside the game; players and decks can be changed during the game. The owner's choices: the pastel look (A), plain
   colours by default, keep the New game screen (shorter), more room around the life total; no camera mode, no
   one-player mode; Planechase, Archenemy and Bounty left out.~~ Released.
11. ~~MTG Trader 1.49: the owner's comments on the tracker (2026-10-19): the random pick 2 seconds longer and slowing
   down at the end; commander damage from your own commander; the mini-grid centred in a frame as in Lifetap, with
   the counter badges beside it (the owner picked A from the mockups).~~ Released.
12. ~~MTG Trader 1.50: the low-life ghost no longer floats over the + (it rises between the total and the +, or on
   the − side where the grid sits beside the total; the owner picked C from the mockups on 2026-10-19), and low life
   starts at a quarter of the starting life (10 at 40).~~ Released.
13. ~~MTG Trader 1.51 (planned with mockups and approved on 2026-10-19): pausing a game to continue it on any phone,
   the Samsung A37's table lined up again, the 6-player end seats' grid next to the total, a ⚙ settings button in
   the middle bar.~~ Released.

### Test 1.46 to 1.51 in an emulator (in a session on the owner's PC)
*Added 2026-10-18. MTG Trader only.*

The owner's words: "add a todo for when I am on a PC to have you run the major changes in 1.46 and 1.47 in an emulator
for testing all the functions that this cloud session could not."

Cloud sessions can't run an Android emulator: 1.46 and 1.47 were checked with unit tests, screenshot tests and a
simulated run of the screens, never on a real (or emulated) phone. In a session on the owner's PC: start an emulator
(Android Studio's, a recent Pixel), install the debug build (`./gradlew installDebug`), drive the app with `adb`
(taps, swipes, screenshots), and report what works and what doesn't, with screenshots. Use a spare Archidekt deck
(not the owner's real decks) for anything that writes to Archidekt, and a spare Nextcloud folder for sync. Fixes
found go through the usual plan → green light → release.

Checklist:
- **Deck editing (1.46):** drag a card by its handle to another category (also in the compact view and the
  maybeboard); press and hold to select, Move to category, To maybeboard, Remove; undo each; a category's ⋮ (add a
  card, select all, rename, delete); the card window's controls (category list from the bottom, In the deck /
  Maybeboard, copies), also with the phone's text set larger; Auto-categorise all cards.
- **Archidekt sending (1.46, 1.47):** edits reach Archidekt (check on archidekt.com); offline edits wait and go when
  back online; adding, renaming and deleting a category happen on Archidekt; send a local deck to Archidekt (new deck,
  folder, visibility); Folder and visibility.
- **Local decks (1.46):** New deck from empty, a precon, scans, a copy, a pasted Moxfield list; detach; a local deck
  reaches the other phone through Nextcloud sync (two emulators, or one emulator and a phone).
- **Menus (1.47):** every long menu (deck ⋮, collection ⋮, sort menus, category lists) fits above the phone's
  buttons, with gesture navigation and with three-button navigation, small and large screens, larger text.
- **Manage categories (1.47):** switch categories off and on (deck page, Hidden categories section, move lists);
  empty categories never on the deck page; rename and delete with undo.
- **Commander odds (1.47):** open the Stats, scroll the odds away and back: they don't start over.
- **Life tracker (1.47, redesigned in 1.48):** New game with 2 to 6 players (layouts, also one at each end for 4 and 6;
  panels facing the right way; the middle bar in a gap), tap and hold on every panel in both tap modes, holding by 1;
  the name label opens the player's sheet facing them; the seating grid (tap +1, hold for − and +, the partner switch);
  every counter tile (tap and hold), K.O., custom counters; changing a player and a deck during the game; background
  picture or colour, contrast; Dead? / out at once / nothing; low life animations; turns, clock, pause (tap the clock),
  undo (hold History); Game settings (players and starting life restart after asking); dice, coin, custom die, random
  player and opponent, the quick d20 (hold the die); the mana pool; the history log; auto-hiding bar; vibration; the
  screen stays on and the system bars hide (and both switches); leaving and coming back keeps the game; End the game:
  Save and rematch, Save then new players, end without saving.
- **Life tracker (1.49):** the random pick's run (about 3 seconds, slowing down) feels right on a phone; your own
  commander's box on the sheet and the mini-grid; the framed mini-grid stays centred with badges beside it on every
  layout, also with large text.
- **Life tracker (1.51):** a narrow emulator (about 384 dp wide, like the Samsung A37) and a wide one: name, total and
  grid in one line on 4 and 5 players; the 6-player end seats with the grid next to the total; the ⚙ opens Game
  settings. History → Pause game and leave: the tracker closes, the game shows under Paused games (date, players,
  round, time); Continue on the other phone after a sync (two emulators, or one and a phone) and check it's gone from
  the first; Continue while another game runs (asks, then pauses that one); ⋮ → Drop it; a clock stopped by hand
  stays stopped.
- **Game records (1.47):** game details (change places, delete), add a past game, stats (sorting, My decks, players,
  head to head), the deck page's Games section, export CSV, Players and their decks; games and players reach the
  other phone through sync; a backup restore keeps them.
- **Tab bar (1.47):** five tabs, Settings behind the gear on every tab, back from Settings.

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

### The turn badge covers the name label
*Added 2026-10-20. MTG Trader only.*

The owner's words: "fix the turn badge to avoid overlaps."

Found in 1.51's 5-player test picture (`life_tracker_5.png`): on the player whose turn it is, the gold "Turn 1:42 ▶|"
badge sits in the panel's corner on top of the name label, hiding part of the name ("oel" for Joel). It happens
where the panel is short and the label is long (name and deck), and it was already so before 1.51. The fix keeps
the badge clear of the label (and of the mini-grid and counter badges) on every table size and layout.

Open questions (for the plan, with mockups):
- Where the badge goes when there's no room in the corner: beside the label, under the total, or the label made
  shorter (the deck name left out) while it's that player's turn?
- Keep the turn time in the badge on small panels, or only "▶|" there?

### When the deck can win (goldfish win turn), also on the bracket card
*Added 2026-10-20. MTG Trader only. Planned with mockups on 2026-10-20; waiting for the green light. Planned as
MTG Trader 1.52 together with the turn badge fix below.*

The owner's words: "Could you add an analysis for each deck for their expected turn when they can win? Simulation
like you did for the mana with 10000 games, where you find the turn where the deck can do enough damage to everyone
(120 damage) or win the game. I think if you pull this off, it might be worth to add the stats to the bracket card."

Tried with a prototype on 2026-10-20 (the owner's decks from Archidekt, cards from Scryfall, combos from Commander
Salt, 10,000 games of 15 turns each; the 1.46 mana simulation plus casting, attacking, tokens, drain, anthems, card
draw and winning combos). Turn the deck usually wins by / 9 in 10 games by:
- Auntie Plague: turn 11 / 13 (a combo wins about 1 in 4 games, from turn 4 at the earliest).
- Taranika band: turn 11 / 15. Isshin Wide: turn 12 / 14.
- Baba and Smasher LCP: not by turn 15 in most games, because most of their damage comes from things a general
  simulation can't read (Vial Smasher's damage on casting, Baba's sacrifice triggers).
So it works and is quick enough for a phone (well under a second per deck), but it's a best case "playing alone"
and it can't understand every card; decks built around their commander's own ability come out too slow.

Proposed (see the plan sent on 2026-10-20 and its mockups):
- Deck page → Stats: a new "Winning the game" card under "Casting the commander": usually by turn X, 9 in 10 by
  turn Y, the chance by turn as a row of tiles, how it won (combat, combo, drain) and a "How?" with what's counted.
- Bracket card: the turn as well (mockup A: a "Speed" section; mockup B: a third tile next to Baseline and
  Realistic), and a small "T11" tile on the all-decks list.
- No new service: Scryfall's power and rules text (already read since 1.44) and Commander Salt's combos (since 1.46),
  so no health check change. Nothing new stored in the database (worked out when needed, kept in a small cache file).

Settled with the owner on 2026-10-20: bracket card **A** (the "Speed" section), the small "T11" tile on the
all-decks list **yes**; only combos that **win the game on their own** count (Commander Salt's "wins the game" or
"infinite damage", and not those that need more cards on the battlefield); decks that don't win by turn 15 show
**"T15+" with a note**; the turn badge fix goes in the **same release**. The owner's bonus wish: "bonus points for
you if your simulation can catch damage trigger effects like vial smasher."

Prototype v2 (2026-10-20), with the owner's combo rule and damage triggers:
- Triggers read from the rules text: casting a spell ("whenever you cast your first spell each turn / a spell / an
  instant or sorcery / a creature spell", damage equal to the spell's mana value or a fixed number, to one opponent
  or each opponent: Vial Smasher, The Lord of Pain…), creatures or lands entering (Purphoros, Corpse Knight,
  landfall), drains at the start of your turn or when attacking, and tap abilities. With a "first spell each turn"
  trigger the biggest spell goes first.
- Smasher LCP went from "not by turn 15" to usually turn 14 (its triggers about a tenth of the damage). Others:
  Auntie Plague turn 11 / 13, Taranika band 11 / 14, Isshin Wide 11 / 14, Baba 15+ (its sacrifice drain needs
  three card types sacrificed at once; not read).
- Commander Salt's "infinite life loss" label isn't counted as a win: Devoted Druid + Mikaeus carries it but needs a
  death-trigger card to kill anyone. With the owner's rule none of these five decks has a counted combo now (Soul
  Immolation + Tree of Perdition needs more on the battlefield).

Open question:
- Turn badge: A (in the name label, the deck name shortened to make room; recommended), B (the panel's other
  corner) or C (under the label; the total gets smaller)? Mockups sent 2026-10-20.

## Done

### Pausing games, and the table on narrower phones (MTG Trader 1.51)
*Added 2026-10-19. MTG Trader only. Planned with mockups; the owner's choices and green light on 2026-10-19.*

The owner's words: "Add the hand-over button, actually just a pause button. Instead of canceling the game, it pauses
the game and leaves, then syncs and can be picked up in another phone." … "In version 1.49 the commander damage, life
total and player name were centered and aligned, just like the lifetap app. In 1.50 you changed that … my pixel 8 pro
shows everything centered as it should be. But that screenshot was from my Samsung A37." … "For six player games,
the commander damage is too close to the +, for the players at the top and bottom … place the commander damage
closer to the life total." … "In the game menu, the button with the number of players … can you change to an actual
settings button?"

What was built:
- **Pause game and leave** on the History page (A: a wide button under Undo last and Pause clock, which are two
  equal halves of its width). The clock stops, the tracker closes, and the game is kept as a paused game (a new
  `paused_games` table, database version 24), synced (sync file and backup format 11). The Games tab lists **Paused
  games** (B: one list): date and time, the players with their colour dots, round and time, Continue, ⋮ → Drop it.
  Several can be paused. Continuing deletes it, so the deletion takes it off the other phone's list; continuing while
  a game runs asks and pauses that one first. A clock stopped by hand stays stopped. Restoring a backup leaves paused
  games as they are. The undo steps stay behind; the log goes along.
- **The Samsung:** 1.50 hadn't changed the layout; since 1.48 a panel shorter than the grid + 150 dp got the grid
  beside the total, and the Samsung's 4-player panels were just under that. The limit is now the grid + 110 dp.
- **6-player end seats** (A): the total stays in the middle of the panel and the grid sits right after it; the
  "+3" change shows on the total's other side there.
- **Settings button** (A): a plain ⚙ in the middle bar instead of the ring with the number of players.
- 5 players were checked in the mockups and left as they are (the owner: "5 players is fine").

### Fixes on the deck page and the card window, and Manage categories (MTG Trader 1.47)
*Added 2026-10-18. MTG Trader only. Reported by the owner with screenshots (phone).*

The owner's words:
1. "Category drop down list seems the last item is always cut?" (the category list in a deck card's window: the
   last entry, under "Commander", is cut off by the bottom of the screen).
2. "Maybeboard button looks weird on the card page." (the Deck / Maybeboard switch in the card window: "Maybeboard"
   breaks over two lines as "Maybeboar / d" and the button sticks out of its row; the "auto: Proliferate" note next
   to the category also breaks letter by letter.)
3. "When scrolling up and down on the deck page, the casting the commander stats seem to be refreshed every time it
   is scrolled by."
4. "Empty categories are visible in the deck page, I would rather have them not visible."
5. "The last item of the menu under the 3 dots for the deck page seems to be cut." (the deck page's menu is longer
   than the screen; its last entry, "Delete deck", is only half visible.)
6. "Cannot drag cards between categories. The card gets selected/highlighted but it does not drag."

Settled with the owner on 2026-10-18:
- **Empty categories:** never shown on the deck page, new or not. A category gets its first card from the card
  window's category list or by selecting cards and using the top bar's "Move to category" button.
- **When:** together with the life tracker, in 1.47.

Owner's choices on the 1.47 mockups (2026-10-18): tab bar B (five tabs, Settings behind a gear at the top);
commander pictures as the default background; the ghost as the default low-life animation; the new card window
layout as drawn.

**Manage categories** (owner, 2026-10-18, replaces "Add a category…" in the deck's ⋮ menu): "add an option to
'manage categories'. Here the user can add categories and choose which categories to be shown and available on drop
down menus in the card pages and so on. This would affect the categories shown when the cards are grouped by
categories. Empty categories are still not shown by default. I also think it is weird that deleted categories in the
app do not get deleted on archidekt, is this not possible at all?"
- Drawn as a mockup (Categories screen: add, a "Shown" switch per category, rename and delete per category).
- Archidekt *can* do it (found 2026-10-18 in the deck page's JavaScript): POST /api/decks/createCategory/ (with the
  deck's id), PATCH /api/decks/category/<id>/ (rename, included in deck) and DELETE /api/decks/category/<id>/. 1.46
  only moved the cards, so an emptied or renamed category stayed on Archidekt. In 1.47 adding, renaming and deleting
  a category also happen on Archidekt (queued like other edits when offline); the health check gets these three
  addresses.
- "Shown" is kept in the app (Archidekt has no such setting) and synced between the phones.

Settled with the owner on 2026-10-18:
- Cards in a category that is switched off show in one folded "Hidden categories" section at the bottom of the deck
  page.
- Empty categories are never shown on the deck page (no option to show them); they're offered in the move lists.
- Health check: after the release, the owner gets the full new text to paste.

What was found (Claude, 2026-10-18):
1. The category list in the card window and 5. the deck page's ⋮ menu: Android's drop-down menus run under the
   phone's own buttons at the bottom of the screen. Fix: the card window's category opens the same list from the
   bottom as "Move to category" (current one ticked, "New category…" last); long menus stop above the phone's buttons
   and scroll.
2. The card window's controls get too little room when the phone's text is larger: labels go above the controls,
   "In the deck / Maybeboard" uses the full width, the auto category goes on its own line.
3. The commander odds sit in a list row that is thrown away when it scrolls out of sight, so the calculation starts
   again each time it comes back. Fix: work it out once per deck change, outside the list.
6. Dragging: when a drag starts, the dragged row is redrawn inside a new box, and that cancels the drag at once (the
   row only flashes). Fix: keep the row in the same box and only lift it; a test that drags a card.

### Life tracker and game records for Commander (MTG Trader 1.47)
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
- **Rules in the health check** (owner, 2026-10-18: "the health check should look for MTG commander rule updates").
  It doesn't today. Added when 1.47 is released: the Comprehensive Rules (magic.wizards.com/en/rules links the
  current text file; effective September 25, 2026 at the time) and Wizards' Commander announcements, checked for the
  rules the tracker relies on: starting life 40 (903.7), 21 *combat* damage from one commander (903.10a, 704.6c),
  10 poison (704.5c), commander tax {2} per earlier cast (903.8), the monarch (725), the initiative (726) and
  day/night. The report says when the rules have a new date and whether any of these changed.

Open questions: none for now; the mockups come with the plan (tab bar, table layouts for 2–6 players, backgrounds,
counters, the low life animation, the end-of-game screen, the stats).


### Changing decks on Archidekt (MTG Trader 1.46)
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

### Combos that don't count for the bracket (MTG Trader 1.46)
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

### All decks' bracket or power level cards (MTG Trader 1.46)
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

### When the deck can cast its commander and has its colours (MTG Trader 1.46)
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

Released in MTG Trader 1.46 (2026-10-18). Notes from building it: the Archidekt addresses were read from its website's code
(modifyCards/v2 with "add", "modify" and "remove"; POST /api/decks/v2/; PATCH /api/decks/<id>/update/; PATCH
/api/massUpdate/ to move a deck to a folder; GET /api/decks/folders/<id>/). Deleting an emptied category on Archidekt is
left to its website. A card in a counted and an uncounted combo shows both flags (as in the approved mockups).

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

