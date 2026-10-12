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
- **Matched cards take the order's details** where the camera can't know them: price paid, condition, foil,
  signed/altered. Cards that matched no order line stay as scanned.
- **Not ahead of the list:** wait for the owner's green light for the whole to-do list.

### 3. Cardmarket orders: import to the collection, and check against scanned cards
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

### 4. Order history
*Added 2026-10-12. MTG Trader only. Suggested by Claude, picked by the owner.*

Once the app reads CardTrader and Cardmarket orders (items 2 and 3), remember each order: date, shop, what was paid
in total, and what those cards are worth now. Today the price paid is stored per card (`purchasePrice`) and the order
itself is forgotten after import.

Open questions:
- Where it lives (a screen under the collection's menu, or in the stats)?
- Neither file carries the order's date or number inside it (Cardmarket's file name has the shipment number): ask for
  the date at import, defaulting to today?
- Should the history sync between phones and be part of backups? (Suggested: yes; this adds to the database, which
  needs the owner's OK under `CLAUDE.md`.)

### 5. Profit and loss
*Added 2026-10-12. MTG Trader only. Suggested by Claude, picked by the owner.*

The app stores what was paid per card and shows it on the card page. Total it: paid versus current value for the
whole collection and per binder, and the best and worst buys. Only cards with a purchase price count; say how many
cards have none.

Open questions:
- A new section in the stats screen, or part of the value screen?
- Per order too, once item 4 exists?

### 6. Condition-adjusted values
*Added 2026-10-12. MTG Trader only. Suggested by Claude, picked by the owner.*

Cardmarket's guide has one price per card whatever its condition, so a Played card counts as much as a Near Mint one
in the collection total. An optional percentage per condition would make totals more realistic (Card Kingdom already
has a price per condition, `PriceSources.ckCondition`).

Open questions:
- The percentages (a default set, e.g. NM 100%, EX 85%, GD 70%, LP 60%, PL 45%, PO 30%, adjustable in Settings?).
- Where it applies: collection totals and stats only, or also trades and the trade binder?
- On by default, or off until switched on? How is an adjusted price marked on screen?

### 7. Fill in purchase prices from past orders
*Added 2026-10-12. MTG Trader only.*

Owner's wording: "I would also be interested in having a way to read a cardtrader excel or cardmarket csv, have the
app find the cards in my collection and update the purchase price accordingly. [...] I have about 300 orders on
cardmarket where the csv would have to be downloaded manually for each order, cardtrader would be less painful with
only 15 orders."

Proposed (2026-10-12): the same order reader as items 2 and 3, but instead of adding cards it finds them in the
collection and sets what was paid.
- Pick one file or many at once (300 files one by one is not workable).
- Match an order line to collection cards by printing, finish and language; condition as a tie-breaker.
- Show a preview before changing anything: would update N cards; lines with no card in the collection (sold or
  traded since); cards that already have a price.
- A restore point (backup) is made first, as before other bulk changes.

Decided with the owner (2026-10-12):
- **Bought several times at different prices:** the stack gets the average paid (weighted by copies).
- **Cards that already have a purchase price:** kept, unless the owner chooses "overwrite".
- **More copies in the collection than the orders cover:** all copies get the price (the owner: "average if the
  cards already have a price").

- **A stack that already has a price and has more copies than the orders cover:** average them, weighted by copies
  (4 copies at €2 with an order for 1 copy at €4 → €2.50). So "keep the existing price" only applies when the orders
  cover no more copies than the price already stands for; "overwrite" replaces instead of averaging.

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
- Collecting the monthly files: the owner finds doing it by hand for every month painful. Offered 2026-10-12: Claude
  reads the purchases list to find the months with purchases, then exports those months in the owner's Chrome with
  the owner's OK. Not part of building the app.
- CardTrader (about 15 orders) by hand is fine.

## Done

*(nothing yet)*
