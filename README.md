# AndroidSimpleTV

A quiet, native Kotlin Android TV launcher: fullscreen bundled landscapes, a clock and date, and a remote-controlled app dock. Native TV MVP, licensed under MIT.

## What works

- Three original bundled vector landscapes (alpine lake, dunes, coast), scaled to fill the screen without letterboxing. Bundled wallpapers remain offline. Optional Commons/NASA wallpaper refresh and weather use internet access; no storage or device-location permissions.
- Each wallpaper is static for **115 seconds**, followed by a **5-second crossfade**. No pan/zoom or continuous redraw during the static interval.
- Slideshow and clock callbacks stop in `onPause`. Returning resumes the same wallpaper/fade position; background time is excluded. Activity recreation also restores progress and dock selection.
- Clock honors the device's locale and 12/24-hour preference; date uses the locale's ordering.
- Saved dock customization: a checkbox list for visibility, remote pick-up/reposition/place ordering, or reset order A–Z. Settings always remains accessible, even with all apps hidden.
- Optional city/postal-code weather search, confirmed location selection, Fahrenheit by default with a Celsius toggle. Current temperature refreshes every 30 minutes while foregrounded. Cached readings are marked when stale, and unavailable weather does not block the launcher.
- A horizontally scrolling dock discovers TV-launchable activities, with icons, labels, visible focus, and remembered selection. It refreshes when the launcher resumes. Phone-only apps are intentionally excluded.
- Separate `LEANBACK_LAUNCHER` and `HOME`/`DEFAULT` intent filters, a TV banner/icon, landscape display, and no touchscreen requirement.

## Remote controls

| Context | Button | Result |
| --- | --- | --- |
| Wallpaper | Down, Up, or OK/Enter | Reveal dock; first press never launches an app |
| Dock | Left / Right | Move focus and scroll through apps |
| Dock | OK/Enter | Launch focused app |
| Dock | Up or Back | Hide dock |
| Wallpaper | Back | Normal Android back behavior (exit when opened as a regular app) |
| Anywhere in launcher | Menu | Open Settings (also available as the final dock tile) |
| Anywhere | Home | Android's configured home launcher |

The dock automatically hides after **10 seconds without remote input**. Every remote key event restarts that timer. Home behavior is controlled by the TV, not intercepted by this app.

## Customize the dock and weather

Open the permanent **Settings** dock tile, or press Menu in the launcher.

- **Show/hide apps** opens one checkbox list. Checked means shown. Toggle several apps with OK without leaving the list; changes save immediately. Hidden apps remain listed so you can restore them.
- **Move apps** opens the complete ordered list, including hidden apps marked as hidden. Use Up/Down to select an app, **OK to pick it up**, Up/Down to reposition it, then **OK to place and save**. Back cancels an unfinished move without closing the list; another Back closes it. Leaving Settings also cancels any unplaced move. Placed moves remain saved.
- **Sort apps A–Z** resets ordering but preserves visibility choices. New apps append alphabetically after the saved order. Nothing is uninstalled or disabled.

Select **Weather location** to open the on-screen keyboard (OK on the text field reopens it). Enter a city or postal code, then confirm a matching result. A new install has no preset location and sends no weather request until configured. Search text and selected city coordinates go to Open-Meteo; no GPS/device location permission is requested. Postal codes may resolve to city-level coordinates. **Temperature** toggles °F/°C; **Turn weather off** removes the saved location and cached reading. Preferences stay on this device and survive app updates.

Weather uses the [Open-Meteo API](https://open-meteo.com/en/docs) and [geocoding API](https://open-meteo.com/en/docs/geocoding-api), with Open-Meteo/GeoNames attribution in Settings. The public endpoint is intended for non-commercial use; review [provider terms](https://open-meteo.com/en/terms) before commercial distribution. Weather data is CC BY 4.0. Internet failures leave wallpapers and app launching functional. Foreground polling stops on pause; requests are cancelled and late results ignored.

Enable **Show five-day forecast** in Settings to display today and the following four days beneath current weather, with conditions and daily highs/lows in your selected units. It is off by default, uses the saved location, shares the foreground 30-minute weather request/cache, and removes past dates from cached forecasts. Disable it to restore the simpler wallpaper view.

## Public wallpaper sources

Open **Settings → Wallpapers**, select **Wikimedia Commons**, **NASA**, or both, then enable **Use online wallpapers**. No account or API key is needed. On returning to the launcher, it displays any saved collection immediately and refreshes if due. **Refresh photos** in Settings uses the same half-hour limit. New installs leave network wallpapers off until enabled; bundled landscapes always remain available.

- **At most one refresh attempt per 30 minutes**, shared across manual refresh, automatic refresh, source changes and app restarts. The lease is saved before network work, so failures/cancellation do not create rapid retries. Longer provider `Retry-After` delays are respected. There is no background service, alarm, or wake-up job: automatic refresh happens only while the launcher is visible, or on the next foreground return when overdue.
- **15 photos maximum**, each JPEG capped at **512 KiB** and at most **1920×1080**. The saved collection is at most **7.5 MiB plus metadata**. Old and new collections coexist briefly for safe replacement, with a **20 MiB hard wallpaper-directory limit**. Old files and interrupted staging files are removed; no accumulating photo files. A bounded history of the last 10,000 photo IDs prevents recent repeats without keeping those images.
- A refresh makes at most **32 HTTP requests**: up to two listings and 30 candidate image attempts. Each image response is capped at **3 MiB**, each listing at **1 MiB**, and a refresh has a **48 MiB response-body budget**. These are worst-case limits, not expected half-hour usage. Oversized or unsuitable candidates are skipped. Recently downloaded photo IDs are skipped before downloading.
- No new wallpaper download occurs every slide: each photo gets **115 seconds static / 5 seconds crossfade**, for two minutes total. Online collections play once, then hold the last photo until a fresh collection arrives; they do not loop. Playback position survives leaving the launcher and restarting the app.
- Failed or empty refreshes keep the last complete collection for offline use. If no usable collection matches the selected sources, bundled landscapes appear. The app skips refresh writes if less than 23 MiB is available. **Clear photos and turn off downloads** releases wallpaper storage, clears the recent-photo history and disables automatic refresh.

Commons supplies nature scenes from rotating landscape searches; NASA supplies space imagery from its Image and Video Library. Topics, result pages and candidate order vary every half hour. Providers may supply fewer than 15 usable new photos; the last photo then remains visible until the next permitted refresh. The 10,000-ID history is bounded, not a guarantee against visual duplicates published under different IDs or very old photos returning. One unavailable provider does not prevent the other from supplying images. Both are queried only when selected. Media are landscape JPG/PNG files, fetched from allowlisted HTTPS hosts with timeouts and no redirects; originals larger than the response cap are never downloaded. Image decoding has dimension limits and bounded output. Fifteen decoded frames can use up to roughly 60 MiB of bitmap memory, separate from disk storage, with old and new collections briefly coexisting (roughly 120 MiB) and additional transient decoding memory during refresh.

Turn off **Show photo information** to hide the home-screen credit overlay. Credits remain available in previews.

**Preview downloaded photos** shows the title, author/source, license or usage guideline, and original source page. Commons accepts recognized CC BY, CC BY-SA, CC0 or public-domain metadata and retains attribution; the cache’s 16:9 crop is disclosed. NASA-specific credit is retained where supplied; NASA imagery is not universally copyright-free, so explicit copyright-marked entries are excluded and its usage guidelines remain linked. Sources and licenses apply to the photos independently of this code’s MIT license. See [Commons API](https://www.mediawiki.org/wiki/API:Imageinfo), [Commons reuse guidance](https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia), [NASA API](https://images.nasa.gov/docs/images.nasa.gov_api_docs.pdf), and [NASA media guidance](https://www.nasa.gov/nasa-brand-center/images-and-media/).

### Paused sources

Reddit integration is paused and removed from the active UI/network path. Its implementation remains in Git history at v0.4; old Reddit image-cache files are removed when the new store is first read. No Reddit credentials are needed. Unsplash is not integrated because its current [API guidelines](https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines) exclude wallpaper applications and require a different integration model. No scraping or unofficial proxy fallback is used.

## Idle screensaver versus Home

Selecting this app as the default Home launcher would not select it as the TV's screensaver. Android runs screensavers as a separate [DreamService](https://developer.android.com/reference/android/service/dreams/DreamService); [Google TV Ambient mode](https://support.google.com/googletv/answer/10070821?hl=en) has its own settings. This version does not implement a screensaver, change Sony Ambient mode settings, or change the default launcher. A shared-wallpaper screensaver is future work, subject to the TV firmware allowing selection. True standby keeps the screen off.

### Keep the slideshow awake

Settings includes **Keep slideshow awake**, off by default. When enabled, the visible launcher sets Android's `FLAG_KEEP_SCREEN_ON`, the supported mechanism used for apps that need the display to remain on. Android documents it as preventing TV Ambient mode for a foreground activity; fullscreen layout alone does not do that. No fake video, simulated keypresses, wake-lock permission, or system timeout changes are involved. See [Android screen-on guidance](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).

The flag is explicitly cleared when the launcher pauses, including when opening Settings or another app, and restored on return only if enabled. The remote Power button and separate TV power policies are not overridden. This prevents idle Ambient mode while this app is visible; it does not replace the screensaver used elsewhere. Disable the switch to allow normal idle behavior again.

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
2. Press OK once: the dock opens without launching an app. Navigate left/right to both ends; verify focus and scrolling. Press Back: only the dock hides. Leave it open without input for 10 seconds and verify automatic hiding; input before the deadline should restart the timer.
3. Reveal the dock and launch a TV app. Press Home: the stock launcher should appear. Open AndroidSimpleTV again from its app tile (or the explicit activity command below).
4. Observe 115 seconds of stillness then a five-second crossfade. Leave midway through a fade, wait, and reopen: it should resume the frozen position.
5. Press Home and verify Google TV remains the default. Check sleep/wake with the physical remote. With Keep slideshow awake off, normal idle behavior should remain. With it on, leave the launcher visible beyond the configured idle interval, then verify that leaving the launcher releases the screen-on request.

```sh
adb -s YOUR_TV_SERIAL shell am start -W -n com.traceur.androidsimpletv/.MainActivity
# Remove ONLY this test app if desired (also removes its app data):
adb -s YOUR_TV_SERIAL uninstall com.traceur.androidsimpletv
```

Never run launcher-disable, system-package-uninstall, or `set-home-activity` commands for this test. See [validation notes](docs/VALIDATION.md) for what was actually exercised.

## Scope and contributions

This MVP has no favorites groups, arbitrary image-host support, custom screensaver, background service, analytics, or boot receiver. Those can be added in focused follow-up PRs. Launching other apps relies on their exported TV activities; unavailable apps fail with a message rather than crashing the launcher.

`WallpaperSettingsActivity.kt` provides source setup and previews, `PublicWallpapers.kt` handles bounded Commons/NASA requests and filtered listings, and `WallpaperStore.kt` owns bounded image decoding and the private cache. `SettingsActivity.kt` provides TV settings, `LauncherPreferences.kt` persists dock/weather choices, and `WeatherClient.kt` handles bounded HTTPS requests. `MainActivity.kt` owns the native view hierarchy, package discovery, remote focus and foreground lifecycle. `SlideshowTimeline.kt` is a pure Kotlin timing model with boundary tests. Robolectric tests exercise actual activity lifecycle and remote key gestures. All three landscapes and the banner artwork were authored for this project; banner lettering uses DejaVu Sans (see [font notice](docs/FONT-NOTICE.txt)).

Manifest setup follows the [Android TV app guide](https://developer.android.com/training/tv/get-started/create). Tool versions follow the [AGP 8.9 compatibility table](https://developer.android.com/build/releases/agp-8-9-0-release-notes).
