// GLASSFORGE Phase 2 — real widgets (written by Muse, 2026-11-13)
// The "add a real widget" section for the Widgets tab. Lists every
// installed widget provider, runs the standard bind flow, and places the
// widget 2x2 on the home grid at the first free anchor.
//
// Bind flow (the standard launcher flow — a third-party launcher cannot
// bind silently):
//   allocate id -> bindAppWidgetIdIfAllowed?
//     yes -> configure activity? (provider.configure) -> place
//     no  -> ACTION_APPWIDGET_BIND intent, wait for RESULT_OK, then as above
// On any cancel/failure the allocated id is deleted so it does not leak.
// Removal is here too: store.removeItem + deleteAppWidgetId.
package com.glassforge.app.workspace

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WidgetPickerSection(
    store: WorkspaceStore,
    widgetHost: WidgetHost,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val items by store.items.collectAsState()
    val placedWidgets = items.filterIsInstance<WorkspaceItem.Widget>()
    var message by remember { mutableStateOf("") }
    var pendingProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var pendingId by remember { mutableIntStateOf(-1) }

    val providers = remember {
        widgetHost.installedProviders()
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }

    // The anchor is cheap and pure, so it is computed up front on
    // the main thread; the durable add runs in the store's scope
    // (P2-22: a tab switch mid-add cannot cancel the write).
    fun placeWidget(provider: AppWidgetProviderInfo, appWidgetId: Int) {
        val anchor = WorkspaceRules.findFreeAnchor(store.items.value, 2, 2)
        if (anchor == null) {
            message = "No free 2×2 space on the home grid"
            widgetHost.deleteAppWidgetId(appWidgetId)
            return
        }
        val label = provider.loadLabel(context.packageManager)?.toString()
            ?: provider.provider.className
        val widget = WorkspaceItem.Widget(
            id = "widget:$appWidgetId",
            appWidgetId = appWidgetId,
            providerPackage = provider.provider.packageName,
            providerClass = provider.provider.className,
            label = label,
            cell = anchor,
            spanX = 2,
            spanY = 2,
        )
        store.runPersist({ store.addWidget(widget) }) { ok ->
            if (!ok) {
                message = "Could not place ${widget.label}"
                widgetHost.deleteAppWidgetId(appWidgetId)
            } else {
                message = "${widget.label} added to Home"
            }
        }
    }

    val configLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val provider = pendingProvider
        val id = pendingId
        pendingProvider = null
        pendingId = -1
        if (provider == null || id < 0) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            placeWidget(provider, id)
        } else {
            widgetHost.deleteAppWidgetId(id)
        }
    }

    // P1-9: the configure intent must name its component — without
    // one the launch can throw ActivityNotFoundException — and a
    // failed launch must delete the allocated id instead of leaking it.
    fun launchConfigure(provider: AppWidgetProviderInfo, appWidgetId: Int) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            setComponent(provider.configure)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val launched = runCatching { configLauncher.launch(intent) }.isSuccess
        if (!launched) {
            widgetHost.deleteAppWidgetId(appWidgetId)
            message = "Could not open ${provider.loadLabel(context.packageManager) ?: "widget"} settings"
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val provider = pendingProvider
        val id = pendingId
        pendingProvider = null
        pendingId = -1
        if (provider == null || id < 0) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            widgetHost.deleteAppWidgetId(id)
            return@rememberLauncherForActivityResult
        }
        if (provider.configure != null) {
            pendingProvider = provider
            pendingId = id
            launchConfigure(provider, id)
        } else {
            placeWidget(provider, id)
        }
    }

    fun requestAdd(provider: AppWidgetProviderInfo) {
        val id = widgetHost.allocateId()
        if (widgetHost.bindIfAllowed(id, provider)) {
            if (provider.configure != null) {
                pendingProvider = provider
                pendingId = id
                launchConfigure(provider, id)
            } else {
                placeWidget(provider, id)
            }
        } else {
            pendingProvider = provider
            pendingId = id
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, Bundle())
                }
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "ADD A REAL WIDGET",
            color = Color(0xFF79E7FF),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        Text(
            "Live Android widgets on the home grid — clock, calendar, weather, whatever is installed. Each takes a 2×2 block.",
            color = Color(0xFF9AA8BC),
            fontSize = 11.sp,
        )
        if (message.isNotEmpty()) {
            Text(message, color = Color.White, fontSize = 12.sp)
        }
        if (providers.isEmpty()) {
            Text(
                "No widget providers found on this device.",
                color = Color(0xFF9AA8BC),
                fontSize = 12.sp,
            )
        }
        providers.take(24).forEach { provider ->
            val label = provider.loadLabel(context.packageManager)?.toString()
                ?: provider.provider.className
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = Color.White, fontSize = 14.sp)
                    Text(
                        provider.provider.packageName,
                        color = Color(0xFF8290A4),
                        fontSize = 10.sp,
                    )
                }
                TextButton(onClick = { requestAdd(provider) }) {
                    Text("ADD", color = Color(0xFF79E7FF), fontSize = 11.sp)
                }
            }
        }
        if (placedWidgets.isNotEmpty()) {
            Text(
                "PLACED WIDGETS",
                color = Color(0xFF79E7FF),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            placedWidgets.forEach { widget ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(widget.label, color = Color.White, fontSize = 14.sp)
                        Text(
                            "2×2 · cell ${widget.cell.x}, ${widget.cell.y}",
                            color = Color(0xFF8290A4),
                            fontSize = 10.sp,
                        )
                    }
                    TextButton(onClick = {
                        store.runPersist({ store.removeItem(widget.id) }) { removed ->
                            if (removed) {
                                widgetHost.deleteAppWidgetId(widget.appWidgetId)
                                message = "${widget.label} removed"
                            }
                        }
                    }) {
                        Text("REMOVE", color = Color(0xFFFF7A7A), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
