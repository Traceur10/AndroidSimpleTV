# Validation — 2026-10-27

## v0.8: optional five-day weather forecast

- Local build, lint and all **37 tests passed**. Tests cover daily parsing, invalid dates/temperatures, Fahrenheit/Celsius conversion, location-timezone date boundaries, persistent cache, visibility on resume and cache removal when location changes.
- Optional setting defaults off. Forecast uses the saved location and units; current weather and forecast share one Open-Meteo request and the foreground 30-minute cache. Old cache entries remain readable. Past forecast dates are omitted; stale readings retain a cached timestamp, and missing data has an unavailable state.
- Installed on Sony BRAVIA and Chromecast with their existing default Home unchanged. Enabled the forecast on the Sony and visually verified five live daily entries with conditions/highs/lows. No reboot, full overnight rollover or network-disconnection hardware test was performed.

## v0.7: TV keyboard and photo information switch

- Local build, lint and all **35 tests passed**. Existing 11 lint warnings, no errors.
- Added a regression test for location field focus, initial soft-keyboard request and remote OK reopening the keyboard. The wallpaper test checks hiding and restoring the credit overlay across activity resume without removing its text.
- Installed on the second TV (Chromecast). Android input-method diagnostics confirmed the keyboard view visible both on opening the location dialog and after Back followed by OK. No location was saved during this check.
- Added a persistent Show photo information switch in Wallpapers, default on. Hiding the overlay leaves credits/license/source information in photo previews. Stock Google TV Home resolution remained unchanged after installation.

## v0.6: two-minute photos and automatic dock hiding

- Local `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL; 34 tests passed; 0 lint errors, 11 warnings**.
- Tests cover 115-second static / five-second fade timing, non-looping online collections holding their final frame, playback restored in a fresh activity, the 30-minute refresh lease, 15-photo limits, 1.5 MiB files, a 20 MiB directory ceiling including staging, recent-ID exclusion and bounded 10,000-ID history. Dock tests cover ten-second inactivity, input resetting the deadline and pause cancelling the callback.
- Updated the Sony BRAVIA API 31 with `install -r`. On-device UI inspection confirmed the dock appears and hides after inactivity. Stock Google TV Home resolution remained unchanged. Existing weather, dock and keep-awake preferences were retained.
- The TV still had its previous four-photo collection and a persisted refresh cooldown until 8:45 AM. Manual refresh correctly respected that gate. **A live 15-photo download and full 30-minute device soak were not performed for this revision**; do not interpret the automated cache tests as a live provider capacity or long-run memory test.
- Saved JPEGs are capped at 1.5 MiB each; the wallpaper directory, including replacement files and metadata, is capped at 20 MiB. Each refresh allows at most 32 HTTP requests / 48 MiB response bodies. No background downloads. Provider shortages or failures hold the last image rather than replaying the collection. The recent-ID history is bounded and cannot detect identical imagery published under different IDs.

## v0.5: hourly Commons/NASA wallpapers and storage limits

- Local `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL; 29 tests passed; 0 lint errors, 11 warnings**. Two new advisory warnings concern intentionally synchronous persistence of the refresh lease and using conservative filesystem free-space checks rather than asking Android to reclaim caches.
- Tests cover Commons attribution/license parsing, NASA size selection, trusted URLs, the persisted hourly lease across recreation/source changes/clock rollback, four-image limits, 512 KiB file and 5 MiB directory checks, staging cleanup, offline retention on failed/empty refresh, unchanged-image reuse without downloading/recompression, cancelled writes, previews and slideshow switching.
- Live public API checks succeeded. On Sony BRAVIA API 31, one app refresh downloaded **two Commons images and two NASA images**. Four JPEG files plus metadata totaled **1,156,056 bytes (about 1.10 MiB)**; the largest image was 418,204 bytes. No API key/account was needed.
- On-device previews displayed credits and usage/license details for both providers. Enabling online wallpapers displayed the real downloaded collection on the launcher. Screenshots inspected. A second manual refresh reported the next allowed hour and left the album unchanged; returning to the launcher also reused the collection. Existing dock/weather/keep-awake settings were preserved, and stock Home resolution remained unchanged.
- Public wallpapers are enabled on this test TV; new installs default to bundled wallpapers until enabled. Reddit is removed from the active UI/network path and its old cache is purged. Unsplash is deferred because its API guidelines exclude wallpaper apps.
- Automatic refresh is scheduled only in the foreground and shares the persisted one-hour gate with manual refresh. The actual next-hour boundary has been tested with injected timestamps, **not a full one-hour device soak**. Long-run traffic, physically full TV storage, provider outages/429s and disconnected-network behavior have not been exercised on hardware; deterministic tests cover cache/gate behavior.
- Limits apply to the wallpaper directory, not the installed APK or unrelated weather/app data. Four-photo steady storage is at most 2 MiB plus metadata; temporary replacement is capped at 5 MiB. Per-refresh HTTP response bodies are budgeted to 16 MiB, with at most 10 requests and no original image over 3 MiB. Decoded bitmap memory is separate from disk usage.

## v0.4: Reddit wallpaper sources (API access pending)

- Local `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL; 28 tests passed; 0 lint errors, 9 warnings**. The additional warning is the new TV settings activity’s fixed landscape orientation.
- New tests exercise subreddit normalization and validation; filtering adult/spoiler/deleted/removed/video/gallery posts; trusted image-host validation; bounded six-image caching; landscape decode and panorama crop; offline loading; source isolation; failed/cancelled refresh preservation; expiry removal; empty listings; preview and setup gating; and switching between cached photos and bundled wallpaper with attribution.
- Fixtures are generated locally in tests. No Reddit photos, credentials or user content are bundled. **Live Reddit authentication and downloading are unverified** because an approved installed-app client ID is not available. The app uses the documented application-only installed-client OAuth flow; Reddit eligibility/approval is an external prerequisite.
- Installed on Sony BRAVIA API 31. TV UI checks verified Wallpapers navigation, multi-subreddit entry/save, setup gating when credentials are missing, and empty-preview fallback guidance. UI screenshot visually inspected. Actual cached photos/previews are tested in Robolectric, not with live Reddit images on the TV.
- Sideload preserved Google TV Home resolution. Existing dock visibility/order, weather location/units and keep-awake preference are preserved. Reddit source remains off; bundled wallpapers remain usable.
- Refresh is manual, foreground-only and cancelled on leaving wallpaper settings. No background downloader, system package changes, default-home changes, new permission, or system timeout changes.
- README documents approval/setup, unsupported script/web client IDs, image limits, 48-hour cache expiry, manual refresh, offline fallback and remaining live-validation requirements.

## v0.3: remote-friendly dock settings and foreground keep-awake

- `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL; 17 tests passed; 0 lint errors, 8 warnings**.
- Tests cover repeated moves and boundaries, cancel versus placement, multi-toggle visibility without closing the dialog, and the screen-on flag being set only when enabled and cleared on pause.
- Installed v0.3 on the Sony BRAVIA API 31. ADB-injected remote events verified pick-up, several moves, Back cancellation without closing, and OK placement. Visibility toggles were exercised in the same open list. Screenshots were inspected.
- User visibility, ordering and location preferences were preserved after testing. Keep slideshow awake was enabled on this TV; it remains off by default for new installs.
- Android window diagnostics confirmed KEEP_SCREEN_ON and the launcher as the held-screen window in the foreground. Opening Settings cleared the held-screen window; stock Home also had no launcher hold. Returned the TV to stock Home, whose resolver is unchanged.
- User subsequently confirmed the launcher stayed visible for **25 minutes** without idle Ambient mode, the physical remote Power button turned the screen off, and powering on resumed the app. Separate sleep-timer behavior remains untested. The option uses Android's supported foreground screen-on flag, not system timeout changes. Sony may have separate power policies.
- No TV system packages, screensaver configuration, default launcher, or system idle settings were changed. Reddit photos and a dedicated DreamService remain outside this revision.

## v0.2: dock customization and weather

- `testDebugUnitTest lintDebug assembleDebug`: BUILD SUCCESSFUL. **12 tests passed**, with **0 lint errors and 8 warnings** (the original seven plus fixed landscape orientation for Settings).
- New tests cover hidden apps/custom ordering surviving a fresh preferences instance, alphabetical reset preserving visibility, missing/new apps, default Fahrenheit with no preset location, changing location clearing cached weather, temperature conversion, and 30-minute cache expiry/clock rollback.
- Live geocoding and current-temperature endpoints responded successfully. The BRAVIA displayed the user-selected city and a live Fahrenheit reading, retained after the app process restarted. Location is stored only in app preferences, never as a repository default.
- Settings uses standard Android dialogs and a permanent dock tile. The manager exposes hidden apps for recovery; ordering applies to the full list. Detailed hardware interaction results are recorded in the PR.
- The final change adds INTERNET permission only. Weather is opt-in through explicit city selection; no GPS or other device-location permission. Requests have timeouts, cache stale labels, lifecycle cancellation, and foreground-only refresh. Offline/error presentation is implemented but has not been exercised by disconnecting the TV network.
- No screensaver/default-home/system-package changes. Sony Ambient mode still owns the idle screensaver. Reddit images and a dedicated DreamService remain future work.

## v0.1 baseline (historical)


## Build and automated checks

- JDK 17.0.20.1, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, SDK Platform 35 / Build Tools 35.0.0.
- `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL**.
- **7 tests passed, 0 failures**: three timing-model tests and four Robolectric activity tests (API 28).
- Coverage: 55,000 ms static boundary, five-second fade and wraparound; restoring a partial fade; empty-list rejection; reveal/hide gestures; consuming held keys; activity recreation; freezing through ten minutes of simulated background time and resuming the same fade.
- Debug APK produced successfully. Gradle wrapper downloaded and passed its distribution SHA-256 check; `./gradlew --version` reports 8.11.1.
- Sideload script passes `bash -n`; Git whitespace check passes.

The local sandbox uses workspace-local SDK, Android debug-key, Gradle and Robolectric caches. Robolectric's first attempt could not create a lock in the read-only user home; a local Gradle init script redirects its test-process home and Maven downloads to the workspace. No workaround is needed on a normally writable developer machine. Android's optional metrics initialization still warns about the read-only home; compilation, tests and packaging succeed.

Lint: **0 errors, 7 warnings**. The warnings are: one API-33-only Back opt-out attribute (older TVs ignore it); three large vector wallpaper dimensions; one TV-specific fixed landscape orientation; one recommendation for Android 12 backup extraction rules; and one density-folder warning for the xhdpi TV banner. These are recorded rather than hidden by a broad lint suppression. Version 0.1 stored no persistent user data; v0.2 now stores dock/weather preferences and cached readings locally. Physical 4K rendering performance and backup/transfer behavior have not been profiled.

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
