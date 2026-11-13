package com.glassforge.app.workspace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Phase 1 workspace screen — GLASSFORGE v0.7
 * New code. No drag-and-drop yet (Phase 1b).
 *
 * Testable move flow, matching the Perplexity gates:
 * - Tap an app: launch it.
 * - Long-press an app: enter move mode (it highlights).
 * - Tap an empty cell: move there, persist, survive restart.
 * - Tap an occupied cell in move mode: rejected, both shortcuts stay,
 *   message shows "Occupied — move rejected".
 *
 * Uses the same colors as MainActivity (Back / Panel / Cyan) so it
 * fits the existing GlassforgeApp theme.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceScreen(store: WorkspaceStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by store.items.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }

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

    val gridCells = (0 until WorkspaceRules.ROWS).flatMap { y ->
        (0 until WorkspaceRules.COLUMNS).map { x -> WorkspaceCell(page = 0, x = x, y = y) }
    }

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
            items(gridCells.size) { index ->
                val cell = gridCells[index]
                val item = items.firstOrNull { it.cell == cell }
                val isSelected = item?.id == selectedId
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isSelected -> Color(0xFF79E7FF)
                                item != null -> Color(0xFF141B28)
                                else -> Color(0xFF0B111B)
                            },
                        )
                        .combinedClickable(
                            onClick = {
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
                                    item is WorkspaceItem.AppShortcut -> {
                                        val ok = store.launchApp(item.packageName)
                                        if (!ok) message = "Could not launch ${item.label}"
                                    }
                                    else -> { /* empty cell, nothing to do */ }
                                }
                            },
                            onLongClick = {
                                if (item != null) {
                                    selectedId = item.id
                                    message = ""
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (item) {
                            is WorkspaceItem.AppShortcut -> item.label
                            is WorkspaceItem.Folder -> "📁 ${item.name}"
                            null -> ""
                        },
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }

        Text(
            text = "Long-press an app to move it. Tap to launch. Moves save before the screen updates, and survive a restart.",
            color = Color(0xFF9AA8BC),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
