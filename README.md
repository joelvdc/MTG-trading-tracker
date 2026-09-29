# MTG Trader (Android)

Track Magic: The Gathering trades, check they're fair using Cardmarket prices, and keep your collection up to date.
Keep your Commander decks at hand too, with their power level, brackets and rule-zero cards.

## Install
Copy `MTG-Trader-1.6.apk` to your phone and open it (allow "install unknown apps" for your file manager/browser when asked).
It's built for 64-bit ARM phones (practically every phone from the last ~6 years). If it refuses to install, use
`MTG-Trader-1.6-universal.apk` instead (bigger, runs on any device).

## Where the data comes from (no app updates needed for new sets)
- **Cards & images:** Scryfall API, looked up live.
- **Decks:** Archidekt (decklists) and Commander Salt (scores and rule-zero cards).
- **Prices:** Cardmarket's public daily price guide (trend, average, low, 1/7/30-day averages, normal + foil), downloaded
  automatically once a day (~26 MB).

## Commander decks
Import a public deck from **Archidekt**: paste its link in the Decks tab, or share it to the app from the Archidekt app or
a browser. The app shows the decklist grouped by your Archidekt categories (or by card type) with Cardmarket prices, and
has [Commander Salt](https://www.commandersalt.com/) score it:
- **Power level** (out of 10), **realistic bracket** (how the deck actually plays) and **baseline bracket** (WotC's
  bracket rules to the letter), plus saltiness and archetype.
- The **bracket** and **power level rule-zero cards**, shown full screen (screen kept on at full brightness) to show
  your table, and shareable as images. They're saved on the phone, so they work without a connection.

Tap the refresh button on a deck after changing it on Archidekt. Commander Salt has no official API: the app uses the
same calls as its website, so this part may break if the site changes. Importing a deck adds it to commandersalt.com.

## Rebuilding
Requires JDK 17+ and the Android SDK (installed at `%USERPROFILE%\Android\sdk`, see `local.properties`).
```
set JAVA_HOME=C:\Program Files\Java\jdk-18.0.2
gradlew assembleRelease
```
APKs land in `%LOCALAPPDATA%\mtgtrader-build\app\outputs\apk\release\` (kept out of OneDrive on purpose).

**Keep `keystore/` and `keystore.properties` safe and private.** Android only installs an update over the existing app
if it is signed with the same key; losing it means uninstalling (and losing app data) to install a new version.

Tests: `gradlew testDebugUnitTest` (logic) and `gradlew connectedDebugAndroidTest` (OCR scanner pipeline, needs a device/emulator).
