// RECOVERED ORIGINAL SOURCE — Claude Shape Studio
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:13 CST.
// This is actual Kotlin source, NOT a summary. Do NOT treat it as integrated:
// it has not been built, and it lives in package com.glassforge.app.shape.seasonal,
// which does not exist in v0.7 yet. Shape Studio is Phase 4 — after the
// workspace is reliable. Keep separate from v0.7 until inspected and verified.
package com.glassforge.app.shape.seasonal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.glassforge.app.shape.ShapeConfig

/**
 * Dismissible banner displayed at the top of Shape Studio when a seasonal
 * event is active.  It shows the event name, a horizontal preset carousel,
 * and a one-tap "apply" for each preset.
 *
 * Dismiss state is managed by the caller (store it in a remembered boolean
 * that resets when [event] changes).
 */
@Composable
fun SeasonalThemeBanner(
    event:            SeasonalEvent,
    presets:          List<SeasonalPresets.NamedPreset>,
    onPresetSelected: (ShapeConfig) -> Unit,
    onDismiss:        () -> Unit,
    modifier:         Modifier = Modifier
) {
    if (event == SeasonalEvent.NONE || presets.isEmpty()) return

    val gradient = Brush.horizontalGradient(
        listOf(Color(event.borderStart), Color(event.borderEnd))
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.medium,
        color    = Color(0xFF111111),
        border   = BorderStroke(1.dp, gradient)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${event.emoji}  ${event.displayName.uppercase()}",
                    color         = Color(event.accentColor),
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 13.sp,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "✕",
                    color    = Color.White.copy(alpha = 0.38f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            Text(
                event.bannerMessage,
                color    = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp
            )

            // Preset carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding        = PaddingValues(vertical = 2.dp)
            ) {
                items(presets) { preset ->
                    SeasonalPresetChip(
                        preset      = preset,
                        accentColor = Color(event.accentColor),
                        onClick     = { onPresetSelected(preset.config) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonalPresetChip(
    preset:      SeasonalPresets.NamedPreset,
    accentColor: Color,
    onClick:     () -> Unit
) {
    Surface(
        shape   = MaterialTheme.shapes.small,
        color   = accentColor.copy(alpha = 0.1f),
        border  = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        onClick = onClick
    ) {
        Text(
            text     = preset.name,
            color    = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
