package com.mtgtrader.data

import java.util.Locale

object Csv {
    private fun escape(s: String) =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun row(vararg fields: Any?): String = fields.joinToString(",") { f ->
        when (f) {
            null -> ""
            is Double -> String.format(Locale.US, "%.2f", f)
            else -> escape(f.toString())
        }
    }

    /** RFC 4180-ish parser: quoted fields, doubled quotes, CRLF or LF line endings. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row += field.toString(); field.clear() }
                '\r' -> {}
                '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString(); rows += row
        }
        return rows
    }
}

/** Maps condition names used by ManaBox / Cardmarket / others onto Cardmarket's short codes. */
fun parseCondition(raw: String?): String {
    val s = raw?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return "NM"
    return when {
        s.isEmpty() -> "NM"
        s == "nm" || "near" in s -> "NM"
        s == "mt" || s == "m" || "mint" in s -> "MT"
        s == "ex" || "excellent" in s -> "EX"
        s == "gd" || s == "good" -> "GD"
        s == "lp" || "light" in s -> "LP"
        s == "po" || "poor" in s || "damaged" in s || "heav" in s -> "PO"
        s == "pl" || "played" in s || "moderate" in s -> "PL"
        else -> "NM"
    }
}

fun manaBoxCondition(code: String) = when (code) {
    "MT" -> "mint"
    "EX" -> "excellent"
    "GD" -> "good"
    "LP" -> "light_played"
    "PL" -> "played"
    "PO" -> "poor"
    else -> "near_mint"
}

/** ManaBox's "Foil" column values; special foils like surge foil are just "foil" there. */
fun manaBoxFinish(finish: Finish) = when (finish) {
    Finish.NONFOIL -> "normal"
    Finish.FOIL -> "foil"
    Finish.ETCHED -> "etched"
}

/** A yes/no column such as ManaBox's "Altered" ("true", "yes", "1"…). Since 1.27. */
fun csvFlag(raw: String?): Boolean = raw?.trim()?.lowercase() in setOf("true", "yes", "y", "1", "x", "signed", "altered")

fun parseLanguage(raw: String?): String {
    val s = raw?.trim()?.lowercase() ?: return "EN"
    LANGUAGES.firstOrNull { (code, name) -> s == code.lowercase() || s == name.lowercase() }?.let { return it.first }
    return when (s) {
        "", "english" -> "EN"
        "jp" -> "JA"
        "zh", "chinese" -> "ZHS"
        else -> "EN"
    }
}
