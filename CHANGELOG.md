# GLASSFORGE change log

## Working-source pass — 2026-11-10

- Widget Workshop now restores the last saved app, quick-action selection, shape, dimensions, curvature, border, glass setting, and column count when the editor is reopened.
- The saved home-screen widget now applies the selected shape using Android `RemoteViews`-compatible drawable resources.
- The saved glass setting now adjusts widget opacity.
- Widget Workshop copy now distinguishes settings applied to the actual home-screen widget from editor-only preview controls and launcher-managed widget sizing.

## Verification status

- Source archive extracted successfully and the original project files were retained.
- XML parsing, Kotlin compilation, Android Gradle build, APK inspection, and device installation have not been completed in this environment. The Gradle distribution host is currently unreachable from the build environment.
