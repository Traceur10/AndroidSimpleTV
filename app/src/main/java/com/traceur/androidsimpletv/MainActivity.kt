package com.traceur.androidsimpletv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
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
    internal lateinit var timeline: SlideshowTimeline
    private val handler = Handler(Looper.getMainLooper())
    private val appLoader = Executors.newSingleThreadExecutor()
    private var loadGeneration = 0
    private var resumed = false
    private var lastTick = 0L
    private var renderedIndex = -1
    private var selectedComponent: String? = null
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
        timeline = SlideshowTimeline(wallpapers.size, savedInstanceState?.getLong("elapsed") ?: 0L)
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
        lastTick = SystemClock.elapsedRealtime()
        slideshowTick.run()
        clockTick.run()
        loadApps()
    }

    override fun onPause() {
        advanceTimeline()
        resumed = false
        consumedKey = null
        handler.removeCallbacks(slideshowTick)
        handler.removeCallbacks(clockTick)
        // Ignore pending package-query results after leaving the foreground.
        loadGeneration++
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        appLoader.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        advanceTimeline()
        outState.putLong("elapsed", timeline.elapsedMs)
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
        if (renderedIndex != timeline.imageIndex) {
            renderedIndex = timeline.imageIndex
            baseImage.setImageResource(wallpapers[timeline.imageIndex])
            nextImage.setImageResource(wallpapers[timeline.nextImageIndex])
        }
        nextImage.alpha = timeline.fadeFraction
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
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            val apps = packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName && it.activityInfo.exported }
                .distinctBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .map { AppEntry(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(packageManager).toString(), it.loadIcon(packageManager)) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            handler.post {
                if (resumed && generation == loadGeneration && !isDestroyed) populateApps(apps)
            }
        }
    }

    private fun populateApps(apps: List<AppEntry>) {
        appRow.removeAllViews()
        if (apps.isEmpty()) appRow.addView(label(getString(R.string.empty_apps), 18f))
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
    }

    private fun hideDock() {
        dock.visibility = View.GONE
        hint.visibility = View.VISIBLE
        root.requestFocus()
    }

    // Consume the complete reveal/hide gesture so OK-up cannot launch a card,
    // Back-up cannot exit, and a held key cannot toggle the dock repeatedly.
    private var consumedKey: Int? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == consumedKey) {
            if (event.action == KeyEvent.ACTION_UP) consumedKey = null
            return true
        }
        val visible = dock.visibility == View.VISIBLE
        val reveal = !visible && event.keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU)
        val hide = visible && event.keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU)
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
    private data class AppEntry(val component: ComponentName, val label: String, val icon: android.graphics.drawable.Drawable)
}
