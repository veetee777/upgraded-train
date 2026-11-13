// GLASSFORGE Phase 2 — real widgets (written by Muse, 2026-11-13)
// Thin wrapper around Android's AppWidgetHost. It owns host ids and the
// live AppWidgetHostViews; the durable placement <-> id mapping lives in
// WorkspaceStore (WorkspaceItem.Widget). No Express, no network, no new
// Gradle dependency — AppWidgetManager/AppWidgetHost are in the SDK.
//
// Lifecycle: the host must be listening for views to receive updates.
// MainActivity calls startListening() on resume and stopListening() on
// pause (see GlassforgeApp). Forgetting that is the classic reason a
// hosted widget renders once and then goes stale.
package com.glassforge.app.workspace

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import java.util.WeakHashMap

class WidgetHost(context: Context) {

    private val appContext = context.applicationContext
    private val appWidgetManager =
        appContext.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
    private val host = AppWidgetHost(appContext, HOST_ID)

    /** Every widget provider installed on the device. */
    fun installedProviders(): List<AppWidgetProviderInfo> =
        try {
            appWidgetManager.installedProviders
        } catch (_: Exception) {
            emptyList()
        }

    /** Allocate a fresh host id. Caller must delete it if binding fails. */
    fun allocateId(): Int = host.allocateAppWidgetId()

    /**
     * True when the id may be bound without asking the user (already
     * granted, or the provider allows it). False means the picker must
     * launch ACTION_APPWIDGET_BIND and wait for RESULT_OK.
     */
    fun bindIfAllowed(appWidgetId: Int, provider: AppWidgetProviderInfo): Boolean =
        try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
        } catch (_: Exception) {
            false
        }

    /** Null when the provider is gone (uninstalled) — render a placeholder. */
    fun getProviderInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        try {
            appWidgetManager.getAppWidgetInfo(appWidgetId)
        } catch (_: Exception) {
            null
        }

    // P1-7: cache live views by host id. Creating a fresh
    // AppWidgetHostView on every move / tab switch orphaned the old
    // one (leaked views, stale widgets). The screen remembers by id,
    // so a move reuses the same view.
    private val viewCache = mutableMapOf<Int, AppWidgetHostView>()
    // P1-8: last size handed to each view, so updateWidgetSize can
    // skip repeat calls with an unchanged size. Weak keys so a view
    // never leaks through this map.
    private val lastSizes = WeakHashMap<AppWidgetHostView, IntArray>()

    /** The live view for a placed widget, or null when its provider is gone. */
    fun createView(context: Context, widget: WorkspaceItem.Widget): AppWidgetHostView? {
        viewCache[widget.appWidgetId]?.let { return it }
        val info = getProviderInfo(widget.appWidgetId) ?: return null
        return try {
            host.createView(context, widget.appWidgetId, info).also {
                viewCache[widget.appWidgetId] = it
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Drop a cached view without deleting its host id. */
    fun releaseView(appWidgetId: Int) {
        viewCache.remove(appWidgetId)?.let { lastSizes.remove(it) }
    }

    /**
     * P1-8: tell a hosted view its cell size in dp so it lays out at
     * full size instead of its minimum. No-ops when the size has not
     * changed since the last call for this view.
     */
    fun updateWidgetSize(
        view: AppWidgetHostView,
        minWidthDp: Int,
        minHeightDp: Int,
        maxWidthDp: Int,
        maxHeightDp: Int,
    ) {
        val size = intArrayOf(minWidthDp, minHeightDp, maxWidthDp, maxHeightDp)
        if (lastSizes[view]?.contentEquals(size) == true) return
        lastSizes[view] = size
        try {
            view.updateAppWidgetSize(Bundle(), minWidthDp, minHeightDp, maxWidthDp, maxHeightDp)
        } catch (_: Exception) {
            // Provider gone or view detached — the placeholder covers it.
        }
    }

    /** Free a host id. Call when a widget is removed or binding is abandoned. */
    fun deleteAppWidgetId(appWidgetId: Int) {
        // Release the cached view too, or it would keep rendering a
        // widget that no longer exists.
        releaseView(appWidgetId)
        try {
            host.deleteAppWidgetId(appWidgetId)
        } catch (_: Exception) {
            // Already gone — nothing to free.
        }
    }

    fun startListening() {
        try {
            host.startListening()
        } catch (_: Exception) {
            // Host already listening or torn down — safe to ignore.
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (_: Exception) {
            // Already stopped — safe to ignore.
        }
    }

    private companion object {
        /** Host id for GLASSFORGE's AppWidgetHost. Must be stable per install. */
        private const val HOST_ID = 1024
    }
}
