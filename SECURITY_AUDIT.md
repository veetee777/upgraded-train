# GLASSFORGE Security Audit Workflow

This project intentionally has no GitHub dependency. Run these commands in the Google Colab build environment or another trusted Gradle environment.

## Added build-time controls

- Gradle dependency locking is enabled for all app configurations.
- `:app:resolveAllDependencies` forces resolution of resolvable app configurations, including transitive dependencies.
- OWASP Dependency-Check produces HTML and JSON vulnerability reports. It fails the build for findings with CVSS >= 7.0. Set `NVD_API_KEY` in the environment to improve NVD feed access and update speed; never commit the key.
- CycloneDX generates aggregate and per-project SBOM files in CycloneDX JSON and XML formats.
- Legacy backup rules and Android 12+ data-extraction rules explicitly exclude all app data. `allowBackup=false` remains enabled.

## First dependency inventory and lock generation

From the project root:

```bash
./gradlew :app:resolveAllDependencies --write-locks
./gradlew :app:dependencies --configuration debugRuntimeClasspath
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

Review and retain the generated `app/gradle.lockfile` (or Gradle's generated lock state path for this project). Do not hand-write or guess dependency versions.

## Generate checksum verification metadata

After resolving the reviewed dependencies in a trusted environment:

```bash
./gradlew --write-verification-metadata sha256 :app:resolveAllDependencies
```

Review `gradle/verification-metadata.xml` before relying on it. Bootstrapping trusts the artifacts currently fetched; compare important checksums against trusted publisher information where available. Gradle will enforce the reviewed metadata on subsequent builds.

## Vulnerability scan and SBOM

```bash
./gradlew :app:dependencyCheckAnalyze
./gradlew cyclonedxBom
```

Expected outputs include Dependency-Check HTML/JSON reports under `app/build/reports` and CycloneDX JSON/XML under `build/reports/cyclonedx` (plus direct project reports).

## APK checks after a successful build

```bash
./gradlew :app:assembleDebug
$ANDROID_HOME/build-tools/35.0.0/aapt dump permissions app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/build-tools/35.0.0/aapt dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml
```

Also inspect the APK signature with `apksigner verify --verbose --print-certs`, and compare its final permissions/components against the source manifest and merged manifest at `app/build/intermediates/merged_manifests/`.

## Current status

The source changes above are in place, but the dependency graph has not yet been resolved in this environment. Therefore no lockfile, checksum allowlist, vulnerability clearance, SBOM, merged manifest, or APK inspection is claimed complete yet. Generate these artifacts during the first successful cloud build, then review the reports before calling the project cleared.


## Source-level review continuation (2026-11-10)

### Package visibility
- The app declares a launcher-activity query (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) for the Widget Workshop app picker. This exposes matching launchable activities to the app process, not to a remote service.
- Narrow intent queries now cover only the widget's implemented implicit actions: music, camera capture, Wi-Fi/Bluetooth settings, timer, calendar insert, plain-text sharing, speech recognition, and calculator. These declarations allow Android 11+ package-visibility-aware intent resolution without requesting broad package access.
- No `QUERY_ALL_PACKAGES` permission is declared in the source manifest. Do not broaden this to all installed packages.
- The source code queries `PackageManager` locally and renders app labels/icons. The picker is limited to launcher activities and deduplicates by package name. No network upload path or `INTERNET` permission is declared in the app manifest. This is a source review, not a runtime traffic capture.

### Components and widget intents
- `MainActivity` is exported because it is the app's launcher entry point; its launcher intent filter requires this.
- `GlassforgeWidgetProvider` is explicitly non-exported. Its refresh action is sent with an explicit in-app component intent.
- Widget `PendingIntent`s use `FLAG_IMMUTABLE`; request codes distinguish widget instances and action slots.
- Stored package names are checked against a package-name pattern before launch resolution. Stored action names are allowlisted. Unknown or unresolvable implicit actions fall back to the app activity.
- Music and Flashlight choices are now handled explicitly. Music resolves to an Android music app category. Flashlight opens GLASSFORGE rather than requesting camera access or attempting a privileged torch toggle; Android has no general permission-free flashlight launch action.

### Local preferences
- Widget configuration is now saved as a versioned JSON record rather than a delimiter-separated string. The provider caps the record size, validates labels, package names, action names, shape values, and glass opacity before use.
- A backward-compatible reader remains for existing v0.7 delimiter-formatted widget configurations.
- Notch presets remain in app-private `SharedPreferences` (`MODE_PRIVATE`).
- Preferences are not encrypted. They contain UI settings and selected app identifiers/labels, not credentials. Backup exclusions are configured for both legacy backup and Android 12+ cloud/device-transfer rules; `allowBackup=false` remains set.

### Current source-level findings and limits
- No `<uses-permission>` entries appear in the app's source manifest. This does not prove the final merged manifest is permission-free; library manifests may contribute entries.
- The final merged manifest, dependency vulnerability report, dependency lockfiles, checksum-verification metadata, SBOM, APK permissions, APK signature, and Galaxy A14 5G runtime behavior remain unverified until a real Gradle build and device test are completed.

### Font Forge source review
- Added a dedicated Font Forge screen with Android's system document picker for user-selected `.ttf` and `.otf` files. It does not request broad storage permissions.
- Imported fonts are copied into the app's private `files/fonts` directory. The selected filename is stored in app-private `SharedPreferences`; no font file or app list is uploaded.
- Import checks the extension/MIME type, enforces a 15 MB size ceiling while streaming, writes to a temporary file, validates it with Android `Typeface`, then atomically renames it into the font library. Failed imports clean up partial files.
- The selected font is applied to GLASSFORGE's Compose Material typography. Android does not permit this app to change typography inside unrelated apps or globally across the device. Standard Android home-screen `RemoteViews` widgets may retain system widget typography.
- Imported fonts are untrusted user files. The importer limits the library to 25 files and 100 MB total, limits each file to 15 MB, validates the platform font parser, and uses temporary-file/rename handling so failed imports do not leave partial font files. These measures reduce risk but do not constitute a formal font-parser security guarantee. Files remain private and are excluded by the configured backup rules.
- Font import and rendering have not yet been runtime-tested on the Galaxy A14 5G because this environment still lacks a Gradle build and connected device.

### Source-only verification performed in this pass
- Parsed `AndroidManifest.xml`, `backup_rules.xml`, and `data_extraction_rules.xml` as XML successfully.
- Confirmed the source manifest has zero explicit `<uses-permission>` entries, no `INTERNET` permission, and no `QUERY_ALL_PACKAGES` permission.
- Confirmed the source declares the launcher activity as exported and the widget receiver as non-exported.
- Confirmed the narrow package-visibility query declarations are present for the widget's implemented actions.
- These are source checks only. Dependency-contributed manifest entries and merged-manifest output remain unverified until Gradle builds the app.
