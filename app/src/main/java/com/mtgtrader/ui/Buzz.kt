package com.mtgtrader.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgtrader.container

/**
 * The life tracker's vibration (since 1.58): a short buzz straight from the vibration motor, as Lifetap does. Until
 * 1.57 the tracker asked for the view's touch feedback, which many phones skip in silent mode or with touch vibration
 * switched off in the system settings, and which is very faint on others.
 */
internal class Buzz(private val vibrator: Vibrator?) {
    /** A tap: life, commander damage, counters, the mana pool. */
    fun tap() = play(TAP_MS)

    /** A roll, a pick or an undo: a little longer. */
    fun roll() = play(ROLL_MS)

    private fun play(ms: Long) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    companion object {
        const val TAP_MS = 25L
        const val ROLL_MS = 60L

        /** Nothing: "Haptic feedback" is off. */
        val OFF = Buzz(null)

        fun vibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

/** The tracker's [Buzz], or [Buzz.OFF] while Settings → Life tracker → Haptic feedback is off. */
@Composable
internal fun rememberBuzz(): Buzz {
    val context = LocalContext.current
    val on by context.container.settings.trackerFlag("trackerHaptics").collectAsStateWithLifecycle()
    val buzz = remember(context) { Buzz(Buzz.vibrator(context)) }
    return if (on) buzz else Buzz.OFF
}
