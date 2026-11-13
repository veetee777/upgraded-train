// RECOVERED ORIGINAL SOURCE — Claude Shape Studio
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:11 CST.
// This is actual Kotlin source, NOT a summary. Do NOT treat it as integrated:
// it has not been built, and it lives in package com.glassforge.app.shape.easter,
// which does not exist in v0.7 yet. Shape Studio is Phase 4 — after the
// workspace is reliable. Keep separate from v0.7 until inspected and verified.
package com.glassforge.app.shape.easter

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.glassforge.app.shape.ShapeConfig
import com.glassforge.app.shape.ShapeType
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════
//  ACTIVATION OVERLAY
// ═══════════════════════════════════════════════════════════════════════

/**
 * Full-screen terminal boot sequence shown the first time Architect Mode
 * unlocks.  Set [visible] = true when [EasterEggController.activationFired]
 * fires; set it back to false from [onComplete].
 */
@Composable
fun ArchitectActivationOverlay(visible: Boolean, onComplete: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(200)),
        exit    = fadeOut(tween(700))
    ) {
        Box(
            modifier            = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment    = Alignment.Center
        ) {
            TerminalBootSequence(onComplete)
        }
    }
}

@Composable
private fun TerminalBootSequence(onComplete: () -> Unit) {
    // (text, charDelayMs) — empty string = blank line pause
    val lines = remember {
        listOf(
            "> GLASSFORGE ARCHITECT PROTOCOLS" to 18L,
            "> BYPASSING GEOMETRY CONSTRAINTS..." to 18L,
            "> LOADING EXPERIMENTAL SHAPES..." to 18L,
            "> DECRYPTING STAFF CREDITS..." to 18L,
            "" to 0L,
            "ACCESS GRANTED" to 0L,
            "" to 0L,
            "Welcome, Architect." to 38L,
            "These tools are unfinished." to 38L,
            "Use them wisely." to 38L,
        )
    }

    var visibleCount by remember { mutableIntStateOf(0) }
    var currentChars by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        for ((text, delay) in lines) {
            visibleCount++
            if (text.isNotEmpty() && delay > 0L) {
                for (i in text.indices) {
                    currentChars = i + 1
                    kotlinx.coroutines.delay(delay)
                }
                currentChars = 0
            } else {
                kotlinx.coroutines.delay(220)
            }
        }
        kotlinx.coroutines.delay(1_500)
        onComplete()
    }

    Column(
        modifier = Modifier.padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        lines.forEachIndexed { i, (text, _) ->
            when {
                i < visibleCount - 1 -> TerminalLine(text, complete = true)
                i == visibleCount - 1 -> TerminalLine(
                    text.take(currentChars), complete = false
                )
                // future lines: not rendered yet
            }
        }
    }
}

@Composable
private fun TerminalLine(text: String, complete: Boolean) {
    if (text.isEmpty()) {
        Spacer(Modifier.height(4.dp))
        return
    }
    val isHeader = text == "ACCESS GRANTED"
    Text(
        text = if (complete) text else "$text█",
        color = when {
            isHeader          -> Color(0xFF00FFB2)
            text.startsWith(">") -> Color(0xFF33DD66)
            else              -> Color(0xFF88BBAA)
        },
        fontFamily  = FontFamily.Monospace,
        fontWeight  = if (isHeader) FontWeight.ExtraBold else FontWeight.Normal,
        fontSize    = if (isHeader) 22.sp else 13.sp,
        letterSpacing = if (isHeader) 3.sp else 0.sp
    )
}

// ═══════════════════════════════════════════════════════════════════════
//  ARCHITECT PANEL  (embedded inside Shape Studio)
// ═══════════════════════════════════════════════════════════════════════

/**
 * The Architect Mode control panel.  Drop this below the standard
 * ShapeStudioScreen controls gated by [EasterEggController.architectUnlocked].
 *
 * @param onChaos  Caller should invoke [chaosShapeConfig] and push the result
 *                 back via [onConfigChanged].
 */
@Composable
fun ArchitectModePanel(
    currentConfig:    ShapeConfig,
    onConfigChanged:  (ShapeConfig) -> Unit,
    onChaos:          () -> Unit,
) {
    var showCredits by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.medium,
        color    = Color(0xFF080808),
        border   = BorderStroke(1.dp, Color(0xFF00FFB2).copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Header bar ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ARCHITECT",
                    color         = Color(0xFF00FFB2),
                    fontFamily    = FontFamily.Monospace,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 11.sp,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "EXPERIMENTAL  ⚠",
                    color         = Color(0xFFFF3B5C).copy(alpha = 0.72f),
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 9.sp,
                    letterSpacing = 2.sp
                )
            }

            HorizontalDivider(color = Color(0xFF00FFB2).copy(alpha = 0.16f))

            // ── Exclusive shape row ────────────────────────────────
            Text(
                "EXCLUSIVE SHAPES",
                color         = Color.White.copy(alpha = 0.38f),
                fontFamily    = FontFamily.Monospace,
                fontSize      = 10.sp,
                letterSpacing = 2.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArchitectShapeCard(
                    label       = "VOID",
                    subtitle    = "Transparent cut-out",
                    accentColor = Color(0xFF4D88FF),
                    selected    = currentConfig.type == ShapeType.VOID,
                    modifier    = Modifier.weight(1f),
                    onClick     = { onConfigChanged(currentConfig.copy(type = ShapeType.VOID)) }
                )
                ArchitectShapeCard(
                    label       = "PRISM",
                    subtitle    = "Faceted refraction",
                    accentColor = Color(0xFFFF3B5C),
                    selected    = currentConfig.type == ShapeType.PRISM,
                    modifier    = Modifier.weight(1f),
                    onClick     = { onConfigChanged(currentConfig.copy(type = ShapeType.PRISM)) }
                )
            }

            HorizontalDivider(color = Color(0xFF00FFB2).copy(alpha = 0.16f))

            // ── Chaos ──────────────────────────────────────────────
            OutlinedButton(
                onClick  = onChaos,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF3B5C)
                ),
                border   = BorderStroke(1.dp, Color(0xFFFF3B5C).copy(alpha = 0.55f))
            ) {
                Text(
                    "⚡  CHAOS MODE",
                    fontFamily    = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }
            Text(
                "Randomises every parameter. Unsaved changes will be lost.",
                color      = Color.White.copy(alpha = 0.26f),
                fontFamily = FontFamily.Monospace,
                fontSize   = 10.sp
            )

            HorizontalDivider(color = Color(0xFF00FFB2).copy(alpha = 0.16f))

            // ── Credits toggle ─────────────────────────────────────
            TextButton(
                onClick         = { showCredits = !showCredits },
                contentPadding  = PaddingValues(horizontal = 0.dp)
            ) {
                Text(
                    if (showCredits) "▾  STAFF CREDITS" else "▸  STAFF CREDITS",
                    color         = Color(0xFF00FFB2).copy(alpha = 0.55f),
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 10.sp,
                    letterSpacing = 2.sp
                )
            }

            AnimatedVisibility(visible = showCredits) {
                StaffCredits()
            }
        }
    }
}

@Composable
private fun ArchitectShapeCard(
    label:       String,
    subtitle:    String,
    accentColor: Color,
    selected:    Boolean,
    modifier:    Modifier = Modifier,
    onClick:     () -> Unit
) {
    Surface(
        modifier = modifier,
        shape    = MaterialTheme.shapes.small,
        color    = if (selected) accentColor.copy(alpha = 0.12f) else Color(0xFF0C0C0C),
        border   = BorderStroke(
            1.dp,
            if (selected) accentColor else accentColor.copy(alpha = 0.28f)
        ),
        onClick  = onClick
    ) {
        Column(
            modifier              = Modifier.padding(12.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                label,
                color      = if (selected) accentColor else Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp
            )
            Text(
                subtitle,
                color      = Color.White.copy(alpha = 0.38f),
                fontFamily = FontFamily.Monospace,
                fontSize   = 10.sp
            )
        }
    }
}

@Composable
private fun StaffCredits() {
    val entries = remember {
        listOf(
            "LEAD ARCHITECT"  to "M. Voss",
            "SHAPE ENGINE"    to "A. Brennan",
            "VISUAL DESIGN"   to "K. Osei",
            "GLASS PHYSICS"   to "R. Tanaka",
            "SEASONAL MAGIC"  to "L. Moreau",
            "CHAOS SUBSYSTEM" to "Nobody claimed it",
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.small,
        color    = Color(0xFF050505)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            entries.forEach { (role, name) ->
                Row {
                    Text(
                        role.padEnd(22),
                        color      = Color(0xFF00FFB2).copy(alpha = 0.55f),
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 10.sp
                    )
                    Text(
                        name,
                        color      = Color.White.copy(alpha = 0.65f),
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 10.sp
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Built with obsession in a glass-walled studio\nat 2am, every night, for three months.",
                color      = Color.White.copy(alpha = 0.26f),
                fontFamily = FontFamily.Monospace,
                fontSize   = 9.sp
            )
        }
    }
}

// ── Chaos utility (called by the host screen) ──────────────────────────

/**
 * Returns a [ShapeConfig] with every numeric parameter randomised within
 * its valid range. VOID and PRISM are only included when
 * [includeArchitectShapes] = true.
 */
fun chaosShapeConfig(
    current: ShapeConfig,
    includeArchitectShapes: Boolean = false
): ShapeConfig {
    val pool = buildList {
        addAll(listOf(
            ShapeType.SMOOTH_WAVY, ShapeType.SHARP_ZIGZAG, ShapeType.SCALLOPED,
            ShapeType.JAGGED, ShapeType.CIRCLE, ShapeType.SQUIRCLE,
            ShapeType.SQUARE, ShapeType.STANDARD_ROUNDED
        ))
        if (includeArchitectShapes) addAll(listOf(ShapeType.VOID, ShapeType.PRISM))
    }
    return current.copy(
        type            = pool.random(),
        amplitude       = Random.nextFloat() * 20f + 2f,
        frequency       = Random.nextFloat() * 14f + 3f,
        cornerRadius    = Random.nextFloat() * 50f,
        horizontalScale = Random.nextFloat() * 0.4f + 0.8f,
        verticalScale   = Random.nextFloat() * 0.4f + 0.8f,
        borderThickness = Random.nextFloat() * 4f + 1f
    )
}
