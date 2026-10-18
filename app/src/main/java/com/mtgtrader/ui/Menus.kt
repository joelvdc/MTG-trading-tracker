package com.mtgtrader.ui

import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A drop-down menu that always fits between the status bar and the phone's own buttons (since 1.47). Android's menu
 * can be measured against the whole screen when the app draws behind the system bars, so a long menu ran under the
 * buttons at the bottom and its last lines were cut off; this one is never taller than the room left and scrolls.
 */
@Composable
fun ScreenMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier.heightIn(max = menuMaxHeight()), content = content)
}

/** The screen's height without the status bar, the phone's buttons and the menu's margins above and below. */
@Composable
fun menuMaxHeight(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    return remember(config.screenHeightDp, config.orientation, density) {
        val px = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            metrics.bounds.height() - insets.top - insets.bottom
        } else {
            null
        }
        val usable = px?.let { with(density) { it.toDp() } } ?: config.screenHeightDp.dp
        // Material keeps 48 dp free above and below a menu; a little extra so the last line is never at the edge.
        (usable - MENU_MARGIN * 2).coerceAtLeast(160.dp)
    }
}

private val MENU_MARGIN = 56.dp
