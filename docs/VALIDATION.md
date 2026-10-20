# MVP validation — 2026-10-20

## Build and automated checks

- JDK 17.0.20.1, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, SDK Platform 35 / Build Tools 35.0.0.
- `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL**.
- **7 tests passed, 0 failures**: three timing-model tests and four Robolectric activity tests (API 28).
- Coverage: 55,000 ms static boundary, five-second fade and wraparound; restoring a partial fade; empty-list rejection; reveal/hide gestures; consuming held keys; activity recreation; freezing through ten minutes of simulated background time and resuming the same fade.
- Debug APK produced successfully. Gradle wrapper downloaded and passed its distribution SHA-256 check; `./gradlew --version` reports 8.11.1.
- Sideload script passes `bash -n`; Git whitespace check passes.

The local sandbox uses workspace-local SDK, Android debug-key, Gradle and Robolectric caches. Robolectric's first attempt could not create a lock in the read-only user home; a local Gradle init script redirects its test-process home and Maven downloads to the workspace. No workaround is needed on a normally writable developer machine. Android's optional metrics initialization still warns about the read-only home; compilation, tests and packaging succeed.

Lint: **0 errors, 7 warnings**. The warnings are: one API-33-only Back opt-out attribute (older TVs ignore it); three large vector wallpaper dimensions; one TV-specific fixed landscape orientation; one recommendation for Android 12 backup extraction rules; and one density-folder warning for the xhdpi TV banner. These are recorded rather than hidden by a broad lint suppression. The app currently stores no persistent user data. Physical 4K rendering performance and backup/transfer behavior have not been profiled.

## BRAVIA hardware smoke test

Device: Sony BRAVIA 4K VH2, Android 12 / API 31. Tests used ADB-injected D-pad events and UI hierarchy inspection, not a hand-operated physical remote.

- Explicit serial selected; debug APK installed successfully, activity cold launch succeeded.
- Fullscreen wallpaper, clock/date and dock rendered at the TV's 1920×1080 app surface; screenshots visually inspected.
- Back hides the dock without exiting the launcher.
- OK on the wallpaper reveals the dock without launching an app.
- Left/right navigation scrolls across the full app row; focus stays inside the row at each end.
- OK on the Apple TV card launches its TV activity successfully.
- Home returns to `com.google.android.apps.tv.launcherx/.home.HomeActivity`.
- Home resolution checked before and after installation/testing: **unchanged**.
- Timed screenshot sampling passed: the wallpaper region is identical at approximately 2.8 and 52.8 seconds after launch; the 57.9-second frame shows the crossfade; the 61.7-second frame shows the next landscape. The initial attempt sampled Android's activity-opening animation, so the repeat starts after it settles. Exact 55/5-second boundaries are verified by the timing-model tests.

The debug app remains installed for further testing. The TV is returned to stock Google TV Home after testing. No system package was disabled, uninstalled, cleared, or otherwise modified. No default-home or button-remapping setting was changed.

## Remaining manual coverage

The precise timing boundaries and background pause are covered by automated tests. Hardware sampling is a visual smoke test, not a frame-time/performance measurement. Still needed: physical-remote sleep/wake, prolonged use, timezone/12-hour preference changes, an empty app list, packages disappearing during discovery, and hardware coverage on API 23 and API 35. The MVP is not a Play Store release or a default-launcher migration procedure.
