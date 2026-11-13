// GLASSFORGE Shape Studio — base file (written by Muse, 2026-11-13)
// This is the missing base file the recovered Claude Shape Studio files
// depend on. It is NOT recovered original source: it was written from the
// integration spec (INTEGRATION_ADDITIONS.md, blocks 1–7) plus the exact
// APIs of the recovered files (verified before writing).
//
// Two deliberate deviations from the spec, both noted:
// 1. LifecycleOwner comes from `LocalContext.current as? LifecycleOwner`
//    instead of LocalLifecycleOwner — the screen runs inside MainActivity
//    (a ComponentActivity, hence a LifecycleOwner), and this avoids adding
//    the lifecycle-runtime-compose dependency. If the cast ever fails the
//    tilt sensor simply never registers; nothing crashes.
// 2. The screen is self-contained: it owns its ShapePreferences and its
//    currentConfig state, and persists on every change. MainActivity only
//    hosts it (tab 1).
//
// Branch: shape-studio. Phase 4, device-verified by build.

package com.glassforge.app.shape

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import com.glassforge.app.shape.easter.ArchitectActivationOverlay
import com.glassforge.app.shape.easter.ArchitectModePanel
import com.glassforge.app.shape.easter.EasterEggController
import com.glassforge.app.shape.easter.chaosShapeConfig
import com.glassforge.app.shape.liquid.LiquidGlassTiltSensor
import com.glassforge.app.shape.liquid.auroraGlassBorder
import com.glassforge.app.shape.seasonal.SeasonalEvent
import com.glassforge.app.shape.seasonal.SeasonalPresets
import com.glassforge.app.shape.seasonal.SeasonalThemeBanner
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShapeStudioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { ShapePreferences(context) }
    var currentConfig by remember { mutableStateOf(prefs.load()) }
    val onConfigChanged: (ShapeConfig) -> Unit = { config ->
        currentConfig = config
        prefs.save(config)
    }
    // P2-12: sliders update state on every tick but persist only when
    // the drag finishes — onValueChangeFinished can fire before the
    // last tick's recomposition, so read through this ref.
    val latestConfig by rememberUpdatedState(currentConfig)

    // ── Block 1: state ──────────────────────────────────────────────
    val lifecycleOwner = LocalContext.current as? LifecycleOwner

    val easterEgg = remember { EasterEggController(context) }
    val tiltSensor = remember { LiquidGlassTiltSensor(context) }

    // Auto-detect season once per composition
    val seasonalEvent = remember { SeasonalEvent.detect() }
    val seasonalPresets = remember(seasonalEvent) { SeasonalPresets.presetsFor(seasonalEvent) }
    // P2-11: dismissal is persisted per event, not just remembered.
    var bannerDismissed by remember(seasonalEvent) {
        mutableStateOf(prefs.isBannerDismissed(seasonalEvent))
    }

    // Activation overlay visibility
    var showActivation by remember { mutableStateOf(false) }

    // Register / unregister the tilt sensor with the lifecycle
    DisposableEffect(lifecycleOwner) {
        if (tiltSensor.isAvailable && lifecycleOwner != null) {
            lifecycleOwner.lifecycle.addObserver(tiltSensor)
        }
        onDispose {
            if (tiltSensor.isAvailable && lifecycleOwner != null) {
                lifecycleOwner.lifecycle.removeObserver(tiltSensor)
            }
            // P2-10: tab switches do not pause the activity, so
            // onPause never fires when leaving Studio — unregister
            // here or the sensor keeps running (and draining battery)
            // on every other tab. Safe even if never registered.
            tiltSensor.unregister()
        }
    }

    // P2-15: the glitch hint (tapProgress >= 4) must decay. This
    // effect restarts on every tap, so the timer always runs from
    // the last tap; if the 4s window passes with no further tap,
    // the progress resets and the hint disappears.
    LaunchedEffect(easterEgg.tapProgress) {
        if (easterEgg.tapProgress > 0) {
            kotlinx.coroutines.delay(EasterEggController.TAP_WINDOW_MS)
            easterEgg.clearTapProgress()
        }
    }

    // Watch for first-time unlock
    LaunchedEffect(easterEgg.activationFired) {
        if (easterEgg.activationFired) {
            showActivation = true
            easterEgg.activationFired = false
        }
    }

    val currentShape = remember(currentConfig) { GlassForgeShape(currentConfig) }
    val isVoid = currentConfig.type == ShapeType.VOID

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0E13))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Shape Studio",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Sculpt every icon's edge. Changes save as you go.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp
            )

            // ── Block 5: seasonal banner ────────────────────────────
            if (!bannerDismissed) {
                SeasonalThemeBanner(
                    event = seasonalEvent,
                    presets = seasonalPresets,
                    onPresetSelected = { config -> onConfigChanged(config) },
                    onDismiss = {
                        bannerDismissed = true
                        prefs.setBannerDismissed(seasonalEvent, true)
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // ── Blocks 2 + 3: live preview ──────────────────────────
            // Offscreen compositing is REQUIRED for VOID's BlendMode.Clear
            // to punch a hole; without it Clear has no effect.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        // P2-17: aurora BEFORE clip. The border strokes
                        // are centred on the outline, so half the
                        // stroke sits outside it — clip() after the
                        // border would cut that half off.
                        .auroraGlassBorder(
                            shape = currentShape,
                            strokeWidth = currentConfig.borderThickness.dp,
                            animated = currentConfig.auroraGlassEnabled || isVoid,
                            tiltX = if (currentConfig.liquidGlassEnabled) tiltSensor.tiltX else 0f,
                            tiltY = if (currentConfig.liquidGlassEnabled) tiltSensor.tiltY else 0f
                        )
                        .clip(currentShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF7C5CFF),
                                    Color(0xFF00E5FF),
                                    Color(0xFFFF3B5C)
                                )
                            )
                        )
                        .clickable { easterEgg.recordPreviewTap() }
                ) {
                    if (isVoid) {
                        // Punch the hole: erase the gradient inside the path.
                        Canvas(Modifier.fillMaxSize()) {
                            drawPath(
                                path = createShapePath(size, currentConfig),
                                color = Color.Transparent,
                                blendMode = BlendMode.Clear
                            )
                        }
                    } else {
                        Text(
                            "G",
                            color = Color.White,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Subtle glitch hint once the easter-egg taps start.
                    if (easterEgg.tapProgress >= 4 && !easterEgg.architectUnlocked) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.08f)
                                .background(Color(0xFF00FFB2))
                        )
                    }
                }
            }

            // ── Shape picker ────────────────────────────────────────
            Text("Shape", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (type in ShapeConfig.standardTypes) {
                    FilterChip(
                        selected = currentConfig.type == type,
                        onClick = { onConfigChanged(currentConfig.copy(type = type)) },
                        label = { Text(typeLabel(type)) }
                    )
                }
                if (easterEgg.architectUnlocked) {
                    FilterChip(
                        selected = isVoid,
                        onClick = { onConfigChanged(currentConfig.copy(type = ShapeType.VOID)) },
                        label = { Text("VOID") }
                    )
                    FilterChip(
                        selected = currentConfig.type == ShapeType.PRISM,
                        onClick = { onConfigChanged(currentConfig.copy(type = ShapeType.PRISM)) },
                        label = { Text("PRISM") }
                    )
                }
            }

            // ── Sliders ─────────────────────────────────────────────
            // P2-12: sliders update state on every tick but persist
            // only when the drag finishes — no prefs write per tick.
            SliderRow(
                label = "Amplitude",
                value = currentConfig.amplitude,
                range = 0f..24f,
                onValueChange = { currentConfig = currentConfig.copy(amplitude = it) },
                onValueChangeFinished = { prefs.save(latestConfig) }
            )
            SliderRow(
                label = "Frequency",
                value = currentConfig.frequency,
                range = 1f..20f,
                onValueChange = { currentConfig = currentConfig.copy(frequency = it) },
                onValueChangeFinished = { prefs.save(latestConfig) }
            )
            SliderRow(
                label = "Corner radius",
                value = currentConfig.cornerRadius,
                range = 0f..50f,
                onValueChange = { currentConfig = currentConfig.copy(cornerRadius = it) },
                onValueChangeFinished = { prefs.save(latestConfig) }
            )
            SliderRow(
                label = "Border thickness",
                value = currentConfig.borderThickness,
                range = 1f..5f,
                onValueChange = { currentConfig = currentConfig.copy(borderThickness = it) },
                onValueChangeFinished = { prefs.save(latestConfig) }
            )
            SliderRow(
                label = "Horizontal scale",
                value = currentConfig.horizontalScale,
                range = 0.8f..1.2f,
                onValueChange = { currentConfig = currentConfig.copy(horizontalScale = it) },
                onValueChangeFinished = { prefs.save(latestConfig) }
            )
            SliderRow(
                label = "Vertical scale",
                value = currentConfig.verticalScale,
                range = 0.8f..1.2f,
                onValueChange = { currentConfig = currentConfig.copy(verticalScale = it) },
                onValueChangeFinished = { prefs.save(latestConfig) }
            )

            // ── Block 4: Glass Physics toggles ──────────────────────
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aurora Glass", color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = currentConfig.auroraGlassEnabled,
                    onCheckedChange = { onConfigChanged(currentConfig.copy(auroraGlassEnabled = it)) }
                )
            }

            if (tiltSensor.isAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Liquid Glass", color = Color.White)
                        Text(
                            "Edges respond to tilt",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = currentConfig.liquidGlassEnabled,
                        onCheckedChange = { onConfigChanged(currentConfig.copy(liquidGlassEnabled = it)) }
                    )
                }
            }

            // ── Block 6: Architect panel ────────────────────────────
            if (easterEgg.architectUnlocked) {
                Spacer(Modifier.height(8.dp))
                ArchitectModePanel(
                    currentConfig = currentConfig,
                    onConfigChanged = onConfigChanged,
                    onChaos = {
                        onConfigChanged(
                            chaosShapeConfig(
                                current = currentConfig,
                                includeArchitectShapes = true
                            )
                        )
                    }
                )
            }
        }

        // ── Block 7: activation overlay (over everything) ───────────
        ArchitectActivationOverlay(
            visible = showActivation,
            onComplete = { showActivation = false }
        )
    }
}

private fun typeLabel(type: ShapeType): String = when (type) {
    ShapeType.STANDARD_ROUNDED -> "Rounded"
    ShapeType.SQUIRCLE -> "Squircle"
    ShapeType.CIRCLE -> "Circle"
    ShapeType.SQUARE -> "Square"
    ShapeType.SMOOTH_WAVY -> "Wavy"
    ShapeType.SHARP_ZIGZAG -> "Zigzag"
    ShapeType.SCALLOPED -> "Scalloped"
    ShapeType.JAGGED -> "Jagged"
    ShapeType.VOID -> "VOID"
    ShapeType.PRISM -> "PRISM"
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Text(
                String.format("%.2f", value),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range
        )
    }
}
