package com.mtgtrader

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtgtrader.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each database update, checked against the schemas Room exports (app/schemas): the migrated
 * database must be exactly what a fresh install creates, and the data must survive. Schemas are
 * exported since version 9 (app 1.12).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun from9to10KeepsTheCollection() {
        helper.createDatabase(dbName, 9).apply {
            execSQL(
                "INSERT INTO collection (id, foil, condition, language, quantity, addedAt, etched, binderId, uid, updatedAt, scryfallId, name, setCode, " +
                    "setName, collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, hasNonFoil, hasFoil, foilType, hasEtched, flavorName) " +
                    "VALUES (1, 0, 'NM', 'EN', 3, 1, 0, 0, 'u1', 5, 'sid', 'Sol Ring', 'c21', 'Commander 2021', '263', 'uncommon', NULL, 559000, 1.0, 2.0, 1, 1, NULL, 0, NULL)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 10, true, AppDatabase.MIGRATION_9_10)
        db.query("SELECT name, quantity, uid, notes, purchasePrice, cardmarketFoilId FROM collection").use { c ->
            c.moveToFirst()
            assertEquals("Sol Ring", c.getString(0))
            assertEquals(3, c.getInt(1))
            assertEquals("u1", c.getString(2))
            assertEquals(true, c.isNull(3) && c.isNull(4) && c.isNull(5))
        }
        db.query("SELECT COUNT(*) FROM wishlist").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }

    @Test
    fun from10to11KeepsTheCollection() {
        helper.createDatabase(dbName, 10).apply {
            execSQL(
                "INSERT INTO collection (id, foil, condition, language, quantity, addedAt, etched, binderId, uid, updatedAt, scryfallId, name, setCode, " +
                    "setName, collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, hasNonFoil, hasFoil, foilType, hasEtched, " +
                    "flavorName, notes, purchasePrice, cardmarketFoilId) VALUES (1, 0, 'NM', 'EN', 2, 1, 0, 0, 'u1', 5, 'sid', 'Sol Ring', 'c21', " +
                    "'Commander 2021', '263', 'uncommon', NULL, 559000, 1.0, 2.0, 1, 1, NULL, 0, NULL, 'from a precon', 0.5, NULL)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 11, true, AppDatabase.MIGRATION_10_11)
        db.query("SELECT name, quantity, notes, purchasePrice FROM collection").use { c ->
            c.moveToFirst()
            assertEquals("Sol Ring", c.getString(0))
            assertEquals(2, c.getInt(1))
            assertEquals("from a precon", c.getString(2))
        }
        for (t in listOf("card_info", "recommendations", "trade_skips")) {
            db.query("SELECT COUNT(*) FROM $t").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }
}
