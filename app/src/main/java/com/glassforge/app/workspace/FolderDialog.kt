package com.glassforge.app.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Phase 3 folder view — GLASSFORGE v0.7
 * New code, same visual language as WorkspaceScreen (Panel cells,
 * Cyan accents, 48dp icons, 10sp labels).
 *
 * The folder stores member ids only, so [members] are the resolved,
 * still-installed apps (label/package/class from PackageManager) and
 * [unresolvedIds] are ids whose app is gone. Unresolved members
 * still render — greyed, "Not installed" — so the user can
 * long-press them away instead of them silently vanishing.
 *
 * Gestures mirror the grid: tap launches (or says frozen),
 * the ❄ button on a tile freezes/thaws it, long-press takes the
 * app back out to the Home grid. Dismiss by tapping outside or the ✕.
 */
@Composable
fun FolderDialog(
    folder: WorkspaceItem.Folder,
    members: List<WorkspaceItem.AppShortcut>,
    unresolvedIds: List<String>,
    iconCache: Map<String, ImageBitmap>,
    frostedApps: Set<String>,
    message: String,
    // P2-25: true while the members are still resolving — the dialog
    // shows a loading state instead of an empty grid that fills in.
    loading: Boolean = false,
    onMemberTap: (WorkspaceItem.AppShortcut) -> Unit,
    onMemberFreezeToggle: (WorkspaceItem.AppShortcut) -> Unit,
    onRemoveMember: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141B28))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folder.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${folder.memberIds.size} apps",
                    color = Color(0xFF9AA8BC),
                    fontSize = 11.sp,
                )
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            // P2-21: fixed-height slot — the message appearing/vanishing
            // no longer shoves the grid up and down.
            Box(
                modifier = Modifier.fillMaxWidth().height(24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (message.isNotEmpty()) {
                    Text(
                        text = message,
                        color = Color(0xFF79E7FF),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (loading) {
                Text(
                    text = "Loading…",
                    color = Color(0xFF9AA8BC),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(top = 12.dp),
                ) {
                    items(members, key = { it.id }) { member ->
                        FolderMemberTile(
                            key = member.id,
                            label = member.label,
                            packageName = member.packageName,
                            icon = iconCache[member.packageName],
                            isFrosted = frostedApps.contains(member.packageName),
                            isUnresolved = false,
                            onTap = { onMemberTap(member) },
                            onFreezeToggle = { onMemberFreezeToggle(member) },
                            onLongPress = { onRemoveMember(member.id) },
                        )
                    }
                    items(unresolvedIds, key = { "dead:$it" }) { id ->
                        FolderMemberTile(
                            key = id,
                            label = packageNameFromItemId(id) ?: id,
                            packageName = packageNameFromItemId(id),
                            icon = null,
                            isFrosted = false,
                            isUnresolved = true,
                            onTap = { onShowMessage("Not installed — long-press to remove") },
                            onLongPress = { onRemoveMember(id) },
                        )
                    }
                }
            }
            Text(
                text = "Tap to launch · ❄ to freeze · Long-press to take out",
                color = Color(0xFF9AA8BC),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun FolderMemberTile(
    key: String,
    label: String,
    packageName: String?,
    icon: ImageBitmap?,
    isFrosted: Boolean,
    isUnresolved: Boolean,
    onTap: () -> Unit,
    onFreezeToggle: () -> Unit = {},
    onLongPress: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B111B))
            .pointerInput(key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(6.dp),
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = label,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF202C3C)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isUnresolved) "?" else label.take(1).uppercase(),
                        color = Color(0xFF79E7FF),
                        fontSize = 20.sp,
                    )
                }
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (isUnresolved) {
                Text(
                    text = "Not installed",
                    color = Color(0xFF9AA8BC),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Frost Lock: the same white glaze as the grid.
        if (isFrosted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.5f)),
            )
        }
        // Frost Lock: the ❄ toggle — tap to freeze/thaw this member.
        // Shown only for installed apps; long-press is already taken
        // (remove from folder), so freeze needs its own button. Drawn
        // after the glaze so it stays visible and tappable above it.
        if (!isUnresolved) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141B28))
                    .clickable { onFreezeToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isFrosted) "☀" else "❄",
                    color = Color(0xFF79E7FF),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
