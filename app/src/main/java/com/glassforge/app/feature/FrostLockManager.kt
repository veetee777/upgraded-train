// GEMINI'S SWING — original source from Gemini, via Tristan
// Provenance: pasted by Tristan in Muse main chat, 2026-11-13 ~06:28 CST,
// from Gemini's response to GLASSFORGE_PACKET_Gemini_2026-11-13.md.
// Feature 1 of 3: Frost Lock (Tempered Apps) — double-tap an app to freeze
// it (frosted glass over the icon, single-tap does nothing), double-tap
// again to thaw. State in SharedPreferences "glassforge_frost".
// This is Gemini's file, NOT integrated yet: it has not been built, and
// the WorkspaceScreen wiring (detectTapGestures, frost overlay) has not
// arrived yet. Keep separate from v0.7 until inspected and verified.
package com.glassforge.app.feature

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FrostLockManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("glassforge_frost", Context.MODE_PRIVATE)
    private val _frostedApps = MutableStateFlow<Set<String>>(emptySet())
    val frostedApps: StateFlow<Set<String>> = _frostedApps.asStateFlow()

    init {
        _frostedApps.value = prefs.getStringSet("packages", emptySet()) ?: emptySet()
    }

    fun toggleFrost(packageName: String) {
        val current = _frostedApps.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _frostedApps.value = current
        prefs.edit().putStringSet("packages", current).apply()
    }
}
