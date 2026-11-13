// RECOVERED ORIGINAL SOURCE — Claude Shape Studio
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:13 CST.
// This is actual Kotlin source, NOT a summary. Do NOT treat it as integrated:
// it has not been built, and it lives in package com.glassforge.app.shape.seasonal,
// which does not exist in v0.7 yet. Shape Studio is Phase 4 — after the
// workspace is reliable. Keep separate from v0.7 until inspected and verified.
// Note: Muse already had a copy of this file from the Claude share link
// (~/workspace/GLASSFORGE/recovery/claude-source/SeasonalPresets.kt, fetched
// 2026-11-13 05:41 CST). This pasted copy is substantively identical — same
// presets, same values — just formatted more compactly. Kept here in its
// correct package folder as the canonical copy.
package com.glassforge.app.shape.seasonal

import com.glassforge.app.shape.ShapeConfig
import com.glassforge.app.shape.ShapeType

/** Maps each [SeasonalEvent] to a curated collection of [ShapeConfig] presets. */
object SeasonalPresets {

    data class NamedPreset(val name: String, val config: ShapeConfig)

    fun presetsFor(event: SeasonalEvent): List<NamedPreset> = when (event) {

        SeasonalEvent.SPRING -> listOf(
            NamedPreset("Cherry Blossom", ShapeConfig(
                type = ShapeType.SCALLOPED, amplitude = 7f, frequency = 7f,
                cornerRadius = 32f, borderThickness = 2f
            )),
            NamedPreset("Petal Soft", ShapeConfig(
                type = ShapeType.SMOOTH_WAVY, amplitude = 5f, frequency = 5f,
                cornerRadius = 40f, borderThickness = 2f
            )),
            NamedPreset("Full Bloom", ShapeConfig(
                type = ShapeType.SCALLOPED, amplitude = 10f, frequency = 9f,
                cornerRadius = 24f, borderThickness = 3f
            ))
        )

        SeasonalEvent.SUMMER -> listOf(
            NamedPreset("Sun Ray", ShapeConfig(
                type = ShapeType.SHARP_ZIGZAG, amplitude = 10f, frequency = 8f,
                cornerRadius = 4f, borderThickness = 2f
            )),
            NamedPreset("Heat Wave", ShapeConfig(
                type = ShapeType.SMOOTH_WAVY, amplitude = 9f, frequency = 4f,
                cornerRadius = 16f, borderThickness = 3f
            )),
            NamedPreset("Glass Prism", ShapeConfig(
                type = ShapeType.SQUIRCLE, amplitude = 2f, frequency = 6f,
                cornerRadius = 10f, auroraGlassEnabled = true, borderThickness = 2f
            ))
        )

        SeasonalEvent.AUTUMN -> listOf(
            NamedPreset("Fallen Leaf", ShapeConfig(
                type = ShapeType.JAGGED, amplitude = 10f, frequency = 6f,
                cornerRadius = 10f, borderThickness = 2f
            )),
            NamedPreset("Harvest Edge", ShapeConfig(
                type = ShapeType.JAGGED, amplitude = 14f, frequency = 8f,
                cornerRadius = 6f, borderThickness = 3f
            )),
            NamedPreset("Ember", ShapeConfig(
                type = ShapeType.SHARP_ZIGZAG, amplitude = 8f, frequency = 10f,
                cornerRadius = 8f, borderThickness = 2f
            ))
        )

        SeasonalEvent.WINTER -> listOf(
            NamedPreset("Frost Crystal", ShapeConfig(
                type = ShapeType.SHARP_ZIGZAG, amplitude = 6f, frequency = 12f,
                cornerRadius = 2f, borderThickness = 2f
            )),
            NamedPreset("Ice Sheet", ShapeConfig(
                type = ShapeType.SQUIRCLE, amplitude = 2f, frequency = 6f,
                cornerRadius = 20f, borderThickness = 2f
            )),
            NamedPreset("Snowflake", ShapeConfig(
                type = ShapeType.SHARP_ZIGZAG, amplitude = 10f, frequency = 6f,
                cornerRadius = 4f, horizontalScale = 1.05f, verticalScale = 1.05f,
                auroraGlassEnabled = true, borderThickness = 2f
            ))
        )

        SeasonalEvent.HALLOWEEN -> listOf(
            NamedPreset("Jagged Dark", ShapeConfig(
                type = ShapeType.JAGGED, amplitude = 16f, frequency = 10f,
                cornerRadius = 2f, borderThickness = 3f
            )),
            NamedPreset("Bat Wing", ShapeConfig(
                type = ShapeType.SHARP_ZIGZAG, amplitude = 14f, frequency = 6f,
                cornerRadius = 4f, borderThickness = 2f
            )),
            NamedPreset("Cauldron Bubble", ShapeConfig(
                type = ShapeType.SCALLOPED, amplitude = 12f, frequency = 5f,
                cornerRadius = 20f, borderThickness = 3f
            ))
        )

        SeasonalEvent.CHRISTMAS -> listOf(
            NamedPreset("Star Point", ShapeConfig(
                type = ShapeType.SHARP_ZIGZAG, amplitude = 14f, frequency = 8f,
                cornerRadius = 6f, auroraGlassEnabled = true, borderThickness = 2f
            )),
            NamedPreset("Holly Edge", ShapeConfig(
                type = ShapeType.SCALLOPED, amplitude = 8f, frequency = 10f,
                cornerRadius = 16f, borderThickness = 3f
            )),
            NamedPreset("Gold Bauble", ShapeConfig(
                type = ShapeType.CIRCLE, amplitude = 0f, frequency = 1f,
                cornerRadius = 50f, auroraGlassEnabled = true, borderThickness = 3f
            ))
        )

        SeasonalEvent.NEW_YEAR -> listOf(
            NamedPreset("Starburst", ShapeConfig(
                type = ShapeType.SHARP_ZIGZAG, amplitude = 18f, frequency = 12f,
                cornerRadius = 2f, auroraGlassEnabled = true, borderThickness = 3f
            )),
            NamedPreset("Confetti Pop", ShapeConfig(
                type = ShapeType.JAGGED, amplitude = 12f, frequency = 14f,
                cornerRadius = 4f, borderThickness = 2f
            )),
            NamedPreset("Gold Prism", ShapeConfig(
                type = ShapeType.SQUIRCLE, amplitude = 4f, frequency = 8f,
                cornerRadius = 12f, auroraGlassEnabled = true, borderThickness = 3f
            ))
        )

        SeasonalEvent.VALENTINES -> listOf(
            NamedPreset("Lace Edge", ShapeConfig(
                type = ShapeType.SCALLOPED, amplitude = 6f, frequency = 10f,
                cornerRadius = 30f, borderThickness = 2f
            )),
            NamedPreset("Heart Wave", ShapeConfig(
                type = ShapeType.SMOOTH_WAVY, amplitude = 8f, frequency = 4f,
                cornerRadius = 50f, borderThickness = 3f
            )),
            NamedPreset("Velvet Soft", ShapeConfig(
                type = ShapeType.STANDARD_ROUNDED, amplitude = 3f, frequency = 4f,
                cornerRadius = 40f, borderThickness = 3f
            ))
        )

        SeasonalEvent.APRIL_FOOLS -> listOf(
            NamedPreset("Completely Normal", ShapeConfig(   // The joke: it's a circle
                type = ShapeType.CIRCLE, amplitude = 0f, frequency = 1f,
                cornerRadius = 50f, borderThickness = 1f
            )),
            NamedPreset("What Is This", ShapeConfig(
                type = ShapeType.JAGGED, amplitude = 20f, frequency = 15f,
                cornerRadius = 2f, borderThickness = 4f
            )),
            NamedPreset("MAXIMUM CHAOS", ShapeConfig(
                type = ShapeType.JAGGED, amplitude = 24f, frequency = 20f,
                cornerRadius = 0f, horizontalScale = 1.2f, verticalScale = 0.8f,
                borderThickness = 5f
            ))
        )

        SeasonalEvent.NONE -> emptyList()
    }
}
