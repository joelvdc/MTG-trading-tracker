package com.mtgtrader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgtrader.container
import com.mtgtrader.data.ThemeMode
import androidx.compose.ui.graphics.Color

private val Gold = Color(0xFFE0A526)
private val Violet = Color(0xFF7E57C2)

private val Dark = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF231A00),
    primaryContainer = Color(0xFF4A3800),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFB39DDB),
    secondaryContainer = Color(0xFF3B2F5C),
    onSecondaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF15131A),
    surface = Color(0xFF15131A),
    surfaceContainer = Color(0xFF211E28),
    surfaceContainerHigh = Color(0xFF2A2633),
    surfaceContainerLow = Color(0xFF1B1921),
)

private val Light = lightColorScheme(
    primary = Color(0xFF7A5900),
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Violet,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF22005D),
)

/** Colours for the fairness verdict; readable on both schemes. */
object VerdictColors {
    val fair = Color(0xFF2E9E5B)
    val favorsYou = Color(0xFF3D8BD9)
    val favorsThem = Color(0xFFD9534F)
}

val FoilColor = Color(0xFF9C6ADE)

/** Price trend arrows; readable on both schemes. */
object TrendColors {
    val up = Color(0xFF2E9E5B)
    val down = Color(0xFFD32F2F)
}

@Composable
fun MtgTheme(content: @Composable () -> Unit) {
    val mode by LocalContext.current.container.settings.themeMode.collectAsStateWithLifecycle()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Status and navigation bar icons readable on the chosen background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MtgColors(dark, content)
}

/** The app's light or dark colours, without reading the theme setting; also used by the screenshot tests. Since 1.25. */
@Composable
fun MtgColors(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
