package com.mtgtrader.ui

import android.content.Context
import android.content.Intent
import com.mtgtrader.AppContainer
import kotlinx.coroutines.CancellationException

/**
 * Syncs, asks the website for the share link of a deck, game, binder or the collection ([kind] and [target] as in
 * [com.mtgtrader.data.WebAccountClient.share]) and opens Android's share sheet with it. Returns a message for the
 * snackbar when that didn't work (not logged in, the website has nothing to share yet, offline…), else null.
 */
suspend fun shareLink(context: Context, c: AppContainer, kind: String, target: String, subject: String): String? {
    val link = try {
        c.sync.share(kind, target)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return e.message ?: "Couldn't get a share link."
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, link.url)
    }
    context.startActivity(Intent.createChooser(send, subject))
    return if (link.on) null else "That link is switched off. Switch it on in the website's Settings."
}
