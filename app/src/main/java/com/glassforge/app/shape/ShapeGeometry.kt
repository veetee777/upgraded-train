// GLASSFORGE Shape Studio — base file (written by Muse, 2026-11-13)
// This is the missing base file the recovered Claude Shape Studio files
// depend on. It is NOT recovered original source: it was written from the
// integration spec (INTEGRATION_ADDITIONS.md) plus the field/type names the
// recovered files actually reference. The PRISM and VOID generators are
// verbatim from the integration spec; the wavy-edge generators are Muse's
// own implementation of the documented behaviour (amplitude = edge
// displacement, frequency = waves around the perimeter).
//
// Known approximations, documented so a later port can tighten them:
// - Amplitude is treated as dp (multiplied by density) so presets read
//   visibly at preview scale; the original Claude units were unspecified.
// - Wavy types are built on an ellipse base; cornerRadius is not applied
//   to them. SQUIRCLE is approximated by a rounded rect.
// - VOID's path is only the hit region; the visible hole comes from
//   BlendMode.Clear in an Offscreen layer (see ShapeStudioScreen).
// Branch: shape-studio. Phase 4, device-verified by build.

package com.glassforge.app.shape

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

private const val TWO_PI_F = (2f * PI).toFloat()

/** Compose [Shape] adapter so a [ShapeConfig] can clip, border and preview. */
class GlassForgeShape(private val config: ShapeConfig) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(createShapePath(size, config, density.density))
}

/**
 * Builds the outline path for a config. [density] converts the dp-scale
 * amplitude to px; pass 1f when working in raw px.
 */
fun createShapePath(size: Size, config: ShapeConfig, density: Float = 1f): Path {
    if (size.width <= 0f || size.height <= 0f) return Path()
    return when (config.type) {
        ShapeType.CIRCLE ->
            Path().apply { addOval(Rect(Offset.Zero, size)) }

        ShapeType.SQUARE ->
            Path().apply { addRect(Rect(Offset.Zero, size)) }

        ShapeType.STANDARD_ROUNDED, ShapeType.SQUIRCLE ->
            createRoundedPath(size, config.cornerRadius)

        ShapeType.SMOOTH_WAVY ->
            createWavyPath(size, config, WaveKind.SMOOTH, density)

        ShapeType.SHARP_ZIGZAG ->
            createWavyPath(size, config, WaveKind.ZIGZAG, density)

        ShapeType.SCALLOPED ->
            createWavyPath(size, config, WaveKind.SCALLOPED, density)

        ShapeType.JAGGED ->
            createWavyPath(size, config, WaveKind.JAGGED, density)

        ShapeType.PRISM -> createPrismPath(size)
        ShapeType.VOID -> createVoidPath(size)
    }
}

private fun createRoundedPath(size: Size, cornerRadius: Float): Path {
    val r = cornerRadius.coerceIn(0f, min(size.width, size.height) / 2f)
    return Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                cornerRadius = CornerRadius(r, r)
            )
        )
    }
}

private enum class WaveKind { SMOOTH, ZIGZAG, SCALLOPED, JAGGED }

/**
 * Wavy edge: samples points around an ellipse base and displaces each
 * radially by amplitude * wave(angle * frequency).
 */
private fun createWavyPath(
    size: Size,
    config: ShapeConfig,
    kind: WaveKind,
    density: Float
): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    // Horizontal/vertical scale stretch the base; clamp so the path stays
    // (just) inside the layout bounds.
    val rx = min(cx * config.horizontalScale.coerceIn(0.5f, 1.2f), cx * 0.99f)
    val ry = min(cy * config.verticalScale.coerceIn(0.5f, 1.2f), cy * 0.99f)
    val amp = (config.amplitude * density).coerceAtLeast(0f)
    val ampFrac = if (min(rx, ry) > 0f) amp / min(rx, ry) else 0f
    val freq = config.frequency.coerceAtLeast(1f)

    val segments = 160
    val path = Path()
    for (i in 0..segments) {
        val t = (i.toFloat() / segments.toFloat()) * TWO_PI_F
        val wave = when (kind) {
            WaveKind.SMOOTH -> sin(t * freq)
            WaveKind.ZIGZAG -> (2f / PI.toFloat()) * asin(sin(t * freq))
            WaveKind.SCALLOPED -> abs(sin(t * freq))
            WaveKind.JAGGED -> {
                // Deterministic per-segment hash in [-1, 1]; stable across frames.
                val h = sin(i.toFloat() * 12.9898f) * 43758.5453f
                (h - floor(h)) * 2f - 1f
            }
        }
        val r = 1f + ampFrac * wave
        val x = cx + rx * r * cos(t)
        val y = cy + ry * r * sin(t)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Generates a faceted polygon path for the PRISM shape type.
 *
 * Alternates between an outer ring ([outerFraction] × half-size) and an inner
 * ring ([innerFraction] × half-size), creating [facets] × 2 vertices that
 * give the characteristic cut-glass look.
 * (Verbatim from the Claude integration spec.)
 */
fun createPrismPath(
    size: Size,
    facets: Int = 12,
    outerFraction: Float = 0.96f,
    innerFraction: Float = 0.88f
): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val total = facets * 2
    val path = Path()

    for (i in 0 until total) {
        val angle = (i.toFloat() / total.toFloat()) * 2f * PI.toFloat() - (PI / 2f).toFloat()
        val frac = if (i % 2 == 0) outerFraction else innerFraction
        val x = cx + cx * frac * cos(angle)
        val y = cy + cy * frac * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Generates the VOID touch-target path.
 * Visually the content is cleared via BlendMode; this path only defines
 * the accessible hit region (a standard squircle).
 * (Verbatim from the Claude integration spec.)
 */
fun createVoidPath(size: Size, cornerFraction: Float = 0.3f): Path {
    val cornerRadius = minOf(size.width, size.height) * cornerFraction
    return Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(offset = Offset.Zero, size = size),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )
        )
    }
}
