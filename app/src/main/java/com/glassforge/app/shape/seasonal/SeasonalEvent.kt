// RECOVERED ORIGINAL SOURCE — Claude Shape Studio
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:12 CST.
// This is actual Kotlin source, NOT a summary. Do NOT treat it as integrated:
// it has not been built, and it lives in package com.glassforge.app.shape.seasonal,
// which does not exist in v0.7 yet. Shape Studio is Phase 4 — after the
// workspace is reliable. Keep separate from v0.7 until inspected and verified.
package com.glassforge.app.shape.seasonal

import java.time.LocalDate
import java.time.MonthDay

/**
 * A seasonal period or calendar event that drives a themed preset
 * collection and banner in Shape Studio.
 *
 * Special events are evaluated before base seasons, so HALLOWEEN takes
 * precedence over AUTUMN during Oct 20–Nov 2.
 *
 * All Long colour values are ARGB (0xAARRGGBB).
 */
enum class SeasonalEvent(
    val displayName:    String,
    val emoji:          String,
    val accentColor:    Long,   // chip / badge colour
    val borderStart:    Long,   // banner gradient start
    val borderEnd:      Long,   // banner gradient end
    val bannerMessage:  String
) {
    // ── Base seasons ──────────────────────────────────────────────────
    SPRING(
        "Spring", "🌸",
        0xFFFF85A2, 0xFFFF85A2, 0xFFCB9BF9,
        "Spring collection available"
    ),
    SUMMER(
        "Summer", "☀️",
        0xFFFFBB33, 0xFFFF8C00, 0xFFFFE066,
        "Summer collection available"
    ),
    AUTUMN(
        "Autumn", "🍂",
        0xFFD2691E, 0xFFD2691E, 0xFFFF8C00,
        "Autumn collection available"
    ),
    WINTER(
        "Winter", "❄️",
        0xFF7EC8E3, 0xFF7EC8E3, 0xFFF0F8FF,
        "Winter collection available"
    ),
    // ── Special events ────────────────────────────────────────────────
    HALLOWEEN(
        "Halloween", "🎃",
        0xFFFF6B00, 0xFFFF6B00, 0xFF4B0082,
        "Spooky shapes available"
    ),
    CHRISTMAS(
        "Christmas", "🎄",
        0xFFCC3333, 0xFFCC3333, 0xFFFFD700,
        "Christmas shapes available"
    ),
    NEW_YEAR(
        "New Year", "🎆",
        0xFFFFD700, 0xFFFFD700, 0xFFC0C0C0,
        "New Year shapes available"
    ),
    VALENTINES(
        "Valentine's Day", "💝",
        0xFFFF4B6E, 0xFFFF4B6E, 0xFFFF8FA3,
        "Valentine shapes available"
    ),
    APRIL_FOOLS(
        "April Fools", "🃏",
        0xFFFFD700, 0xFFFF6B6B, 0xFF6BFFB8,
        "Chaos shapes unlocked"
    ),
    // ── Fallback ──────────────────────────────────────────────────────
    NONE("", "", 0xFF888888, 0xFF888888, 0xFF888888, "");

    companion object {

        /**
         * Determine which event is active for the given [date].
         * Defaults to [LocalDate.now] when omitted.
         */
        fun detect(date: LocalDate = LocalDate.now()): SeasonalEvent {
            val md = MonthDay.from(date)
            return when {
                // New Year wraps the year boundary: Dec 29 – Jan 3
                md >= MonthDay.of(12, 29) || md <= MonthDay.of(1, 3)   -> NEW_YEAR
                // Valentine's: Feb 7 – Feb 21
                md in MonthDay.of(2,  7)..MonthDay.of(2, 21)           -> VALENTINES
                // April Fools: Mar 31 – Apr 2
                md in MonthDay.of(3, 31)..MonthDay.of(4,  2)           -> APRIL_FOOLS
                // Halloween: Oct 20 – Nov 2
                md in MonthDay.of(10, 20)..MonthDay.of(11, 2)          -> HALLOWEEN
                // Christmas: Dec 15 – Dec 28
                md in MonthDay.of(12, 15)..MonthDay.of(12, 28)         -> CHRISTMAS
                // Base seasons (remaining dates after specials are stripped)
                md in MonthDay.of(3,  1)..MonthDay.of(5, 31)           -> SPRING
                md in MonthDay.of(6,  1)..MonthDay.of(8, 31)           -> SUMMER
                md in MonthDay.of(9,  1)..MonthDay.of(11, 30)          -> AUTUMN
                else                                                    -> WINTER
            }
        }
    }
}
