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
3. ~~**Poké Trader 1.20:** sort by value change (item 1 on Poké Trader's list; the code is nearly the same as MTG's
   1.32).~~ Released (the owner said on 2026-10-26 that Poké Trader 1.20 is out; don't update the Pokémon app yet).

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
14. ~~MTG Trader 1.52 (planned with mockups, green light on 2026-10-20): when each deck can win (Stats and the bracket
   card), the turn badge in the name label, and the scanner list jumping to the newest card.~~ Released.
15. ~~MTG Trader 1.53 (green light on 2026-10-20): combos by what they do and tutors in the win turn.~~ Released.
- **Since 1.53 (2026-10-20):** the weekly health check also checks Commander Salt's combo prerequisites; the full text is
  in `docs/health-check-instructions.txt` and was given to the owner to paste.
16. ~~MTG Trader 1.54 (green light on 2026-10-21): the win turn at 4 in 10 games, the first win highlighted, and the
   simulator reading Shrines, Gates, Maze's End, sacrifice decks and X spells.~~ Released.
17. ~~MTG Trader 1.56 (green light on 2026-10-23): each attacking creature hits one opponent (Ruby's Jumbo Cactuar no
   longer knocks out all three).~~ Released.
18. ~~MTG Trader 1.57 (green light on 2026-10-23): hold − or + in the commander damage window for ±10.~~ Released.
19. ~~MTG Trader 1.58 (green light on 2026-10-23): the owner's life tracker feedback (vibration, "1st" badge, Drain /
   burn, changing past games' players and decks, guests' commanders, "Anonymous", tracker settings in Settings, Games
   above Tokens).~~ Released.
20. ~~MTG Trader 1.59 (green light on 2026-10-24): Lifetap's dice (Dicefont), the roll box with Again, the quick-roll
   ring, and "I'm …" for the random opponent.~~ Released.
21. ~~MTG Trader 1.60 (green light on 2026-10-26, option B): purchase and agreed trade prices remember the currency they
   were typed in, still kept in euros.~~ Released.

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

### Win turn: lands, ability costs, finishers, Sagas, Vehicles, toxic (MTG Trader 1.69)
*Asked 2026-10-28: the owner had Mistral Large audit the simulator; its claims were checked by reading 34 of the named
cards through `classify`, and the owner gave the green light for items 1–5 of the checked list.*

Released in MTG Trader 1.69 (2026-10-28), `WinOddsCalc.VERSION` 10:
- Audit claims that were wrong: loot spells (Faithless Looting, Frantic Search) were read; Great Train Heist's extra
  combat and Treasure, Oketra's Monument's cast trigger, Caretaker's Talent's draw were read; Mechanized Production's
  count includes the enchanted Clue; filter lands only fix colours (left at one mana). Lotus Field isn't faster than a
  basic land on its own (three mana for three lands' worth), so Quintorius moved 1 point slower.
- New: land `amount`/`uses`/`sacrificesLands` (ManaOdds too; `Source.until`), Crypt of Agadeem (`GRAVEYARD_KIND`),
  ability costs `sacrificeCost`/`blightCost`/`counterCost`/`drawX` (`costPaid`, `sacrificeLoss`), `teamDouble`,
  Jeska's Will (`OPPONENT_HAND` = 5), `blightX`, `mizzix`, `reanimateAsOne`, Saga `chapters` (`chapter()`, `granted`,
  `addMana`, `cheapArtifact`), Vehicles `crew()`, `toxic`/`gearToxic`/`TokenKind.toxic`, `toadWin`. Spell modes are
  read without their "•"; `modeScore` values double strike 6 (Boros Charm had flipped to 4 damage).
- The owner's 19 "Ready to Play" decks, 10,000 games with Commander Salt's combos, 1.68 → 1.69 (share won by a turn):
  Council of Four by T9 25% → 33%, by T10 50% → 59%; Tawnos' Army by T9 31% → 36%, by T10 54% → 60%; Cult of Athreos
  by T10 22% → 25%; Haunted Ganax and Cloud by T10 69% → 71% (4 in 10 by T9 instead of T10); Vohar, Ruby, Poly
  Jace, Teval, Smasher +1–2 points; Auntie Plague's combo share 35% → 38%; Quintorius, Deep Clue Sea, Turtle Power
  within 1 point either way; Baba, Taranika, Shrine Gate Win, Zombies unchanged. No first-win turn changed.
- Rivaz of the Claw's "spend this mana only" two mana stays two: only lands with a condition count as one.
- Not counted still: spree (Great Train Heist, Three Steps Ahead), "when this dies" triggers, Athreos's recursion,
  "untap all" (Wilderness Reclamation), Frantic Search's untap, Mirrex's Mites, Smasher's X spells copied by Mizzix.

### Neater tiles on the printed bracket cards (MTG Trader 1.68)
*Asked 2026-10-28: after 1.67 the tiles had "empty space under the bracket/turn numbers"; of three mockups the owner
chose "something between now and A": tiles 9.8 mm instead of 11 mm, same text sizes, label and number centred.*

Released in MTG Trader 1.68 (2026-10-28).

### Tidier bracket and power level cards (MTG Trader 1.67)
*Asked 2026-10-28, with notes on an exported PDF: remove "4 in 10 games" from all bracket cards (and everywhere else
in the app: "it is self explanatory from the github readme"), "Bracket data: Commander Salt", the power card's
saltiness line and footer didn't fit; then "remove the 'core' and 'upgraded' text from the bracket cards" (after a
mockup: only on the printed cards, the in-app bracket card stays as it was).*

Released in MTG Trader 1.67 (2026-10-28):
- No "4 in 10 games" on the printed bracket card, the in-app card's Speed section ("Wins by turn 8") or the deck
  page's Winning the game headline; the README's rule-zero section says what the turn means.
- Footers: "Bracket data: Commander Salt"; power card on two lines, "Power level: MTG Trader app" (or the site) and
  "Data: Commander Salt".
- The printed power card's "mostly community salt ratings" wraps onto a second line.
- The printed bracket card's Baseline and Realistic tiles show only the number.

### The app's own power level; loading circle during price downloads (MTG Trader 1.66)
*Asked 2026-10-28: "a new way to the app to calculate the power level based on the win-turn simulator. An option in
settings to use the calculated power level, instead of using the external sources", using the turn scale of
https://imgur.com/guide-to-power-levels-edh-OcMdyUH; and "the app still shows loading while download prices" (also
while downloading, not only while saving).*

Released in MTG Trader 1.66 (2026-10-28):
- Settings → Commander deck power level → **This app**: speed from the turn by which half the games are won (with
  decimals) on the guide's turn scale, plus Commander Salt's interaction, consistency, efficiency and mana base ratings
  (±1 at most), rounded to a whole number (the owner's choice: "refined version without table", no decimals).
- Card Kingdom's prices and Cardmarket's price guide and product list are saved in batches (each its own transaction).
  The cause: a long transaction kept the database's writer busy, and the collection, opened meanwhile, couldn't start
  watching its tables (Room sets up its change triggers with a short write), so it showed the loading circle. The
  Cardmarket saves run during Card Kingdom's download, which is why it also happened while downloading.

How the method was chosen (the owner's 19 Ready to Play decks, 2026-10-28; whole table in the conversation):
- The guide has no data behind its turn column; its author meant the turn a deck "establishes dominance", and the
  r/EDH thread moved cEDH to turns 2–4. playgroup.gg's logged games: the median 4-player game lasts about 9 rounds.
- Compared: speed at 40% and 50% of games (differ 0.1–0.3), speed with a "table" correction (+1 turn × commander
  damage share, +0.5 × other combat; left out by the owner), the refined versions, Disciple of the Vault's formula
  (2/avg MV + draw/4 + tutors/2 + cheap ramp/4 + interaction/20: swings 5.1–12.3, mostly card draw counts) and
  Commander Salt (about 1.5 lower). The refined version without the table put the decks at 5.7–7.2.
- Checked on the way: Turtle Power wins early (T7.7 for half the games) because both commanders grow every turn
  (66% commander damage); Leonardo is castable by turn 4 in 61% of games, coloured pips are paid only by sources of
  that colour, mulligans follow Karsten's model (lands only). Poly Jace's polymorph cards and Ruby's cascade are read.
- Haunted Ganax had no Commander Salt analysis (its stored deck was marked invalid and the import kept answering
  "Deck URL invalid"); checked again later the same morning, Commander Salt had it (power level 4.1).

### Print the bracket and power level cards as PDF (MTG Trader 1.65)
*Asked 2026-10-27 ("generate a PDF with bracket or power cards for the decks… same size as MTG cards, so that they can
be cut. PDF in A4 format… select which decks to include… sent to my notes to self on my molly/signal"). Mockups shown;
the owner chose layout B (bracket cards first, then power), an ink saver switch, the app's speed bars instead of the
"can win from turn x" line, a preview, and Preview, Print and Share both on the choices screen and Print and Share in
the preview. Green light on 2026-10-28.*

Released in MTG Trader 1.65:
- `CardSheets` draws the A4 pages on a canvas (sharp text in the PDF; the same drawing makes the screenshot pictures
  `print_bracket_sheet.png` and `print_power_sheet_ink_saver.png`). 3 × 3 cards of 63 × 88 mm, touching, 10.5 mm side
  and 16.5 mm top and bottom margins, 6.5 mm cut marks. `PrintCardsScreen` (route `decks/print`, 🖨 on the all-decks
  card screens): Bracket / Power level / Both, Ink saver, decks; Preview (Android's `PdfRenderer`), Print
  (`PrintManager`), Share (`application/pdf` through the existing file provider). Choice and ink saver per phone.
- Android's PDF writer can't run in the unit tests (Robolectric), so the PDF file itself is only checked on a phone.
  The owner tries it on the phone (preview, print, share to Molly) and reports anything odd.


### Win turn: deaths, sacrifice outlets, -1/-1 counters (MTG Trader 1.64)
*Asked 2026-10-27: the owner had GLM 5.3 review the simulator and asked whether to act on it. Plan agreed and green
light the same day.*

Released in MTG Trader 1.64 (2026-10-28), `WinOddsCalc.VERSION` 9:
- The review's points checked against the code first: Animate Dead (read as gear; Necromancy was already fine), death
  drains (only `dieDrain`'s wording; `deathPayoff` only in combo loops), -1/-1 counters (nothing), Mana Geyser (the
  review said "untapped", the card says **tapped**), Sunbird's Invocation, Approach, Dreadhorde Invasion: all real.
  Not real: the Go-Shintai cycle was already read per Shrine (`Scaled`); the general gap was `{2}, {T}: … loses 1 life
  for each X you control` read as 1 (now `Ability.drainPer`). Vial Smasher casting everything: left as is (plan item 6).
- Found while checking: "whenever … dies, draw a card" (Midnight Reaper, Grim Haruspex, Liliana) and Auntie Ool's
  draw counted as `drawTurn` (a card every turn). Now `DeathDrain.draw` and `minusDraw`.
- New: `DeathDrain` (read in `read64`), `freeSac` outlets used by `sacrificeForDeaths` after combat (deaths worth more
  than `keepWorth`, or the drains finish everyone), Entry `toughness`/`minus`, `minus()` with payoffs, `died()`,
  `minusOwnTarget` (survivor losing least power, else a token; a "you may" blight only with a payoff out),
  `minusTarget` (an opponent's creature from turn 4), `countersPut` (All Will Be One), `amass`, `sunbird`, Approach
  (`approached`, back 7th from the top), Mana Geyser (`ritualOf`: alive opponents × (turn − 1) / 2). Sweepers that put
  counters on each creature (Black Sun's Zenith, Darkness Descends) aren't cast.
- The owner's 19 "Ready to Play" decks, 2,000 games each without combos, 1.63 → 1.64: Council of Four T11 → T10
  (Approach); Smasher LCP, Baba, Poly Jace a few points sooner; Auntie Plague by T11 64% → 48% and Cult of Athreos by
  T10 31% → 23%, but against 1.63 without the every-turn draws (Ool; Reaper, Haruspex, Erebos, Hunter, Tallyman,
  Liliana) they're faster (Auntie Plague by T11 41% → 48%, Cult 21% → 23% by T10). The rest within 2 points.
- Not counted still: combat deaths (no blockers), opponents' creatures dying, wither/infect, persist, "each opponent
  blights", Sephiroth's sacrifices, Kardur, sac outlets costing mana (Erebos), Ashnod's Altar's mana.

### Win turn: battles, planeswalker ultimates, proliferate and poison (MTG Trader 1.63)
*Asked 2026-10-27, after 1.62: "Ruby deck has a battle, which is a tutor and can get a strong creature for example. For
planeswalkers, proliferate is quite important even though my decks do not really play that, and ultimates can end the
game, for example in my Quintorius deck." Green light the same day.*

Released in MTG Trader 1.63 (2026-10-27), `WinOddsCalc.VERSION` 8:
- Battles in the owner's decks: Ruby's Invasion of Zendikar (two basic lands; `ManaOddsCalc.classify` now reads the
  face a card is cast as, so its creature back no longer makes it a mana creature, and a Siege's land search is RAMP),
  Baba's Invasion of Ikoria (`xCreature`/`xCreatureGraveyard`, cast with the mana left after the main phase when the
  creature found is worth 4 or more), Teval's Invasion of Amonkhet (already read). The creature tutor the owner
  mentioned is Baba's Ikoria; Ruby's battle finds lands. Battles are never attacked to flip them.
- Ultimates: loyalty abilities of −6 or more are kept (dropped before 1.63) and may use the last loyalty; `emblemAnthem`
  (Elspeth), Windgrace's tokens, `poisonTo` (Vraska). Ultimates that only remove creatures (Liliana, Aminatou) read as
  nothing and aren't used. Quintorius's −4 (Spirits double strike) was already counted since 1.55.
- Proliferate (`Ability.proliferate`, `landfallProliferate`): loyalty, +1/+1 counters, poison; `Game.poison`, ten
  knock a player out; `proliferateWorth` values it.
- The owner's 19 "Ready to Play" decks, 4,000 games each without combos, 1.62 → 1.63: the same turn for all; Auntie
  Plague won by turn 10 in 17% → 22% of games, Poly Jace 25% → 28%, Taranika band 45% → 48%; the rest within 1 point
  (Ruby's change couldn't be compared this way: the old copy shared the new mana reading).
- Not counted still: flipping battles, Tezzeret's −6 emblem, Cankerbloom's and High Perfect Morcant's proliferate (a
  sacrifice or tapping three Elves as the cost), toxic and infect.

### Win turn: trigger timing, mana, land drops, modes (MTG Trader 1.62)
*Asked 2026-10-27: a list of simulator gaps to check (turn structure, tokens, equipment and auras, planeswalkers, land
drops, card value heuristics, missing card types, mana); green light for the fixes the same day.*

Released in MTG Trader 1.62 (2026-10-27), `WinOddsCalc.VERSION` 7:
- Timing: `earlyTrigger`/`lateTrigger`/`tapTurn`/`drawLate` on cards; upkeep-only cards skip the turn they come in,
  combat and end step ones also go that turn (with `eachTurn` and their draws); "each end step"/"each combat" count once
  a turn unless the line has ", if" or "this turn" (Smuggler's Share); `TokenKind.haste` (Legion Warboss).
- Mana: `ritual` (cast by `ritualFor` when it makes a card payable, or for spell triggers); lands with `manaPer` and
  `manaPerCost` (Gaea's Cradle, Cabal Coffers), "on the battlefield" (Priest of Titania); a mana creature's `manaPer`
  only once it can tap, less the one it taps for; reducers for all spells, commander spells, `NOT_FROM_HAND` and
  `FROM_GRAVEYARD`.
- Lands: `landDrops` with `extraLands`/`extraLandsNow` (played at once, not held back for landfall later); `fetchLand`
  sets off landfall twice.
- Modes: `chooseModes` keeps the best "choose one/two" modes by `modeScore` (a growing token counts extra); "one or
  more" keeps all. Spell // land: `spellSide`, cast by `spellSides` with three lands out and five counting the hand; on
  the battlefield the card has its land face's types. Transforming cards with a land back (Thousand Moons Smithy) are
  read the same way.
- Checked, already right or left as is: equip costs are paid and gear goes to the commander or the biggest creature
  (no blockers here, so that's the best carrier); one loyalty ability per planeswalker a turn is the rule; token copies
  were already counted (1.61). Not counted still: battles, proliferate on planeswalkers, ultimates (−6 or more),
  abilities in quotes on tokens, Nykthos, Skullclamp's draw, lands from the top of the library.
- The owner's 19 "Ready to Play" decks (4,000 games each, without Commander Salt's combos): the same turn (4 in 10
  games) and first win turn for all of them; the share won by a turn moved at most 5 points (Baba up, Deep Clue Sea
  down 3).

### Compare the win turn with ScrollVault's (done 2026-10-26, a report, no app change)
*Added 2026-10-21 ("Scrollvault also has a prediction for the turn a deck can win by, could be interesting to check the
differences between that and the simulator"). Done on the owner's PC on 2026-10-26, for the 19 decks in the Archidekt
folder "01. Ready to Play" only (the owner's choice).*

ScrollVault's page was run in the desktop app's browser; the app's numbers come from its own simulator (1.60, the same
cards, card details and Commander Salt combos the app reads; worked out by a temporary test, not committed).

| Deck | ScrollVault typical (earliest) | App 4 in 10 / 5 in 10 (first win) | Difference |
|---|---|---|---|
| Baba | T9 (T7) | T12 / T13 (T8) | app +3 |
| Auntie Plague | T8 (T7) | T11 / T11 (T6) | app +3 |
| Cult of Athreos (LC) | T8 (T7) | T11 / T11 (T9) | app +3 |
| Smasher LCP | T10 (T8) | T12 / T12 (T9) | app +2 |
| Poly Jace | T9 (T8) | T11 / T11 (T8) | app +2 |
| Taranika band | T8 (T7) | T10 / T11 (T8) | app +2 |
| Sarge | T10 (T8) | T8 / T8 (T6) | app −2 |
| Haunted Ganax | T9 (T7) | T10 / T10 (T7) | app +1 |
| Vohar Danse | T10 (T8) | T11 / T11 (T8) | app +1 |
| The Council of Four | T10 (T8) | T11 / T11 (T8) | app +1 |
| Cloud for Mirrodin! | T8 (T6) | T9 / T10 (T7) | app +1 |
| Zombies | T8 (T6) | T9 / T10 (T8) | app +1 |
| Teval | T8 (T7) | T9 / T10 (T7) | app +1 |
| Tawnos' Army | T9 (T8) | T10 / T11 (T7) | app +1 |
| Quintorius (upgraded precon) | T9 (T7) | T9 / T9 (T6) | same |
| Deep Clue Sea upgrade 2.0 | T9 (T7) | T9 / T9 (T6) | same |
| Shrine Gate Win | T10 (T8) | T10 / T10 (T7) | same |
| Ruby | T10 (T8) | T10 / T10 (T7) | same |
| Turtle Power! (TMNT upgrade) | T8 (T7) | T8 / T8 (T6) | same |

In short: the app's 4 in 10 is on average one turn later than ScrollVault's "typical win" (same for 5 decks, later
for 13, earlier for 1); the earliest wins agree within a turn on average. What explains the biggest differences:
- **Combat in go-wide decks (Baba, Cult of Athreos, Taranika band, Poly Jace):** ScrollVault's typical turn is its
  combat line, which it marks "rate not measured" (an estimate, not counted per game). The app plays each game out
  against three opponents at 40 life, each attacker hitting one of them (since 1.56), so a wide board has to finish
  all three; that takes it 2–3 turns longer. ScrollVault's own commander-damage line lands turn 11–13 for these decks,
  close to the app's.
- **Equipment (Sarge, the one deck where the app is earlier):** ScrollVault says its simulation never casts equipment
  (nor removal, counterspells or wipes). Sarge wins by suited-up commander damage in the app (93% of the damage; gear
  and pumps counted), so the app sees T8 where ScrollVault, without the equipment, sees T10.
- **Combos (Auntie Plague, Smasher):** both read the same combos. ScrollVault has Auntie Plague's combos landing on T10
  in 25% of hands, the app 34% by T11; ScrollVault's T8 again comes from its combat estimate. Smasher's Dualcaster Mage
  + Necromantic Selection comes up in ~4% (ScrollVault) and ~1% (app) of games, so it barely moves either number.
- ScrollVault counts 101 cards for most of these decks (100 for Haunted Ganax); the app counts 100 without the
  maybeboard. One card doesn't change the turn.

Possible follow-ups were offered (ScrollVault's commander-damage and combo turns on the deck page, or a note on the
Stats page that the app finishes all three opponents); the owner said on 2026-10-26 not to do anything about it.

### Loading circle during Card Kingdom downloads; win turn sacrifice payoffs and token fixes (MTG Trader 1.61)
*Asked 2026-10-27: "sometimes the mtg app shows loading circle when on the collection page while downloading the card
prices from cardkingdom", and whether the win turn counts the Tawnos deck's cards (Illustrious Wanderglyph, Whirler
Rogue, Urza, Nesting Dovehawk, Cybermen Squadron, Mirkwood Bats, Cyberdrive Awakener, Marionette Master, Academy
Manufactor, Tawnos's two abilities).*

Released in MTG Trader 1.61 (2026-10-27):
- Card Kingdom's list is downloaded to a cache file first, then saved (before, the database transaction stayed open for
  the whole download and the collection's query waited).
- `WinOddsCalc.VERSION` 6: `sacrificeForPayoffs` (Treasures left over, Sai's `sacArtifactsDraw`, Food with
  `foodTreasure`, Maps from `extraMap`) when a `sacrificePayoff` card is out; "whenever a creature you control enters"
  damage (with a `nontoken` flag); `populate` and `tokenEnterCounter`; `tokensOnceCount`/`tokensTurnCount` so tokens
  are made one by one; token copies call `tokenMade`; `Lord.ascend` (city's blessing at ten permanents); `pumpable`
  0-power creatures; Urza's `copyArtifact`; Tawnos's copies get `extraTypes` (artifact) and copy card tokens with the card.
- Already counted before 1.61 (checked): Cybermen Squadron's myriad, Cyberdrive Awakener's 4/4 artifacts, Academy
  Manufactor, Clues setting off Marionette Master, Tawnos's two abilities.
- The owner's Tawnos' Army (Archidekt 6292366), 10,000 games: T10 before and after (4 in 10 games); won by turn 10 in
  50% → 54% of games; drains 6% → 7% of the damage.
- Not counted still: Fabricate always takes the counters; Map's explore and Food's life do nothing; blockers and removal.

### Purchase prices keep the currency they were typed in (MTG Trader 1.60)
*Added 2026-10-25 ("ok the mtg app should not work like that and should remember that the price was inserted in dkk
or usd"). Also on Poké Trader's list (item 13); not built there yet: the owner said on 2026-10-26 not to update the
Pokémon app yet.*

Settled with the owner on 2026-10-26: Archidekt has no currency for purchase prices (checked on the owner's public
collection: `purchasePrice` is a bare number), so prices stay in euros in the database (option B): the euros are what
totals, trades, CSV and Archidekt use, and the amount and currency as typed are kept next to them only to show the
price back as typed. Also: agreed trade prices too (yes), totals at the euros kept (yes), a new sync format (12, yes:
older phones stop syncing until updated), and when stacks with prices in different currencies join, the app asks
which currency the average is kept in.

Released in MTG Trader 1.60 (2026-10-26): `TypedPrice` (`data/Currency.kt`), `paidTyped`/`paidCurrency` on collection
stacks and `customTyped`/`customCurrency` on trade lines (database version 26), `Money.format(eur, typed)`,
`PurchasePrices.joinedTyped`/`mixes`, `MtgRepository.joinCurrencies` and `JoinCurrencyDialog`; precon shares split the
typed amount too; CSV columns "Purchase price as typed" and "Typed currency".

### Random test failures on the owner's PC (fixed 2026-10-24, no release)
*Asked by the owner on 2026-10-24 ("why database failures on this pc? worth fixing?"), green light the same day.*

2 to 6 random tests failed per local run with `UncaughtExceptionsBeforeTest` ("Illegal connection pointer"): the
offline pictures' watcher (`OfflineImages`, started with the app) was still reading the database when Robolectric
reset it between tests. GitHub's faster machines never showed it. Fixed with `MtgApp.onTerminate` (only called in
tests): the app's jobs stop and finish their current database read, then the database closes. Three full runs in a
row passed (476 tests). Test-only effect: no app version.

### Lifetap's dice, quick-roll ring and "I'm …" (MTG Trader 1.59)
*From the owner's Lifetap screenshots on 2026-10-23/24 (Dice & Misc page, the roll box, the ring around the held die,
the "I'm Player N" panels). Green light on 2026-10-24.*

Released in MTG Trader 1.59 (2026-10-24): Dicefont (fponticelli/dicefont, SIL OFL 1.1: `res/font/dicefont.ttf`,
`assets/licenses/dicefont.txt`, credited on the Dice page) for the dice and results (`dieGlyph` in `ui/Dice.kt`; glyphs
numbered by name in alphabetical order); `RollOverlay` keeps the Dice page behind it (one-sided there, two-sided over
the table); the ring (`DieRing`, `dieRingGesture`, `DieRingOverlay`: a half circle 126 dp round the die, toward the
middle of the screen); `whoAmI` panels and `opponentPool` for the random opponent.

### Life tracker feedback (MTG Trader 1.58)
*From the owner's game on 2026-10-23, with the screenshot of Game stats. Green light the same day. The owner also
asked on 2026-10-23 to drop the to-do "Test 1.46 to 1.52 in an emulator": they test those things themselves.*

Released in MTG Trader 1.58 (2026-10-23):
- Vibration straight from the motor (`ui/Buzz.kt`, VIBRATE permission): Compose's haptic feedback was skipped by the
  owner's phone (silent mode / touch vibration off). Life, commander damage, counters, mana pool, dice, picks, undo.
- The "1st" badge (`firstBadgeSeat`) while the game is untouched, turns off.
- `WinType.DRAIN` ("Drain / burn").
- Past games: tap a player to change player or deck (`changeSeat`; same deck keeps its scores of the day).
- Guests: `SeatChoice.guest` (`GuestCommander`); recorded as a seat with a commander and no deck, so the stats row is
  the commander pair. Partners then get two counts on their own.
- The no-deck stats row is "Anonymous" (`GameStats.ANONYMOUS_KEY`).
- Settings → Life tracker → Life tracker settings (`TrackerSettingsScreen`, route `games/settings`).
- Deck page: Games above Tokens.
- Open: the owner asked whether partners get two commander damage trackers automatically. They do when the deck lists
  two commanders (Archidekt Commander category, a friend's deck with a partner, a guest's partner). If the owner finds
  a partner deck with only one count, look at that deck.

### Hold − or + for 10 commander damage (MTG Trader 1.57)
*Reported by the owner on 2026-10-23: "when pressing +- here for a few seconds nothing happens. I would like it to
assign +10 or -10 like it does for regular damage". Green light the same day.*

Released in MTG Trader 1.57 (2026-10-23): the commander damage detail (hold a box on the player's sheet) uses the
life panel's taps and holds (`tapOrHoldSteps` in `ui/Tracker.kt`): a tap ±1, a hold ±10 and again every 0.7 s, or
±1 fast with the "By 1, fast" setting. A hint under the boxes says so.

### Simulator: one attacker hits only one opponent (Ruby's turn-5 win) (MTG Trader 1.56)
*Added 2026-10-22. MTG Trader only. Green light on 2026-10-23 ("no plan needed, go ahead and fix it").*

The owner's words: "I am also interest in understanding how the simulator is giving my Ruby deck a 1% chance of
winning turn 5. Is there a way to find out how that game would play out?" and then "just write down what needs to be
fixed for the simulator (related to Ruby's deck) into the todo.md, no need to draft a plan."

What was found (1.55, traced games of the Ruby deck): every turn-5 win (about 1 in 100 games) is the same line: Ruby on
turn 2, a two-land ramp spell on turn 3 (New Generation's Technique, Vastwood Surge, Skyshroud Claim or Map the
Frontier) for 6 lands, Jumbo Cactuar on turn 4, and on turn 5 its attack trigger (+9999/+0) makes 10,000 combat damage.

What needs fixing (data/WinOdds.kt, the combat step in `Game.play`): the damage of the attackers that aren't
commanders is added up and spread over the opponents, lowest life first, so one huge creature knocks out all three.
A creature can only attack one player: each attacker's damage should go to one opponent (still nobody blocking),
for example the biggest attackers first, each onto the opponent it finishes or else the one with the least life.
Then Jumbo Cactuar takes out one opponent, not three. This affects every deck a little (one big attacker, overrun
pumps on few creatures), so the before/after numbers for all decks go in the release notes.

Settled 2026-10-23: keep "nobody blocks" (no chump blocks).

Released in MTG Trader 1.56 (2026-10-23): each attacker's damage goes to one opponent, the biggest first, onto the
opponent with the most life it finishes or else the one with the least life; myriad copies hit each opponent; an extra
combat lets a creature attack someone else. Ruby's first win turn 5 → 7 (4 in 10 still turn 10); Baba's first win
8 → 9; 9 in 10 one turn later for Taranika band and Tawnos' Army; Grismold's 4 in 10 14 → 15; the rest unchanged.
No health check change.

### The simulator reads ramp, Treasures, the graveyard, copies, planeswalkers, Clues and counters (MTG Trader 1.55)
*Added 2026-10-21. MTG Trader only. Planned 2026-10-21; green light the same day ("A, one release, green light").*

The owner's words: "For the Ruby deck is the simulator capturing the 2-4-7 ramp? … ETB effects that pump creatures and give
trample? For quintorius deck, the commander generates tokens and can pump all the spirits. … Poly Jace deck commander's
ability to cheat creatures into the battlefield … are etb effects accounted for? Important ones like the craterhoof in the
Teval deck." Then: "Vohar Danse is also a deck that cheats creatures from the graveyard and has a lot of massive etb
effects, the deck does not care about attacking with Vohar … The tawnos deck also mills while creating artifact copies and
eventually reanimating … Is the Ganax deck accounting for the extra treasures when dragons come in and Ganax is on the
battlefield? And also accounting for the Haunted One pump?" While it was being built the owner added (and approved:
"You are free to work on a fix everytime you catch something that needs to be improved"): Deep Clue Sea (Morska's Clue
and counters, "second card each turn" triggers), Gilanra as a mana creature that doesn't attack, Turtle Power
(Leonardo's counters, "mana can be used … to pump all creatures"), Cloud (attack trigger's cards and Treasures at power
7, free equip), Varina (attack loot, discards for mass reanimation), The Council of Four (cards drawn on the opponents'
turns, Howling Mine), then cascade ("cascade triggers are accounted properly? Can impact Ruby's deck") and paradigm
("Cards like improvisation capstone have paradigm, make sure that is also accounted for as it gets cast for free on
following turns").

Settled with the owner: planeswalker loyalty stored with the card details (**A**: a new card_info column, database 25,
not synced), **one release**.

What 1.55 reads (data/WinOdds.kt, data/ManaOdds.kt, from the card text): ramp spells' every land and Cultivate's land in
hand (also in Casting the commander); Treasures as mana (Ganax, Goldspan, Xorn), cost reducers, affinity, improvise,
Urza's and Jaheira's mana; lands and rocks pay first, a creature that taps for mana or an ability doesn't attack;
team pumps on creatures, +X/+X per creature (Craterhoof), lords of a type, pumps with the mana left, Ruby's own pump,
Haunted One, haste for many, myriad; life loss in longer sentences, "enters or attacks", X per Dragon, devotion, Terror
of the Peaks, landfall tokens and counters, token and death drains, attack triggers only when attacking; a graveyard
(mill, looting with discards picked by rules, surveil, reanimation incl. mass, casting from it, flashback, "cards leave
your graveyard" tokens, discard payoffs); flicker, token copies, Tawnos, polymorph, Sneak Attack, X token spells;
planeswalkers' loyalty abilities; Clues, the second card drawn each turn, Mechanized Production and Hellkite Tyrant
wins, the monarch; +1/+1 counters (Leonardo, Mutagens, Corpsejack, High Score, Raphael); the opponents' draw steps
(Howling Mine, Council of Four, Faerie Mastermind, Scrawling Crawler); cascade and discover (the spell found cast for
free with its own triggers, so cascades chain; Monstrous Vortex, Maelstrom Nexus, Aurora Phoenix coming back, Sweet-Gum
Recluse's counters), Improvisation Capstone, paradigm's free copy every turn. Rules version 4, so every deck is worked out
again once; the 10,000 games run in four batches side by side. The How? note lists the new counts.

Results with the owner's decks (4 in 10 games by, before → after; 10,000 games): Turtle Power 12 → 8, Deep Clue Sea
12 → 9, Teval 12 → 9, Quintorius 12 → 9, Cloud 11 → 9, Zombies 11 → 9, Ruby 11 → 10 (first win turn 5: Jumbo
Cactuar), Haunted Ganax 12 → 10, Tawnos' Army 12 → 10, Council of Four 13 → 11, Poly Jace 13 → 11, Smasher LCP
13 → 12, Vohar Danse 11 → 11 (9 in 10 by 13 → 12); Taranika band, Auntie Plague, Baba, Cult of Athreos, Plague of
Grismold, Omnath and Sarge the same. Cascade and paradigm on their own (added last): Ruby wins by turn 9 in 34 of 100
games instead of 25 and by turn 10 in 66 instead of 56; Smasher gains 4–6 in 100 from turn 11; Omnath barely moves. Still not counted: opponents' spells and creatures (blockers, removal, Massacre
Wurm, stealing), board wipes, dice, free spells other than cascade and discover (Etali), and abilities the patterns don't recognise.

Health check: **changed**: Scryfall's `loyalty` (Jace, Multiverse Architect "4"; Quintorius, History Chaser "5").
Released in MTG Trader 1.55 (2026-10-21).

### The win turn at 4 in 10 games, the first win highlighted, and a better simulator (MTG Trader 1.54)
*Added 2026-10-20. MTG Trader only. Planned with mockups and a prototype on 2026-10-20; green light on 2026-10-21.*

The owner's words: "I think I want to shown the turn by which the decks win in the bracket cards with 40% or higher
probability. In the deck page just highlight the fastest the deck can win and the turn with 40%+ probability." Then:
"Can you improve the simulator for decks like Baba, Smasher and Shrine Gate Win? Does maze's end is taken into account
as a win condition for shrine gates win?" and "Scrollvault also has a prediction for the turn a deck can win by, could
be interesting to check the differences between that and the simulator."

Settled with the owner on 2026-10-20:
- Bracket cards (the small tile on the all-decks list and the full card's Speed section): the turn by which 4 in 10
  games are won, instead of 5 in 10. The small tile keeps its caption "Wins by".
- Deck page → Stats → "Winning the game": style **A** (coloured turns in the headline, outlined tiles: green = first
  win, gold = 4 in 10); the first win is the first turn with 1% or more of games; the row starts at the first win (8 to
  10 turns). The bracket card's Speed section the same way.
- Nothing added to the Decks list.

The simulator:
- Effects that count permanents: "for each Shrine/Gate/creature you control", "where X is the number of …", "equal to
  the number of …" (damage, drain, tokens, cards drawn, +1/+1 counters, a creature's own +1/+1 for each).
- Triggers on a kind of permanent entering: "whenever (this or) another Shrine/Gate/enchantment you control enters"
  (drain, tokens with their types, e.g. Shrine tokens that count as Shrines, cards, counters, a basic land).
- Cards that put a Shrine onto the battlefield (Sanctum of All each upkeep, Hei Bai once).
- Maze's End as a way to win (10 Gates with different names): played early, used each turn when it can, Gate searches
  (Circuitous Route, Navigation Orb, Open the Gates, Gatecreeper Vine, Sylvan Scrying for Maze's End itself) bring real
  Gates; lands entering untapped (Amulet of Vigor, Spelunking, Gond Gate, Tiller Engine, Horizon Explorer);
  Crackling Perimeter, Basilisk Gate, Sage of the Maze, Gatebreaker Ram. Maze's End wins shown on their own, not as
  a combo.
- Sacrifice decks: abilities used at once (Thousand-Year Elixir, Tyvar, Lightning Greaves/Swiftfoot Boots), extra uses
  (Instill Energy, Nature's Chosen, Patriar's Seal, Thousand-Year Elixir, Tyvar), Marvin copying Baba, Liquimetal
  making a permanent an artifact, payoffs (Marionette Master, Titania, Baloth Prime with its stun counters, Mazirek,
  lands leaving tokens), fabricate.
- X spells that drain each opponent (Exsanguinate, Jaya's Immolating Inferno): with the mana left, first when Vial
  Smasher-like triggers make them the biggest spell, or at once when they finish the game; Double Vision copies them.
- Fix: "if you control three or more Deserts, create…" (Hour of Promise) no longer counts without Deserts.
- The rules version goes up, so every deck is played again once on the phone.

Results with the owner's decks on 2026-10-21 (4 in 10 games by, before → after): Shrine Gate Win 15 → 10 (Maze's End
wins about 20% of games; first win 12 → 7), Baba 14 → 13 (1 in 10 by 12 → 10), Smasher LCP 13 → 13 (9 in 10 by
turn 15 now). The other 16 decks: the same turns. Still not counted: opponents (Kaervek), free spells (Etali,
Sunbird's Invocation), copying held for later (Dualcaster Mage, Reverberate), blockers and removal.

ScrollVault's win turns are compared later (see "Compare the win turn with ScrollVault's").

No health check change (no new service or data field).
Released in MTG Trader 1.54 (2026-10-21).

### When the deck can win (goldfish win turn), also on the bracket card
*Added 2026-10-20. MTG Trader only. Planned with mockups and given the green light on 2026-10-20.*

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

Added during the build (owner, 2026-10-20): "make sure that turn calculations work with commander damage, you can use
my Cloud deck or my Sarge deck. One has equipments to pump and the other has a lot of pump spells." Then: "Is the win by
turn calculation accounting for Baba's card draw and drain". Both went in: equipment, auras, pump spells (also +X/+X),
double strike and haste on the commander, 21 commander damage per commander knocking a player out, drawing from combat
damage (Sarge's "draw that many cards"), Food/Clue/Treasure makers, and sacrifice abilities like Baba Lysaga's (the
cheapest permanents covering three card types; in such a deck every permanent is cast as fodder).

Released in MTG Trader 1.52 (2026-10-20). Results with the owner's decks (usually by / 9 in 10 by): Sarge 8 / 10,
Cloud 11 / 12, Auntie Plague 11 / 13, Taranika band 11 / 13, Isshin Wide 11 / 13, Smasher LCP 13 / 15+, Baba 15 / 15+.
Kept in `win_odds.json` on the phone (not synced, not in the database). No health check change (Scryfall's power and
rules text and Commander Salt's combos were already read).

### Combos and tutors in the win turn (MTG Trader 1.53)
*Added 2026-10-20. MTG Trader only.*

The owner's words, after 1.52: "I think something is off. What about combo wins? Especially in auntie's deck". 1.52
counted only combos Commander Salt marks as winning and needing nothing else on the battlefield, which left out all 8
of Auntie Plague's, although Soul Immolation + Tree of Perdition wins outright and Devoted Druid + Mikaeus and the
Blowfly Infestation loops win with Obelisk Spider, Grave Venerations or The Meathook Massacre. Planned and approved on
2026-10-20 ("Green light, count token loops next turn, add tutors"):
- combos read by what they do: outright wins; loops (deaths, creatures entering, -1/-1 counters you put) with a matching
  life-loss card; endless tokens winning the turn after; Commander Spellbook's prerequisites (other creatures, a card
  that can start the loop, an opponent's creature from turn 4); {T} pieces out since the turn before; instant and
  sorcery pieces held for the combo;
- tutors (not land searches) fetch the missing piece of the combo closest to done.
Released in MTG Trader 1.53 (2026-10-20). Auntie Plague: about 1 in 4 games won by combo, 9% by turn 8, still
usually turn 11. Health check: Commander Salt's combo prerequisites (complexity.*Prerequisites / preconditions lines)
were added.

### The turn badge covers the name label
*Added 2026-10-20. MTG Trader only.*

The owner's words: "fix the turn badge to avoid overlaps."

Found in 1.51's 5-player test picture (`life_tracker_5.png`): on the player whose turn it is, the gold "Turn 1:42 ▶|"
badge sits in the panel's corner on top of the name label, hiding part of the name ("oel" for Joel). It happens
where the panel is short and the label is long (name and deck), and it was already so before 1.51. The fix keeps
the badge clear of the label (and of the mini-grid and counter badges) on every table size and layout.

Settled with the owner on 2026-10-20 from mockups: **A**, the badge at the start of the name label (the deck name
shortened when there's no room), with the turn time; in the big-number layout (no label) it stays in the corner.
Released in MTG Trader 1.52 (2026-10-20).

### The scanner list shows the newest card
*Added 2026-10-20. MTG Trader only.*

The owner's words: "When scanning cards, I would like that the list under the scanner scrolls automatically so that the
last card scanner is always visible on top of the list." New cards were already put on top, but the list kept showing
the card that was on top before. Now it jumps to the newest card each time one is added (not when a printing is
changed). Approved for 1.52 by the owner on 2026-10-20; released in MTG Trader 1.52.


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

