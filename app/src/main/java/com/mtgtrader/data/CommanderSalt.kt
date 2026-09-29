package com.mtgtrader.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.roundToInt

@Serializable
private data class SaltHeadline(val saltPercentage: Double? = null)

@Serializable
private data class SaltProfile(val headline: SaltHeadline? = null)

@Serializable
private data class SaltSalt(val profile: SaltProfile? = null)

@Serializable
private data class SaltBrackets(val csBracket: Double? = null, val wotcBracket: Double? = null)

@Serializable
private data class SaltDetails(val brackets: SaltBrackets? = null, val salt: SaltSalt? = null)

@Serializable
private data class SaltDeck(
    val id: String = "",
    val powerLevelRating: Double? = null,
    val bracketRating: Double? = null,
    val archetypeLabel: String? = null,
    val details: SaltDetails? = null,
)

data class SaltScores(
    val saltId: String,
    val powerLevel: Double?,
    val bracketRealistic: Int?,
    val bracketBaseline: Int?,
    val saltPercent: Double?,
    val archetype: String?,
) {
    val complete get() = powerLevel != null && bracketRealistic != null
}

/**
 * Commander Salt (commandersalt.com) scores decks for power level, brackets and salt. It has no
 * official API; these are the calls its own website makes, so they may change without notice.
 */
class CommanderSaltApi(private val http: OkHttpClient) {
    private val base = "https://api.commandersalt.com"

    /**
     * Imports (or re-imports) the Archidekt deck, which makes Commander Salt score its current list.
     * Its import often fails on the first try and works on the next, so it's retried a few times;
     * if every try fails, the scores from an earlier import are used when there are any.
     */
    suspend fun score(archidektId: Long): SaltScores {
        val deckUrl = "https://archidekt.com/decks/$archidektId"
        val url = "$base/decks".toHttpUrl().newBuilder().addQueryParameter("url", deckUrl).build()
        var error: Exception? = null
        for (wait in RETRY_WAITS_MS) {
            if (wait > 0) delay(wait)
            try {
                val body = call(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build())
                val scores = parse(body)
                if (scores.complete) return scores
                return existing(scores.saltId.ifEmpty { deckId(archidektId) }) ?: scores
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
        }
        return existing(deckId(archidektId)) ?: throw error ?: IOException("Commander Salt couldn't score the deck")
    }

    /** Scores from an earlier import, or null if Commander Salt doesn't know the deck. */
    private suspend fun existing(saltId: String): SaltScores? = runCatching {
        val url = "$base/decks".toHttpUrl().newBuilder().addQueryParameter("id", saltId).build()
        parse(call(Request.Builder().url(url).get().build())).takeIf { it.complete }
    }.getOrNull()

    /** The rule-zero card as a PNG (360×504), with every section shown. */
    suspend fun ruleZeroCard(saltId: String, card: RuleZeroCard): ByteArray = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("id", saltId)
            put("isAuthor", false)
            put("isUser", false)
            put("format", "png")
            putJsonObject("displayOptions") {
                put("background", true)
                put("powerlevel", true)
                put("saltiness", true)
                put("wincons", true)
            }
        }.toString()
        val req = Request.Builder().url("$base/${card.endpoint}")
            .post(payload.toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val bytes = r.body?.bytes() ?: ByteArray(0)
            if (!r.isSuccessful) throw IOException(errorMessage(bytes.decodeToString(), r.code))
            if (!isPng(bytes)) throw IOException("Commander Salt didn't send an image")
            bytes
        }
    }

    private suspend fun call(req: Request): String = withContext(Dispatchers.IO) {
        http.newCall(req.newBuilder().header("Accept", "application/json").build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException(errorMessage(body, r.code))
            body
        }
    }

    companion object {
        private val RETRY_WAITS_MS = longArrayOf(0, 2_000, 4_000, 8_000)
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

        fun parse(body: String): SaltScores {
            if (body.isBlank() || body == "null") throw IOException("Commander Salt doesn't know this deck")
            val d = json.decodeFromString<SaltDeck>(body)
            val b = d.details?.brackets
            return SaltScores(
                saltId = d.id,
                powerLevel = d.powerLevelRating,
                bracketRealistic = (b?.csBracket ?: d.bracketRating)?.roundToInt(),
                bracketBaseline = b?.wotcBracket?.roundToInt(),
                saltPercent = d.details?.salt?.profile?.headline?.saltPercentage,
                archetype = d.archetypeLabel?.takeIf { it.isNotBlank() },
            )
        }

        /** Commander Salt's id for an Archidekt deck: the MD5 of the deck's Archidekt API address. */
        fun deckId(archidektId: Long): String =
            MessageDigest.getInstance("MD5").digest("https://archidekt.com/api/decks/$archidektId/".toByteArray())
                .joinToString("") { "%02x".format(it) }

        /** The server's own explanation ({"message": "ERROR: …"}) when it gives one. */
        fun errorMessage(body: String, code: Int): String {
            val msg = runCatching { (json.parseToJsonElement(body) as JsonObject)["message"]?.jsonPrimitive?.content }.getOrNull()
            return msg?.removePrefix("ERROR:")?.trim()?.takeIf { it.isNotEmpty() } ?: "Commander Salt error $code"
        }

        fun isPng(bytes: ByteArray) = bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
    }
}
