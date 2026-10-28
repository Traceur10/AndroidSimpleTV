package com.traceur.androidsimpletv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val wallpapers = intArrayOf(R.drawable.wallpaper_alpine, R.drawable.wallpaper_dunes, R.drawable.wallpaper_coast)
    private val wallpaperExecutor = Executors.newSingleThreadExecutor()
    private var wallpaperJob: java.util.concurrent.Future<*>? = null
    private var wallpaperGeneration = 0
    private val wallpaperClient = PublicWallpaperClient()
    private var loadedAlbum: LoadedAlbum? = null
    private var restoredAlbumKey: String? = null
    private var restoredElapsed = 0L
    private lateinit var photoCredit: TextView
    private val wallpaperCheck = Runnable { loadWallpapers() }
    internal lateinit var timeline: SlideshowTimeline
    private lateinit var preferences: LauncherPreferences
    private lateinit var forecastView: LinearLayout
    private lateinit var weatherView: TextView
    private val weatherExecutor = Executors.newSingleThreadExecutor()
    private val weatherClient = WeatherClient()
    private var weatherJob: java.util.concurrent.Future<*>? = null
    private var weatherGeneration = 0
    private val weatherTick = Runnable { refreshWeather() }
    private val handler = Handler(Looper.getMainLooper())
    private val appLoader = Executors.newSingleThreadExecutor()
    private var loadGeneration = 0
    private var resumed = false
    private var lastTick = 0L
    private var renderedIndex = -1
    private var selectedComponent: String? = null
    private val dockIdleTimeout = Runnable { if (resumed && dock.visibility == View.VISIBLE) hideDock() }
    private fun resetDockIdleTimeout() {
        handler.removeCallbacks(dockIdleTimeout)
        if (resumed && dock.visibility == View.VISIBLE) handler.postDelayed(dockIdleTimeout, 10_000L)
    }
    private fun saveWallpaperPosition() {
        loadedAlbum?.album?.let { preferences.saveWallpaperPosition("${it.source}:${it.fetched}", timeline.elapsedMs) }
    }
    private lateinit var root: FrameLayout
    private lateinit var baseImage: ImageView
    private lateinit var nextImage: ImageView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var hint: TextView
    private lateinit var dock: LinearLayout
    private lateinit var appRow: LinearLayout

    private val slideshowTick = object : Runnable {
        override fun run() {
            if (!resumed) return
            advanceTimeline()
            renderWallpaper()
            handler.postDelayed(this, timeline.nextFrameDelayMs)
        }
    }
    private val clockTick = object : Runnable {
        override fun run() {
            if (!resumed) return
            val now = Date()
            clock.text = DateFormat.getTimeFormat(this@MainActivity).format(now)
            val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")
            date.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = LauncherPreferences(this)
        timeline = SlideshowTimeline(wallpapers.size, savedInstanceState?.getLong("elapsed") ?: 0L)
        restoredAlbumKey = savedInstanceState?.getString("album")
        restoredElapsed = savedInstanceState?.getLong("elapsed") ?: 0L
        selectedComponent = savedInstanceState?.getString("selected")
        createLayout()
        renderWallpaper()
        if (savedInstanceState?.getBoolean("dock") == true) showDock()
    }

    @Suppress("DEPRECATION")
    private fun fullscreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullscreen()
    }

    override fun onResume() {
        super.onResume()
        fullscreen()
        resumed = true
        if (preferences.keepSlideshowAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lastTick = SystemClock.elapsedRealtime()
        slideshowTick.run()
        clockTick.run()
        loadApps()
        refreshWeather()
        loadWallpapers()
        resetDockIdleTimeout()
    }

    override fun onPause() {
        advanceTimeline()
        saveWallpaperPosition()
        handler.removeCallbacks(dockIdleTimeout)
        resumed = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        consumedKey = null
        wallpaperGeneration++
        wallpaperJob?.cancel(true)
        wallpaperClient.cancel()
        handler.removeCallbacks(wallpaperCheck)
        weatherGeneration++
        weatherJob?.cancel(true)
        weatherClient.cancel()
        handler.removeCallbacks(weatherTick)
        handler.removeCallbacks(slideshowTick)
        handler.removeCallbacks(clockTick)
        // Ignore pending package-query results after leaving the foreground.
        loadGeneration++
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        appLoader.shutdownNow()
        weatherExecutor.shutdownNow()
        wallpaperExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        advanceTimeline()
        outState.putLong("elapsed", timeline.elapsedMs)
        outState.putString("album", loadedAlbum?.album?.let { "${it.source}:${it.fetched}" })
        outState.putBoolean("dock", dock.visibility == View.VISIBLE)
        outState.putString("selected", selectedComponent)
        super.onSaveInstanceState(outState)
    }

    private fun advanceTimeline() {
        if (!resumed) return
        val now = SystemClock.elapsedRealtime()
        timeline.advance((now - lastTick).coerceAtLeast(0L))
        lastTick = now
    }

    private fun renderWallpaper() {
        photoCredit.visibility = if (preferences.showPhotoInformation) View.VISIBLE else View.GONE
        if (renderedIndex != timeline.imageIndex) {
            renderedIndex = timeline.imageIndex
            saveWallpaperPosition()
            val photos = loadedAlbum?.images
            if (photos.isNullOrEmpty()) {
                baseImage.setImageResource(wallpapers[timeline.imageIndex])
                nextImage.setImageResource(wallpapers[timeline.nextImageIndex])
                photoCredit.text = ""
            } else {
                baseImage.setImageBitmap(photos[timeline.imageIndex].bitmap)
                nextImage.setImageBitmap(photos[timeline.nextImageIndex].bitmap)
                photoCredit.text = photos[timeline.imageIndex].photo.let { "${it.credit} · ${it.license} · Cropped · Settings → Wallpapers for source" }
            }
        }
        nextImage.alpha = timeline.fadeFraction
    }

    private fun loadWallpapers() {
        handler.removeCallbacks(wallpaperCheck)
        val token = ++wallpaperGeneration
        val enabled = preferences.publicWallpapersEnabled
        val config = preferences.wallpaperConfig
        val source = config.sourceKey
        if (!enabled || loadedAlbum?.album?.let { it.source != source || !it.usable(System.currentTimeMillis()) } == true) applyAlbum(null)
        val previous = loadedAlbum
        wallpaperJob = wallpaperExecutor.submit {
            val result = runCatching {
                val store = WallpaperStore(this)
                val metadata = store.read(source)
                val initial = if (!enabled) null else if (metadata != null && metadata == previous?.album) previous else store.load(source)
                // Show the existing offline album before waiting for network refresh.
                handler.post { if (resumed && token == wallpaperGeneration) applyAlbum(initial) }
                if (enabled && preferences.claimWallpaperRefresh()) {
                    try { store.refresh(config, wallpaperClient) }
                    catch (e: WallpaperHttpException) {
                        if (e.code == 429) preferences.wallpaperRetryAt = maxOf(preferences.wallpaperRetryAt, System.currentTimeMillis() + e.retryMs)
                    } catch (_: java.io.IOException) { /* Keep the last complete album on failure. */ }
                    checkInterrupted()
                    val current = store.read(source)
                    if (current == initial?.album) initial else store.load(source)
                } else initial
            }.getOrNull()
            handler.post {
                if (!resumed || token != wallpaperGeneration) return@post
                applyAlbum(result)
                // Recheck the persisted hourly lease without making a network request every minute.
                handler.postDelayed(wallpaperCheck, 60_000L)
            }
        }
    }

    private fun applyAlbum(album: LoadedAlbum?) {
        if (loadedAlbum === album) return
        loadedAlbum = album
        val key = album?.album?.let { "${it.source}:${it.fetched}" }
        timeline = SlideshowTimeline(album?.images?.size ?: wallpapers.size,
            if (key != null && key == restoredAlbumKey) restoredElapsed else key?.let { preferences.wallpaperPosition(it) } ?: 0L,
            loop = album == null)
        restoredAlbumKey = null
        lastTick = SystemClock.elapsedRealtime()
        renderedIndex = -1
        renderWallpaper()
        if (resumed) {
            handler.removeCallbacks(slideshowTick)
            handler.postDelayed(slideshowTick, timeline.nextFrameDelayMs)
        }
    }

    private fun createLayout() {
        root = FrameLayout(this).apply {
            isFocusableInTouchMode = true
            tag = "wallpaper_root"
        }
        setContentView(root)
        baseImage = wallpaperView()
        nextImage = wallpaperView()
        root.addView(baseImage)
        root.addView(nextImage)
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x5507111C, 0x0007111C, 0x8807111C.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val clockGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = label("", 64f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
        date = label("", 20f)
        clockGroup.addView(clock)
        clockGroup.addView(date)
        root.addView(clockGroup, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(48), dp(34), dp(48), 0)
        })
        weatherView = label("", 17f).apply { gravity = Gravity.END }
        val weatherGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        weatherGroup.addView(weatherView)
        forecastView = LinearLayout(this).apply {
            tag = "weather_forecast_container"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(0x66132333.toInt(), 14f)
            visibility = View.GONE
        }
        weatherGroup.addView(forecastView)
        root.addView(weatherGroup, FrameLayout.LayoutParams(dp(720), -2, Gravity.TOP or Gravity.END).apply {
            setMargins(dp(48), dp(48), dp(48), 0)
        })
        photoCredit = label("", 12f).apply { maxLines = 2; tag = "photo_credit" }
        root.addView(photoCredit, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(48), 0, dp(48), dp(8))
        })
        hint = label(getString(R.string.dock_hint), 16f).apply { gravity = Gravity.CENTER }
        root.addView(hint, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM).apply { bottomMargin = dp(24) })
        dock = LinearLayout(this).apply {
            tag = "app_dock"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(12))
            background = rounded(0xED132333.toInt(), 20f)
            visibility = View.GONE
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(getString(R.string.dock_title), 20f), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(label(getString(R.string.dock_controls), 13f))
        dock.addView(heading)
        appRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            addView(appRow)
        }
        dock.addView(scroll, LinearLayout.LayoutParams(-1, dp(122)))
        root.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(40), 0, dp(40), dp(28))
        })
        root.requestFocus()
    }

    private fun wallpaperView() = ImageView(this).apply {
        layoutParams = FrameLayout.LayoutParams(-1, -1)
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        val generation = ++loadGeneration
        appLoader.execute {
            val discovered = loadTvApps(this)
            val byId = discovered.associateBy { it.component.flattenToString() }
            val apps = preferences.ordered(discovered.map { it.component.flattenToString() })
                .filterNot { preferences.isHidden(it) }.mapNotNull { byId[it] }
            handler.post {
                if (resumed && generation == loadGeneration && !isDestroyed) populateApps(apps)
            }
        }
    }

    private fun populateApps(apps: List<AppEntry>) {
        appRow.removeAllViews()
        apps.forEach { app ->
            val card = LinearLayout(this).apply {
                tag = app.component.flattenToString()
                id = View.generateViewId()
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                contentDescription = app.label
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = rounded(0xFF23364A.toInt(), 12f)
                setOnFocusChangeListener { view, focused ->
                    view.background = rounded(if (focused) 0xFFB8E4DE.toInt() else 0xFF23364A.toInt(), 12f)
                    (getChildAt(1) as TextView).setTextColor(if (focused) 0xFF102332.toInt() else Color.WHITE)
                    if (focused) selectedComponent = app.component.flattenToString()
                }
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, R.string.launch_failed, Toast.LENGTH_SHORT).show()
                        loadApps()
                    } catch (_: SecurityException) {
                        Toast.makeText(this@MainActivity, R.string.launch_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            card.addView(ImageView(this).apply { setImageDrawable(app.icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(48), dp(48)))
            card.addView(label(app.label, 14f).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, dp(30)))
            appRow.addView(card, LinearLayout.LayoutParams(dp(136), dp(104)).apply { marginEnd = dp(12) })
        }
        val settings = label(getString(R.string.settings_title), 18f).apply {
            id = View.generateViewId()
            tag = "settings"
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = rounded(0xFF23364A.toInt(), 12f)
            setOnFocusChangeListener { _, focused ->
                background = rounded(if (focused) 0xFFB8E4DE.toInt() else 0xFF23364A.toInt(), 12f)
                setTextColor(if (focused) 0xFF102332.toInt() else Color.WHITE)
                if (focused) selectedComponent = "settings"
            }
            setOnClickListener { openSettings() }
        }
        appRow.addView(settings, LinearLayout.LayoutParams(dp(136), dp(104)))
        // Keep left/right navigation inside the row, including the end cards.
        for (index in 0 until appRow.childCount) {
            appRow.getChildAt(index).apply {
                nextFocusLeftId = appRow.getChildAt((index - 1).coerceAtLeast(0)).id
                nextFocusRightId = appRow.getChildAt((index + 1).coerceAtMost(appRow.childCount - 1)).id
            }
        }
        if (dock.visibility == View.VISIBLE) focusSelectedApp()
    }

    private fun focusSelectedApp() {
        val selected = (0 until appRow.childCount).map { appRow.getChildAt(it) }
            .firstOrNull { it.tag == selectedComponent && it.isFocusable }
        if (selected?.requestFocus() != true && appRow.getChildAt(0)?.requestFocus() != true) root.requestFocus()
    }

    private fun showDock() {
        dock.visibility = View.VISIBLE
        hint.visibility = View.GONE
        focusSelectedApp()
        resetDockIdleTimeout()
    }

    private fun hideDock() {
        handler.removeCallbacks(dockIdleTimeout)
        dock.visibility = View.GONE
        hint.visibility = View.VISIBLE
        root.requestFocus()
    }

    // Consume the complete reveal/hide gesture so OK-up cannot launch a card,
    // Back-up cannot exit, and a held key cannot toggle the dock repeatedly.
    private var consumedKey: Int? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Every key event, including repeat/down/up, counts as activity while the dock is open.
        resetDockIdleTimeout()
        if (event.keyCode == consumedKey) {
            if (event.action == KeyEvent.ACTION_UP) consumedKey = null
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
            consumedKey = event.keyCode
            openSettings()
            return true
        }
        val visible = dock.visibility == View.VISIBLE
        val reveal = !visible && event.keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
        val hide = visible && event.keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK)
        if (event.action == KeyEvent.ACTION_DOWN && (reveal || hide)) {
            consumedKey = event.keyCode
            if (hide) hideDock() else showDock()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Legacy Back support is needed for older TVs")
    override fun onBackPressed() {
        if (dock.visibility == View.VISIBLE) hideDock() else super.onBackPressed()
    }

    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        setShadowLayer(3f, 0f, 1f, 0x66000000)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * resources.displayMetrics.density
    }
    private fun openSettings() { startActivity(Intent(this, SettingsActivity::class.java)) }

    private fun refreshWeather() {
        if (!resumed) return
        handler.removeCallbacks(weatherTick)
        val location = preferences.location
        if (location == null) { weatherView.setText(R.string.weather_setup); forecastView.visibility = View.GONE; return }
        val includeForecast = preferences.showForecast
        val cached = preferences.weather
        if (cached != null && cached.isFresh(System.currentTimeMillis()) && (!includeForecast || cached.forecast.size == 5 && cached.forecast.first().date == ForecastDay.today(cached.timezone))) {
            displayWeather(location, cached)
            handler.postDelayed(weatherTick, (cached.fetchedAt + 30 * 60_000L - System.currentTimeMillis()).coerceAtLeast(1_000L))
            return
        }
        displayWeather(location, cached)
        val token = ++weatherGeneration
        weatherJob = weatherExecutor.submit {
            val result = runCatching { weatherClient.current(location, includeForecast) }
            handler.post {
                if (!resumed || token != weatherGeneration) return@post
                result.fold(onSuccess = {
                    preferences.weather = it
                    displayWeather(location, it)
                }, onFailure = { displayWeather(location, cached, true) })
                handler.postDelayed(weatherTick, 30 * 60_000L)
            }
        }
    }

    private fun displayWeather(location: WeatherLocation, reading: WeatherReading?, failed: Boolean = false) {
        val temperature = reading?.let { "${it.temperature(preferences.fahrenheit)}${if (preferences.fahrenheit) "°F" else "°C"}" }
            ?: getString(if (failed) R.string.weather_unavailable else R.string.weather_loading)
        val stale = if (reading != null && !reading.isFresh(System.currentTimeMillis())) {
            "\n" + getString(R.string.weather_cached, DateFormat.getDateFormat(this).format(Date(reading.fetchedAt)) + " " + DateFormat.getTimeFormat(this).format(Date(reading.fetchedAt)))
        } else ""
        weatherView.text = getString(R.string.weather_display, location.name, temperature, stale)
        forecastView.visibility = if (preferences.showForecast) View.VISIBLE else View.GONE
        // The API returns the forecast in local-date order; keep cached days visible even
        // when the TV crosses midnight before the next refresh.
        val days = reading?.forecast.orEmpty().take(5)
        forecastView.removeAllViews()
        if (days.isEmpty()) {
            forecastView.addView(label(getString(if (failed || reading != null) R.string.forecast_unavailable else R.string.forecast_loading), 14f).apply { tag = "weather_forecast" })
        } else {
            days.forEach { day ->
                val card = TextView(this).apply {
                    if (day == days.first()) tag = "weather_forecast"
                    text = day.label(preferences.fahrenheit).replace("\n", "\n")
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    maxLines = 4
                    minWidth = dp(120)
                }
                forecastView.addView(card, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
    }
}
