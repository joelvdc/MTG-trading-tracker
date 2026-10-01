package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What ScrollVault's Commander Bracket Calculator says about a deck. */
@Serializable
data class ScrollVaultReading(
    val power: Double,
    /** The "±" on the power level. */
    val margin: Double? = null,
    /** ScrollVault's own bracket verdict (the app's brackets are Commander Salt's). */
    val bracket: Int? = null,
    /** The neighbouring bracket when the verdict is borderline. */
    val borderline: Int? = null,
    /** Turn a typical hand wins on, and the earliest (best 5% of hands), from its goldfish simulation. */
    val typicalWin: Int? = null,
    val earliestWin: Int? = null,
    /** Its "Tell your pod" summary, e.g. "Bracket 3 (Upgraded) · 0 Game Changers · typical win ~T8". */
    val podLine: String? = null,
    val gameChangers: Int? = null,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String?): ScrollVaultReading? = text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }

        private val bracketRe = Regex("""VERDICT\s+B(\d)\b""")
        private val borderlineRe = Regex("""borderline\s*[↑↓]\s*B(\d)""")
        private val powerRe = Regex("""(\d+(?:\.\d+)?)\s*±\s*(\d+(?:\.\d+)?)\s*POWER\s*/\s*10""")
        private val winRe = Regex("""typical win ~T(\d+)(?:\s*\(earliest T(\d+)\))?""")
        private val podRe = Regex("""TELL YOUR POD\s*\n\s*([^\n]+)""")
        private val gcRe = Regex("""(\d+)\s*\n\s*GAME CHANGERS?\b""")

        /** The result in the page's text, or null while it's still working. */
        fun parse(pageText: String): ScrollVaultReading? {
            val verdict = pageText.substringAfter("VERDICT", "").takeIf { it.isNotEmpty() } ?: return null
            val text = "VERDICT$verdict"
            val power = powerRe.find(text) ?: return null
            val win = winRe.find(text)
            return ScrollVaultReading(
                power = power.groupValues[1].toDouble(),
                margin = power.groupValues[2].toDoubleOrNull(),
                bracket = bracketRe.find(text)?.groupValues?.get(1)?.toIntOrNull(),
                borderline = borderlineRe.find(text)?.groupValues?.get(1)?.toIntOrNull(),
                typicalWin = win?.groupValues?.get(1)?.toIntOrNull(),
                earliestWin = win?.groupValues?.get(2)?.toIntOrNull(),
                podLine = podRe.find(text)?.groupValues?.get(1)?.trim(),
                gameChangers = gcRe.find(text)?.groupValues?.get(1)?.toIntOrNull(),
            )
        }
    }
}

/**
 * ScrollVault (scrollvault.net/tools/commander-bracket) has no API: its engine runs in the page.
 * The app opens the page out of sight, enters the deck's Archidekt link, presses Analyze and reads
 * the verdict. Ads and trackers aren't loaded.
 */
class ScrollVaultApi(private val browser: HiddenBrowser) {
    suspend fun rate(archidektUrl: String): ScrollVaultReading {
        val link = archidektUrl.replace("\\", "\\\\").replace("'", "\\'")
        val start = """
            (function() {
              var ta = document.querySelector('textarea');
              var btn = Array.prototype.slice.call(document.querySelectorAll('button')).find(function(b) { return /analy[sz]e deck/i.test(b.innerText); });
              if (!ta || !btn) return 'wait';
              Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(ta, '$link');
              ta.dispatchEvent(new Event('input', { bubbles: true }));
              btn.click();
              return 'started';
            })()
        """.trimIndent()
        return browser.read(PAGE, "ScrollVault", ALLOWED_HOSTS, start, timeoutS = 90, parse = ScrollVaultReading::parse)
    }

    private companion object {
        const val PAGE = "https://scrollvault.net/tools/commander-bracket/"
        /** The calculator itself and the card data it uses; everything else on the page is ads and tracking. */
        val ALLOWED_HOSTS = setOf("scrollvault.net", "scryfall.com", "scryfall.io")
    }
}
