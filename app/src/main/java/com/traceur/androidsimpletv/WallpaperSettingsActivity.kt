package com.traceur.androidsimpletv

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import java.util.concurrent.Executors

class WallpaperSettingsActivity : Activity() {
    private lateinit var preferences: LauncherPreferences
    private lateinit var store: WallpaperStore
    private lateinit var status: TextView
    private lateinit var source: Switch
    private lateinit var sourcesButton: Button
    private lateinit var refreshButton: Button
    private lateinit var previewButton: Button
    private lateinit var clearButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val client = PublicWallpaperClient()
    private var job: java.util.concurrent.Future<*>? = null
    private var generation = 0
    private var active = false
    private var dialog: AlertDialog? = null
    private var preview: LoadedAlbum? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = LauncherPreferences(this)
        store = WallpaperStore(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(20), dp(32), dp(20)); setBackgroundColor(0xFF132333.toInt())
        }
        setContentView(ScrollView(this).apply { addView(column) })
        fun text(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(-1); setPadding(dp(8), dp(6), dp(8), dp(6)); column.addView(this)
        }
        fun button(title: String, action: () -> Unit) = Button(this).apply {
            text = title; gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { action() }; column.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        text(getString(R.string.wallpapers_title), 28f)
        source = Switch(this).apply {
            text = getString(R.string.public_enable); textSize = 18f; setPadding(dp(8), dp(10), dp(8), dp(10))
            isChecked = preferences.publicWallpapersEnabled
            setOnCheckedChangeListener { _, checked ->
                preferences.publicWallpapersEnabled = checked
                if (!checked && busy) {
                    generation++; job?.cancel(true); client.cancel(); setBusy(false)
                    status.setText(R.string.public_off)
                }
            }
        }
        column.addView(source, LinearLayout.LayoutParams(-1, -2))
        text(getString(R.string.public_intro))
        sourcesButton = button("") { chooseSources() }
        refreshButton = button(getString(R.string.public_refresh)) { refresh() }
        previewButton = button(getString(R.string.public_preview)) { loadPreview() }
        clearButton = button(getString(R.string.public_clear)) {
            source.isChecked = false
            runWork({ store.clear() }) { status.setText(R.string.public_cleared) }
        }
        button(getString(R.string.done)) { finish() }
        status = text("")
        text(getString(R.string.public_privacy), 13f)
        labels()
    }

    override fun onResume() {
        super.onResume(); active = true
        runWork({ store.read(preferences.wallpaperConfig.sourceKey) }) { album ->
            status.text = album?.let { resources.getQuantityString(R.plurals.public_cached_count, it.photos.size, it.photos.size) } ?: getString(R.string.public_empty)
        }
    }
    override fun onPause() {
        active = false; generation++; job?.cancel(true); client.cancel()
        dialog?.dismiss(); dialog = null; preview = null
        setBusy(false)
        super.onPause()
    }
    override fun onDestroy() { executor.shutdownNow(); handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun labels() {
        sourcesButton.text = getString(R.string.public_sources, preferences.wallpaperConfig.sources.sortedBy { it.name }.joinToString(" + ") { it.label })
    }
    private fun chooseSources() {
        val values = WallpaperSource.values()
        val chosen = preferences.wallpaperConfig.sources.toMutableSet()
        dialog = AlertDialog.Builder(this).setTitle(R.string.public_choose_sources)
            .setMultiChoiceItems(values.map { it.label }.toTypedArray(), values.map { it in chosen }.toBooleanArray()) { _, index, checked ->
                if (checked) chosen.add(values[index]) else chosen.remove(values[index])
            }.setPositiveButton(R.string.public_save, null).setNegativeButton(R.string.cancel, null).create().apply {
                setOnShowListener { getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (chosen.isEmpty()) { Toast.makeText(this@WallpaperSettingsActivity, R.string.public_need_source, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    preferences.wallpaperConfig = WallpaperConfig(chosen.toSet())
                    labels(); status.setText(R.string.public_source_saved); dismiss()
                } }; show()
            }
    }
    private fun refresh() {
        val config = preferences.wallpaperConfig
        status.setText(R.string.public_downloading)
        runWork({
            if (!preferences.claimWallpaperRefresh()) throw RefreshNotDue()
            store.refresh(config, client)
        }) { album -> status.text = resources.getQuantityString(R.plurals.public_downloaded, album.photos.size, album.photos.size) }
    }
    private class RefreshNotDue : Exception()
    private fun loadPreview() {
        runWork({ store.load(preferences.wallpaperConfig.sourceKey) }) { loaded ->
            if (loaded == null) { status.setText(R.string.public_empty); return@runWork }
            preview = loaded; showPreview(0)
        }
    }
    private fun showPreview(index: Int) {
        val loaded = preview ?: return
        val item = loaded.images[index]
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(ImageView(this).apply { setImageBitmap(item.bitmap); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, dp(240)))
        column.addView(TextView(this).apply { text = getString(R.string.public_preview_caption, item.photo.title, item.photo.credit, item.photo.license, item.photo.licenseUrl); textSize = 16f; setPadding(dp(16), dp(4), dp(16), dp(4)) })
        dialog = AlertDialog.Builder(this).setTitle(getString(R.string.public_preview_number, index + 1, loaded.images.size)).setView(ScrollView(this).apply { addView(column) })
            .setNegativeButton(R.string.done) { _, _ -> preview = null }
            .setNeutralButton(R.string.public_open_source) { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.photo.pageUrl))) }
                catch (_: ActivityNotFoundException) { status.setText(R.string.public_no_browser) }
            }.setPositiveButton(R.string.public_next) { _, _ -> showPreview((index + 1) % loaded.images.size) }.show()
    }
    private fun setBusy(value: Boolean) {
        busy = value
        listOf(sourcesButton, refreshButton, previewButton, clearButton).forEach { it.isEnabled = !value }
    }
    private fun <T> runWork(work: () -> T, success: (T) -> Unit) {
        if (busy) return
        val token = ++generation; setBusy(true)
        job = executor.submit {
            val result = runCatching(work)
            handler.post {
                if (!active || token != generation) return@post
                setBusy(false)
                result.fold(success) { error ->
                    if (error is WallpaperHttpException && error.code == 429) preferences.wallpaperRetryAt = maxOf(preferences.wallpaperRetryAt, System.currentTimeMillis() + error.retryMs)
                    status.text = if (error is RefreshNotDue || error is WallpaperHttpException && error.code == 429) {
                        getString(R.string.public_wait, android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(preferences.wallpaperRetryAt)))
                    } else getString(R.string.public_failed)
                }
            }
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
