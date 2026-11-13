// GLASSFORGE Shape Studio — base file (written by Muse, 2026-11-13)
// This is the missing base file the recovered Claude Shape Studio files
// depend on. It is NOT recovered original source: it was written from the
// integration spec (INTEGRATION_ADDITIONS.md) plus the field/type names the
// recovered files actually reference (SeasonalPresets, ArchitectModePanel,
// chaosShapeConfig). Verified against those files before writing.
// Branch: shape-studio. Phase 4, device-verified by build.

package com.glassforge.app.shape

/**
 * The outline family for a shape. The first eight are the standard picker;
 * VOID and PRISM are architect-exclusive and only appear once the Architect
 * easter egg is unlocked.
 */
enum class ShapeType {
    /** Classic rounded rectangle. */
    STANDARD_ROUNDED,
    /** Squircle (superellipse-approximated by a rounded rect). */
    SQUIRCLE,
    /** Full circle / ellipse. */
    CIRCLE,
    /** Sharp square. */
    SQUARE,
    /** Smooth sinusoidal edge wave. */
    SMOOTH_WAVY,
    /** Sharp triangle-wave edge. */
    SHARP_ZIGZAG,
    /** Outward-only bumps. */
    SCALLOPED,
    /** Deterministic jagged edge. */
    JAGGED,

    /**
     * Architect-exclusive.
     * Renders with BlendMode.Clear inside a hardware compositing layer,
     * creating a transparent hole-punch effect over layered surfaces.
     * Always pairs with the aurora glass border to show its outline.
     */
    VOID,

    /**
     * Architect-exclusive.
     * A 12-faceted polygon with alternating outer/inner radii.
     * Designed to be used with auroraGlassEnabled for a refractive look.
     */
    PRISM
}

/**
 * The full, persisted description of one shape.
 *
 * Amplitude / frequency drive the wavy edge types and are ignored by the
 * plain types. cornerRadius drives the rounded types. horizontalScale /
 * verticalScale stretch the shape (0.8–1.2 in the UI).
 */
data class ShapeConfig(
    val type: ShapeType = ShapeType.SQUIRCLE,
    val amplitude: Float = 2f,
    val frequency: Float = 6f,
    val cornerRadius: Float = 20f,
    val horizontalScale: Float = 1f,
    val verticalScale: Float = 1f,
    val borderThickness: Float = 2f,

    /**
     * Enables the Liquid Glass feature:
     *   - Accelerometer tilt is mapped to asymmetric edge displacement.
     *   - Requires a [com.glassforge.app.shape.liquid.LiquidGlassTiltSensor]
     *     in the host composable.
     */
    val liquidGlassEnabled: Boolean = false,

    /**
     * Enables the Aurora Glass border:
     *   - Chromatic-aberration border drawn with BlendMode.Plus.
     *   - Works on any ShapeType; always enabled for VOID.
     */
    val auroraGlassEnabled: Boolean = false
) {
    /** True for the architect-exclusive types. */
    val isArchitectOnly: Boolean
        get() = type == ShapeType.VOID || type == ShapeType.PRISM

    companion object {
        /** The types shown in the standard picker. */
        val standardTypes: List<ShapeType> = listOf(
            ShapeType.STANDARD_ROUNDED,
            ShapeType.SQUIRCLE,
            ShapeType.CIRCLE,
            ShapeType.SQUARE,
            ShapeType.SMOOTH_WAVY,
            ShapeType.SHARP_ZIGZAG,
            ShapeType.SCALLOPED,
            ShapeType.JAGGED
        )
    }
}
