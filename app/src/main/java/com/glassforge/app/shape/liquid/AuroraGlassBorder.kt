// RECOVERED ORIGINAL SOURCE — Claude Shape Studio
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:10 CST.
// This is actual Kotlin source, NOT a summary. Do NOT treat it as integrated:
// it has not been built, and it lives in package com.glassforge.app.shape.liquid,
// which does not exist in v0.7 yet. Shape Studio is Phase 4 — after the
// workspace is reliable. Keep separate from v0.7 until inspected and verified.
package com.glassforge.app.shape.liquid

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * AURORA GLASS BORDER — GLASSFORGE Special Feature
 * ─────────────────────────────────────────────────
 * Paints a chromatic-aberration border that mimics the prismatic edge of
 * thick optical glass lit from a point source.  The three colour channels
 * are drawn at sinusoidally offset positions, creating an aurora shimmer
 * when [animated] = true.  [tiltX] / [tiltY] from LiquidGlassTiltSensor
 * shift the channel offsets so the effect responds to device orientation.
 *
 * Uses BlendMode.Plus throughout — it only brightens; it never darkens
 * the underlying content, so it works on both light and dark surfaces.
 *
 * Usage:
 *   Box(
 *       modifier = Modifier
 *           .size(72.dp)
 *           .clip(shape)
 *           .auroraGlassBorder(shape, animated = config.auroraGlassEnabled,
 *               tiltX = sensor.tiltX, tiltY = sensor.tiltY)
 *   )
 */
@Composable
fun Modifier.auroraGlassBorder(
    shape: Shape,
    strokeWidth: Dp = 2.dp,
    animated: Boolean = true,
    tiltX: Float = 0f,
    tiltY: Float = 0f,
): Modifier {
    // The transition is created unconditionally. The original code called
    // rememberInfiniteTransition inside this `if (animated)` branch; when
    // the toggle flips, the composition's call structure changes and
    // Compose crashes. The phase is only *used* when animated; otherwise
    // it holds at 0. (Fix applied by Muse when staging for shape-studio.)
    val transition = rememberInfiniteTransition(label = "aurora_glass")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_000, easing = LinearEasing)
        ),
        label = "aurora_phase"
    )
    val phase: Float = if (animated) animatedPhase else 0f

    return this.drawWithContent {
        drawContent()

        val sw       = strokeWidth.toPx()
        val outline  = shape.createOutline(size, layoutDirection, this)
        val basePath = outline.toPath()

        // Channel offsets are driven by the animation phase and live tilt.
        val maxOff    = sw * 0.65f
        val redOff    = Offset(
            x = maxOff * sin(phase + tiltX * 1.2f),
            y = maxOff * sin(phase * 0.6f + tiltY) * 0.5f
        )
        val blueOff = Offset(x = -redOff.x * 1.1f, y = -redOff.y * 0.9f)

        // Red channel — slightly wider
        drawPath(
            path  = basePath.withOffset(redOff),
            color = Color(0xFFFF3B5C),
            style = Stroke(width = sw),
            blendMode = BlendMode.Plus
        )
        // Green channel — centred and narrower (keeps the core sharp)
        drawPath(
            path  = basePath,
            color = Color(0xFF00FFB2),
            style = Stroke(width = sw * 0.75f),
            blendMode = BlendMode.Plus
        )
        // Blue channel — wider, opposite offset
        drawPath(
            path  = basePath.withOffset(blueOff),
            color = Color(0xFF4D88FF),
            style = Stroke(width = sw),
            blendMode = BlendMode.Plus
        )
        // White core — crispness anchor
        drawPath(
            path  = basePath,
            color = Color.White.copy(alpha = 0.12f),
            style = Stroke(width = sw * 0.4f),
            blendMode = BlendMode.Plus
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────

private val TWO_PI = 2f * PI.toFloat()

/** Returns a copy of this Path translated by [offset]. */
private fun Path.withOffset(offset: Offset): Path =
    Path().also { it.addPath(this, offset) }

/** Converts a Compose Outline to a Path without a catch-all branch. */
private fun Outline.toPath(): Path = when (this) {
    is Outline.Generic   -> path
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded   -> Path().apply { addRoundRect(roundRect) }
}
