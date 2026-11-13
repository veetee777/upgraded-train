package com.glassforge.app.workspace

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import com.glassforge.app.core.LocalFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 1 workspace store — GLASSFORGE v0.7, v2 wired to actual v0.7 code.
 * New code written from the recovery-pack spec.
 *
 * Wiring decisions based on the real files Tristan pasted 2026-11-13:
 * - GlassForgeDatabase is Room v1 with only DocumentEntity, and uses
 *   fallbackToDestructiveMigration(). Adding a WorkspaceEntity would
 *   require a v2 migration and risks wiping documents. So Phase 1 does
 *   NOT touch Room. Workspace lives in files, via LocalFileStore.
 * - LocalFileStore (118 lines) is already atomic: writeBytes uses a temp
 *   file + rename + fd.sync, with traversal protection, under
 *   filesDir/glassforge. We reuse it for snapshots, and resolve() for WAL.
 * - LocalSettings uses SharedPreferences.apply() (async). That is fine
 *   for settings, but NOT durable enough for workspace moves. WAL appends
 *   here use FileOutputStream + fd.sync before StateFlow is updated.
 * - No Express, no localhost polling, no network. StateFlow only.
 * - No new Gradle dependency (org.json is in Android, coroutines already
 *   present via lifecycle / Room KTX).
 *
 * Files (under filesDir/glassforge/, private to the app):
 * - workspace/workspace_snapshot.json — full state, versioned
 * - workspace/workspace.wal.jsonl — appended move / add / remove records
 */
class WorkspaceStore(
    context: Context,
    private val fileStore: LocalFileStore,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val snapshotFile = fileStore.resolve("workspace/workspace_snapshot.json")
    private val walFile = fileStore.resolve("workspace/workspace.wal.jsonl")

    init {
        snapshotFile.parentFile?.mkdirs()
    }

    private val _items = MutableStateFlow<List<WorkspaceItem>>(emptyList())
    val items: StateFlow<List<WorkspaceItem>> = _items.asStateFlow()

    private val writeLock = Any()

    // P0-1: load gate. load() runs once; `loaded` flips true only after
    // the snapshot + WAL have been read, so first-run placement can
    // never race ahead of the saved layout and clobber it.
    private val loadStarted = AtomicBoolean(false)
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    // P0-2: strictly increasing WAL sequence. Wall-clock ms alone
    // collides when two ops land in the same millisecond and the second
    // is dropped on replay. Note the naive form
    // maxOf(now, counter.incrementAndGet()) does NOT fix this — the
    // counter starts at 0, far below the wall clock, so two calls in
    // the same ms both return `now`. updateAndGet with prev + 1 is
    // what actually guarantees strict monotonicity.
    private val seqCounter = AtomicLong(0L)
    private fun nextSeq(): Long =
        seqCounter.updateAndGet { prev -> maxOf(prev + 1, System.currentTimeMillis()) }

    // P2-19: icon cache hoisted into the store so it survives tab
    // switches. The screen's remember{} map was dropped every time
    // Home left composition, forcing a full reload on return.
    private val _iconCache = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val iconCache: StateFlow<Map<String, ImageBitmap>> = _iconCache.asStateFlow()

    fun putIcons(icons: Map<String, ImageBitmap>) {
        if (icons.isEmpty()) return
        _iconCache.value = _iconCache.value + icons
    }

    // P2-22: run a persistence op in the store's scope (which lives at
    // the app level and survives tab switches), delivering the result
    // on the main thread. Screen scopes are cancelled on tab switch,
    // which used to abort a move mid-persist.
    fun runPersist(block: suspend () -> Boolean, onResult: (Boolean) -> Unit) {
        scope.launch {
            onResult(runCatching { block() }.getOrDefault(false))
        }
    }

    // P2-25: folder members resolve from the same discovery list the
    // first-run placement used, instead of re-querying PackageManager
    // every time a folder opens.
    private var discoveredCache: List<WorkspaceItem.AppShortcut>? = null

    fun discoverAppsCached(packageManager: PackageManager): List<WorkspaceItem.AppShortcut> {
        discoveredCache?.let { return it }
        return discoverApps(packageManager).also { discoveredCache = it }
    }

    fun load() {
        if (!loadStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            _items.value = loadSync()
            _loaded.value = true
        }
    }

    private fun loadSync(): List<WorkspaceItem> {
        val fromSnapshot = readSnapshot()
        return replayWal(fromSnapshot)
    }

    private fun readSnapshot(): List<WorkspaceItem> {
        if (!fileStore.exists("workspace/workspace_snapshot.json")) return emptyList()
        return try {
            val json = JSONObject(fileStore.readText("workspace/workspace_snapshot.json"))
            if (json.optInt("version", 0) != 1) return emptyList()
            parseItems(json.optJSONArray("items") ?: JSONArray())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun replayWal(base: List<WorkspaceItem>): List<WorkspaceItem> {
        if (!walFile.exists()) return base
        var items = base
        var lastSeq = 0L
        try {
            walFile.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty()) continue
                    val record = try {
                        JSONObject(line)
                    } catch (_: Exception) {
                        continue // truncated final line: skip, keep prior state
                    }
                    val seq = record.optLong("seq", -1)
                    if (seq <= lastSeq && lastSeq != 0L) continue
                    lastSeq = seq
                    items = applyRecord(items, record) ?: items
                }
            }
        } catch (_: Exception) {
            // return what we have so far
        }
        return items
    }

    private fun applyRecord(
        items: List<WorkspaceItem>,
        record: JSONObject,
    ): List<WorkspaceItem>? {
        return when (record.optString("op")) {
            "move" -> {
                val id = record.optString("id")
                val page = record.optInt("page", 0)
                val x = record.optInt("x", -1)
                val y = record.optInt("y", -1)
                if (id.isEmpty() || x < 0 || y < 0) return null
                WorkspaceRules.applyMove(items, id, WorkspaceCell(page, x, y))
            }
            "add" -> {
                val item = parseItem(record.optJSONObject("item") ?: return null)
                    ?: return null
                if (items.any { it.id == item.id }) items else items + item
            }
            "remove" -> {
                val id = record.optString("id")
                items.filterNot { it.id == id }
            }
            // Phase 3: folders. Each folder action is ONE record, so
            // replay can never strand an app in two places or none:
            // create removes both apps and adds the folder, add
            // removes the app and appends its id, remove drops the id
            // and puts the app back (restored item JSON in the record).
            "folder-create" -> {
                val folder = parseItem(record.optJSONObject("item"))
                    as? WorkspaceItem.Folder ?: return null
                if (items.any { it.id == folder.id }) return items
                val ids = mutableListOf<String>()
                val arr = record.optJSONArray("memberIds")
                if (arr != null) {
                    for (i in 0 until arr.length()) ids += arr.optString(i)
                }
                items.filterNot { it.id in ids } + folder
            }
            "folder-add" -> {
                val folderId = record.optString("folderId")
                val appId = record.optString("appId")
                if (folderId.isEmpty() || appId.isEmpty()) return null
                val folder = items.firstOrNull { it.id == folderId }
                    as? WorkspaceItem.Folder ?: return items
                if (items.none { it.id == appId }) return items
                if (appId in folder.memberIds) return items
                items.filterNot { it.id == appId }.map { item ->
                    if (item.id == folderId && item is WorkspaceItem.Folder) {
                        item.copy(memberIds = item.memberIds + appId)
                    } else {
                        item
                    }
                }
            }
            "folder-remove" -> {
                val folderId = record.optString("folderId")
                val appId = record.optString("appId")
                if (folderId.isEmpty() || appId.isEmpty()) return null
                val folder = items.firstOrNull { it.id == folderId }
                    as? WorkspaceItem.Folder ?: return items
                if (appId !in folder.memberIds) return items
                val restored = parseItem(record.optJSONObject("item"))
                    as? WorkspaceItem.AppShortcut
                // The app comes back to the grid only if a cell is
                // actually free. If the grid filled up since this
                // record was written, keep the app in the folder
                // instead of silently losing it (the old code dropped
                // the membership first and then had nowhere to put it).
                var restoredCell: WorkspaceCell? = null
                if (restored != null && items.none { it.id == restored.id }) {
                    restoredCell = if (WorkspaceRules.isInBounds(restored.cell) &&
                        WorkspaceRules.occupantAt(items, restored.cell) == null
                    ) {
                        restored.cell
                    } else {
                        WorkspaceRules.findFreeAnchor(items, 1, 1)
                    }
                    if (restoredCell == null) return items // grid full: keep in folder
                }
                var next = items.map { item ->
                    if (item.id == folderId && item is WorkspaceItem.Folder) {
                        item.copy(memberIds = item.memberIds - appId)
                    } else {
                        item
                    }
                }
                if (restored != null && restoredCell != null) {
                    next = next + restored.copy(cell = restoredCell)
                }
                // An emptied folder disappears instead of lingering.
                val updated = next.firstOrNull { it.id == folderId }
                    as? WorkspaceItem.Folder
                if (updated != null && updated.memberIds.isEmpty()) {
                    next = next.filterNot { it.id == folderId }
                }
                next
            }
            else -> null
        }
    }

    suspend fun move(itemId: String, to: WorkspaceCell): Boolean =
        withContext(Dispatchers.IO) {
            val current = _items.value
            if (WorkspaceRules.validateMove(current, itemId, to) !=
                WorkspaceRules.MoveResult.Allowed
            ) {
                return@withContext false
            }
            val next = WorkspaceRules.applyMove(current, itemId, to)
            if (next == current) return@withContext true // own cell, no-op
            val ok = appendRecord(
                JSONObject().apply {
                    put("op", "move")
                    put("seq", nextSeq())
                    put("id", itemId)
                    put("page", to.page)
                    put("x", to.x)
                    put("y", to.y)
                },
            )
            if (!ok) return@withContext false
            _items.value = next
            maybeCompact(next)
            true
        }

    suspend fun addShortcut(item: WorkspaceItem.AppShortcut): Boolean =
        withContext(Dispatchers.IO) {
            val current = _items.value
            if (current.any { it.id == item.id }) return@withContext false
            if (WorkspaceRules.occupantAt(current, item.cell) != null) {
                return@withContext false
            }
            val ok = appendRecord(
                JSONObject().apply {
                    put("op", "add")
                    put("seq", nextSeq())
                    put("item", itemToJson(item))
                },
            )
            if (!ok) return@withContext false
            val next = current + item
            _items.value = next
            maybeCompact(next)
            true
        }

    /**
     * Phase 2: add a hosted widget. The whole covered block must be
     * free (span-aware) and fit inside the grid, or nothing is added.
     * The caller owns the AppWidgetHost id: on failure it must delete
     * the allocated id, and on removal it must delete it too.
     */
    suspend fun addWidget(item: WorkspaceItem.Widget): Boolean =
        withContext(Dispatchers.IO) {
            val current = _items.value
            if (current.any { it.id == item.id }) return@withContext false
            val covered = WorkspaceRules.coveredCells(item)
            if (covered.any { !WorkspaceRules.isInBounds(it) }) {
                return@withContext false
            }
            if (covered.any { WorkspaceRules.occupantAt(current, it) != null }) {
                return@withContext false
            }
            val ok = appendRecord(
                JSONObject().apply {
                    put("op", "add")
                    put("seq", nextSeq())
                    put("item", itemToJson(item))
                },
            )
            if (!ok) return@withContext false
            val next = current + item
            _items.value = next
            maybeCompact(next)
            true
        }

    /** Phase 2: remove any item by id (widget, shortcut, folder). */
    suspend fun removeItem(itemId: String): Boolean =
        withContext(Dispatchers.IO) {
            val current = _items.value
            if (current.none { it.id == itemId }) return@withContext false
            val ok = appendRecord(
                JSONObject().apply {
                    put("op", "remove")
                    put("seq", nextSeq())
                    put("id", itemId)
                },
            )
            if (!ok) return@withContext false
            val next = current.filterNot { it.id == itemId }
            _items.value = next
            maybeCompact(next)
            true
        }

    /**
     * Phase 3: create a folder from apps already on the grid. One WAL
     * record removes the member apps and adds the folder, so replay
     * can never strand an app in two places or none. The folder takes
     * the cell of one of its members (the tapped app).
     */
    suspend fun createFolder(
        folder: WorkspaceItem.Folder,
        memberIds: List<String>,
    ): Boolean = withContext(Dispatchers.IO) {
        val current = _items.value
        if (current.any { it.id == folder.id }) return@withContext false
        val members = memberIds.mapNotNull { id ->
            current.firstOrNull { it.id == id } as? WorkspaceItem.AppShortcut
        }
        if (members.size != memberIds.size) return@withContext false
        if (members.none { it.cell == folder.cell }) return@withContext false
        val ok = appendRecord(
            JSONObject().apply {
                put("op", "folder-create")
                put("seq", nextSeq())
                put("item", itemToJson(folder))
                put("memberIds", JSONArray(memberIds))
            },
        )
        if (!ok) return@withContext false
        val next = current.filterNot { it.id in memberIds } + folder
        _items.value = next
        maybeCompact(next)
        true
    }

    /**
     * Phase 3: drop a grid app into a folder. One WAL record removes
     * the shortcut from the grid and appends its id to the folder.
     * Only AppShortcuts go in; widgets and folders are rejected.
     */
    suspend fun addAppToFolder(folderId: String, appId: String): Boolean =
        withContext(Dispatchers.IO) {
            val current = _items.value
            val folder = current.firstOrNull { it.id == folderId }
                as? WorkspaceItem.Folder ?: return@withContext false
            val app = current.firstOrNull { it.id == appId }
                as? WorkspaceItem.AppShortcut ?: return@withContext false
            if (appId in folder.memberIds) return@withContext false
            val ok = appendRecord(
                JSONObject().apply {
                    put("op", "folder-add")
                    put("seq", nextSeq())
                    put("folderId", folderId)
                    put("appId", appId)
                },
            )
            if (!ok) return@withContext false
            val next = current.filterNot { it.id == appId }.map { item ->
                if (item.id == folderId && item is WorkspaceItem.Folder) {
                    item.copy(memberIds = item.memberIds + appId)
                } else {
                    item
                }
            }
            _items.value = next
            maybeCompact(next)
            true
        }

    /**
     * Phase 3: take an app out of a folder. [restoredItem] is the
     * shortcut to put back on the grid at a free cell the caller
     * found (null when the app is uninstalled — the id is just
     * dropped from the folder). One WAL record. An emptied folder
     * is removed instead of lingering with no members.
     */
    suspend fun removeAppFromFolder(
        folderId: String,
        appId: String,
        restoredItem: WorkspaceItem.AppShortcut?,
    ): Boolean = withContext(Dispatchers.IO) {
        val current = _items.value
        val folder = current.firstOrNull { it.id == folderId }
            as? WorkspaceItem.Folder ?: return@withContext false
        if (appId !in folder.memberIds) return@withContext false
        if (restoredItem != null) {
            if (restoredItem.id != appId) return@withContext false
            if (!WorkspaceRules.isInBounds(restoredItem.cell)) return@withContext false
            if (WorkspaceRules.occupantAt(current, restoredItem.cell) != null) {
                return@withContext false
            }
        }
        val ok = appendRecord(
            JSONObject().apply {
                put("op", "folder-remove")
                put("seq", nextSeq())
                put("folderId", folderId)
                put("appId", appId)
                if (restoredItem != null) put("item", itemToJson(restoredItem))
            },
        )
        if (!ok) return@withContext false
        var next = current.map { item ->
            if (item.id == folderId && item is WorkspaceItem.Folder) {
                item.copy(memberIds = item.memberIds - appId)
            } else {
                item
            }
        }
        if (restoredItem != null) next = next + restoredItem
        val updated = next.firstOrNull { it.id == folderId }
            as? WorkspaceItem.Folder
        if (updated != null && updated.memberIds.isEmpty()) {
            next = next.filterNot { it.id == folderId }
        }
        _items.value = next
        maybeCompact(next)
        true
    }

    /** First-run placement: fill page 0 in reading order, 4 cols x 5 rows. */
    suspend fun placeDiscoveredIfEmpty(
        packageManager: PackageManager,
    ): Boolean = withContext(Dispatchers.IO) {
        // Never place before the saved layout has loaded: on a cold
        // start items is briefly empty while load() is still reading,
        // and placing then would overwrite the snapshot.
        if (!_loaded.value) return@withContext false
        if (_items.value.isNotEmpty()) return@withContext false
        val apps = discoverAppsCached(packageManager).take(20)
        var placed = _items.value
        for ((index, app) in apps.withIndex()) {
            val cell = WorkspaceCell(
                page = 0,
                x = index % WorkspaceRules.COLUMNS,
                y = index / WorkspaceRules.COLUMNS,
            )
            placed += app.copy(cell = cell)
        }
        // Persist the whole first-run layout as one snapshot, atomically.
        try {
            fileStore.writeText(
                "workspace/workspace_snapshot.json",
                JSONObject().apply {
                    put("version", 1)
                    put("items", itemsToJson(placed))
                }.toString(),
            )
        } catch (_: Exception) {
            return@withContext false
        }
        _items.value = placed
        true
    }

    private fun appendRecord(record: JSONObject): Boolean {
        synchronized(writeLock) {
            return try {
                walFile.parentFile?.mkdirs()
                FileOutputStream(walFile, true).use { out ->
                    out.write((record.toString() + "\n").toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun maybeCompact(items: List<WorkspaceItem>) {
        synchronized(writeLock) {
            try {
                if (!walFile.exists() || walFile.length() < 256 * 1024) return
                fileStore.writeText(
                    "workspace/workspace_snapshot.json",
                    JSONObject().apply {
                        put("version", 1)
                        put("items", itemsToJson(items))
                    }.toString(),
                )
                walFile.writeText("")
            } catch (_: Exception) {
                // Compaction failure must not lose the WAL.
            }
        }
    }

    private fun itemToJson(item: WorkspaceItem): JSONObject =
        when (item) {
            is WorkspaceItem.AppShortcut -> JSONObject().apply {
                put("type", "app")
                put("id", item.id)
                put("packageName", item.packageName)
                put("className", item.className)
                put("label", item.label)
                put("page", item.cell.page)
                put("x", item.cell.x)
                put("y", item.cell.y)
            }
            is WorkspaceItem.Folder -> JSONObject().apply {
                put("type", "folder")
                put("id", item.id)
                put("name", item.name)
                put("members", JSONArray(item.memberIds))
                put("page", item.cell.page)
                put("x", item.cell.x)
                put("y", item.cell.y)
            }
            is WorkspaceItem.Widget -> JSONObject().apply {
                put("type", "widget")
                put("id", item.id)
                put("appWidgetId", item.appWidgetId)
                put("providerPackage", item.providerPackage)
                put("providerClass", item.providerClass)
                put("label", item.label)
                put("page", item.cell.page)
                put("x", item.cell.x)
                put("y", item.cell.y)
                put("spanX", item.spanX)
                put("spanY", item.spanY)
            }
        }

    private fun parseItems(array: JSONArray): List<WorkspaceItem> {
        val out = mutableListOf<WorkspaceItem>()
        for (i in 0 until array.length()) {
            parseItem(array.optJSONObject(i))?.let { out += it }
        }
        return out
    }

    private fun parseItem(json: JSONObject?): WorkspaceItem? {
        if (json == null) return null
        val cell = WorkspaceCell(
            page = json.optInt("page", 0),
            x = json.optInt("x", -1),
            y = json.optInt("y", -1),
        )
        if (!WorkspaceRules.isInBounds(cell)) return null
        return when (json.optString("type")) {
            "app" -> WorkspaceItem.AppShortcut(
                id = json.optString("id"),
                packageName = json.optString("packageName"),
                className = json.optString("className").ifEmpty { null },
                label = json.optString("label"),
                cell = cell,
            ).takeIf { it.id.isNotEmpty() && it.packageName.isNotEmpty() }
            "folder" -> {
                val members = mutableListOf<String>()
                val arr = json.optJSONArray("members")
                if (arr != null) {
                    for (i in 0 until arr.length()) members += arr.optString(i)
                }
                WorkspaceItem.Folder(
                    id = json.optString("id"),
                    name = json.optString("name"),
                    memberIds = members,
                    cell = cell,
                ).takeIf { it.id.isNotEmpty() }
            }
            "widget" -> WorkspaceItem.Widget(
                id = json.optString("id"),
                appWidgetId = json.optInt("appWidgetId", -1),
                providerPackage = json.optString("providerPackage"),
                providerClass = json.optString("providerClass"),
                label = json.optString("label"),
                cell = cell,
                spanX = json.optInt("spanX", 2).coerceIn(1, WorkspaceRules.COLUMNS),
                spanY = json.optInt("spanY", 2).coerceIn(1, WorkspaceRules.ROWS),
            ).takeIf {
                it.id.isNotEmpty() && it.appWidgetId >= 0 &&
                    it.providerPackage.isNotEmpty()
            }
            else -> null
        }
    }

    private fun itemsToJson(items: List<WorkspaceItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(itemToJson(it)) }
        return arr
    }

    fun discoverApps(packageManager: PackageManager): List<WorkspaceItem.AppShortcut> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return try {
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    WorkspaceItem.AppShortcut(
                        id = "app:${activity.packageName}/${activity.name}",
                        packageName = activity.packageName,
                        className = activity.name,
                        label = info.loadLabel(packageManager).toString(),
                        cell = WorkspaceCell(page = 0, x = 0, y = 0),
                    )
                }
                .sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = appContext.packageManager
                .getLaunchIntentForPackage(packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: return false
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
