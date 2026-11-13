package com.glassforge.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Compact, standalone vector icon definitions providing exact rounded glyphs
 * for GLASSFORGE navigation and action controls without requiring the 50 MB
 * material-icons-extended dependency.
 */
object AppIcons {
    val Search = Icons.Rounded.Search
    val Notifications = Icons.Rounded.Notifications

    private var _apps: ImageVector? = null
    val Apps: ImageVector
        get() {
            if (_apps != null) return _apps!!
            _apps = ImageVector.Builder(
                name = "Rounded.Apps",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).path(fill = SolidColor(Color.White)) {
                moveTo(4.0f, 8.0f)
                horizontalLineToRelative(4.0f)
                lineTo(8.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(10.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(4.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                lineTo(4.0f, 16.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(4.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                lineTo(4.0f, 10.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(10.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(16.0f, 4.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(4.0f)
                lineTo(20.0f, 4.0f)
                horizontalLineToRelative(-4.0f)
                close()
                moveTo(10.0f, 8.0f)
                horizontalLineToRelative(4.0f)
                lineTo(14.0f, 4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(16.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(16.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
            }.build()
            return _apps!!
        }

    private var _bolt: ImageVector? = null
    val Bolt: ImageVector
        get() {
            if (_bolt != null) return _bolt!!
            _bolt = ImageVector.Builder(
                name = "Rounded.Bolt",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).path(fill = SolidColor(Color.White)) {
                moveTo(10.67f, 21.0f)
                lineTo(10.67f, 21.0f)
                curveToRelative(-0.35f, 0f, -0.62f, -0.31f, -0.57f, -0.66f)
                lineTo(11.0f, 14.0f)
                horizontalLineTo(7.5f)
                curveToRelative(-0.88f, 0f, -0.33f, -0.75f, -0.31f, -0.78f)
                curveToRelative(1.26f, -2.23f, 3.15f, -5.53f, 5.65f, -9.93f)
                curveToRelative(0.1f, -0.18f, 0.3f, -0.29f, 0.5f, -0.29f)
                horizontalLineToRelative(0f)
                curveToRelative(0.35f, 0f, 0.62f, 0.31f, 0.57f, 0.66f)
                lineTo(13.01f, 10.0f)
                horizontalLineToRelative(3.51f)
                curveToRelative(0.4f, 0f, 0.62f, 0.19f, 0.4f, 0.66f)
                curveToRelative(-3.29f, 5.74f, -5.2f, 9.09f, -5.75f, 10.05f)
                curveTo(11.07f, 20.89f, 10.88f, 21.0f, 10.67f, 21.0f)
                close()
            }.build()
            return _bolt!!
        }

    private var _musicNote: ImageVector? = null
    val MusicNote: ImageVector
        get() {
            if (_musicNote != null) return _musicNote!!
            _musicNote = ImageVector.Builder(
                name = "Rounded.MusicNote",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 5.0f)
                verticalLineToRelative(8.55f)
                curveToRelative(-0.94f, -0.54f, -2.1f, -0.75f, -3.33f, -0.32f)
                curveToRelative(-1.34f, 0.48f, -2.37f, 1.67f, -2.61f, 3.07f)
                curveToRelative(-0.46f, 2.74f, 1.86f, 5.08f, 4.59f, 4.65f)
                curveToRelative(1.96f, -0.31f, 3.35f, -2.11f, 3.35f, -4.1f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(2f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-1.1f, 0f, -2.0f, 0.9f, -2.0f, 2f)
                close()
            }.build()
            return _musicNote!!
        }

    private var _tune: ImageVector? = null
    val Tune: ImageVector
        get() {
            if (_tune != null) return _tune!!
            _tune = ImageVector.Builder(
                name = "Rounded.Tune",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).path(fill = SolidColor(Color.White)) {
                moveTo(3.0f, 18.0f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(-2.0f)
                lineTo(4.0f, 17.0f)
                curveToRelative(-0.55f, 0f, -1.0f, 0.45f, -1.0f, 1f)
                close()
                moveTo(3.0f, 6.0f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(9.0f)
                lineTo(13.0f, 5.0f)
                lineTo(4.0f, 5.0f)
                curveToRelative(-0.55f, 0f, -1.0f, 0.45f, -1.0f, 1f)
                close()
                moveTo(13.0f, 20.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1f)
                verticalLineToRelative(4.0f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1.0f)
                close()
                moveTo(7.0f, 10.0f)
                verticalLineToRelative(1f)
                lineTo(4.0f, 11.0f)
                curveToRelative(-0.55f, 0f, -1.0f, 0.45f, -1.0f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(1f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1f)
                close()
                moveTo(21.0f, 12.0f)
                curveToRelative(0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-9.0f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(9.0f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1.0f)
                close()
                moveTo(16.0f, 9.0f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1.0f)
                lineTo(17.0f, 7.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-3.0f)
                lineTo(17.0f, 4.0f)
                curveToRelative(0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1f)
                verticalLineToRelative(4.0f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                close()
            }.build()
            return _tune!!
        }

    private var _textFields: ImageVector? = null
    val TextFields: ImageVector
        get() {
            if (_textFields != null) return _textFields!!
            _textFields = ImageVector.Builder(
                name = "Rounded.TextFields",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).path(fill = SolidColor(Color.White)) {
                moveTo(2.5f, 5.5f)
                curveTo(2.5f, 6.33f, 3.17f, 7.0f, 4.0f, 7.0f)
                horizontalLineToRelative(3.5f)
                verticalLineToRelative(10.5f)
                curveToRelative(0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f)
                verticalLineTo(7.0f)
                horizontalLineTo(14.0f)
                curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveTo(14.83f, 4.0f, 14.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f)
                close()
                moveTo(20.0f, 9.0f)
                horizontalLineToRelative(-6.0f)
                curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f)
                reflectiveCurveTo(13.17f, 12.0f, 14.0f, 12.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(5.5f)
                curveToRelative(0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f)
                verticalLineTo(12.0f)
                horizontalLineTo(20.0f)
                curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveTo(20.83f, 9.0f, 20.0f, 9.0f)
                close()
            }.build()
            return _textFields!!
        }

    private var _save: ImageVector? = null
    val Save: ImageVector
        get() {
            if (_save != null) return _save!!
            _save = ImageVector.Builder(
                name = "Rounded.Save",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).path(fill = SolidColor(Color.White)) {
                moveTo(17.59f, 3.59f)
                curveToRelative(-0.38f, -0.38f, -0.89f, -0.59f, -1.42f, -0.59f)
                lineTo(5.0f, 3.0f)
                curveToRelative(-1.11f, 0f, -2.0f, 0.9f, -2.0f, 2f)
                verticalLineToRelative(14.0f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2.0f)
                lineTo(21.0f, 7.83f)
                curveToRelative(0f, -0.53f, -0.21f, -1.04f, -0.59f, -1.41f)
                lineToRelative(-2.82f, -2.83f)
                close()
                moveTo(12.0f, 19.0f)
                curveToRelative(-1.66f, 0f, -3.0f, -1.34f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.34f, -3.0f, 3.0f, -3.0f)
                reflectiveCurveToRelative(3.0f, 1.34f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.34f, 3.0f, -3.0f, 3.0f)
                close()
                moveTo(13.0f, 9.0f)
                lineTo(7.0f, 9.0f)
                curveToRelative(-1.1f, 0f, -2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2f, -2.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
                reflectiveCurveToRelative(-0.9f, 2f, -2.0f, 2f)
                close()
            }.build()
            return _save!!
        }
}
