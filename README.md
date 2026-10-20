# AndroidSimpleTV

A quiet, native Kotlin Android TV launcher: fullscreen bundled landscapes, a clock and date, and a remote-controlled app dock. Initial offline MVP, licensed under MIT.

## What works

- Three original bundled vector landscapes (alpine lake, dunes, coast), scaled to fill the screen without letterboxing. No network or storage permissions.
- Each wallpaper is static for **55 seconds**, followed by a **5-second crossfade**. No pan/zoom or continuous redraw during the static interval.
- Slideshow and clock callbacks stop in `onPause`. Returning resumes the same wallpaper/fade position; background time is excluded. Activity recreation also restores progress and dock selection.
- Clock honors the device's locale and 12/24-hour preference; date uses the locale's ordering.
- A horizontally scrolling dock discovers TV-launchable activities, with icons, labels, visible focus, and remembered selection. It refreshes when the launcher resumes. Phone-only apps are intentionally excluded.
- Separate `LEANBACK_LAUNCHER` and `HOME`/`DEFAULT` intent filters, a TV banner/icon, landscape display, and no touchscreen requirement.

## Remote controls

| Context | Button | Result |
| --- | --- | --- |
| Wallpaper | Down, Up, OK/Enter, or Menu | Reveal dock; first press never launches an app |
| Dock | Left / Right | Move focus and scroll through apps |
| Dock | OK/Enter | Launch focused app |
| Dock | Up, Back, or Menu | Hide dock |
| Wallpaper | Back | Normal Android back behavior (exit when opened as a regular app) |
| Anywhere | Home | Android's configured home launcher |

The dock stays visible until hidden or another app opens. Home behavior is controlled by the TV, not intercepted by this app.

## Build

Requires JDK **17**, Android SDK Platform **35**, and Build Tools **35.0.0**. Android Studio is optional. Gradle **8.11.1** is included via the wrapper, with a distribution SHA-256 check; Android Gradle Plugin **8.9.2** and Kotlin **2.1.20** are pinned. Android **6.0 / API 23** or newer is supported; target SDK is 35.

1. Clone this repository and open the root folder in Android Studio, or use a terminal.
2. Install SDK Platform 35 and Build Tools 35.0.0 with Android Studio's SDK Manager (or `sdkmanager 'platforms;android-35' 'build-tools;35.0.0'`). Accept the SDK licenses.
3. Set `ANDROID_HOME` to your SDK directory, or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.
4. Run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows: use `gradlew.bat`. First build needs internet to download dependencies. Output: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed development APK, not a production release. Local keys, SDK paths, build outputs, and caches are excluded from Git.

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`. GitHub Actions runs the same checks and uploads a debug APK.

## Safe TV sideload test

Keep the stock Google TV launcher installed, enabled, and selected. The app declares Home capability so it can be developed as a launcher, but this MVP does **not** request a home-role change, disable packages, remap buttons, or run ADB commands. Some TV firmware may present a Home chooser after installing any Home-capable app; choose your existing Google TV launcher if prompted. Do not select AndroidSimpleTV as the default during this test.

Enable developer debugging and authorize your computer on the TV. Use `adb devices -l` and verify the intended TV's serial; never rely on an implicit device selection.

```sh
# Substitute the serial shown by adb devices -l; connect first if needed.
./scripts/sideload-test.sh YOUR_TV_SERIAL
```

The script records Home resolution, installs only `com.traceur.androidsimpletv`, starts its activity explicitly, and checks that Home resolution is unchanged. It does not touch system packages. `install -r` updates an existing development install and preserves its data. Debug builds from different computers may have different signing keys; do not uninstall automatically to resolve a signature mismatch.

Manual checks:

1. Confirm fullscreen wallpaper, readable clock/date, and hidden dock.
2. Press OK once: the dock opens without launching an app. Navigate left/right to both ends; verify focus and scrolling. Press Back: only the dock hides.
3. Reveal the dock and launch a TV app. Press Home: the stock launcher should appear. Open AndroidSimpleTV again from its app tile (or the explicit activity command below).
4. Observe 55 seconds of stillness then a five-second crossfade. Leave midway through a fade, wait, and reopen: it should resume the frozen position.
5. Press Home and verify Google TV remains the default. Check sleep/wake with the physical remote. This app deliberately does not keep the screen awake.

```sh
adb -s YOUR_TV_SERIAL shell am start -W -n com.traceur.androidsimpletv/.MainActivity
# Remove ONLY this test app if desired (also removes its app data):
adb -s YOUR_TV_SERIAL uninstall com.traceur.androidsimpletv
```

Never run launcher-disable, system-package-uninstall, or `set-home-activity` commands for this test. See [validation notes](docs/VALIDATION.md) for what was actually exercised.

## Scope and contributions

This MVP has no Reddit feeds, downloads, wallpaper picker, favorites/reordering, weather, settings screen, background service, analytics, or boot receiver. Those can be added in focused follow-up PRs. Launching other apps relies on their exported TV activities; unavailable apps fail with a message rather than crashing the launcher.

`MainActivity.kt` owns the native view hierarchy, package discovery, remote focus and foreground lifecycle. `SlideshowTimeline.kt` is a pure Kotlin timing model with boundary tests. Robolectric tests exercise actual activity lifecycle and remote key gestures. All three landscapes and the banner artwork were authored for this project; banner lettering uses DejaVu Sans (see [font notice](docs/FONT-NOTICE.txt)).

Manifest setup follows the [Android TV app guide](https://developer.android.com/training/tv/get-started/create). Tool versions follow the [AGP 8.9 compatibility table](https://developer.android.com/build/releases/agp-8-9-0-release-notes).
