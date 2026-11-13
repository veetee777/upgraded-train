// RECOVERED ORIGINAL SOURCE — Claude Shape Studio
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:09 CST.
// This is actual Kotlin source, NOT a summary. Do NOT treat it as integrated:
// it has not been built, and it lives in package com.glassforge.app.shape.liquid,
// which does not exist in v0.7 yet. Shape Studio is Phase 4 — after the
// workspace is reliable. Keep separate from v0.7 until inspected and verified.
package com.glassforge.app.shape.liquid

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * GLASS PHYSICS — tilt input source.
 *
 * Wraps Android's accelerometer as Compose-observable state.
 * A low-pass filter (alpha = 0.08) gives the response the slow, viscous
 * feel of liquid glass rather than a jittery direct mapping.
 *
 * Usage:
 *   val sensor = remember { LiquidGlassTiltSensor(context) }
 *   DisposableEffect(lifecycleOwner) {
 *       lifecycleOwner.lifecycle.addObserver(sensor)
 *       onDispose { lifecycleOwner.lifecycle.removeObserver(sensor) }
 *   }
 *   // Then read sensor.tiltX / sensor.tiltY in your composable.
 */
class LiquidGlassTiltSensor(context: Context) :
    SensorEventListener, DefaultLifecycleObserver {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** True when this device exposes an accelerometer. */
    val isAvailable: Boolean get() = accelerometer != null

    /** Lateral tilt. –1.0 = device rolled fully left, +1.0 = fully right. */
    var tiltX by mutableFloatStateOf(0f)
        private set

    /** Fore-aft tilt. –1.0 = face-down, +1.0 = face-up / tilted back. */
    var tiltY by mutableFloatStateOf(0f)
        private set

    // Smooth the signal — lower alpha = more lag = more "viscous glass" feel.
    private val alpha = 0.08f
    private var smoothX = 0f
    private var smoothY = 0f

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onResume(owner: LifecycleOwner) = register()
    override fun onPause(owner: LifecycleOwner)  = unregister()

    fun register() {
        if (accelerometer != null) {
            sensorManager.registerListener(
                this, accelerometer, SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun unregister() = sensorManager.unregisterListener(this)

    // ── SensorEventListener ───────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        // Negate X so "tilt right" → positive tiltX.
        val rawX = -(event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val rawY =  (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        smoothX = smoothX * (1f - alpha) + rawX * alpha
        smoothY = smoothY * (1f - alpha) + rawY * alpha
        tiltX = smoothX
        tiltY = smoothY
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
