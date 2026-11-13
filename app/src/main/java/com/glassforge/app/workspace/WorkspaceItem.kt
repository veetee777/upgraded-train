package com.glassforge.app.workspace

/**
 * Phase 1 workspace model — GLASSFORGE v0.7
 * New code written from the recovery-pack spec, not a verbatim
 * copy of any recovered file. No Express / localhost dependency.
 *
 * Core rule from the Perplexity review:
 * shortcut identity is separate from cell placement.
 * A move that collides must be a no-op — neither shortcut is lost.
 *
 * Phase 2 adds WorkspaceItem.Widget: a real hosted Android widget
 * (AppWidgetHostView) placed on the grid. A widget spans more than
 * one cell (default 2x2), so occupancy is span-aware: every rule
 * below works on the set of cells an item covers, not just its
 * anchor cell. A widget's identity is its host id ("widget:<id>"),
 * separate from its placement, same as shortcuts.
 */

data class WorkspaceCell(
    val page: Int = 0,
    val x: Int,
    val y: Int,
)

sealed interface WorkspaceItem {
    val id: String
    val cell: WorkspaceCell

    data class AppShortcut(
        override val id: String, // stable id, e.g. "app:<package>/<class>"
        val packageName: String,
        val className: String?,
        val label: String,
        override val cell: WorkspaceCell,
    ) : WorkspaceItem

    data class Folder(
        override val id: String, // stable id, e.g. "folder:<uuid>"
        val name: String,
        val memberIds: List<String>, // ids of AppShortcut items inside
        override val cell: WorkspaceCell,
    ) : WorkspaceItem

    /**
     * A real hosted widget. The AppWidgetHost owns the live view;
     * this record is the durable placement <-> widget-ID mapping.
     * The host id must be deleted (WidgetHost.deleteAppWidgetId) when
     * this item is removed, or the id leaks in AppWidgetManager.
     */
    data class Widget(
        override val id: String, // stable id, "widget:<appWidgetId>"
        val appWidgetId: Int,
        val providerPackage: String,
        val providerClass: String,
        val label: String,
        override val cell: WorkspaceCell, // anchor = top-left covered cell
        val spanX: Int = 2,
        val spanY: Int = 2,
    ) : WorkspaceItem
}

object WorkspaceRules {
    const val COLUMNS = 4
    const val ROWS = 5
    const val MAX_PAGES = 5

    sealed interface MoveResult {
        data object Allowed : MoveResult
        data object OutOfBounds : MoveResult
        data class Collision(val occupantId: String) : MoveResult
        data object NotFound : MoveResult
    }

    /** Grid span of an item, in cells. Shortcuts and folders are 1x1. */
    fun spanOf(item: WorkspaceItem): Pair<Int, Int> = when (item) {
        is WorkspaceItem.AppShortcut -> 1 to 1
        is WorkspaceItem.Folder -> 1 to 1
        is WorkspaceItem.Widget -> item.spanX to item.spanY
    }

    /** Every cell an item covers, from its anchor cell and span. */
    fun coveredCells(item: WorkspaceItem): List<WorkspaceCell> {
        val (spanX, spanY) = spanOf(item)
        return buildList {
            for (dy in 0 until spanY) {
                for (dx in 0 until spanX) {
                    add(WorkspaceCell(item.cell.page, item.cell.x + dx, item.cell.y + dy))
                }
            }
        }
    }

    /** Cells an item WOULD cover if its anchor moved to [to]. */
    fun coveredCellsAt(item: WorkspaceItem, to: WorkspaceCell): List<WorkspaceCell> {
        val (spanX, spanY) = spanOf(item)
        return buildList {
            for (dy in 0 until spanY) {
                for (dx in 0 until spanX) {
                    add(WorkspaceCell(to.page, to.x + dx, to.y + dy))
                }
            }
        }
    }

    fun isInBounds(cell: WorkspaceCell): Boolean =
        cell.page in 0 until MAX_PAGES &&
            cell.x in 0 until COLUMNS &&
            cell.y in 0 until ROWS

    /** Span-aware: an item occupies a cell if the cell is in its covered set. */
    fun occupantAt(items: List<WorkspaceItem>, cell: WorkspaceCell): WorkspaceItem? =
        items.firstOrNull { item -> cell in coveredCells(item) }

    /**
     * First free anchor on page 0 where a spanX x spanY block fits
     * entirely inside the grid with no occupant. Scans top-left first.
     */
    fun findFreeAnchor(
        items: List<WorkspaceItem>,
        spanX: Int,
        spanY: Int,
    ): WorkspaceCell? {
        if (spanX < 1 || spanY < 1) return null
        for (y in 0..ROWS - spanY) {
            for (x in 0..COLUMNS - spanX) {
                val anchor = WorkspaceCell(page = 0, x = x, y = y)
                val covered = coveredCellsAt(
                    WorkspaceItem.Widget(
                        id = "", appWidgetId = -1, providerPackage = "",
                        providerClass = "", label = "", cell = anchor,
                        spanX = spanX, spanY = spanY,
                    ),
                    anchor,
                )
                if (covered.all { occupantAt(items, it) == null }) return anchor
            }
        }
        return null
    }

    /**
     * Pure validation — no I/O, no UI change.
     * Moving an item onto its own cell is allowed (no-op).
     * Span-aware: the whole covered block at the target must be in
     * bounds and free of other items.
     */
    fun validateMove(
        items: List<WorkspaceItem>,
        itemId: String,
        to: WorkspaceCell,
    ): MoveResult {
        val item = items.firstOrNull { it.id == itemId } ?: return MoveResult.NotFound
        if (item.cell == to) return MoveResult.Allowed
        val covered = coveredCellsAt(item, to)
        if (covered.any { !isInBounds(it) }) return MoveResult.OutOfBounds
        val occupant = covered.firstNotNullOfOrNull { occupantAt(items, it) }
        if (occupant != null && occupant.id != itemId) {
            return MoveResult.Collision(occupant.id)
        }
        return MoveResult.Allowed
    }

    fun applyMove(
        items: List<WorkspaceItem>,
        itemId: String,
        to: WorkspaceCell,
    ): List<WorkspaceItem> {
        if (validateMove(items, itemId, to) != MoveResult.Allowed) return items
        return items.map { item ->
            when (item) {
                is WorkspaceItem.AppShortcut ->
                    if (item.id == itemId) item.copy(cell = to) else item
                is WorkspaceItem.Folder ->
                    if (item.id == itemId) item.copy(cell = to) else item
                is WorkspaceItem.Widget ->
                    if (item.id == itemId) item.copy(cell = to) else item
            }
        }
    }
}
