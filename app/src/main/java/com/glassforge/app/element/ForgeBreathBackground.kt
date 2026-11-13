// GEMINI'S SWING — original source from Gemini, via Tristan
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:28 CST,
// from Gemini's response to GLASSFORGE_PACKET_Gemini_2026-11-13.md.
// Element 2 of 3: Breath of the Forge — workspace background breathes
// (radial gradient pulse) with rhythm/color from battery state: icy cyan
// when full, warm orange molten pulse while charging, deep purple when low.
// This is Gemini's file, NOT integrated yet: it has not been built, and
// the WorkspaceScreen wrapper wiring has not arrived yet. Keep separate
// from v0.7 until inspected and verified.
//
// REVIEW NOTE (Muse, on receipt): `center = Offset.Infinite` in the
// radialGradient looks wrong — an infinite center likely produces a
// degenerate gradient; the intent is probably the screen center
// (default Center) or a measured center. Verify/fix at integration time,
// do not ship as-is without checking it renders.
//
// FIX APPLIED at integration (gemini-feature branch): the `center =
// Offset.Infinite` argument was removed so radialGradient uses its
// default center (the middle of the box), and the now-unused Offset
// import was dropped. Nothing else in this file was changed.
package com.glassforge.app.element

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun ForgeBreathBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var batteryLevel by remember { mutableFloatStateOf(1f) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        // Shared parsing for the sticky intent (first paint) and for
        // every later broadcast — one place that turns an intent into
        // battery state.
        fun applyBatteryIntent(intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            batteryLevel = if (level != -1 && scale != -1) level.toFloat() / scale.toFloat() else 1f
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                applyBatteryIntent(intent)
            }
        }
        // P2-18: ACTION_BATTERY_CHANGED is sticky — registerReceiver
        // returns the last broadcast, so parse it immediately instead
        // of painting the wrong color until the next battery event.
        // The 3-arg overload with RECEIVER_NOT_EXPORTED is required on
        // Android 14+ for targetSdk 35; the 2-arg form throws there.
        val stickyIntent = context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
        applyBatteryIntent(stickyIntent)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val targetColor = when {
        isCharging -> Color(0x33FF6B00) // Hot orange
        batteryLevel > 0.5f -> Color(0x2200D4FF) // Cool cyan
        else -> Color(0x224A00E0) // Deep cooling purple
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ForgeBreath")
    val animatedColor by infiniteTransition.animateColor(
        initialValue = targetColor.copy(alpha = targetColor.alpha * 0.3f),
        targetValue = targetColor,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isCharging) 2500 else 4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathColor"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(animatedColor, Color.Transparent),
                        radius = 1500f
                    )
                )
        )
        content()
    }
}
