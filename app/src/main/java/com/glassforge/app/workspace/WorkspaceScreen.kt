package com.glassforge.app.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.glassforge.app.element.ForgeBreathBackground
import com.glassforge.app.feature.FrostLockManager
import com.glassforge.app.shape.easter.ShatteredCellOverlay

/**
 * Phase 1b workspace screen — GLASSFORGE v0.7
 * New code. Phase 1 was text labels only; this version draws the
 * real app icon above each label, One4Home-style.
 *
 * Icon loading, same pattern MainActivity already uses
 * (LauncherHomeScreen / WidgetsScreen):
 * - PackageManager.getApplicationIcon(packageName), off the main
 *   thread (Dispatchers.IO), runCatching so one bad icon never
 *   breaks the grid.
 * - Drawable -> 96x96 ImageBitmap, same rasterization as
 *   MainActivity.drawableToBitmap (96px, ARGB_8888, Canvas draw).
 *   Adaptive icons draw correctly with setBounds + draw.
 * - In-memory cache keyed by package name (remembered
 *   mutableStateMapOf). A move / recomposition never reloads
 *   icons; only packages not in the cache are loaded.
 * - No icon on disk, no new Gradle dependency, no network.
 *
 * Testable move flow, matching the Perplexity gates:
 * - Tap an app: launch it.
 * - Long-press an app: enter move mode (it highlights).
 * - Tap an empty cell: move there, persist, survive restart.
 * - Tap an occupied cell in move mode: rejected, both shortcuts stay,
 *   message shows "Occupied — move rejected".
 *
 * Phase 2: widgets render live in the grid. The grid is slot-based
 * now, not one item per cell: a widget spans 2 columns (GridItemSpan)
 * and 2 rows (aspectRatio), and cells its span covers are skipped.
 * A widget tap does nothing here — the hosted view handles its own
 * touches. Long-press still selects it for moving, and moving is
 * span-aware through the store (the whole 2x2 block must be free).
 *
 * Gemini's swing (gemini-feature branch): Frost Lock (double-tap an
 * app to freeze it — a white glaze covers the icon and single taps do
 * nothing; double-tap again to thaw; state in SharedPreferences
 * "glassforge_frost"), Breath of the Forge (the whole screen wrapped
 * in ForgeBreathBackground — the background breathes with the battery),
 * and the Shattered Cell (5 rapid taps on an empty cell crack it;
 * long-press sweeps the glass). Two adaptations from Gemini's diff:
 * the cell gestures are one detectTapGestures handler (combinedClickable
 * cannot do double-tap), and the shatter state/gestures live in this
 * screen, not in the overlay, so the overlay cannot swallow the taps
 * that move items. Single taps wait out the double-tap window (~300ms)
 * — launches feel slightly slower; that is the price of the freeze.
 *
 * Uses the same colors as MainActivity (Back / Panel / Cyan) so it
 * fits the existing GlassforgeApp theme.
 */
@Composable
fun WorkspaceScreen(store: WorkspaceStore, widgetHost: WidgetHost) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by store.items.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }

    // Gemini's swing: Frost Lock + Shattered Cell state.
    val frostManager = remember { FrostLockManager(context) }
    val frostedApps by frostManager.frostedApps.collectAsState()
    // Shattered cells: cell -> true once cracked. In-memory only, as
    // in Gemini's spec (persists across swipes, not across restarts).
    val shatteredCells = remember { mutableStateMapOf<WorkspaceCell, Boolean>() }
    // Empty-cell tap counting: cell -> (tap count, last tap time ms).
    // A crack needs 5 taps inside a 2-second window.
    val shatterTaps = remember { mutableStateMapOf<WorkspaceCell, Pair<Int, Long>>() }

    fun recordShatterTap(cell: WorkspaceCell) {
        val now = System.currentTimeMillis()
        val (count, last) = shatterTaps[cell] ?: (0 to 0L)
        val next = if (now - last > 2000L) 1 else count + 1
        if (next >= 5) {
            shatteredCells[cell] = true
            shatterTaps.remove(cell)
        } else {
            shatterTaps[cell] = next to now
        }
    }

    // Real app icons, cached by package name. Survives recomposition
    // and moves; only missing packages are loaded, on IO.
    val pm = context.packageManager
    val iconCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(items) {
        val missing = items
            .filterIsInstance<WorkspaceItem.AppShortcut>()
            .map { it.packageName }
            .distinct()
            .filter { it !in iconCache }
        if (missing.isEmpty()) return@LaunchedEffect
        val loadedIcons = withContext(Dispatchers.IO) {
            missing.mapNotNull { pkg ->
                val bitmap = runCatching { pm.getApplicationIcon(pkg) }
                    .getOrNull()
                    ?.let { drawable ->
                        runCatching { drawableToBitmap(drawable) }.getOrNull()
                    }
                bitmap?.let { pkg to it }
            }
        }
        // Publish Compose state on the main coroutine after background I/O.
        loadedIcons.forEach { (pkg, bitmap) ->
            iconCache[pkg] = bitmap
        }
    }

    LaunchedEffect(store) {
        store.load()
        // Small delay is not needed: load() sets StateFlow on IO,
        // placeDiscoveredIfEmpty checks the loaded value.
        // We do placement after load by observing items once below.
    }

    // First-run placement: only when the store is loaded and empty.
    // This runs once because after placement items is non-empty.
    LaunchedEffect(items) {
        if (items.isEmpty()) {
            store.placeDiscoveredIfEmpty(context.packageManager)
        }
    }

    // Slot-based grid: one slot per cell, except a widget's anchor slot
    // spans its block and the cells it covers are skipped (they have no
    // slot of their own — the widget draws over them).
    val slots = remember(items) { buildGridSlots(items) }

    // Gemini's swing: Breath of the Forge wraps the whole screen —
    // the background breathes with the battery (cyan when full, molten
    // orange while charging, deep purple when low).
    ForgeBreathBackground {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = "GLASSFORGE  •  Workspace",
            color = Color(0xFF79E7FF),
            fontSize = 12.sp,
        )
        if (selectedId != null) {
            Text(
                text = "Move mode — tap an empty cell",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (message.isNotEmpty()) {
            Text(
                text = message,
                color = Color(0xFF9AA8BC),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(WorkspaceRules.COLUMNS),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            items(
                count = slots.size,
                key = { index ->
                    when (val slot = slots[index]) {
                        is GridSlot.Cell -> "cell:${slot.cell.x},${slot.cell.y}"
                        is GridSlot.WidgetSlot -> slot.widget.id
                    }
                },
                span = { index ->
                    when (val slot = slots[index]) {
                        is GridSlot.WidgetSlot -> GridItemSpan(slot.widget.spanX)
                        is GridSlot.Cell -> GridItemSpan(1)
                    }
                },
            ) { index ->
                val slot = slots[index]
                val cell = when (slot) {
                    is GridSlot.Cell -> slot.cell
                    is GridSlot.WidgetSlot -> slot.widget.cell
                }
                val item = when (slot) {
                    is GridSlot.Cell -> items.firstOrNull { it.cell == cell }
                    is GridSlot.WidgetSlot -> slot.widget
                }
                val isSelected = item?.id == selectedId
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(
                            if (item is WorkspaceItem.Widget) {
                                item.spanX.toFloat() / item.spanY
                            } else {
                                1f
                            }
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isSelected -> Color(0xFF79E7FF)
                                item != null -> Color(0xFF141B28)
                                else -> Color(0xFF0B111B)
                            },
                        )
                        // Gemini's swing: one gesture handler per cell.
                        // detectTapGestures replaces combinedClickable so
                        // a double-tap can freeze/thaw an app (Frost Lock).
                        // Cost: single taps wait out the double-tap window
                        // (~300ms) before firing — launches feel slightly
                        // slower. That is the price of the freeze.
                        .pointerInput(cell, item?.id, selectedId, frostedApps) {
                            detectTapGestures(
                                onTap = {
                                    when {
                                        selectedId != null && item == null -> {
                                            val movingId = selectedId!!
                                            selectedId = null
                                            scope.launch {
                                                val ok = store.move(movingId, cell)
                                                message = if (ok) "Moved" else "Move failed — kept old spot"
                                            }
                                        }
                                        selectedId != null && item != null -> {
                                            message = "Occupied — move rejected"
                                            selectedId = null
                                        }
                                        item == null -> {
                                            // Empty cell: count taps toward a shatter.
                                            recordShatterTap(cell)
                                        }
                                        item is WorkspaceItem.AppShortcut &&
                                            frostedApps.contains(item.packageName) -> {
                                            message = "${item.label} is frozen — double-tap to thaw"
                                        }
                                        item is WorkspaceItem.AppShortcut -> {
                                            val ok = store.launchApp(item.packageName)
                                            if (!ok) message = "Could not launch ${item.label}"
                                        }
                                        // Widget / Folder: nothing on tap.
                                        else -> {}
                                    }
                                },
                                onDoubleTap = {
                                    if (item is WorkspaceItem.AppShortcut) {
                                        val wasFrosted = frostedApps.contains(item.packageName)
                                        frostManager.toggleFrost(item.packageName)
                                        message = if (wasFrosted) {
                                            "${item.label} thawed"
                                        } else {
                                            "${item.label} frozen"
                                        }
                                    }
                                },
                                onLongPress = {
                                    if (item == null && shatteredCells[cell] == true) {
                                        // Sweep the glass.
                                        shatteredCells.remove(cell)
                                        shatterTaps.remove(cell)
                                        message = "Swept the glass"
                                    } else if (item != null) {
                                        selectedId = item.id
                                        message = ""
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (item) {
                        is WorkspaceItem.AppShortcut -> {
                            val isFrosted = frostedApps.contains(item.packageName)
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(6.dp),
                                ) {
                                    val icon = iconCache[item.packageName]
                                    if (icon != null) {
                                        Image(
                                            bitmap = icon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(48.dp),
                                        )
                                    } else {
                                        // Placeholder until the icon loads, or if
                                        // the package has no icon: first letter.
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF202C3C)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = item.label.take(1).uppercase(),
                                                color = Color(0xFF79E7FF),
                                                fontSize = 20.sp,
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                // Frost Lock: the glaze over a frozen app.
                                if (isFrosted) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color.White.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                        is WorkspaceItem.Folder -> {
                            Text(
                                text = "📁 ${item.name}",
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                        is WorkspaceItem.Widget -> {
                            // Live hosted view, created once per widget (the
                            // grid key keeps it stable across recompositions).
                            // Null means its provider is gone (uninstalled).
                            val hostView = remember(item) {
                                widgetHost.createView(context, item)
                            }
                            if (hostView != null) {
                                AndroidView(
                                    factory = { hostView },
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(6.dp),
                                ) {
                                    Text(
                                        text = "⚠ ${item.label}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "Widget unavailable — remove it from the Widgets tab",
                                        color = Color(0xFF9AA8BC),
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                        null -> {
                            // Shattered Cell easter egg: the crack, if this
                            // cell was shattered by 5 rapid taps.
                            ShatteredCellOverlay(isShattered = shatteredCells[cell] == true)
                        }
                    }
                }
            }
        }

        Text(
            text = "Long-press an app to move it. Tap to launch. Moves save before the screen updates, and survive a restart. Widgets live 2×2 on the grid — add them from the Widgets tab, long-press to move them, remove them there. Double-tap an app to freeze it.",
            color = Color(0xFF9AA8BC),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    }
}

/**
 * Same rasterization MainActivity.drawableToBitmap uses (96x96,
 * ARGB_8888, Canvas draw). Kept local to the workspace package
 * because MainActivity's copy is private.
 */
private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): ImageBitmap {
    val width = 96
    val height = 96
    val bitmap = android.graphics.Bitmap.createBitmap(
        width, height, android.graphics.Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

/**
 * Phase 2 grid slots. The grid is row-major over page 0; a widget's
 * anchor cell emits a WidgetSlot (spanning its block) and every other
 * cell its span covers is skipped — it has no slot of its own.
 */
private sealed interface GridSlot {
    data class Cell(val cell: WorkspaceCell) : GridSlot
    data class WidgetSlot(val widget: WorkspaceItem.Widget) : GridSlot
}

private fun buildGridSlots(items: List<WorkspaceItem>): List<GridSlot> {
    val covered = mutableSetOf<WorkspaceCell>()
    val out = mutableListOf<GridSlot>()
    for (y in 0 until WorkspaceRules.ROWS) {
        for (x in 0 until WorkspaceRules.COLUMNS) {
            val cell = WorkspaceCell(page = 0, x = x, y = y)
            if (cell in covered) continue
            val item = items.firstOrNull { it.cell == cell }
            if (item is WorkspaceItem.Widget) {
                out += GridSlot.WidgetSlot(item)
                covered += WorkspaceRules.coveredCells(item)
            } else {
                out += GridSlot.Cell(cell)
            }
        }
    }
    return out
}
