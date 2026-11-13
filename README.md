# GLASSFORGE Android Starter v0.7 — Security Hardening

An Android Studio source project for the GLASSFORGE customization studio, targeting Android 8+ (minSdk 26) and designed for testing on the Samsung Galaxy A14 5G / Android 13.

## Included
- Compose-based Studio, Notch Studio, and Widget Workshop screens.
- Installed-app picker with search and app icons.
- Widget design controls for quick actions, shape, width, height, curvature, border, glass level, and grid columns.
- Local persistence for the latest widget design.
- **Real Android home-screen widget provider** (`GlassforgeWidgetProvider`) that reads the saved design, displays the selected app, and wires supported quick-action buttons to Android intents.
- Resizable widget provider metadata and launcher widget-picker registration.
- Notch Studio preset saving: dimensions, vertical position, material settings, visual style, and feature toggles persist locally across app restarts.

## Add the widget
1. Open and run the project from Android Studio on the phone.
2. In GLASSFORGE, open **Widgets**, choose an installed app, configure the design and quick buttons, then tap **Save Widget Design**.
3. Long-press an empty area of the Android home screen, open **Widgets**, find **GLASSFORGE**, and place the widget.
4. Save a new design in the app to refresh existing GLASSFORGE widgets.

## Important platform limits
- Android does not let one app automatically turn every third-party app into a full-featured interactive widget or access another app's private APIs. GLASSFORGE can launch any installed app that exposes a launcher activity; quick buttons use supported Android intents. Deeper controls require that app's own public widget, shortcut, or integration API.
- The widget currently applies the saved app label, shape label, glass tint/opacity approximation, and up to four selected quick actions. Android RemoteViews has styling and rendering limits; this is not yet the GPU-backed optical glass engine.
- Notch/overlay hardware interactions still require their own runtime permissions and service implementations. The current notch editor is a design preview; saving a preset stores its configuration locally but does not activate a system overlay.
- This is source code, not a compiled APK. Build and device testing have not yet been completed in this environment.

## Build
Open this folder in Android Studio with a compatible Android Gradle Plugin/Kotlin toolchain, allow Gradle sync, then build the `app` debug variant.


## Performance-first design (v0.6)

GLASSFORGE is being built with a performance budget, especially for mid-range devices such as the Galaxy A14 5G:

- Installed-app discovery and icon bitmap preparation run off the main/UI thread.
- App icons are cached as image bitmaps in screen state; recomposition does not repeatedly decode them.
- Search results are capped to a small visible set to keep layout and measurement work bounded.
- No always-running animation, polling loop, or background service is started by the editor.
- The current glass previews use lightweight gradients and borders; expensive real-time blur/refraction is not applied across the whole screen.
- Home-screen widgets update only when Android requests an update or the user saves a new design; no periodic refresh is configured.

Performance is a design target, not a measured guarantee yet: test on the Galaxy A14 5G before release, profile frame time and memory, and keep costly effects opt-in and limited to the visible component. Prefer static rendering when idle, pause animations off-screen, and use reduced-motion / low-power settings.


## Security baseline (v0.7)

- No `INTERNET` permission is declared; the current app has no network client or analytics SDK.
- Removed unused camera, vibration, and system-overlay permissions. Add a permission only alongside the feature that needs it, with an in-context explanation and runtime consent where Android requires it.
- Android app-data backup is disabled (`allowBackup=false`, `fullBackupContent=false`).
- Cleartext network traffic is disabled. If networking is introduced later, use HTTPS, certificate validation, and a documented data-minimization policy.
- The launcher activity is the only exported app component. The widget receiver is explicitly non-exported.
- Widget `PendingIntent`s are immutable. Persisted widget configuration is bounded and validated before being rendered or used to resolve an installed app.
- Release builds enable R8 code shrinking, optimization, and resource shrinking.

### Security release gate

Before public release, run Android Lint, dependency vulnerability/license checks, APK permission review, static analysis, and tests on Android 13 / Galaxy A14 5G. Verify exported components, backup behavior, deep links/intents, malformed widget configuration, and release signing. Keep signing keys out of source control. This source baseline is hardened but is not a security certification or a substitute for a device-tested audit.

## Build a debug APK with GitHub Actions

This repository includes `.github/workflows/android-apk.yml`. After the project is pushed to a GitHub repository, open **Actions → Build GLASSFORGE APK → Run workflow**. The workflow installs JDK 17, Gradle 8.9, Android platform 35 and build-tools 35.0.0, then builds `:app:assembleDebug`. Download `app-debug.apk` from the successful run's **GLASSFORGE-debug-APK** artifact.

The workflow produces a debug-signed APK for installation/testing. It does not create a Play Store release or replace device testing.


## Security audit workflow

See [SECURITY_AUDIT.md](SECURITY_AUDIT.md) for dependency locking, vulnerability scanning, SBOM generation, backup exclusions, and final APK inspection commands.
## Security review

See [SECURITY_AUDIT.md](SECURITY_AUDIT.md) for dependency locking, vulnerability scanning, SBOM generation, manifest/APK verification, and source-level security notes.


## Font Forge reliability update

- Font import, font validation, and font-library scans now run on an IO dispatcher rather than the UI thread.
- Font import rejects empty files and keeps the existing 15 MB per-font, 25-font, and 100 MB library limits.
- Imported font filenames are constrained to the app-generated UUID format when loading the library.
- Added per-font deletion. Deleting the active font also restores the system default and clears the saved selection.
- The previous source archive is retained separately; this update is applied to the recovered v0.8 source tree.

Build status: source/static checks only. An Android APK has not yet been built or device-tested.
