package com.mtgtrader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * A Nextcloud account: [login] and [password] (an app password) to log in with, and [userId], the
 * id in WebDAV paths (it differs from the login when logging in with an e-mail address).
 */
data class NextcloudAccount(val server: String, val login: String, val password: String, val userId: String = login)

/** Talks to Nextcloud: logging in (Login Flow v2) and reading/writing the sync file over WebDAV. */
class NextcloudClient(http: OkHttpClient) {
    // Nextcloud names the app password after the User-Agent ("MTG Trader (Android)" in the user's security settings).
    private val http = http.newBuilder()
        .addInterceptor { it.proceed(it.request().newBuilder().header("User-Agent", "MTG Trader (Android)").build()) }
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Remote {
        /** Unchanged since the ETag we passed. */
        data object NotModified : Remote
        data object Missing : Remote
        class Found(val bytes: ByteArray, val etag: String?) : Remote
    }

    sealed interface PutResult {
        class Ok(val etag: String?) : PutResult
        /** Someone else wrote the file since we read it. */
        data object Conflict : PutResult
    }

    data class LoginStart(val loginUrl: String, val pollUrl: String, val token: String)

    /** Starts a browser login: the user approves the app at [LoginStart.loginUrl]; then [pollLogin] returns the app password. */
    suspend fun startLogin(server: String): LoginStart = call {
        val url = base(server).newBuilder().addPathSegments("index.php/login/v2").build()
        http.newCall(Request.Builder().url(url).post(FormBody.Builder().build()).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException(if (r.code == 404) "No Nextcloud found at $server" else "Nextcloud answered ${r.code}")
            val o = json.parseToJsonElement(r.body!!.string()).jsonObject
            val poll = o["poll"]!!.jsonObject
            LoginStart(o.str("login")!!, poll.str("endpoint")!!, poll.str("token")!!)
        }
    }

    /** The account once the user has approved the login in the browser; null while still waiting. */
    suspend fun pollLogin(start: LoginStart): NextcloudAccount? = call {
        val body = FormBody.Builder().add("token", start.token).build()
        http.newCall(Request.Builder().url(start.pollUrl).post(body).build()).execute().use { r ->
            if (r.code == 404) return@call null
            if (!r.isSuccessful) throw IOException("Nextcloud answered ${r.code}")
            val o = json.parseToJsonElement(r.body!!.string()).jsonObject
            NextcloudAccount(o.str("server")!!.trimEnd('/'), o.str("loginName")!!, o.str("appPassword")!!)
        }
    }

    /** Checks the login and returns the account with Nextcloud's user id (which may differ from the login name, e.g. an e-mail address). */
    suspend fun verify(account: NextcloudAccount): NextcloudAccount = call {
        val url = base(account.server).newBuilder().addPathSegments("ocs/v2.php/cloud/user").addQueryParameter("format", "json").build()
        http.newCall(ocs(account, url).get().build()).execute().use { r ->
            if (r.code == 401) throw SyncException("Nextcloud didn't accept the user name or app password.")
            if (!r.isSuccessful) throw IOException("Nextcloud answered ${r.code}")
            val id = json.parseToJsonElement(r.body!!.string()).jsonObject["ocs"]?.jsonObject?.get("data")?.jsonObject?.str("id")
            account.copy(userId = id ?: account.login)
        }
    }

    /** Deletes the app password on the server (on disconnecting). */
    suspend fun revoke(account: NextcloudAccount) = call {
        val url = base(account.server).newBuilder().addPathSegments("ocs/v2.php/core/apppassword").build()
        http.newCall(ocs(account, url).delete().build()).execute().close()
    }

    suspend fun get(account: NextcloudAccount, etag: String?): Remote = call {
        val req = auth(account, file(account)).get().apply { if (etag != null) header("If-None-Match", etag) }.build()
        http.newCall(req).execute().use { r ->
            when {
                r.code == 304 -> Remote.NotModified
                r.code == 404 -> Remote.Missing
                r.isSuccessful -> Remote.Found(r.body!!.bytes(), r.etag())
                else -> throw failure(r)
            }
        }
    }

    /** Writes the file if it's still the version with [ifMatch] (or still absent when null). */
    suspend fun put(account: NextcloudAccount, bytes: ByteArray, ifMatch: String?): PutResult = call {
        fun attempt(): Response {
            val req = auth(account, file(account))
                .put(bytes.toRequestBody("application/gzip".toMediaType()))
                .apply { if (ifMatch != null) header("If-Match", ifMatch) else header("If-None-Match", "*") }
                .build()
            return http.newCall(req).execute()
        }
        fun makeFolder() = http.newCall(auth(account, folder(account)).method("MKCOL", null).build()).execute().close()
        // A new file may need its folder first (Nextcloud answers 405 if it exists already).
        if (ifMatch == null) makeFolder()
        var r = attempt()
        if (r.code == 404 || r.code == 409) {
            r.close()
            makeFolder()
            r = attempt()
        }
        r.use {
            when {
                it.code == 412 -> PutResult.Conflict
                it.isSuccessful -> PutResult.Ok(it.etag() ?: head(account))
                else -> throw failure(it)
            }
        }
    }

    private fun head(account: NextcloudAccount): String? =
        http.newCall(auth(account, file(account)).head().build()).execute().use { it.etag() }

    private fun Response.etag() = header("OC-ETag") ?: header("ETag")

    private fun failure(r: Response) = when (r.code) {
        401 -> SyncException("Nextcloud no longer accepts this phone's login. Disconnect and connect again.")
        507 -> SyncException("Your Nextcloud storage is full.")
        else -> IOException("Nextcloud answered ${r.code}")
    }

    private fun auth(a: NextcloudAccount, url: HttpUrl) =
        Request.Builder().url(url).header("Authorization", Credentials.basic(a.login, a.password, Charsets.UTF_8))

    private fun ocs(a: NextcloudAccount, url: HttpUrl) = auth(a, url).header("OCS-APIRequest", "true")

    private fun folder(a: NextcloudAccount) =
        base(a.server).newBuilder().addPathSegments("remote.php/dav/files").addPathSegment(a.userId).addPathSegment(FOLDER).build()

    private fun file(a: NextcloudAccount) = folder(a).newBuilder().addPathSegment(FILE).build()

    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    companion object {
        const val FOLDER = "MTG Trader"
        const val FILE = "sync.json.gz"

        /** "cloud.example.com" or "https://cloud.example.com/index.php/apps/files/" → "https://cloud.example.com". */
        fun normalizeServer(input: String): String? {
            var s = input.trim().trimEnd('/')
            if (s.isEmpty()) return null
            if (!s.contains("://")) s = "https://$s"
            for (cut in listOf("/index.php", "/apps/", "/remote.php", "/login")) {
                val i = s.indexOf(cut, s.indexOf("://") + 3)
                if (i > 0) s = s.substring(0, i)
            }
            s = s.trimEnd('/')
            return s.takeIf { it.toHttpUrlOrNull() != null }
        }

        private fun base(server: String): HttpUrl = server.toHttpUrlOrNull() ?: throw SyncException("That doesn't look like a server address.")
    }
}
