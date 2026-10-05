package com.mtgtrader.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.Normalizer
import java.time.LocalDate

/** Where recommendations come from. Since 1.18. */
enum class RecSource(val key: String, val label: String) {
    EDHREC("edhrec", "EDHREC"),
    RECOMMANDER("recommander", "recommander.cards");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: EDHREC
    }
}

/** One recommended card, with what the source says about it and what Scryfall says it is. */
@Serializable
data class RecCard(
    val name: String,
    val card: CardRef? = null,
    val typeLine: String = "",
    val cmc: Double = 0.0,
    /** recommander.cards' score (0.7–1). */
    val score: Double? = null,
    /** EDHREC: how much more often the card is played with this commander than elsewhere (−1…1). */
    val synergy: Double? = null,
    /** EDHREC: decks with this commander that play the card, of those that could. */
    val decks: Int? = null,
    val potentialDecks: Int? = null,
    /** First printed in the last year. */
    val isNew: Boolean = false,
    val releasedAt: String? = null,
) {
    val inclusionPct: Int? get() = if (decks != null && potentialDecks != null && potentialDecks > 0) decks * 100 / potentialDecks else null
    val primaryType: String get() = DeckGrouping.primaryType(typeLine.substringBefore(" // ").substringBefore(" — "))
}

@Serializable
data class RecSection(val key: String, val title: String, val cards: List<RecCard>)

/** A deck's recommendations from one source, in the source's own sections. */
@Serializable
data class RecResult(val source: String, val fetchedAt: Long, val sections: List<RecSection>, val note: String? = null) {
    val allCards: List<RecCard> get() = sections.flatMap { it.cards }.distinctBy { it.name }
}

/** Saved recommendations per deck and source, so they show without waiting and offline. */
@Entity(tableName = "recommendations", primaryKeys = ["deckId", "source"])
data class RecCache(val deckId: Long, val source: String, val fetchedAt: Long, val json: String)

@Dao
interface RecDao {
    @Query("SELECT * FROM recommendations WHERE deckId = :deckId AND source = :source")
    suspend fun get(deckId: Long, source: String): RecCache?

    @Query("SELECT * FROM recommendations WHERE source = :source")
    suspend fun all(source: String): List<RecCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: RecCache)

    @Query("DELETE FROM recommendations WHERE deckId NOT IN (SELECT archidektId FROM decks)")
    suspend fun deleteOrphans()
}

/**
 * Puts a card into recommander.cards' categories. Its public API only gives names and scores, so
 * the function categories (ramp, removal, card advantage, tutors) are read from the rules text and
 * the type categories from the type line, the way the site lays them out.
 */
object RecCategories {
    val FUNCTION = listOf("tutors" to "Tutors", "mass-removal" to "Mass Removal", "spot-removal" to "Spot Removal", "ramp" to "Ramp", "card-advantage" to "Card Advantage")
    val TYPES = listOf(
        "Creature" to "Creatures", "Artifact" to "Artifacts", "Enchantment" to "Enchantments", "Instant" to "Instants",
        "Sorcery" to "Sorceries", "Planeswalker" to "Planeswalkers", "Battle" to "Battles",
    )

    private fun clean(text: String) = text.lowercase().replace(Regex("\\([^)]*\\)"), "")

    private val mass = Regex("""destroy all|exile all|destroy each|exile each (creature|nonland|artifact|enchantment|other)|all creatures get -|each creature gets -|damage to each creature|return all [^.]* to (their|its) owners?'? hands?""")
    private val spot = Regex("""destroy target|exile target (creature|artifact|enchantment|permanent|planeswalker|nonland|attacking|blocking|tapped|nontoken|card from a graveyard)|damage to (any target|target creature|target planeswalker|target attacking)|target creature gets -\d|target creature an opponent controls gets -|fights? (target|another target|up to one target)|return target (creature|nonland permanent|permanent|artifact|enchantment) to its owner's hand""")
    private val ramp = Regex("""add \{|add one mana|add (two|three|x) mana|adds? an additional|mana of any (color|type)|onto the battlefield tapped|play an additional land|put a land card from your hand onto the battlefield""")
    private val draw = Regex("""draws? (a|an|one|two|three|four|five|x|that many|cards equal|\d+) cards?|\binvestigate\b|draw cards equal|exile the top [^.]* (you may|until end of turn,? you may) (play|cast)|look at the top [^.]* put [^.]* into your hand""")
    private val tutor = Regex("""search your library for ([^.]*)""")

    /** The function category of a card, or null. */
    fun function(typeLine: String, text: String): String? {
        val t = clean(text)
        val tutored = tutor.findAll(t).map { it.groupValues[1] }.toList()
        // A search for lands only is ramp (or fixing), not a tutor.
        if (tutored.any { s -> !Regex("""\b(land|forest|island|swamp|mountain|plains|gate|desert)\b""").containsMatchIn(s.substringBefore(" card")) }) return "tutors"
        if (mass.containsMatchIn(t)) return "mass-removal"
        if (spot.containsMatchIn(t)) return "spot-removal"
        val land = "Land" in typeLine
        if (!land && (ramp.containsMatchIn(t) || tutored.isNotEmpty() && "onto the battlefield" in t)) return "ramp"
        if (draw.containsMatchIn(t)) return "card-advantage"
        return null
    }

    /** "Utility Lands" (lands that do more than make mana) or "Lands". */
    fun landKind(text: String): String {
        val lines = clean(text).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val plain = Regex("""^(\{t\}(, pay \d+ life)?: add [^.]*\.?|.*enters (the battlefield )?tapped.*|as .* enters.*choose a (color|basic land type).*|\{t\}, sacrifice .*: search your library for [^.]*land.*|.*you may pay \d+ life.*|.*unless you control.*)$""")
        return if (lines.all { plain.matches(it) }) "land" else "utility-lands"
    }
}

/** EDHREC's card lists for a commander (json.edhrec.com, what its website shows). */
class EdhrecApi(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /** "Baba Lysaga, Night Witch" → "baba-lysaga-night-witch"; partners are joined in alphabetical order. */
    fun slug(commanders: List<String>): String = commanders.map { name ->
        Normalizer.normalize(name.substringBefore(" // "), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .lowercase().replace(Regex("[^a-z0-9 -]"), "").trim().replace(Regex("[ -]+"), "-")
    }.sorted().joinToString("-")

    data class Entry(val id: String?, val name: String, val synergy: Double?, val decks: Int?, val potential: Int?)
    data class Page(val sections: List<Triple<String, String, List<Entry>>>)

    suspend fun commander(commanders: List<String>): Page = withContext(Dispatchers.IO) {
        val url = "https://json.edhrec.com/pages/commanders/${slug(commanders)}.json"
        http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (r.code == 404 || r.code == 403) throw IOException("EDHREC has no page for ${commanders.joinToString(" & ")} yet")
            if (!r.isSuccessful) throw IOException("EDHREC answered ${r.code}")
            parse(r.body!!.string())
        }
    }

    fun parse(body: String): Page {
        val root = json.parseToJsonElement(body) as? JsonObject ?: throw IOException("EDHREC sent something unexpected")
        val lists = ((root["container"] as? JsonObject)?.get("json_dict") as? JsonObject)?.get("cardlists") as? JsonArray
            ?: throw IOException("EDHREC has no card lists for this commander")
        return Page(lists.mapNotNull { l ->
            val o = l as? JsonObject ?: return@mapNotNull null
            val title = (o["header"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val tag = (o["tag"] as? JsonPrimitive)?.contentOrNull ?: title.lowercase()
            val cards = (o["cardviews"] as? JsonArray).orEmpty().mapNotNull { v ->
                val c = v as? JsonObject ?: return@mapNotNull null
                val name = (c["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                Entry(
                    (c["id"] as? JsonPrimitive)?.contentOrNull, name, (c["synergy"] as? JsonPrimitive)?.doubleOrNull,
                    (c["num_decks"] as? JsonPrimitive)?.intOrNull, (c["potential_decks"] as? JsonPrimitive)?.intOrNull,
                )
            }
            Triple(tag, title, cards)
        })
    }
}

/** recommander.cards' public API: recommendations for a commander and its current decklist. */
class RecommanderApi(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    data class Rec(val oracleId: String, val name: String, val score: Double)

    /** Up to 200 cards scoring above 0.7; with [candidates] (card names), only among those. */
    suspend fun recommend(commanders: List<String>, deck: List<String>, candidates: List<String>? = null): List<Rec> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("card_format", "name")
            put("commander", commanders.first())
            commanders.getOrNull(1)?.let { put("partner", it) }
            put("deck", JsonArray(deck.take(500).map(::JsonPrimitive)))
            if (candidates != null) put("candidate_step", buildJsonObject {
                put("source", "explicit")
                put("parameters", JsonArray(candidates.take(1000).map(::JsonPrimitive)))
            })
        }
        val req = Request.Builder().url("https://api.recommander.cards/public-release/api/decks/recommend/top")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val o = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull()
            val code = (o?.get("result_code") as? JsonPrimitive)?.contentOrNull
            when {
                code == "success" -> {}
                code == "error_invalid_cards" -> throw IOException("recommander.cards doesn't know this commander yet")
                code == "error_rate_limited" -> throw IOException("recommander.cards is busy; try again in a minute")
                code == "error_booting" || code == "error_model_loading" -> throw IOException("recommander.cards is starting up; try again shortly")
                !r.isSuccessful -> throw IOException("recommander.cards answered ${r.code}")
                else -> throw IOException("recommander.cards sent something unexpected")
            }
            val recs = ((o?.get("data") as? JsonObject)?.get("recommendations") as? JsonArray).orEmpty()
            recs.mapNotNull { e ->
                val c = e as? JsonObject ?: return@mapNotNull null
                Rec(
                    (c["oracle_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                    (c["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                    (c["score"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                )
            }
        }
    }
}

/** Progress of fetching recommendations for every deck. */
data class RecJob(val done: Int, val total: Int, val current: String)

/**
 * Recommendations for a deck from EDHREC or recommander.cards, kept in [RecCache]. Cards already in
 * the deck are left out; each card gets its Scryfall printing (for pictures, prices and adding it)
 * and whether it's new (first printed in the last year). Since 1.18.
 */
class Recommendations(
    private val db: AppDatabase,
    private val scryfall: ScryfallApi,
    private val edhrec: EdhrecApi,
    private val recommander: RecommanderApi,
    private val scope: CoroutineScope,
) {
    private val dao = db.recDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()

    private val _job = MutableStateFlow<RecJob?>(null)
    val job: StateFlow<RecJob?> = _job

    suspend fun cached(deckId: Long, source: RecSource): RecResult? =
        dao.get(deckId, source.key)?.let { runCatching { json.decodeFromString(RecResult.serializer(), it.json) }.getOrNull() }

    suspend fun allCached(source: RecSource): Map<Long, RecResult> = dao.all(source.key).mapNotNull { c ->
        runCatching { json.decodeFromString(RecResult.serializer(), c.json) }.getOrNull()?.let { c.deckId to it }
    }.toMap()

    /** Fetches fresh recommendations for a deck and saves them. */
    suspend fun fetch(deck: Deck, source: RecSource): RecResult = lock.withLock {
        val cards = db.deckDao().cards(deck.archidektId)
        val commanders = cards.filter { it.commander }.map { it.card.name }.ifEmpty { deck.commanders.split(" & ") }
        val inDeck = cards.map { key(it.card.name) }.toSet()
        val result = when (source) {
            RecSource.EDHREC -> fromEdhrec(commanders, inDeck)
            RecSource.RECOMMANDER -> fromRecommander(commanders, cards.filterNot { it.commander }.map { it.card.name }, deck.colorIdentity, inDeck)
        }
        dao.put(RecCache(deck.archidektId, source.key, result.fetchedAt, json.encodeToString(RecResult.serializer(), result)))
        result
    }

    /** Fetches every deck's recommendations in the background, one deck after the other. */
    fun fetchAll(source: RecSource): Boolean {
        if (_job.value != null) return false
        _job.value = RecJob(0, 0, "")
        scope.launch {
            try {
                val decks = db.deckDao().all()
                decks.forEachIndexed { i, d ->
                    _job.value = RecJob(i, decks.size, d.name)
                    try {
                        fetch(d, source)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // One deck failing (e.g. a brand-new commander) doesn't stop the others.
                    }
                }
                dao.deleteOrphans()
            } finally {
                _job.value = null
            }
        }
        return true
    }

    private fun key(name: String) = name.substringBefore(" // ").trim().lowercase()

    private val newSince get() = LocalDate.now().minusYears(1).toString()

    private fun ScryCard.isNewCard() = !reprint && (releasedAt ?: "") >= newSince

    private fun ScryCard.rec(base: RecCard) = base.copy(
        name = name, card = toRef(), typeLine = typeLine.ifEmpty { cardFaces?.joinToString(" // ") { it.typeLine } ?: "" },
        cmc = cmc, isNew = isNewCard(), releasedAt = releasedAt,
    )

    private suspend fun fromEdhrec(commanders: List<String>, inDeck: Set<String>): RecResult {
        val page = edhrec.commander(commanders)
        val ids = page.sections.flatMap { s -> s.third.mapNotNull { it.id } }.distinct()
        val sc = scryfall.collection(ids.map(ScryfallApi::idIdentifier)).associateBy { it.id }
        fun card(e: EdhrecApi.Entry): RecCard {
            val base = RecCard(e.name, synergy = e.synergy, decks = e.decks, potentialDecks = e.potential)
            return e.id?.let { sc[it] }?.rec(base) ?: base
        }
        val sections = page.sections.map { (tag, title, entries) ->
            RecSection(tag, title, entries.filter { key(it.name) !in inDeck }.map(::card))
        }.filter { it.cards.isNotEmpty() }
        // EDHREC shows five new cards; every recommended card first printed in the last year is listed.
        val edhNew = sections.firstOrNull { it.key == "newcards" }?.cards.orEmpty()
        val recent = (edhNew + sections.flatMap { it.cards }.filter { it.isNew }).distinctBy { it.name }
            .sortedByDescending { it.synergy ?: 0.0 }
        val newSection = RecSection("newcards", "New Cards (recent releases)", recent)
        val rest = sections.filterNot { it.key == "newcards" }
        return RecResult(RecSource.EDHREC.key, System.currentTimeMillis(), if (recent.isEmpty()) rest else listOf(newSection) + rest)
    }

    private suspend fun fromRecommander(commanders: List<String>, deck: List<String>, colorIdentity: String, inDeck: Set<String>): RecResult {
        val recs = recommander.recommend(commanders, deck).filter { key(it.name) !in inDeck }
        // New cards: the year's cards in the deck's colours, ranked for this deck.
        var newFailed = false
        val recent = runCatching {
            val ci = colorIdentity.ifEmpty { "c" }.lowercase()
            val newNames = scryfall.searchAll("f:commander id<=$ci not:reprint date>=$newSince -is:digital", maxPages = 6).map { it.name }
            if (newNames.isEmpty()) emptyList() else recommander.recommend(commanders, deck, newNames).filter { key(it.name) !in inDeck }
        }.onFailure { newFailed = true }.getOrDefault(emptyList())
        val ids = (recs + recent).map { it.oracleId }.distinct()
        val sc = scryfall.collection(ids.map(ScryfallApi::oracleIdentifier)).associateBy { it.oracleId }
        fun card(r: RecommanderApi.Rec): Pair<RecCard, ScryCard?> {
            val s = sc[r.oracleId]
            val base = RecCard(r.name, score = r.score)
            return (s?.rec(base) ?: base) to s
        }
        val all = recs.map(::card)
        val sections = mutableListOf<RecSection>()
        sections += RecSection("top", "Top Recommendations", all.take(30).map { it.first })
        val newCards = (recent.map(::card).map { it.first } + all.map { it.first }.filter { it.isNew }).distinctBy { it.name }
        if (newCards.isNotEmpty()) sections += RecSection("new-cards", "New Cards", newCards.sortedByDescending { it.score ?: 0.0 })
        val byFunction = all.groupBy { (c, s) -> s?.let { RecCategories.function(c.typeLine, it.fullText) } }
        for ((k, title) in RecCategories.FUNCTION) byFunction[k]?.let { sections += RecSection(k, title, it.map { p -> p.first }) }
        val staples = all.filter { (c, s) -> (s?.edhrecRank ?: Int.MAX_VALUE) <= 300 && "Land" !in c.typeLine }
        if (staples.isNotEmpty()) sections += RecSection("staples", "General Staples", staples.map { it.first })
        val nonLand = all.filterNot { "Land" in it.first.typeLine.substringBefore(" // ") }
        for ((type, title) in RecCategories.TYPES) {
            val list = nonLand.filter { it.first.primaryType == type }
            if (list.isNotEmpty()) sections += RecSection(type.lowercase(), title, list.map { it.first })
        }
        val lands = all.filter { "Land" in it.first.typeLine.substringBefore(" // ") }.groupBy { (_, s) -> RecCategories.landKind(s?.fullText.orEmpty()) }
        lands["utility-lands"]?.let { sections += RecSection("utility-lands", "Utility Lands", it.map { p -> p.first }) }
        lands["land"]?.let { sections += RecSection("land", "Lands", it.map { p -> p.first }) }
        return RecResult(
            RecSource.RECOMMANDER.key, System.currentTimeMillis(), sections,
            note = if (newFailed) "The new cards couldn't be fetched this time; refresh to try again." else null,
        )
    }

    /** Adds a recommended card to the deck (kept when the deck is reloaded from Archidekt). */
    suspend fun addToDeck(deckId: Long, rec: RecCard): Boolean {
        val card = rec.card ?: return false
        db.withTransaction {
            val same = db.deckDao().cardsAddedInApp(deckId).firstOrNull { it.card.scryfallId == card.scryfallId && !it.foil }
            if (same != null) db.deckDao().addQuantity(same.id, 1)
            else db.deckDao().insertCard(
                DeckCard(
                    deckId = deckId, card = card, quantity = 1, category = DeckToCollection.ADDED_IN_APP,
                    types = rec.typeLine.substringBefore(" — "), cmc = rec.cmc, commander = false, foil = false, addedInApp = true,
                )
            )
        }
        return true
    }
}
