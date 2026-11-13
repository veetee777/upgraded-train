package com.glassforge.app.workspace

/**
 * Phase 1 workspace model — GLASSFORGE v0.7
 * New code written from the recovery-pack spec, not a verbatim
 * copy of any recovered file. No Express / localhost dependency.
 *
 * Core rule from the Perplexity review:
 * shortcut identity is separate from cell placement.
 * A move that collides must be a no-op — neither shortcut is lost.
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

    fun isInBounds(cell: WorkspaceCell): Boolean =
        cell.page in 0 until MAX_PAGES &&
            cell.x in 0 until COLUMNS &&
            cell.y in 0 until ROWS

    fun occupantAt(items: List<WorkspaceItem>, cell: WorkspaceCell): WorkspaceItem? =
        items.firstOrNull { it.cell == cell }

    /**
     * Pure validation — no I/O, no UI change.
     * Moving an item onto its own cell is allowed (no-op).
     */
    fun validateMove(
        items: List<WorkspaceItem>,
        itemId: String,
        to: WorkspaceCell,
    ): MoveResult {
        val item = items.firstOrNull { it.id == itemId } ?: return MoveResult.NotFound
        if (item.cell == to) return MoveResult.Allowed
        if (!isInBounds(to)) return MoveResult.OutOfBounds
        val occupant = occupantAt(items, to)
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
            }
        }
    }
}
