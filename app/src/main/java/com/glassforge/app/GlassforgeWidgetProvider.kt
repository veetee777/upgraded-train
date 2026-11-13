package com.glassforge.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews

/** A real Android home-screen widget backed by the latest saved Widget Workshop design. */
class GlassforgeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_REFRESH == intent.action) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, GlassforgeWidgetProvider::class.java))
                .forEach { updateOne(context, manager, it) }
        }
    }

    private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
        val prefs = context.getSharedPreferences("glassforge_widgets", Context.MODE_PRIVATE)
        val rawConfig = prefs.getString("widget_config", null).orEmpty().take(MAX_CONFIG_CHARS)
        // Treat local preferences as untrusted input. New saves use JSON. The legacy
        // delimiter reader preserves existing v0.7 designs and is removed after migration.
        val config = parseConfig(rawConfig)
        val rawPackage = config.optString("package")
        val packageName = rawPackage.takeIf { PACKAGE_NAME.matches(it) }.orEmpty()
        val label = config.optString("label", "GLASSFORGE").take(40)
            .filter { !it.isISOControl() }.ifBlank { "GLASSFORGE" }
        val buttons = config.optJSONArray("buttons")?.let { array ->
            (0 until minOf(array.length(), 12)).mapNotNull { index ->
                array.optString(index).takeIf { it in ALLOWED_ACTIONS }
            }.distinct().take(4)
        }.orEmpty()
        val shape = config.optString("shape", "Squircle").takeIf { it in ALLOWED_SHAPES } ?: "Squircle"
        val glass = config.optDouble("glass", .78).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .78f
        val views = RemoteViews(context.packageName, R.layout.glassforge_widget)
        views.setTextViewText(R.id.widget_title, label)
        views.setTextViewText(R.id.widget_subtitle, "${shape.uppercase()}  •  GLASSFORGE")
        // Use a real RemoteViews-compatible drawable so the saved shape is visible
        // on the Android home screen, not just in the in-app preview.
        val shapeBackground = when (shape) {
            "Rounded" -> R.drawable.widget_shape_rounded
            "Pill" -> R.drawable.widget_shape_pill
            "Square" -> R.drawable.widget_shape_square
            "Circle" -> R.drawable.widget_shape_circle
            else -> R.drawable.widget_shape_squircle
        }
        views.setInt(R.id.widget_root, "setBackgroundResource", shapeBackground)
        // Keep the glass control visible on-device; this subtly fades the full
        // widget while preserving its selected rounded drawable.
        views.setFloat(R.id.widget_root, "setAlpha", 0.55f + glass * 0.45f)

        val slots = listOf(R.id.action_1, R.id.action_2, R.id.action_3, R.id.action_4)
        slots.forEachIndexed { index, viewId ->
            val action = buttons.getOrNull(index)
            if (action == null) {
                views.setViewVisibility(viewId, View.GONE)
            } else {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, action)
                val clickIntent = actionIntent(context, action, packageName)
                val pending = PendingIntent.getActivity(
                    context, id * 20 + index, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(viewId, pending)
            }
        }
        val launch = packageLaunchIntent(context, packageName) ?: Intent(context, MainActivity::class.java)
        val open = PendingIntent.getActivity(context, id, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_open, open)
        manager.updateAppWidget(id, views)
    }

    private fun packageLaunchIntent(context: Context, packageName: String): Intent? {
        if (!PACKAGE_NAME.matches(packageName)) return null
        return runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
    }

    private fun actionIntent(context: Context, action: String, packageName: String): Intent {
        val intent = when (action) {
            "Camera" -> Intent("android.media.action.IMAGE_CAPTURE")
            "Wi-Fi settings" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "Bluetooth settings" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "Timer" -> Intent(AlarmClock.ACTION_SET_TIMER)
            "Calendar" -> Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            "Share" -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Shared from GLASSFORGE")
            "Voice" -> Intent("android.speech.action.RECOGNIZE_SPEECH")
            "Calculator" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            "Music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            // Android has no public, permission-free flashlight launch action. Keep
            // this button safe: open GLASSFORGE rather than requesting CAMERA access.
            "Flashlight" -> Intent(context, MainActivity::class.java)
            "Custom shortcut", "Open app" -> packageLaunchIntent(context, packageName)
            else -> packageLaunchIntent(context, packageName)
        }
        val fallback = Intent(context, MainActivity::class.java)
        return intent?.takeIf { candidate ->
            // Resolve implicit actions before creating a PendingIntent so a missing
            // handler cannot leave a dead widget button on the home screen.
            candidate.component != null || candidate.resolveActivity(context.packageManager) != null
        } ?: fallback
    }

    private fun parseConfig(raw: String): org.json.JSONObject {
        if (raw.isBlank()) return org.json.JSONObject()
        runCatching { org.json.JSONObject(raw) }.getOrNull()?.let { return it }
        // Backward-compatible reader for the original v0.7 pipe-delimited format.
        val fields = raw.split('|')
        return org.json.JSONObject().apply {
            put("package", fields.getOrNull(0).orEmpty())
            put("label", fields.getOrNull(1).orEmpty())
            put("buttons", org.json.JSONArray(fields.getOrNull(2).orEmpty().split(',').filter { it.isNotBlank() }))
            put("shape", fields.getOrNull(3).orEmpty())
            put("glass", fields.getOrNull(8)?.toDoubleOrNull() ?: .78)
        }
    }

    companion object {
        private const val MAX_CONFIG_CHARS = 4096
        const val ACTION_REFRESH = "com.glassforge.app.REFRESH_WIDGETS"
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        private val ALLOWED_ACTIONS = setOf(
            "Camera", "Wi-Fi settings", "Bluetooth settings", "Timer", "Calendar",
            "Share", "Voice", "Calculator", "Custom shortcut", "Open app", "Music", "Flashlight"
        )
        private val ALLOWED_SHAPES = setOf("Squircle", "Rounded", "Pill", "Square", "Circle")
        fun refresh(context: Context) {
            context.sendBroadcast(Intent(context, GlassforgeWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }
    }
}
