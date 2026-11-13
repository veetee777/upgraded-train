package com.glassforge.app.workspace

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassforge.app.drawableToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 1b workspace screen — GLASSFORGE v0.7.
 *
 * App icons are loaded away from the main thread and held in a small
 * process-memory LRU cache. Workspace placement, tap-to-launch, and the
 * existing long-press + tap move interaction remain unchanged.
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
    }

    LaunchedEffect(items) {
        if (items.isEmpty()) {
            store.placeDiscoveredIfEmpty(context.packageManager)
        }
    }

    val gridCells = (0 until WorkspaceRules.ROWS).flatMap { y ->
        (0 until WorkspaceRules.COLUMNS).map { x ->
            WorkspaceCell(page = 0, x = x, y = y)
        }
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
                                            message = if (ok) {
                                                "Moved"
                                            } else {
                                                "Move failed — kept old spot"
                                            }
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
                                    else -> Unit
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
                    when (item) {
                        is WorkspaceItem.AppShortcut -> WorkspaceAppShortcutContent(
                            item = item,
                            isSelected = isSelected,
                        )
                        is WorkspaceItem.Folder -> Text(
                            text = "📁 ${item.name}",
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(6.dp),
                        )
                        null -> Unit
                    }
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

@Composable
private fun WorkspaceAppShortcutContent(
    item: WorkspaceItem.AppShortcut,
    isSelected: Boolean,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = item.packageName,
        key2 = context.packageManager,
    ) {
        value = WorkspaceAppIconCache.load(context.packageManager, item.packageName)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 3.dp),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon!!,
                contentDescription = item.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(42.dp),
            )
        } else {
            // Stable space while the icon loads or if Android cannot provide it.
            Box(modifier = Modifier.size(42.dp))
        }
        Text(
            text = item.label,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, horizontal = 2.dp),
        )
    }
}

private object WorkspaceAppIconCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    suspend fun load(
        packageManager: PackageManager,
        packageName: String,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(packageName)?.let { return@withContext it }

        val bitmap = try {
            drawableToBitmap(packageManager.getApplicationIcon(packageName))
        } catch (_: Exception) {
            null
        }

        if (bitmap != null) {
            cache.put(packageName, bitmap)
        }
        bitmap
    }
}
