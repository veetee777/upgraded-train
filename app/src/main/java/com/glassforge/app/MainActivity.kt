package com.glassforge.app

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.glassforge.app.core.LocalFileStore
import com.glassforge.app.workspace.WorkspaceScreen
import com.glassforge.app.workspace.WorkspaceStore

private val Back = Color(0xFF080B12)
private val Panel = Color(0xFF141B28)
private val Cyan = Color(0xFF79E7FF)
private val Violet = Color(0xFF9B8CFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GlassforgeApp() }
    }
}

@Composable
fun GlassforgeApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val workspaceScope = rememberCoroutineScope()
    val workspaceStore = remember { WorkspaceStore(context, LocalFileStore(context), workspaceScope) }
    var selectedFont by remember { mutableStateOf(context.getSharedPreferences("glassforge_fonts", Context.MODE_PRIVATE).getString("selected_font", "").orEmpty()) }
    val fontFamily = remember(selectedFont) {
        val safeFontName = selectedFont.takeIf { it.matches(Regex("^font_[a-f0-9-]{36}\\.(ttf|otf)$")) }
        val fontFile = safeFontName?.let { File(File(context.filesDir, "fonts"), it) }
        if (fontFile != null && fontFile.isFile) runCatching { FontFamily(Font(fontFile, weight = FontWeight.Normal)) }.getOrDefault(FontFamily.Default) else FontFamily.Default
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Back, surface = Panel, primary = Cyan), typography = Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = fontFamily),
            displayMedium = displayMedium.copy(fontFamily = fontFamily),
            displaySmall = displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = titleLarge.copy(fontFamily = fontFamily),
            titleMedium = titleMedium.copy(fontFamily = fontFamily),
            titleSmall = titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = bodySmall.copy(fontFamily = fontFamily),
            labelLarge = labelLarge.copy(fontFamily = fontFamily),
            labelMedium = labelMedium.copy(fontFamily = fontFamily),
            labelSmall = labelSmall.copy(fontFamily = fontFamily)
        )
    }) {
        Scaffold(containerColor = Back, bottomBar = {
            NavigationBar(containerColor = Color(0xFF101621)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(AppIcons.Apps, null) }, label = { Text("Home") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(AppIcons.Tune, null) }, label = { Text("Studio") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(AppIcons.Bolt, null) }, label = { Text("Notch") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(AppIcons.MusicNote, null) }, label = { Text("Widgets") })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 }, icon = { Icon(AppIcons.TextFields, null) }, label = { Text("Fonts") })
            }
        }) { pad ->
            when (tab) {
                0 -> WorkspaceScreen(store = workspaceStore)
                1 -> StudioScreen(Modifier.padding(pad))
                2 -> NotchStudio(Modifier.padding(pad))
                3 -> WidgetsScreen(Modifier.padding(pad))
                else -> FontForgeScreen(Modifier.padding(pad), selectedFont) { selectedFont = it }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFF9AA8BC), fontSize = 13.sp)
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF202C3C), Panel, Color(0xFF101722))))
        .border(1.dp, Color(0x5579E7FF), RoundedCornerShape(22.dp))
        .padding(16.dp), content = content)
}


@Composable
private fun LauncherHomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pm = context.packageManager
    val apps by produceState(initialValue = emptyList<LaunchableApp>(), key1 = context) {
        value = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            runCatching {
                pm.queryIntentActivities(intent, 0).mapNotNull { info ->
                    runCatching {
                        val label = info.loadLabel(pm)?.toString() ?: return@runCatching null
                        LaunchableApp(label, info.activityInfo.packageName, drawableToBitmap(info.loadIcon(pm)))
                    }.getOrNull()
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    var query by remember { mutableStateOf("") }
    val favorites = remember { context.getSharedPreferences("glassforge_home", Context.MODE_PRIVATE) }
    var favoritePackages by remember { mutableStateOf(favorites.getStringSet("favorites", emptySet()).orEmpty()) }
    val filtered = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    LazyColumn(modifier.fillMaxSize()) {
        item { Header("GLASSFORGE", "Your home. Your layout. Your apps.") }
        item { GlassPanel {
            Text("APP LIBRARY", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B111B)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Search, null, tint = Cyan)
                Spacer(Modifier.width(8.dp))
                BasicTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Find an app…", color = Color(0xFF8290A4)); inner() })
            }
            Text("${filtered.size} apps", color = Color(0xFF9AA8BC), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        } }
        if (filtered.isEmpty()) item { Text(if (apps.isEmpty()) "Loading installed apps…" else "No matching apps.", color = Color.White, modifier = Modifier.padding(24.dp)) }
        items(filtered.size) { index ->
            val app = filtered[index]
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Panel).clickable {
                val launch = pm.getLaunchIntentForPackage(app.packageName)
                if (launch != null) runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                app.icon?.let { androidx.compose.foundation.Image(it, app.label, Modifier.size(42.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(app.packageName, color = Color(0xFF8290A4), fontSize = 9.sp, maxLines = 1)
                }
                Text(if (app.packageName in favoritePackages) "★" else "☆", color = Cyan, fontSize = 22.sp,
                    modifier = Modifier.clickable {
                        favoritePackages = if (app.packageName in favoritePackages) favoritePackages - app.packageName else favoritePackages + app.packageName
                        favorites.edit().putStringSet("favorites", favoritePackages).apply()
                    }.padding(8.dp))
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun StudioScreen(modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) {
        item { Header("GLASSFORGE", "Build your screen. Shape every detail.") }
        item {
            GlassPanel {
                Text("LIQUID GLASS ENGINE", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF5B6F9D), Color(0xFF17243A), Color(0xFF6C4D87)))), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(116.dp).height(116.dp).clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x2279E7FF), Color(0x667F8CFF))))
                        .border(2.dp, Color(0xCCFFFFFF), RoundedCornerShape(30.dp)))
                }
                Text("Material preview • adjustable glass controls", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        item { GlassPanel { Text("MATERIAL PRESETS", color = Cyan, fontWeight = FontWeight.Bold); listOf("Liquid Crystal", "Iridescent Prism", "Hyperreal Glass", "Aurora Glass", "Clear Optical Glass").forEach { Text("◇  $it", color = Color.White, modifier = Modifier.padding(top = 12.dp)) } } }
        item { GlassPanel { Text("CREATION TOOLS", color = Cyan, fontWeight = FontWeight.Bold); Text("Icon Forge  •  Widget Workshop  •  Theme Builder  •  Screen Layout", color = Color.White, modifier = Modifier.padding(top = 12.dp)) } }
    }
}

@Composable
private fun NotchStudio(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notchPrefs = remember { context.getSharedPreferences("glassforge_notch", Context.MODE_PRIVATE) }
    var width by remember { mutableFloatStateOf(notchPrefs.getFloat("width", 0.62f)) }
    var length by remember { mutableFloatStateOf(notchPrefs.getFloat("length", 0.48f)) }
    var height by remember { mutableFloatStateOf(notchPrefs.getFloat("height", 0.42f)) }
    var radius by remember { mutableFloatStateOf(notchPrefs.getFloat("radius", 0.9f)) }
    var border by remember { mutableFloatStateOf(notchPrefs.getFloat("border", 0.65f)) }
    var glass by remember { mutableFloatStateOf(notchPrefs.getFloat("glass", 0.8f)) }
    var glow by remember { mutableFloatStateOf(notchPrefs.getFloat("glow", 0.55f)) }
    var position by remember { mutableFloatStateOf(notchPrefs.getFloat("position", 0.12f)) }
    var savedNotch by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(notchPrefs.getBoolean("music", true)) }
    var showFlash by remember { mutableStateOf(notchPrefs.getBoolean("flash", true)) }
    var showCharge by remember { mutableStateOf(notchPrefs.getBoolean("charge", true)) }
    var showNotifications by remember { mutableStateOf(notchPrefs.getBoolean("notifications", true)) }
    var showTimer by remember { mutableStateOf(notchPrefs.getBoolean("timer", true)) }
    var showVisualizer by remember { mutableStateOf(notchPrefs.getBoolean("visualizer", true)) }
    var style by remember { mutableStateOf(notchPrefs.getString("style", "Liquid Prism") ?: "Liquid Prism") }
    LazyColumn(modifier.fillMaxSize()) {
        item { Header("NOTCH STUDIO", "Design every dimension and interaction.") }
        item {
            GlassPanel {
                Text("LIVE PREVIEW", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().height(190.dp).padding(top = 12.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF05070B)), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.padding(top = (8 + position * 90).dp).fillMaxWidth(width.coerceIn(.25f, .94f)).height((30 + height * 48).dp)
                        .clip(RoundedCornerShape((12 + radius * 22).dp))
                        .background(Brush.linearGradient(listOf(Color(0xCCFFFFFF).copy(alpha = glass * .55f), Color(0xAA526B89).copy(alpha = glass), Color(0xCC202B40))))
                        .border((border * 2.5f).dp, Brush.horizontalGradient(listOf(Cyan.copy(alpha = glow), Violet.copy(alpha = glow), Color.White.copy(alpha = glow))), RoundedCornerShape((12 + radius * 22).dp)), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (showMusic) Icon(AppIcons.MusicNote, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            if (showFlash) Icon(AppIcons.Bolt, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            if (showNotifications) Icon(AppIcons.Notifications, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            if (showCharge) Text("⚡", color = Color.White)
                        }
                    }
                    Text("Preview only — system overlay requires permission", color = Color(0xFF9AA8BC), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
                }
            }
        }
        item { GlassPanel {
            Text("SHAPE & DIMENSIONS", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            SettingSlider("Width", width) { width = it; savedNotch = false }
            SettingSlider("Length", length) { length = it; savedNotch = false }
            SettingSlider("Height", height) { height = it; savedNotch = false }
            SettingSlider("Corner roundness", radius) { radius = it; savedNotch = false }
            SettingSlider("Vertical position", position) { position = it; savedNotch = false }
        } }
        item { GlassPanel {
            Text("EDGE & MATERIAL", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            SettingSlider("Border thickness", border) { border = it; savedNotch = false }
            SettingSlider("Glass transparency", glass) { glass = it; savedNotch = false }
            SettingSlider("Edge glow intensity", glow) { glow = it; savedNotch = false }
            Text("Style", color = Color.White, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Liquid Prism", "Optical", "Aurora").forEach { FilterChip(selected = style == it, onClick = { style = it; savedNotch = false }, label = { Text(it, fontSize = 11.sp) }) } }
        } }
        item { GlassPanel {
            Text("FEATURE MODULES", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            FeatureToggle("Music controls & album art", showMusic) { showMusic = it }
            FeatureToggle("Flashlight shortcut", showFlash) { showFlash = it }
            FeatureToggle("Charging symbol & animation", showCharge) { showCharge = it }
            FeatureToggle("Expandable notifications", showNotifications) { showNotifications = it }
            FeatureToggle("Timers & call indicators", showTimer) { showTimer = it }
            FeatureToggle("Audio visualizer", showVisualizer) { showVisualizer = it }
        } }
        item { GlassPanel {
            Text("BEHAVIOR & SETTINGS", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Tap actions • long-press actions • auto-hide • animation speed • app-specific rules • saved presets", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Text("Hardware flashlight and live music playback need Android permissions and service integration. The editor controls and preview are included in this foundation.", color = Color(0xFF9AA8BC), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            Button(onClick = {
                notchPrefs.edit()
                    .putFloat("width", width).putFloat("length", length).putFloat("height", height)
                    .putFloat("radius", radius).putFloat("border", border).putFloat("glass", glass)
                    .putFloat("glow", glow).putFloat("position", position).putString("style", style)
                    .putBoolean("music", showMusic).putBoolean("flash", showFlash)
                    .putBoolean("charge", showCharge).putBoolean("notifications", showNotifications)
                    .putBoolean("timer", showTimer).putBoolean("visualizer", showVisualizer).apply()
                savedNotch = true
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245C72))) {
                Icon(AppIcons.Save, null); Spacer(Modifier.width(8.dp)); Text(if (savedNotch) "PRESET SAVED ON DEVICE" else "SAVE NOTCH PRESET")
            }
        } }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text("${(value * 100).toInt()}%", color = Cyan, fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onChange, colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
    }
}

@Composable
private fun FeatureToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private data class LaunchableApp(val label: String, val packageName: String, val icon: androidx.compose.ui.graphics.ImageBitmap?)

@Composable
private fun WidgetsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pm = context.packageManager
    // Package discovery and icon rasterization happen off the UI thread. The resulting
    // bitmaps are held in Compose state, so recomposition never reloads installed apps.
    val apps by produceState(initialValue = emptyList<LaunchableApp>(), key1 = context) {
        value = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            runCatching {
                pm.queryIntentActivities(intent, 0)
                    .mapNotNull { info ->
                        runCatching {
                            val label = info.loadLabel(pm)?.toString() ?: return@runCatching null
                            val icon = runCatching { drawableToBitmap(info.loadIcon(pm)) }.getOrNull()
                            LaunchableApp(label, info.activityInfo.packageName, icon)
                        }.getOrNull()
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    val widgetPrefs = remember { context.getSharedPreferences("glassforge_widgets", Context.MODE_PRIVATE) }
    val restoredConfig = remember {
        runCatching { org.json.JSONObject(widgetPrefs.getString("widget_config", "{}") ?: "{}") }
            .getOrDefault(org.json.JSONObject())
    }
    var query by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var selectedButtons by remember {
        mutableStateOf(
            restoredConfig.optJSONArray("buttons")?.let { array ->
                (0 until array.length()).map { array.optString(it) }
                    .filter { it in setOf("Open app", "Music", "Camera", "Flashlight", "Wi-Fi settings", "Bluetooth settings", "Share", "Timer", "Voice", "Calculator", "Calendar", "Custom shortcut") }
                    .toSet()
            }?.takeIf { it.isNotEmpty() } ?: setOf("Open app", "Music", "Camera")
        )
    }
    var shape by remember { mutableStateOf(restoredConfig.optString("shape", "Squircle").takeIf { it in setOf("Squircle", "Rounded", "Pill", "Square", "Circle") } ?: "Squircle") }
    var width by remember { mutableFloatStateOf(restoredConfig.optDouble("width", .82).toFloat().takeIf { it.isFinite() }?.coerceIn(.25f, 1f) ?: .82f) }
    var height by remember { mutableFloatStateOf(restoredConfig.optDouble("height", .62).toFloat().takeIf { it.isFinite() }?.coerceIn(.25f, 1f) ?: .62f) }
    var radius by remember { mutableFloatStateOf(restoredConfig.optDouble("radius", .72).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .72f) }
    var border by remember { mutableFloatStateOf(restoredConfig.optDouble("border", .55).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .55f) }
    var glass by remember { mutableFloatStateOf(restoredConfig.optDouble("glass", .78).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .78f) }
    var columns by remember { mutableIntStateOf(restoredConfig.optInt("columns", 2).coerceIn(1, 4)) }
    var saved by remember { mutableStateOf(widgetPrefs.contains("widget_config")) }
    val buttonChoices = listOf("Open app", "Music", "Camera", "Flashlight", "Wi-Fi settings", "Bluetooth settings", "Share", "Timer", "Voice", "Calculator", "Calendar", "Custom shortcut")
    LaunchedEffect(apps) {
        if (selectedApp == null) {
            val savedPackage = restoredConfig.optString("package")
            selectedApp = apps.firstOrNull { it.packageName == savedPackage }
        }
    }
    val filtered = apps.asSequence().filter { it.label.contains(query, true) || it.packageName.contains(query, true) }.take(24).toList()

    LazyColumn(modifier.fillMaxSize()) {
        item { Header("WIDGET WORKSHOP", "Build a widget around any installed app.") }
        item { GlassPanel {
            Text("1 · CHOOSE AN APP", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B111B)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Search, null, tint = Cyan)
                Spacer(Modifier.width(8.dp))
                BasicTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp), decorationBox = { inner -> if (query.isEmpty()) Text("Search all installed apps…", color = Color(0xFF8290A4), fontSize = 14.sp); inner() })
            }
            selectedApp?.let { app ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x223FE0C0)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    app.icon?.let { androidx.compose.foundation.Image(it, null, Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold); Text("Selected widget app", color = Cyan, fontSize = 11.sp) }
                    Text("CHANGE", color = Cyan, fontSize = 11.sp, modifier = Modifier.clickable { selectedApp = null })
                }
            }
            if (selectedApp == null) {
                Text("${apps.size} launchable apps found", color = Color(0xFF9AA8BC), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                filtered.forEach { app ->
                    Row(Modifier.fillMaxWidth().clickable { selectedApp = app; saved = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        app.icon?.let { androidx.compose.foundation.Image(bitmap = it, contentDescription = null, modifier = Modifier.size(30.dp)) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(app.label, color = Color.White, fontSize = 14.sp); Text(app.packageName, color = Color(0xFF8290A4), fontSize = 10.sp) }
                        Text("SELECT", color = Cyan, fontSize = 10.sp)
                    }
                }
            }
        } }
        item { GlassPanel {
            Text("2 · QUICK BUTTONS", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose the controls that appear inside your widget.", color = Color(0xFF9AA8BC), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
            buttonChoices.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { option ->
                        FilterChip(selected = option in selectedButtons, onClick = { selectedButtons = if (option in selectedButtons) selectedButtons - option else selectedButtons + option; saved = false }, label = { Text(option, fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text("${selectedButtons.size} controls selected", color = Cyan, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        } }
        item { GlassPanel {
            Text("3 · SHAPE & LAYOUT", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) { listOf("Squircle", "Rounded", "Pill", "Square", "Circle").forEach { FilterChip(selected = shape == it, onClick = { shape = it; saved = false }, label = { Text(it, fontSize = 10.sp) }) } }
            SettingSlider("Widget width", width) { width = it; saved = false }
            SettingSlider("Widget height", height) { height = it; saved = false }
            SettingSlider("Corner curvature", radius) { radius = it; saved = false }
            SettingSlider("Border thickness", border) { border = it; saved = false }
            SettingSlider("Glass transparency", glass) { glass = it; saved = false }
            Text("Grid columns", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row { listOf(1, 2, 3, 4).forEach { n -> FilterChip(selected = columns == n, onClick = { columns = n; saved = false }, label = { Text("$n") }, modifier = Modifier.padding(end = 5.dp)) } }
        } }
        item { GlassPanel {
            Text("4 · LIVE DESIGN PREVIEW", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            val corner = when (shape) { "Pill" -> 100.dp; "Square" -> 5.dp; "Circle" -> 100.dp; "Rounded" -> 18.dp; else -> (10 + radius * 28).dp }
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(170.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF293D5B), Color(0xFF111827), Color(0xFF51406D)))), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth(width).height((80 + height * 95).dp).clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(Color.White.copy(alpha = glass * .30f), Color(0xFF83B9D8).copy(alpha = glass * .20f), Color(0xFF171D30).copy(alpha = .9f)))).border((border * 2).dp, Brush.horizontalGradient(listOf(Cyan, Violet, Color.White.copy(alpha = .7f))), RoundedCornerShape(corner)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(selectedApp?.label ?: "Choose an app", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    selectedButtons.toList().take(columns * 2).chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { Text(it, color = Color.White, fontSize = 9.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x5579E7FF)).padding(horizontal = 7.dp, vertical = 5.dp)) } } }
                }
            }
            Button(onClick = {
                val prefs = widgetPrefs
                // Use a structured record rather than delimiter-separated user/app data.
                // Keep field names and types explicit so labels cannot corrupt the record.
                val config = org.json.JSONObject().apply {
                    put("version", 1)
                    put("package", selectedApp?.packageName.orEmpty())
                    put("label", selectedApp?.label.orEmpty().take(80))
                    put("buttons", org.json.JSONArray(buttonChoices.filter { it in selectedButtons }))
                    put("shape", shape)
                    put("width", width.toDouble())
                    put("height", height.toDouble())
                    put("radius", radius.toDouble())
                    put("border", border.toDouble())
                    put("glass", glass.toDouble())
                    put("columns", columns)
                }
                prefs.edit().putString("widget_config", config.toString()).apply()
                saved = true
                GlassforgeWidgetProvider.refresh(context)
            }, enabled = selectedApp != null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245C72))) { Icon(AppIcons.Save, null); Spacer(Modifier.width(8.dp)); Text(if (saved) "DESIGN SAVED" else "SAVE WIDGET DESIGN") }
            Text("Save this design, then add GLASSFORGE Widget from your launcher's widget picker. The saved widget applies its selected shape and glass opacity and supports up to four quick actions. Exact size is controlled with your launcher's resize handles; curvature, border, and grid remain editor-preview controls.", color = Color(0xFF9AA8BC), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        } }
        item { GlassPanel { Text("ICON FORGE", color = Cyan, fontWeight = FontWeight.Bold); Text("Custom shapes • gradients • glass materials • borders • shadows • icon packs", color = Color.White, modifier = Modifier.padding(top = 12.dp)) } }
        item { Spacer(Modifier.height(18.dp)) }
    }
}


@Composable
private fun FontForgeScreen(modifier: Modifier = Modifier, selectedFont: String, onSelectFont: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontDir = remember { File(context.filesDir, "fonts").apply { mkdirs() } }
    var fontFiles by remember { mutableStateOf(emptyList<File>()) }
    var message by remember { mutableStateOf("Import TTF or OTF fonts. Files stay inside GLASSFORGE's private app storage.") }
    var importing by remember { mutableStateOf(false) }
    var deletingFont by remember { mutableStateOf<String?>(null) }

    suspend fun refreshFonts() {
        fontFiles = withContext(Dispatchers.IO) {
            fontDir.listFiles()?.filter {
                it.isFile && it.extension.lowercase() in setOf("ttf", "otf") && it.name.matches(Regex("^font_[a-f0-9-]{36}\\.(ttf|otf)$"))
            }?.sortedBy { it.name.lowercase() }.orEmpty()
        }
    }

    LaunchedEffect(fontDir) {
        fontFiles = withContext(Dispatchers.IO) {
            fontDir.listFiles()?.filter {
                it.isFile && it.extension.lowercase() in setOf("ttf", "otf") && it.name.matches(Regex("^font_[a-f0-9-]{36}\\.(ttf|otf)$"))
            }?.sortedBy { it.name.lowercase() }.orEmpty()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !importing) {
            importing = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val resolver = context.contentResolver
                        val mime = resolver.getType(uri).orEmpty().lowercase()
                        val displayName = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }.orEmpty()
                        val ext = displayName.substringAfterLast('.', "").lowercase()
                        require(ext in setOf("ttf", "otf") || mime in setOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype")) {
                            "Choose a .ttf or .otf font file."
                        }
                        val existingFonts = fontDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }.orEmpty()
                        require(existingFonts.size < 25) { "Font Forge currently supports up to 25 imported fonts." }
                        require(existingFonts.sumOf { it.length() } < 100L * 1024L * 1024L) { "Your font library has reached its 100 MB storage limit." }
                        val targetExt = if (ext in setOf("ttf", "otf")) ext else if (mime.contains("opentype")) "otf" else "ttf"
                        val target = File(fontDir, "font_${UUID.randomUUID()}.$targetExt")
                        val temporary = File(fontDir, ".${target.name}.part")
                        try {
                            var total = 0L
                            resolver.openInputStream(uri).use { input ->
                                requireNotNull(input) { "Could not read the selected file." }
                                FileOutputStream(temporary).use { output ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        require(total <= 15L * 1024L * 1024L) { "Font files must be 15 MB or smaller." }
                                        output.write(buffer, 0, count)
                                    }
                                    output.fd.sync()
                                }
                            }
                            require(total > 0L) { "The selected font file is empty." }
                            Typeface.createFromFile(temporary)
                            require(temporary.renameTo(target)) { "Could not safely save the font." }
                            target
                        } catch (error: Throwable) {
                            temporary.delete()
                            target.delete()
                            if (error is IllegalArgumentException) throw error
                            throw IllegalArgumentException("That file could not be imported as a valid Android font.")
                        }
                    }
                }
                result.onSuccess { file ->
                    refreshFonts()
                    onSelectFont(file.name)
                    context.getSharedPreferences("glassforge_fonts", Context.MODE_PRIVATE).edit().putString("selected_font", file.name).apply()
                    message = "Font imported and applied across the GLASSFORGE interface."
                }.onFailure { error -> message = error.message ?: "Font import failed." }
                importing = false
            }
        }
    }

    LazyColumn(modifier.fillMaxSize()) {
        item { Header("FONT FORGE", "Bring your own type. Shape the entire GLASSFORGE experience.") }
        item { GlassPanel {
            Text("YOUR TYPOGRAPHY LIBRARY", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(message, color = Color(0xFF9AA8BC), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = { picker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*")) }, enabled = !importing && deletingFont == null, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245C72))) {
                Text(if (importing) "IMPORTING…" else "IMPORT TTF / OTF FONT")
            }
            Text("Only font files are accepted • 15 MB maximum • Stored privately on this device", color = Color(0xFF8290A4), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        } }
        item { GlassPanel {
            Text("LIVE TYPE PREVIEW", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("GLASSFORGE", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text("The quick brown fox jumps over the lazy dog.", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            Text("0123456789  •  Aa Bb Cc  •  Symbols & punctuation", color = Color(0xFF9AA8BC), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        } }
        item { GlassPanel {
            Text("INSTALLED FONTS (${fontFiles.size})", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable {
                onSelectFont("")
                context.getSharedPreferences("glassforge_fonts", Context.MODE_PRIVATE).edit().remove("selected_font").apply()
                message = "System default typography is active."
            }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("System default", color = Color.White)
                if (selectedFont.isBlank()) Text("ACTIVE", color = Cyan, fontSize = 10.sp)
            }
            fontFiles.forEach { file ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable {
                        onSelectFont(file.name)
                        context.getSharedPreferences("glassforge_fonts", Context.MODE_PRIVATE).edit().putString("selected_font", file.name).apply()
                        message = "${file.name} applied across the GLASSFORGE interface."
                    }) {
                        Text(file.name.removePrefix("font_").take(36), color = Color.White, fontSize = 13.sp)
                        Text("${(file.length() / 1024).coerceAtLeast(1)} KB • ${file.extension.uppercase()}", color = Color(0xFF8290A4), fontSize = 10.sp)
                    }
                    if (selectedFont == file.name) Text("ACTIVE", color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(end = 10.dp))
                    TextButton(enabled = deletingFont == null && !importing, onClick = {
                        deletingFont = file.name
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) { file.delete() }
                            if (deleted) {
                                if (selectedFont == file.name) {
                                    onSelectFont("")
                                    context.getSharedPreferences("glassforge_fonts", Context.MODE_PRIVATE).edit().remove("selected_font").apply()
                                }
                                refreshFonts()
                                message = "Font deleted."
                            } else {
                                message = "Could not delete this font."
                            }
                            deletingFont = null
                        }
                    }) { Text(if (deletingFont == file.name) "…" else "DELETE", color = Color(0xFFFF8D9A), fontSize = 10.sp) }
                }
            }
            if (fontFiles.isEmpty()) Text("No custom fonts yet. Import your first font above.", color = Color(0xFF9AA8BC), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        } }
        item { GlassPanel {
            Text("COMPATIBILITY", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Custom fonts apply to GLASSFORGE screens. Android does not allow an ordinary app to replace fonts inside unrelated apps or the entire system. Home-screen widgets use Android RemoteViews and may retain system widget typography.", color = Color(0xFF9AA8BC), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        } }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): androidx.compose.ui.graphics.ImageBitmap {
    val width = 96
    val height = 96
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
