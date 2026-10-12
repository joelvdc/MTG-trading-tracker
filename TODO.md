# To-do list for MTG Trader

Ideas the owner (joelvdc) is collecting. **Nothing here is built until the owner gives the green light**; then
everything on the list is planned together and carried out (see `CLAUDE.md`).

How to use this file:
- Add new ideas under "Collecting", newest last, with the date and the owner's own wording.
- "Open questions" under an item are things to settle with the owner before building it.
- When an item is released, move it to "Done" with the version it shipped in.

## Collecting

### 1. Sort the collection by value change
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

### 2. Check a scanned package against its CardTrader order
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
- **Not ahead of the list:** wait for the owner's green light for the whole to-do list.

Open question:
- When the cards are added, should they take the order's details where the camera can't know them (price paid,
  condition, foil, signed/altered), or stay exactly as scanned? Suggested: take the order's details for cards that
  matched an order line.

## Done

*(nothing yet)*
