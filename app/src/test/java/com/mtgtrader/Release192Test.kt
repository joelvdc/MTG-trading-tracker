package com.mtgtrader

import com.mtgtrader.data.Binder
import com.mtgtrader.data.NetworkMonitor
import com.mtgtrader.data.NextcloudClient
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncManager
import com.mtgtrader.data.SyncStore
import com.mtgtrader.data.WebAccountClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 1.92: the sync driver ([SyncManager]) against a fake website: an edit made while a sync is saving still goes out
 * with the next sync, edits made during a sync start another one, an unexpected error becomes a message instead of
 * closing the app, and a collision with another phone is merged and saved again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release192Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var c: AppContainer
    private lateinit var site: FakeSite
    private lateinit var scope: CoroutineScope

    /** The website's /api/sync: one file, versions "v1", "v2"…, with the If-Match / If-None-Match rules. */
    private class FakeSite : Interceptor {
        var bytes: ByteArray? = null
        var version = 0
        var puts = 0
        /** Runs while a save is on its way, before the website accepts it. */
        var duringPut: (() -> Unit)? = null
        /** Thrown at the next request. */
        var failWith: RuntimeException? = null
        /** The next save answers 412, as if another phone saved first (and its file is [bytes] by then). */
        var collideOnce: ByteArray? = null

        fun file(): SyncFile = SyncFile.decode(bytes!!)

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            failWith?.let { failWith = null; throw it }
            fun answer(code: Int, etag: String? = null, bin: ByteArray? = null) = Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(code).message("")
                .apply { if (etag != null) header("ETag", etag) }
                .body((bin ?: ByteArray(0)).toResponseBody("application/gzip".toMediaType()))
                .build()
            val tag = "\"v$version\""
            return when (req.method) {
                "GET" -> when {
                    bytes == null -> answer(404)
                    req.header("If-None-Match") == tag -> answer(304, tag)
                    else -> answer(200, tag, bytes)
                }
                "PUT" -> {
                    duringPut?.let { duringPut = null; it() }
                    collideOnce?.let { other -> collideOnce = null; bytes = other; version++; return answer(412) }
                    val ok = if (req.header("If-Match") != null) req.header("If-Match") == tag else bytes == null
                    if (!ok) return answer(412)
                    bytes = Buffer().also { req.body!!.writeTo(it) }.readByteArray()
                    version++
                    puts++
                    answer(204, "\"v$version\"")
                }
                else -> answer(405)
            }
        }
    }

    @Before
    fun setUp() {
        listOf("settings", "sync").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
        context.deleteDatabase("mtgtrader.db")
        c = AppContainer(context)
        site = FakeSite()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Logged in and past the first sync (the keystore isn't there in tests, so the token is stored as it is).
        context.getSharedPreferences("sync", 0).edit()
            .putString("webUser", "joel").putString("webToken", "tok").putBoolean("firstDone", true).commit()
    }

    @After
    fun tearDown() {
        scope.cancel()
        c.db.close()
    }

    private fun manager(debounceMs: Long = 60_000) = SyncManager(
        context, c.db, SyncStore(c.db, c.settings), NextcloudClient(OkHttpClient()),
        WebAccountClient(OkHttpClient.Builder().addInterceptor(site).build(), base = "https://fake.test/"),
        NetworkMonitor(context), scope, decrypt = { it }, debounceMs = debounceMs,
    )

    private fun binder(name: String) = runBlocking { c.db.binderDao().insert(Binder(name = name)) }

    private fun binderNamesOnSite() = site.file().data.binders.map { it.name }.toSet()

    @Test
    fun editWhileSavingGoesOutWithTheNextSync() = runBlocking {
        val sync = manager()
        binder("First")
        assertTrue(sync.sync())
        assertEquals(setOf("First"), binderNamesOnSite())

        binder("Second")
        // "Third" is made while the save with "Second" is on its way: that save doesn't hold it.
        site.duringPut = { binder("Third") }
        assertTrue(sync.sync())
        assertEquals(setOf("First", "Second"), binderNamesOnSite())

        // Until 1.91 the phone then thought it had sent everything, and this sync only asked "changed?" and stopped.
        assertTrue(sync.sync())
        assertEquals(setOf("First", "Second", "Third"), binderNamesOnSite())
    }

    @Test
    fun nothingChangedSendsNothing() = runBlocking {
        val sync = manager()
        binder("First")
        assertTrue(sync.sync())
        val puts = site.puts
        assertTrue(sync.sync())
        assertEquals(puts, site.puts)
    }

    @Test
    fun editsDuringASyncStartAnotherOne() = runBlocking {
        val sync = manager(debounceMs = 200)
        binder("First")
        site.duringPut = { binder("Made meanwhile") }
        assertTrue(sync.sync())
        // No "Sync now": the change made while syncing starts the next sync by itself.
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until && "Made meanwhile" !in binderNamesOnSite()) Thread.sleep(50)
        assertTrue("Made meanwhile" in binderNamesOnSite())
    }

    @Test
    fun anUnexpectedErrorBecomesAMessage() = runBlocking {
        val sync = manager()
        binder("First")
        site.failWith = IllegalStateException("odd answer")
        assertFalse(sync.sync())
        val error = sync.status.value.lastError
        assertNotNull(error)
        assertTrue(error!!, error.contains("IllegalStateException") && error.contains("odd answer"))
        assertFalse(sync.status.value.running)
        // And the next try works.
        assertTrue(sync.sync())
        assertNull(sync.status.value.lastError)
        assertEquals(setOf("First"), binderNamesOnSite())
    }

    @Test
    fun aCollisionIsMergedAndSavedAgain() = runBlocking {
        val sync = manager()
        binder("Mine")
        assertTrue(sync.sync())
        // Another phone adds "Theirs" just before this phone's next save.
        val theirs = site.file().let { f ->
            val b = f.data.binders.single().copy(uid = "other-phone-binder", name = "Theirs", updatedAt = System.currentTimeMillis())
            SyncFile.encode(f.copy(data = f.data.copy(binders = f.data.binders + b)))
        }
        binder("Mine too")
        site.collideOnce = theirs
        assertTrue(sync.sync())
        assertEquals(setOf("Mine", "Mine too", "Theirs"), binderNamesOnSite())
        assertTrue(c.db.binderDao().all().any { it.name == "Theirs" })
    }

    @Test
    fun webSyncFormatMatchesTheApp() {
        // The website reads files up to its WEB_FORMAT and refuses newer ones: both numbers move together.
        val ts = listOf(File("../web/src/lib/syncfile.ts"), File("web/src/lib/syncfile.ts")).first { it.exists() }.readText()
        val web = Regex("""export const WEB_FORMAT = (\d+)""").find(ts)!!.groupValues[1].toInt()
        assertEquals(SyncFile.FORMAT, web)
    }
}
