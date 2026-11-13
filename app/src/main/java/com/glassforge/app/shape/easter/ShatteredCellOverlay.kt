// GEMINI'S SWING — original source from Gemini, via Tristan
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:29 CST,
// from Gemini's response to GLASSFORGE_PACKET_Gemini_2026-11-13.md.
// Easter egg 3 of 3: The Shattered Cell — 5 rapid taps on an empty cell
// crack the glass (jagged Canvas path); long-press sweeps it away.
//
// RESTRUCTURED at integration (gemini-feature branch): Gemini's version
// was one full-screen overlay with its own pointerInput — placed over
// the grid it would swallow taps meant for apps (including the tap that
// moves an item in move mode), and the crack was drawn centered on the
// whole screen, not on one cell. The drawing is unchanged; the state
// (tap counting, shattered flag) and the gestures now live in
// WorkspaceScreen, one per empty cell, and this composable only draws
// the crack when isShattered is true. Behaviour is the same: 5 rapid
// taps (inside a 2-second window) crack the cell, long-press sweeps it.
package com.glassforge.app.shape.easter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Draws the glass crack over one cell. Stateless: the caller owns
 * [isShattered] and drives it from the cell's gestures.
 */
@Composable
fun ShatteredCellOverlay(
    isShattered: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isShattered) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.5f)
            lineTo(size.width * 0.2f, size.height * 0.1f)
            moveTo(size.width * 0.5f, size.height * 0.5f)
            lineTo(size.width * 0.8f, size.height * 0.2f)
            moveTo(size.width * 0.5f, size.height * 0.5f)
            lineTo(size.width * 0.1f, size.height * 0.7f)
            moveTo(size.width * 0.5f, size.height * 0.5f)
            lineTo(size.width * 0.7f, size.height * 0.9f)
        }
        drawPath(path = path, color = Color.White.copy(alpha = 0.6f), style = Stroke(width = 4f))
        drawPath(path = path, color = Color.Cyan.copy(alpha = 0.3f), style = Stroke(width = 8f))
    }
}
