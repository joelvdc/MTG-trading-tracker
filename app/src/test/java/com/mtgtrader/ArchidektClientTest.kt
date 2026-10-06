package com.mtgtrader

import com.mtgtrader.data.ArchidektCollectionClient
import com.mtgtrader.data.ArchidektLogin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class ArchidektClientTest {
    /** A one-request server that answers with exactly [response] and remembers the request line. */
    private fun serve(response: String): Pair<Int, () -> String> {
        val server = ServerSocket(0)
        var requestLine = ""
        val t = thread {
            server.accept().use { s ->
                val reader = s.getInputStream().bufferedReader()
                requestLine = reader.readLine()
                var length = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                }
                repeat(length) { reader.read() }
                s.getOutputStream().write(response.toByteArray())
                s.getOutputStream().flush()
            }
            server.close()
        }
        return server.localPort to { t.join(); requestLine }
    }

    @Test
    fun deletionAnsweredWith204AndABodyCountsAsDone() = runBlocking {
        // What Archidekt sends back for a bulk delete: no content, yet a 20-byte body.
        val (port, request) = serve("HTTP/1.1 204 No Content\r\nContent-Length: 20\r\nConnection: close\r\n\r\n{\"detail\": \"done!\"}\n")
        val client = ArchidektCollectionClient(OkHttpClient()) { "http://127.0.0.1:$port" }
        val login = ArchidektLogin(1, "test", "not-a-jwt", "refresh")
        val after = client.delete(login, listOf(11L, 12L))
        assertEquals(login, after)
        assertTrue(request().startsWith("DELETE /api/collection/bulk/"))
    }
}
