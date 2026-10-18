package com.mtgtrader

import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.DeckStats
import com.mtgtrader.data.Settings
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncPrefs
import com.mtgtrader.data.TokenPart
import com.mtgtrader.data.scryfallImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.45: choosing which printing of a token a deck shows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release145Test {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clear() {
        context.getSharedPreferences("settings", 0).edit().clear().commit()
    }

    private val treasureA = TokenPart("t1", "Treasure", "Token Artifact — Treasure")
    private val treasureB = TokenPart("t2", "Treasure", "Token Artifact — Treasure")

    private fun maker(name: String, vararg tokens: TokenPart) =
        StatsCard(name, name, 1, 2.0, "Creature", false, "Creature", "{1}{R}", "", "", TokenPart.encode(tokens.toList()))

    private val cards = listOf(maker("Scuzzback Scrounger", treasureA), maker("Village Pillagers", treasureB))
    private val details = mapOf(
        "t1" to CardInfo("t1", "treasure-oracle", "", "", "Token Artifact — Treasure", 0.0, null),
        "t2" to CardInfo("t2", "treasure-oracle", "", "", "Token Artifact — Treasure", 0.0, null),
    )

    @Test
    fun aPickedPrintingIsShownForItsToken() {
        val parts = DeckStats.tokenParts(cards)
        // Without a pick: the printing the first card links to.
        val plain = DeckStats.tokens(parts, details).single()
        assertEquals("treasure-oracle", plain.key)
        assertEquals("t1", plain.id)
        assertFalse(plain.picked)
        // Picked: that printing shows, and the cards' own is still known (to go back to it).
        val picked = DeckStats.tokens(parts, details, mapOf("treasure-oracle" to "hobbit-13")).single()
        assertEquals("hobbit-13", picked.id)
        assertEquals("t1", picked.defaultId)
        assertTrue(picked.picked)
        assertEquals(listOf("Scuzzback Scrounger", "Village Pillagers"), picked.makers)
        // A pick for another token changes nothing here.
        assertEquals("t1", DeckStats.tokens(parts, details, mapOf("insect-oracle" to "x")).single().id)
    }

    @Test
    fun picksAreKeptPerDeckAndSynced() {
        val s = Settings(context)
        s.setDeckTokenPick(7, "treasure-oracle", "hobbit-13")
        s.setDeckTokenPick(8, "treasure-oracle", "sld-2820")
        s.setDeckTokenPick(7, "insect-oracle", "insect-2")
        assertEquals(mapOf("treasure-oracle" to "hobbit-13", "insect-oracle" to "insect-2"), s.deckTokenPicks.value[7])
        // Back to the cards' own printing.
        s.setDeckTokenPick(7, "insect-oracle", null)
        assertEquals(mapOf("treasure-oracle" to "hobbit-13"), s.deckTokenPicks.value[7])
        // Kept when the app restarts.
        assertEquals(s.deckTokenPicks.value, Settings(context).deckTokenPicks.value)
        // And on the other phone, through sync.
        val prefs = s.syncedPrefs()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        val other = Settings(context)
        other.applySyncedPrefs(prefs)
        assertEquals(mapOf(7L to mapOf("treasure-oracle" to "hobbit-13"), 8L to mapOf("treasure-oracle" to "sld-2820")), other.deckTokenPicks.value)
        // Settings from a phone still on 1.44 (without picks) leave this phone's picks alone.
        other.applySyncedPrefs(SyncPrefs(prefs.updatedAt + 1, prefs.values - "deckTokenPicks"))
        assertEquals("hobbit-13", other.deckTokenPicks.value[7]?.get("treasure-oracle"))
        // One more preference in the shared settings, no new sync format.
        assertTrue(SyncFile.FORMAT >= 8)
    }

    @Test
    fun theTokensPicturesAreSavedForOfflineUse() {
        val ids = DeckStats.tokenImageIds(listOf(TokenPart.encode(listOf(treasureA)), null), mapOf(7L to mapOf("treasure-oracle" to "hobbit-13")))
        assertEquals(setOf("t1", "hobbit-13"), ids)
        assertEquals("https://cards.scryfall.io/small/front/h/o/hobbit-13.jpg", scryfallImage("hobbit-13", "small"))
    }
}
