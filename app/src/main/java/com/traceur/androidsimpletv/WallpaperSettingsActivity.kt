package com.traceur.androidsimpletv

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.util.concurrent.Executors

class WallpaperSettingsActivity : Activity() {
    private lateinit var preferences: LauncherPreferences
    private lateinit var store: WallpaperStore
    private lateinit var status: TextView
    private lateinit var source: Switch
    private lateinit var subredditButton: Button
    private lateinit var setupButton: Button
    private lateinit var refreshButton: Button
    private lateinit var previewButton: Button
    private lateinit var clearButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val client = RedditClient()
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
            text = getString(R.string.reddit_enable); textSize = 18f; setPadding(dp(8), dp(10), dp(8), dp(10))
            isChecked = preferences.redditEnabled
            setOnCheckedChangeListener { _, checked -> preferences.redditEnabled = checked }
        }
        column.addView(source, LinearLayout.LayoutParams(-1, -2))
        text(getString(R.string.reddit_intro))
        subredditButton = button("") { editSubreddits() }
        setupButton = button("") { editAccess() }
        refreshButton = button(getString(R.string.reddit_refresh)) { refresh() }
        previewButton = button(getString(R.string.reddit_preview)) { loadPreview() }
        clearButton = button(getString(R.string.reddit_clear)) {
            runWork({ store.clear() }) { status.setText(R.string.reddit_cleared) }
        }
        button(getString(R.string.done)) { finish() }
        status = text("")
        text(getString(R.string.reddit_privacy), 13f)
        labels()
    }

    override fun onResume() {
        super.onResume(); active = true
        runWork({ store.read(preferences.redditConfig.sourceKey) }) { album ->
            status.text = album?.let { resources.getQuantityString(R.plurals.reddit_cached_count, it.photos.size, it.photos.size) } ?: getString(R.string.reddit_empty)
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
        subredditButton.text = getString(R.string.reddit_subreddits, preferences.redditConfig.subreddits.joinToString(", ") { "r/$it" })
        setupButton.setText(if (preferences.redditConfig.ready) R.string.reddit_access_saved else R.string.reddit_access)
    }
    private fun input(value: String, hintText: String) = EditText(this).apply {
        setText(value); hint = hintText; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; setSingleLine()
    }
    private fun editSubreddits() {
        val field = input(preferences.redditConfig.subreddits.joinToString(", "), getString(R.string.reddit_subreddit_hint))
        dialog = AlertDialog.Builder(this).setTitle(R.string.reddit_choose_subreddits).setView(field)
            .setPositiveButton(R.string.reddit_save, null).setNegativeButton(R.string.cancel, null).create().apply {
                setOnShowListener { getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val names = runCatching { RedditConfig.subreddits(field.text.toString()) }.getOrNull()
                    if (names == null) { field.error = getString(R.string.reddit_subreddit_error); return@setOnClickListener }
                    preferences.redditConfig = preferences.redditConfig.copy(subreddits = names)
                    labels(); status.setText(R.string.reddit_source_saved); dismiss()
                } }; show()
            }
    }
    private fun editAccess() {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        fields.addView(TextView(this).apply { text = getString(R.string.reddit_access_help); textSize = 16f })
        val id = input(preferences.redditConfig.clientId, getString(R.string.reddit_client_id))
        val contact = input(preferences.redditConfig.contact, getString(R.string.reddit_contact))
        fields.addView(id); fields.addView(contact)
        dialog = AlertDialog.Builder(this).setTitle(R.string.reddit_access).setView(fields)
            .setPositiveButton(R.string.reddit_save, null).setNegativeButton(R.string.cancel, null).create().apply {
                setOnShowListener { getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val config = preferences.redditConfig.copy(clientId = id.text.toString().trim(), contact = contact.text.toString().trim().removePrefix("u/"))
                    if (!config.ready) { id.error = getString(R.string.reddit_access_invalid); return@setOnClickListener }
                    preferences.redditConfig = config; labels(); status.setText(R.string.reddit_access_not_verified); dismiss()
                } }; show()
            }
    }
    private fun refresh() {
        val config = preferences.redditConfig
        if (!config.ready) { status.setText(R.string.reddit_setup_needed); editAccess(); return }
        if (System.currentTimeMillis() < preferences.redditRetryAt) { status.setText(R.string.reddit_rate_limit); return }
        preferences.redditRetryAt = System.currentTimeMillis() + 60_000L
        status.setText(R.string.reddit_downloading)
        runWork({ store.refresh(config, client) }) { album ->
            status.text = resources.getQuantityString(R.plurals.reddit_downloaded, album.photos.size, album.photos.size)
        }
    }
    private fun loadPreview() {
        runWork({ store.load(preferences.redditConfig.sourceKey) }) { loaded ->
            if (loaded == null) { status.setText(R.string.reddit_empty); return@runWork }
            preview = loaded; showPreview(0)
        }
    }
    private fun showPreview(index: Int) {
        val loaded = preview ?: return
        val item = loaded.images[index]
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(ImageView(this).apply { setImageBitmap(item.bitmap); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, dp(240)))
        column.addView(TextView(this).apply { text = getString(R.string.reddit_preview_caption, item.photo.title, item.photo.credit); textSize = 16f; setPadding(dp(16), dp(4), dp(16), dp(4)) })
        dialog = AlertDialog.Builder(this).setTitle(getString(R.string.reddit_preview_number, index + 1, loaded.images.size)).setView(column)
            .setNegativeButton(R.string.done) { _, _ -> preview = null }
            .setNeutralButton(R.string.reddit_open_post) { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.photo.permalink))) }
                catch (_: ActivityNotFoundException) { status.setText(R.string.reddit_no_browser) }
            }.setPositiveButton(R.string.reddit_next) { _, _ -> showPreview((index + 1) % loaded.images.size) }.show()
    }
    private fun setBusy(value: Boolean) {
        busy = value
        listOf(subredditButton, setupButton, refreshButton, previewButton, clearButton).forEach { it.isEnabled = !value }
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
                    if (error is RedditAccessException && error.code == 429) preferences.redditRetryAt = System.currentTimeMillis() + error.retryMs
                    status.setText(when {
                        error is RedditAccessException && error.code in listOf(401, 403) -> R.string.reddit_denied
                        error is RedditAccessException && error.code == 429 -> R.string.reddit_rate_limit
                        else -> R.string.reddit_failed
                    })
                }
            }
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
