package com.mtgtrader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mtgtrader.ui.AppNav
import com.mtgtrader.ui.MtgTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            MtgTheme {
                AppNav()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Changes not synced yet go out in the background.
        if (!isChangingConfigurations) {
            container.sync.onAppBackground()
            container.archidekt.onAppBackground()
            container.appScope.launch { container.backups.dailyIfDue() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /**
     * A deck link shared from Archidekt (or a browser) is imported on the Decks tab; a price alert's
     * notification opens its card on the wishlist (since 1.37).
     */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == com.mtgtrader.data.AlertNotifier.ACTION_OPEN_WISH) {
            intent.getLongExtra(com.mtgtrader.data.AlertNotifier.EXTRA_WISH_ID, 0L).takeIf { it > 0 }?.let { container.openWish.value = it }
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { container.sharedText.value = it }
    }
}
