package com.mtgtrader

import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionFilter
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.ColorMatch
import com.mtgtrader.data.DeckFilter
import com.mtgtrader.data.EdhrecApi
import com.mtgtrader.data.MtgColors
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.RecCategories
import com.mtgtrader.data.SortField
import com.mtgtrader.data.SortLevel
import com.mtgtrader.data.SortSpec
import com.mtgtrader.data.TradeBinderPlanner
import com.mtgtrader.data.TradeBinderRules
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private var nextId = 1L

private fun row(
    name: String, price: Double, colors: String = "", type: String = "Creature — Elf", cmc: Double = 2.0, rank: Int? = null,
    qty: Int = 1, binder: Long = 0, set: String = "tst", number: String = "1", avg30: Double = price, id: String = name,
): CollectionRow {
    val ref = CardRef(id, name, set, set.uppercase(), number, "rare", null, 1, null, null, true, false)
    return CollectionRow(
        CollectionItem(id = nextId++, card = ref, foil = false, quantity = qty, binderId = binder),
        PriceEntity(1, price, price, price, price, price, avg30, null, null, null, null, null, null),
        CardInfo(id, null, colors, colors, type, cmc, rank),
    )
}

class CollectionSortTest {
    @Test
    fun colorThenName() {
        val rows = listOf(
            row("Zombie", 1.0, "B"), row("Angel", 1.0, "W"), row("Forest", 0.1, "", "Basic Land — Forest"),
            row("Golgari Guildmage", 1.0, "BG"), row("Sol Ring", 1.0, "", "Artifact"), row("Bat", 1.0, "B"),
        )
        val spec = SortSpec(listOf(SortLevel(SortField.COLOR), SortLevel(SortField.NAME)))
        assertEquals(
            listOf("Angel", "Bat", "Zombie", "Golgari Guildmage", "Sol Ring", "Forest"),
            rows.sortedWith(spec.comparator(PriceType.TREND)).map { it.item.card.name },
        )
    }

    @Test
    fun levelsHaveTheirOwnDirection() {
        val rows = listOf(row("A", 5.0, cmc = 3.0), row("B", 1.0, cmc = 1.0), row("C", 9.0, cmc = 3.0))
        val spec = SortSpec(listOf(SortLevel(SortField.MANA_VALUE, reversed = true), SortLevel(SortField.VALUE, reversed = true)))
        assertEquals(listOf("A", "C", "B"), rows.sortedWith(spec.comparator(PriceType.TREND)).map { it.item.card.name })
        assertEquals(spec, SortSpec.decode(spec.encode()))
    }

    @Test
    fun colorGroupsInWubrgOrder() {
        assertTrue(MtgColors.sortKey("W", false) < MtgColors.sortKey("G", false))
        assertTrue(MtgColors.sortKey("G", false) < MtgColors.sortKey("WU", false))
        assertTrue(MtgColors.sortKey("WUBRG", false) < MtgColors.sortKey("", false))
        assertTrue(MtgColors.sortKey("", false) < MtgColors.sortKey("", true))
    }
}

class CollectionFilterTest {
    private val elf = row("Llanowar Elves", 0.3, "G", "Creature — Elf")
    private val pact = row("Golgari Charm", 1.2, "BG", "Instant")
    private val ring = row("Sol Ring", 1.5, "", "Artifact")

    @Test
    fun colors() {
        val any = CollectionFilter(colors = setOf('G'))
        assertTrue(any.matches(elf, PriceType.TREND, emptySet()) && any.matches(pact, PriceType.TREND, emptySet()))
        val exact = CollectionFilter(colors = setOf('G'), colorMatch = ColorMatch.EXACT)
        assertTrue(exact.matches(elf, PriceType.TREND, emptySet()) && !exact.matches(pact, PriceType.TREND, emptySet()))
        val within = CollectionFilter(colors = setOf('G'), colorMatch = ColorMatch.WITHIN)
        assertTrue(within.matches(ring, PriceType.TREND, emptySet()) && !within.matches(pact, PriceType.TREND, emptySet()))
        assertTrue(CollectionFilter(colors = setOf('C')).matches(ring, PriceType.TREND, emptySet()))
        assertTrue(CollectionFilter(colors = setOf('M')).matches(pact, PriceType.TREND, emptySet()))
    }

    @Test
    fun typePriceAndDecks() {
        val f = CollectionFilter(types = setOf("Instant", "Artifact"), minPrice = 1.3)
        assertEquals(listOf("Sol Ring"), listOf(elf, pact, ring).filter { f.matches(it, PriceType.TREND, emptySet()) }.map { it.item.card.name })
        val inDecks = CollectionFilter(decks = DeckFilter.IN_DECKS)
        assertTrue(inDecks.matches(ring, PriceType.TREND, setOf("sol ring")) && !inDecks.matches(elf, PriceType.TREND, setOf("sol ring")))
        assertEquals(2, f.count)
        assertEquals(f, CollectionFilter.decode(f.encode()))
    }
}

class TradeBinderPlannerTest {
    private val rules = TradeBinderRules(maxCards = 3, keepOne = true, minValue = 1.0)

    @Test
    fun onlySpareValuableCopiesByScore() {
        val rows = listOf(
            row("Sol Ring", 2.0, rank = 1, qty = 3),         // decks use 2: 1 spare
            row("Bulk Bear", 0.2, qty = 5),                  // too cheap
            row("Popular", 3.0, rank = 50, qty = 2),         // 1 spare (keep one)
            row("Obscure", 3.5, rank = 20_000, qty = 2),     // 1 spare, but not wanted by players
            row("Wanted", 10.0, qty = 2),                    // on the wishlist
            row("Forest", 5.0, type = "Basic Land — Forest", qty = 9),
        )
        val changes = TradeBinderPlanner.plan(rows, mapOf("sol ring" to 2), setOf("wanted"), 99L, rules, PriceType.TREND, emptySet())
        assertTrue(changes.all { it.add })
        // Popular (€3, top 100) outranks Obscure (€3.50, unranked); Sol Ring fills the third slot.
        assertEquals(listOf("Popular", "Obscure", "Sol Ring").sorted(), changes.map { it.card.name }.sorted())
        assertEquals("Popular", changes.first().card.name)
        assertEquals(3, changes.sumOf { it.copies })
    }

    @Test
    fun takesOutWhatDecksNeedNowAndRespectsSkips() {
        val rows = listOf(
            row("Sol Ring", 2.0, rank = 1, qty = 1, binder = 7, id = "sr"),
            row("Sol Ring", 2.0, rank = 1, qty = 1, binder = 0, id = "sr"),
            row("Popular", 3.0, rank = 50, qty = 2),
        )
        val changes = TradeBinderPlanner.plan(rows, mapOf("sol ring" to 2), emptySet(), 7L, rules, PriceType.TREND, emptySet())
        val out = changes.single { !it.add }
        assertEquals("Sol Ring", out.card.name)
        assertTrue(out.reason, "decks use" in out.reason)
        // Turning the removal down keeps it in; turning the addition down keeps it out.
        val again = TradeBinderPlanner.plan(rows, mapOf("sol ring" to 2), emptySet(), 7L, rules, PriceType.TREND, setOf(out.skipKey, changes.first { it.add }.skipKey))
        assertTrue(again.isEmpty())
    }
}

class RecommendationTest {
    @Test
    fun functionCategories() {
        assertEquals("tutors", RecCategories.function("Sorcery", "Search your library for a card, put that card into your hand, then shuffle."))
        assertEquals("ramp", RecCategories.function("Sorcery", "Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle."))
        assertEquals("ramp", RecCategories.function("Artifact", "{T}: Add {C}{C}."))
        assertEquals("mass-removal", RecCategories.function("Sorcery", "Destroy all creatures. They can't be regenerated."))
        assertEquals("spot-removal", RecCategories.function("Instant", "Destroy target creature or planeswalker."))
        assertEquals("card-advantage", RecCategories.function("Sorcery", "Draw two cards."))
        assertEquals(null, RecCategories.function("Creature — Bear", ""))
        assertEquals("land", RecCategories.landKind("({T}: Add {B} or {G}.)\nThis land enters tapped."))
        assertEquals("utility-lands", RecCategories.landKind("{T}: Add {C}.\n{2}{B}, {T}: Each opponent loses 1 life."))
    }

    @Test
    fun edhrecSlugsAndPages() {
        val api = EdhrecApi(OkHttpClient())
        assertEquals("baba-lysaga-night-witch", api.slug(listOf("Baba Lysaga, Night Witch")))
        assertEquals("kraum-ludevics-opus-tymna-the-weaver", api.slug(listOf("Tymna the Weaver", "Kraum, Ludevic's Opus")))
        assertEquals("jotun-grunt", api.slug(listOf("Jötun Grunt")))
        val page = api.parse(
            """{"container":{"json_dict":{"cardlists":[{"header":"New Cards","tag":"newcards","cardviews":[
              {"id":"abc","name":"Gardenize","synergy":0.04,"num_decks":21,"potential_decks":269}]},
              {"header":"Creatures","tag":"creatures","cardviews":[{"id":"def","name":"Bear","num_decks":5,"potential_decks":10}]}]}}}"""
        )
        assertEquals(listOf("newcards", "creatures"), page.sections.map { it.first })
        assertEquals(269, page.sections[0].third[0].potential)
    }
}
