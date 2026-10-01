package com.mtgtrader.data

import java.net.URLEncoder

/** What edhpowerlevel.com reports for a deck. */
data class EdhReading(val power: Double, val imported: Int?)

/** Building the edhpowerlevel.com link for a deck, and reading its result from the page text. */
object EdhPowerLevelLink {
    /** "Cut // Ribbons" → "Cut": the site only knows front faces. */
    fun frontFace(name: String) = name.substringBefore(" // ").trim()

    /** The site's shared-list link: "Commander" and "Mainboard" sections, one "qty name" per line, lines joined by "~". */
    fun url(commanders: List<String>, mainboard: List<Pair<String, Int>>): String {
        val lines = listOf("Commander") + commanders.map { "1 ${frontFace(it)}" } +
            listOf("", "Mainboard") + mainboard.map { (name, qty) -> "$qty ${frontFace(name)}" }
        return "https://edhpowerlevel.com/?d=" + lines.joinToString("~") { URLEncoder.encode(it, "UTF-8") } + "~Z~"
    }

    private val powerRe = Regex("""Power Level\s*([\d.]+)\s*/\s*10\b""", RegexOption.IGNORE_CASE)
    private val importedRe = Regex("""(\d+) total cards imported""", RegexOption.IGNORE_CASE)

    /** The result in the page's text, or null while it's still working. */
    fun parse(pageText: String): EdhReading? {
        val power = powerRe.find(pageText)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return EdhReading(power, importedRe.find(pageText)?.groupValues?.get(1)?.toIntOrNull())
    }
}

/** edhpowerlevel.com has no API: its calculator runs in the web page, opened out of sight by [HiddenBrowser]. */
class EdhPowerLevelApi(private val browser: HiddenBrowser) {
    /** Rates the deck at [url]; [commander] must show up in the result (so it's this deck's, not the sample's). */
    suspend fun rate(url: String, commander: String): EdhReading =
        browser.read(url, "edhpowerlevel.com") { text -> EdhPowerLevelLink.parse(text)?.takeIf { text.contains(commander, ignoreCase = true) } }
}
