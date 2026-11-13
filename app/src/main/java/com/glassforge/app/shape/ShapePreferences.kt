// GLASSFORGE Shape Studio — base file (written by Muse, 2026-11-13)
// This is the missing base file the recovered Claude Shape Studio files
// depend on. It is NOT recovered original source: it was written from the
// integration spec (INTEGRATION_ADDITIONS.md), which named the keys and the
// read/write pairs for the glass flags. All values have safe defaults so a
// corrupt or first-run store yields the default shape.
// Branch: shape-studio. Phase 4, device-verified by build.

package com.glassforge.app.shape

import android.content.Context
import com.glassforge.app.shape.seasonal.SeasonalEvent

/**
 * Persists the current [ShapeConfig] in SharedPreferences.
 * Separate store from the workspace (LocalFileStore/WAL) and from the
 * architect prefs (glassforge_architect) — this owns shape settings only.
 */
class ShapePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Loads the saved config, or the defaults on first run / bad data. */
    fun load(): ShapeConfig {
        val type = try {
            ShapeType.valueOf(prefs.getString(KEY_TYPE, ShapeType.SQUIRCLE.name)!!)
        } catch (e: IllegalArgumentException) {
            ShapeType.SQUIRCLE
        }
        return ShapeConfig(
            type = type,
            amplitude = prefs.getFloat(KEY_AMPLITUDE, 2f),
            frequency = prefs.getFloat(KEY_FREQUENCY, 6f),
            cornerRadius = prefs.getFloat(KEY_CORNER_RADIUS, 20f),
            horizontalScale = prefs.getFloat(KEY_HORIZONTAL_SCALE, 1f),
            verticalScale = prefs.getFloat(KEY_VERTICAL_SCALE, 1f),
            borderThickness = prefs.getFloat(KEY_BORDER_THICKNESS, 2f),
            liquidGlassEnabled = prefs.getBoolean(KEY_LIQUID_GLASS, false),
            auroraGlassEnabled = prefs.getBoolean(KEY_AURORA_GLASS, false)
        )
    }

    /** Saves the config. apply() is fine here: shape settings are not a
     *  crash-critical write (unlike workspace moves, which persist first). */
    fun save(config: ShapeConfig) {
        prefs.edit()
            .putString(KEY_TYPE, config.type.name)
            .putFloat(KEY_AMPLITUDE, config.amplitude)
            .putFloat(KEY_FREQUENCY, config.frequency)
            .putFloat(KEY_CORNER_RADIUS, config.cornerRadius)
            .putFloat(KEY_HORIZONTAL_SCALE, config.horizontalScale)
            .putFloat(KEY_VERTICAL_SCALE, config.verticalScale)
            .putFloat(KEY_BORDER_THICKNESS, config.borderThickness)
            .putBoolean(KEY_LIQUID_GLASS, config.liquidGlassEnabled)
            .putBoolean(KEY_AURORA_GLASS, config.auroraGlassEnabled)
            .apply()
    }

    // P2-11: seasonal banner dismissal, persisted per event so a
    // dismissed banner stays dismissed across restarts (until the
    // season changes and a new event arrives).
    fun isBannerDismissed(event: SeasonalEvent): Boolean =
        prefs.getBoolean(KEY_BANNER_DISMISSED_PREFIX + event.name, false)

    fun setBannerDismissed(event: SeasonalEvent, dismissed: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BANNER_DISMISSED_PREFIX + event.name, dismissed)
            .apply()
    }

    private companion object {
        private const val PREFS_NAME = "glassforge_shape"
        private const val KEY_TYPE = "shape_type"
        private const val KEY_AMPLITUDE = "amplitude"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_CORNER_RADIUS = "corner_radius"
        private const val KEY_HORIZONTAL_SCALE = "horizontal_scale"
        private const val KEY_VERTICAL_SCALE = "vertical_scale"
        private const val KEY_BORDER_THICKNESS = "border_thickness"
        private const val KEY_LIQUID_GLASS = "liquid_glass_enabled"
        private const val KEY_AURORA_GLASS = "aurora_glass_enabled"
        private const val KEY_BANNER_DISMISSED_PREFIX = "banner_dismissed_"
    }
}
