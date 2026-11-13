// RECOVERED ORIGINAL SOURCE — Claude Shape Studio
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:11 CST.
// This is actual Kotlin source, NOT a summary. Do NOT treat it as integrated:
// it has not been built, and it lives in package com.glassforge.app.shape.easter,
// which does not exist in v0.7 yet. Shape Studio is Phase 4 — after the
// workspace is reliable. Keep separate from v0.7 until inspected and verified.
package com.glassforge.app.shape.easter

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * ARCHITECT MODE — Easter egg state machine.
 * ───────────────────────────────────────────
 * Trigger: tap the shape preview exactly 7 times within 4 seconds.
 *
 * On completion the state persists through restarts.
 * When unlocked, Shape Studio gains two hidden shape types (VOID, PRISM),
 * the CHAOS randomiser, and the hidden staff credits.
 *
 * The UI should:
 *   1. Call recordPreviewTap() on every tap of the preview icon.
 *   2. Watch activationFired — when it flips to true, play the boot
 *      animation (ArchitectActivationOverlay), then set it back to false.
 *   3. Watch architectUnlocked to decide whether to show the panel.
 */
class EasterEggController(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Compose-observable state ───────────────────────────────────────

    var architectUnlocked by mutableStateOf(prefs.getBoolean(KEY_UNLOCKED, false))
        private set

    /**
     * How many qualifying taps have been recorded in the current window.
     * The UI can read this to show a subtle glitch at ≥ 4 taps.
     */
    var tapProgress by mutableIntStateOf(0)
        private set

    /**
     * Flips to true for one "frame" on first unlock.
     * The caller must reset it to false after consuming it.
     */
    var activationFired by mutableStateOf(false)

    // ── Internal tap tracking ──────────────────────────────────────────

    private var tapCount    = 0
    private var firstTapMs  = 0L

    fun recordPreviewTap() {
        val now = System.currentTimeMillis()
        if (tapCount == 0 || now - firstTapMs > TAP_WINDOW_MS) {
            tapCount   = 1
            firstTapMs = now
        } else {
            tapCount++
        }
        tapProgress = tapCount.coerceIn(0, REQUIRED_TAPS)

        if (tapCount >= REQUIRED_TAPS && !architectUnlocked) {
            tapCount  = 0
            tapProgress = 0
            unlock()
        }
    }

    private fun unlock() {
        architectUnlocked = true
        activationFired   = true
        prefs.edit().putBoolean(KEY_UNLOCKED, true).apply()
    }

    /**
     * P2-15: drop the in-progress tap count (e.g. when the 4s window
     * expires with no further tap) so the glitch hint decays instead
     * of lingering forever at >= 4 taps.
     */
    fun clearTapProgress() {
        tapCount    = 0
        tapProgress = 0
    }

    /** Debug/QA only — not wired into production UI. */
    internal fun debugReset() {
        architectUnlocked = false
        activationFired   = false
        tapCount          = 0
        tapProgress       = 0
        prefs.edit().remove(KEY_UNLOCKED).apply()
    }

    companion object {
        private const val PREFS_NAME    = "glassforge_architect"
        private const val KEY_UNLOCKED  = "architect_unlocked"
        const val REQUIRED_TAPS         = 7
        const val TAP_WINDOW_MS         = 4_000L
    }
}
