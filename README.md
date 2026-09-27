# MTG Trader (Android)

Track Magic: The Gathering trades, check they're fair using Cardmarket prices, and keep your collection up to date.

## Install
Copy `MTG-Trader-1.2.apk` to your phone and open it (allow "install unknown apps" for your file manager/browser when asked).
It's built for 64-bit ARM phones (practically every phone from the last ~6 years). If it refuses to install, use
`MTG-Trader-1.2-universal.apk` instead (bigger, runs on any device).

## Where the data comes from (no app updates needed for new sets)
- **Cards & images:** Scryfall API, looked up live.
- **Prices:** Cardmarket's public daily price guide (trend, average, low, 1/7/30-day averages, normal + foil), downloaded
  automatically once a day (~26 MB).

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
