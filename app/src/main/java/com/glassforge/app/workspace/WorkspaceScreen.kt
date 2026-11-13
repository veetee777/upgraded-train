package com.glassforge.app.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
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
 * Gemini's swing (gemini-feature branch): Frost Lock (long-press an
 * app to pick it up, then tap the Freeze chip — a white glaze covers
 * the icon and single taps do nothing; tap Thaw to thaw; state in
 * SharedPreferences "glassforge_frost"), Breath of the Forge (the
 * whole screen wrapped in ForgeBreathBackground — the background
 * breathes with the battery), and the Shattered Cell (5 rapid taps on
 * an empty cell crack it; long-press sweeps the glass). Two adaptations
 * from Gemini's diff: the cell gestures are one detectTapGestures
 * handler, and the shatter state/gestures live in this screen, not in
 * the overlay, so the overlay cannot swallow the taps that move items.
 * Taps are instant — with no onDoubleTap handler, onTap fires without
 * waiting out a double-tap window.
 *
 * Phase 3: folders. A folder cell shows a 2x2 preview of up to 4
 * member icons; tapping it opens FolderDialog. Folders are made
 * with the move gesture, not a separate menu: long-press an app to
 * pick it up, then tap another app to fold both into a new folder,
 * or tap a folder to drop the app in. Long-press a member inside
 * the folder view takes it back out to the first free cell. Each
 * folder change is one WAL record in WorkspaceStore, so membership
 * survives restarts through the same snapshot/WAL as everything
 * else — no second store. An emptied folder removes itself.
 *
 * Uses the same colors as MainActivity (Back / Panel / Cyan) so it
 * fits the existing GlassforgeApp theme.
 */
@Composable
fun WorkspaceScreen(store: WorkspaceStore, widgetHost: WidgetHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val items by store.items.collectAsState()
    // P0-1: placement waits for this — items is briefly empty on a
    // cold start while load() is still reading the saved layout.
    val loaded by store.loaded.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }

    // P2-21: status messages auto-clear so they never linger.
    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            delay(2500)
            message = ""
        }
    }

    // Gemini's swing: Frost Lock + Shattered Cell state.
    val frostManager = remember { FrostLockManager(context) }
    val frostedApps by frostManager.frostedApps.collectAsState()
    // Shattered cells: cell -> true once cracked. In-memory only, as
    // in Gemini's spec (persists across swipes, not across restarts).
    val shatteredCells = remember { mutableStateMapOf<WorkspaceCell, Boolean>() }
    // Empty-cell tap counting: cell -> (tap count, last tap time ms).
    // A crack needs 5 taps inside a 2-second window.
    val shatterTaps = remember { mutableStateMapOf<WorkspaceCell, Pair<Int, Long>>() }

    // Phase 3: folders. openFolderId drives the folder dialog; the
    // folder is derived from items, so the dialog closes itself if
    // the folder is removed (e.g. emptied) while it is open.
    var openFolderId by remember { mutableStateOf<String?>(null) }
    val openFolder = items.firstOrNull { it.id == openFolderId } as? WorkspaceItem.Folder
    var folderMessage by remember { mutableStateOf("") }

    // P2-21: the folder's message auto-clears too.
    LaunchedEffect(folderMessage) {
        if (folderMessage.isNotEmpty()) {
            delay(2500)
            folderMessage = ""
        }
    }
    // Resolved members (installed apps only). The folder stores ids,
    // so labels/package/class come from PackageManager at open time.
    var folderMembers by remember { mutableStateOf<List<WorkspaceItem.AppShortcut>>(emptyList()) }
    var folderMembersLoaded by remember { mutableStateOf(false) }
    val unresolvedMemberIds = if (folderMembersLoaded && openFolder != null) {
        openFolder.memberIds.filter { id -> folderMembers.none { it.id == id } }
    } else {
        emptyList()
    }

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

    // Real app icons, cached by package name in the store (P2-19:
    // hoisted out of this screen so the cache survives tab switches).
    // Only packages not in the cache are loaded, on IO.
    val pm = context.packageManager
    val iconCache by store.iconCache.collectAsState()
    LaunchedEffect(items) {
        // Grid shortcuts plus folder members (a folder stores ids,
        // so their packages are parsed out for icon loading).
        val missing = (
            items
                .filterIsInstance<WorkspaceItem.AppShortcut>()
                .map { it.packageName } +
                items
                    .filterIsInstance<WorkspaceItem.Folder>()
                    .flatMap { it.memberIds }
                    .mapNotNull { packageNameFromItemId(it) }
            )
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
        if (loadedIcons.isNotEmpty()) store.putIcons(loadedIcons.toMap())
    }

    // Phase 3: resolve the open folder's member ids to launchable
    // apps (label/package/class) off the main thread. Members whose
    // app is gone stay in the folder as unresolved tiles the user
    // can long-press away.
    LaunchedEffect(openFolder) {
        folderMembersLoaded = false
        val folder = openFolder
        if (folder == null) {
            folderMembers = emptyList()
            folderMessage = ""
            return@LaunchedEffect
        }
        folderMembers = withContext(Dispatchers.IO) {
            val all = store.discoverAppsCached(context.packageManager)
            folder.memberIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
        }
        folderMembersLoaded = true
    }

    LaunchedEffect(store) {
        store.load()
    }

    // First-run placement: only once the store has finished loading
    // and is empty. Gating on `loaded` is what stops a cold start
    // from clobbering the saved layout with a fresh alphabetical one
    // (items is briefly empty while load() is still reading).
    LaunchedEffect(loaded) {
        if (loaded && items.isEmpty()) {
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
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = "GLASSFORGE  •  Workspace",
            color = Color(0xFF79E7FF),
            fontSize = 12.sp,
        )
        if (selectedId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Move mode — empty cell to move · app to make a folder · folder to drop in",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f).padding(top = 4.dp),
                )
                // Frost Lock: freeze/thaw the carried app. Shown only
                // for app shortcuts; tapping toggles frost and does NOT
                // drop the app — it stays picked up, so Tristan can
                // freeze then still place it.
                val carried = items.firstOrNull { it.id == selectedId }
                if (carried is WorkspaceItem.AppShortcut) {
                    val isCarriedFrosted = frostedApps.contains(carried.packageName)
                    Text(
                        text = if (isCarriedFrosted) "☀ Thaw" else "❄ Freeze",
                        color = Color(0xFF79E7FF),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable {
                                frostManager.toggleFrost(carried.packageName)
                                message = if (isCarriedFrosted) {
                                    "${carried.label} thawed"
                                } else {
                                    "${carried.label} frozen"
                                }
                            }
                            .padding(4.dp),
                    )
                }
                // P2-20: a way out of move mode without tapping a cell.
                Text(
                    text = "Cancel",
                    color = Color(0xFF79E7FF),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable {
                            selectedId = null
                            message = ""
                        }
                        .padding(4.dp),
                )
            }
        }
        // P2-21: fixed-height slot — the message appearing/vanishing
        // no longer shoves the grid up and down.
        Box(
            modifier = Modifier.fillMaxWidth().height(22.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    color = Color(0xFF9AA8BC),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(WorkspaceRules.COLUMNS),
            contentPadding = PaddingValues(bottom = 12.dp),
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
                        // detectTapGestures with no onDoubleTap, so onTap
                        // fires immediately — no ~300ms double-tap wait.
                        // Frost Lock lives on the Freeze/Thaw chip in the
                        // move-mode bar instead of a double-tap.
                        .pointerInput(cell, item?.id, selectedId, frostedApps) {
                            detectTapGestures(
                                onTap = {
                                    when {
                                        selectedId != null && item == null -> {
                                            val movingId = selectedId!!
                                            selectedId = null
                                            // P2-22: persist in the store's scope so a tab
                                            // switch mid-move cannot cancel the write.
                                            store.runPersist({ store.move(movingId, cell) }) { ok ->
                                                message = if (ok) "Moved" else "Move failed — kept old spot"
                                            }
                                        }
                                        selectedId != null && item != null && item.id == selectedId -> {
                                            // Tapped the item being carried: put it back down.
                                            selectedId = null
                                            message = ""
                                        }
                                        selectedId != null && item is WorkspaceItem.Folder -> {
                                            // Phase 3: drop a carried app into the folder.
                                            val moving = store.items.value
                                                .firstOrNull { it.id == selectedId }
                                            selectedId = null
                                            if (moving is WorkspaceItem.AppShortcut) {
                                                store.runPersist({ store.addAppToFolder(item.id, moving.id) }) { ok ->
                                                    message = if (ok) {
                                                        "Added to ${item.name}"
                                                    } else {
                                                        "Could not add to folder"
                                                    }
                                                }
                                            } else {
                                                message = "Only apps go into folders"
                                            }
                                        }
                                        selectedId != null && item is WorkspaceItem.AppShortcut -> {
                                            // Phase 3: drop a carried app onto another app:
                                            // fold both into a new folder at the tapped
                                            // app's cell. This is also how folders are
                                            // created — there is no separate menu.
                                            val moving = store.items.value
                                                .firstOrNull { it.id == selectedId }
                                            selectedId = null
                                            if (moving is WorkspaceItem.AppShortcut) {
                                                val folder = WorkspaceItem.Folder(
                                                    id = "folder:${UUID.randomUUID()}",
                                                    name = "Folder",
                                                    memberIds = listOf(item.id, moving.id),
                                                    cell = item.cell,
                                                )
                                                store.runPersist({
                                                    store.createFolder(
                                                        folder,
                                                        listOf(item.id, moving.id),
                                                    )
                                                }) { ok ->
                                                    message = if (ok) {
                                                        "Folder created"
                                                    } else {
                                                        "Could not create folder"
                                                    }
                                                }
                                            } else {
                                                message = "Occupied — move rejected"
                                                selectedId = null
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
                                        item is WorkspaceItem.Folder -> {
                                            // Phase 3: open the folder view.
                                            openFolderId = item.id
                                        }
                                        item is WorkspaceItem.AppShortcut &&
                                            frostedApps.contains(item.packageName) -> {
                                            message = "${item.label} is frozen — long-press, then tap Thaw"
                                        }
                                        item is WorkspaceItem.AppShortcut -> {
                                            val ok = store.launchApp(item.packageName)
                                            if (!ok) message = "Could not launch ${item.label}"
                                        }
                                        // Widget / Folder: nothing on tap.
                                        else -> {}
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
                            // Phase 3: mini preview — up to 4 member
                            // icons in a 2x2 cluster, so a folder
                            // reads as a folder at a glance.
                            val previews = item.memberIds
                                .mapNotNull { packageNameFromItemId(it) }
                                .mapNotNull { iconCache[it] }
                                .take(4)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(6.dp),
                            ) {
                                if (previews.isNotEmpty()) {
                                    Column {
                                        Row {
                                            FolderPreviewIcon(previews.getOrNull(0))
                                            FolderPreviewIcon(previews.getOrNull(1))
                                        }
                                        Row {
                                            FolderPreviewIcon(previews.getOrNull(2))
                                            FolderPreviewIcon(previews.getOrNull(3))
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF202C3C)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "📁",
                                            fontSize = 20.sp,
                                        )
                                    }
                                }
                                Text(
                                    text = item.name,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        is WorkspaceItem.Widget -> {
                            // Live hosted view, cached in WidgetHost by
                            // appWidgetId and remembered here by id (not
                            // by item: a move creates a new item instance
                            // and must not orphan the view). Null means
                            // its provider is gone (uninstalled).
                            val hostView = remember(item.appWidgetId) {
                                widgetHost.createView(context, item)
                            }
                            if (hostView != null) {
                                // P1-8: measure the cell and tell the
                                // hosted view its size, or it renders
                                // at its minimum size.
                                BoxWithConstraints(
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                ) {
                                    val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
                                    val heightDp = maxHeight.value.toInt().coerceAtLeast(1)
                                    AndroidView(
                                        factory = { hostView },
                                        update = { view ->
                                            widgetHost.updateWidgetSize(
                                                view, widthDp, heightDp, widthDp, heightDp,
                                            )
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
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
            text = "Long-press an app to move it. Tap to launch. Moves save before the screen updates, and survive a restart. Widgets live 2×2 on the grid — add them from the Widgets tab, long-press to move them, remove them there. Long-press an app, then tap Freeze to freeze it. Tap a folder to open it. Long-press an app, then tap another app to make a folder, or tap a folder to drop the app in. Long-press an app inside a folder to take it back out.",
            color = Color(0xFF9AA8BC),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 12.dp),
        )

        // Phase 3: the folder view. Derived from items, so it closes
        // itself if the folder is removed while open.
        if (openFolder != null) {
            FolderDialog(
                folder = openFolder,
                members = folderMembers,
                unresolvedIds = unresolvedMemberIds,
                iconCache = iconCache,
                frostedApps = frostedApps,
                message = folderMessage,
                // P2-25: show a loading state until the members resolve
                // instead of an empty grid that fills in a beat later.
                loading = !folderMembersLoaded,
                onMemberTap = { member ->
                    if (frostedApps.contains(member.packageName)) {
                        folderMessage = "${member.label} is frozen — tap ❄ to thaw"
                    } else {
                        val ok = store.launchApp(member.packageName)
                        if (ok) {
                            // P2-26: a successful launch closes the folder.
                            openFolderId = null
                        } else {
                            folderMessage = "Could not launch ${member.label}"
                        }
                    }
                },
                onMemberFreezeToggle = { member ->
                    val wasFrosted = frostedApps.contains(member.packageName)
                    frostManager.toggleFrost(member.packageName)
                    folderMessage = if (wasFrosted) {
                        "${member.label} thawed"
                    } else {
                        "${member.label} frozen"
                    }
                },
                onRemoveMember = { memberId ->
                    val folder = openFolder
                    if (folder != null) {
                        val app = folderMembers.firstOrNull { it.id == memberId }
                        if (app == null) {
                            // Uninstalled: the id is just dropped.
                            store.runPersist({ store.removeAppFromFolder(folder.id, memberId, null) }) { ok ->
                                folderMessage = if (ok) {
                                    "Removed from folder"
                                } else {
                                    "Could not remove"
                                }
                            }
                        } else {
                            val free = WorkspaceRules.findFreeAnchor(
                                store.items.value, 1, 1,
                            )
                            if (free == null) {
                                folderMessage = "No free cell — move something first"
                            } else {
                                store.runPersist({
                                    store.removeAppFromFolder(
                                        folder.id, memberId, app.copy(cell = free),
                                    )
                                }) { ok ->
                                    if (ok) {
                                        folderMessage = "${app.label} is back on Home"
                                        if (folder.memberIds.size == 1) {
                                            message = "Folder emptied and removed"
                                        }
                                    } else {
                                        folderMessage = "Could not remove"
                                    }
                                }
                            }
                        }
                    }
                },
                onShowMessage = { folderMessage = it },
                onDismiss = { openFolderId = null },
            )
        }
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
 * Phase 3: package name out of a shortcut id ("app:<package>/<class>").
 * Null for anything that is not an app id. Used to load folder
 * member icons and to label members whose app is gone.
 */
internal fun packageNameFromItemId(id: String): String? {
    if (!id.startsWith("app:")) return null
    return id.removePrefix("app:").substringBefore("/").takeIf { it.isNotEmpty() }
}

/** Phase 3: one mini icon (or an empty slot) in a folder preview. */
@Composable
private fun FolderPreviewIcon(icon: ImageBitmap?) {
    Box(
        modifier = Modifier.size(22.dp).padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
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
