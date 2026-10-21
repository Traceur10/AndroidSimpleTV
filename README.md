# AndroidSimpleTV

A quiet, native Kotlin Android TV launcher: fullscreen bundled landscapes, a clock and date, and a remote-controlled app dock. Native TV MVP, licensed under MIT.

## What works

- Three original bundled vector landscapes (alpine lake, dunes, coast), scaled to fill the screen without letterboxing. Bundled wallpapers remain offline. Optional Reddit downloads and weather use internet access; no storage or device-location permissions.
- Each wallpaper is static for **55 seconds**, followed by a **5-second crossfade**. No pan/zoom or continuous redraw during the static interval.
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

The dock stays visible until hidden or another app opens. Home behavior is controlled by the TV, not intercepted by this app.

## Customize the dock and weather

Open the permanent **Settings** dock tile, or press Menu in the launcher.

- **Show/hide apps** opens one checkbox list. Checked means shown. Toggle several apps with OK without leaving the list; changes save immediately. Hidden apps remain listed so you can restore them.
- **Move apps** opens the complete ordered list, including hidden apps marked as hidden. Use Up/Down to select an app, **OK to pick it up**, Up/Down to reposition it, then **OK to place and save**. Back cancels an unfinished move without closing the list; another Back closes it. Leaving Settings also cancels any unplaced move. Placed moves remain saved.
- **Sort apps A–Z** resets ordering but preserves visibility choices. New apps append alphabetically after the saved order. Nothing is uninstalled or disabled.

Select **Weather location**, enter a city or postal code, then confirm a matching result. A new install has no preset location and sends no weather request until configured. Search text and selected city coordinates go to Open-Meteo; no GPS/device location permission is requested. Postal codes may resolve to city-level coordinates. **Temperature** toggles °F/°C; **Turn weather off** removes the saved location and cached reading. Preferences stay on this device and survive app updates.

Weather uses the [Open-Meteo API](https://open-meteo.com/en/docs) and [geocoding API](https://open-meteo.com/en/docs/geocoding-api), with Open-Meteo/GeoNames attribution in Settings. The public endpoint is intended for non-commercial use; review [provider terms](https://open-meteo.com/en/terms) before commercial distribution. Weather data is CC BY 4.0. Internet failures leave wallpapers and app launching functional. Foreground polling stops on pause; requests are cancelled and late results ignored.

## Reddit wallpaper sources

Open **Settings → Wallpapers**. New installs use bundled landscapes and make no Reddit requests.

1. **Choose subreddits**: enter up to five names separated by commas, optionally prefixed with `r/`. The initial suggestion is `earthporn`; no content is downloaded merely by saving it.
2. **Set up Reddit access**: enter an approved **installed-app** client ID and the app owner’s Reddit username (used as the API contact). No password or client secret belongs in the TV app or this repository.
3. **Refresh photos**: fetch top posts from the past week and download up to six suitable images. Leave this screen to cancel. Refresh is manual and rate-limited to at most once a minute; Reddit’s longer `Retry-After` responses are respected.
4. **Preview downloaded photos**: inspect each image with Next photo, see its title and author, or open its Reddit post if a browser is installed.
5. Enable **Use Reddit photos** and return to the launcher. Turning it off restores the bundled landscapes. **Clear downloaded photos** removes the private cache.

### Reddit API setup and current limitations

Reddit requires approved API access and OAuth; creating an ID alone may not grant access. Start with [Developer Platform & Accessing Reddit Data](https://support.reddithelp.com/hc/en-us/articles/14945211791892-Developer-Platform-Accessing-Reddit-Data) and the request link in the [Data API Wiki](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki). Describe this native TV wallpaper use case, how images are displayed, and caching/retention when applying. Reddit determines eligibility; access is not guaranteed.

This implementation targets an approved **installed app** using Reddit’s [application-only OAuth installed-client grant](https://github.com/reddit-archive/reddit/wiki/OAuth2#application-only-oauth). It requests a temporary, user-less token with no client secret, uses `oauth.reddit.com` for listings, and keeps the token in memory only. `DO_NOT_TRACK_THIS_DEVICE` is used instead of a persistent device identifier. The owner username identifies the app in its User-Agent; it is not an account login. Script/web-app IDs requiring a secret are unsupported. Legacy technical documentation can change, so live compatibility must be verified against the approval granted to your app.

**Live Reddit authentication/downloads have not yet been validated with approved credentials.** The UI, parser, cache, preview and slideshow paths are tested with local fixtures. Unapproved/invalid access reports an error and does not break the launcher. There is no anonymous-feed, scraping, proxy or login-password fallback.

### Images, offline behavior and retention

Only direct static JPG/PNG images hosted on `i.redd.it` are downloaded. Posts marked NSFW or spoilers, deleted/removed posts, videos, galleries and external image hosts are skipped. At least 1280×720 landscape images are required, center-cropped to 16:9 and capped at 1920×1080. Oversized responses and extreme dimensions are rejected; all network operations have timeouts and redirects are rejected. Up to 24 candidates are tried to obtain six images. This is a bounded 1080p cache, not original-resolution 4K storage.

Photos, titles and author/post attribution stay in app-private storage; no storage permission is needed. No wallpaper network work runs in the background or automatically on the slideshow. Cached images remain usable offline for **up to 48 hours**; expired content is removed on the next foreground cache check and bundled landscapes take over. A successful refresh replaces the old collection; an empty successful listing clears it. Failed requests retain the last usable, unexpired collection. A changed subreddit selection will not display the previous source’s cache. Clearing the cache or uninstalling removes the local collection.

Attribution appears on the slideshow and in previews, with a link to the original post. Images remain their creators’ content; Reddit hosting does not grant reuse rights. Review Reddit’s current access, display and retention requirements before distribution. No Reddit content is bundled in the repository or test APK.

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
2. Press OK once: the dock opens without launching an app. Navigate left/right to both ends; verify focus and scrolling. Press Back: only the dock hides.
3. Reveal the dock and launch a TV app. Press Home: the stock launcher should appear. Open AndroidSimpleTV again from its app tile (or the explicit activity command below).
4. Observe 55 seconds of stillness then a five-second crossfade. Leave midway through a fade, wait, and reopen: it should resume the frozen position.
5. Press Home and verify Google TV remains the default. Check sleep/wake with the physical remote. With Keep slideshow awake off, normal idle behavior should remain. With it on, leave the launcher visible beyond the configured idle interval, then verify that leaving the launcher releases the screen-on request.

```sh
adb -s YOUR_TV_SERIAL shell am start -W -n com.traceur.androidsimpletv/.MainActivity
# Remove ONLY this test app if desired (also removes its app data):
adb -s YOUR_TV_SERIAL uninstall com.traceur.androidsimpletv
```

Never run launcher-disable, system-package-uninstall, or `set-home-activity` commands for this test. See [validation notes](docs/VALIDATION.md) for what was actually exercised.

## Scope and contributions

This MVP has no favorites groups, automatic wallpaper refresh, arbitrary image-host support, custom screensaver, background service, analytics, or boot receiver. Those can be added in focused follow-up PRs. Launching other apps relies on their exported TV activities; unavailable apps fail with a message rather than crashing the launcher.

`WallpaperSettingsActivity.kt` provides source setup and previews, `RedditWallpapers.kt` handles OAuth and filtered listings, and `WallpaperStore.kt` owns bounded image decoding and the private cache. `SettingsActivity.kt` provides TV settings, `LauncherPreferences.kt` persists dock/weather choices, and `WeatherClient.kt` handles bounded HTTPS requests. `MainActivity.kt` owns the native view hierarchy, package discovery, remote focus and foreground lifecycle. `SlideshowTimeline.kt` is a pure Kotlin timing model with boundary tests. Robolectric tests exercise actual activity lifecycle and remote key gestures. All three landscapes and the banner artwork were authored for this project; banner lettering uses DejaVu Sans (see [font notice](docs/FONT-NOTICE.txt)).

Manifest setup follows the [Android TV app guide](https://developer.android.com/training/tv/get-started/create). Tool versions follow the [AGP 8.9 compatibility table](https://developer.android.com/build/releases/agp-8-9-0-release-notes).
