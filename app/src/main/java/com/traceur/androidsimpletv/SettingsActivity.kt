package com.traceur.androidsimpletv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch
import java.util.concurrent.Executors

class SettingsActivity : Activity() {
    private lateinit var preferences: LauncherPreferences
    private lateinit var locationButton: Button
    private lateinit var unitsButton: Button
    private lateinit var status: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val client = WeatherClient()
    private var generation = 0
    private var active = false
    private var pending: java.util.concurrent.Future<*>? = null
    private var dockDialog: AlertDialog? = null
    private var apps = emptyList<AppEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = LauncherPreferences(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
            setBackgroundColor(0xFF132333.toInt())
        }
        setContentView(ScrollView(this).apply { addView(column) })
        column.addView(TextView(this).apply { text = getString(R.string.settings_title); textSize = 28f; setTextColor(-1) })
        fun button(title: String, action: () -> Unit): Button = Button(this).apply {
            text = title
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { action() }
            column.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        button(getString(R.string.move_apps)) {
            if (apps.isEmpty()) status.setText(R.string.apps_loading)
            else dockDialog = DockSettingsDialogs.reorder(this, apps, preferences)
        }
        button(getString(R.string.show_hide_apps)) {
            if (apps.isEmpty()) status.setText(R.string.apps_loading)
            else dockDialog = DockSettingsDialogs.visibility(this, apps, preferences)
        }
        button(getString(R.string.wallpapers_title)) {
            startActivity(android.content.Intent(this, WallpaperSettingsActivity::class.java))
        }
        button(getString(R.string.sort_apps)) {
            preferences.sortAlphabetically()
            status.setText(R.string.sorted_apps)
        }
        column.addView(Switch(this).apply {
            text = getString(R.string.keep_slideshow_awake)
            textSize = 18f
            setPadding(16, 20, 16, 20)
            isChecked = preferences.keepSlideshowAwake
            setOnCheckedChangeListener { _, checked -> preferences.keepSlideshowAwake = checked }
        }, LinearLayout.LayoutParams(-1, -2))
        column.addView(TextView(this).apply {
            text = getString(R.string.keep_slideshow_awake_help)
            textSize = 14f
            setPadding(16, 0, 16, 16)
        })
        locationButton = button("") { searchLocation() }
        unitsButton = button("") {
            preferences.fahrenheit = !preferences.fahrenheit
            updateLabels()
        }
        button(getString(R.string.weather_disable)) {
            generation++
            pending?.cancel(true)
            client.cancel()
            preferences.location = null
            status.setText(R.string.weather_disabled)
            updateLabels()
        }
        button(getString(R.string.done)) { finish() }
        status = TextView(this).apply { textSize = 16f; setTextColor(-1) }
        column.addView(status)
        column.addView(TextView(this).apply {
            text = getString(R.string.weather_attribution)
            textSize = 14f
            setTextColor(0xFFB8E4DE.toInt())
        })
        updateLabels()
    }

    override fun onResume() {
        super.onResume()
        active = true
        executor.execute {
            val loaded = loadTvApps(this)
            handler.post { if (active) apps = loaded }
        }
    }

    override fun onPause() {
        active = false
        dockDialog?.dismiss()
        dockDialog = null
        generation++
        pending?.cancel(true)
        client.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun updateLabels() {
        locationButton.text = getString(R.string.weather_location, preferences.location?.name ?: getString(R.string.not_set))
        unitsButton.text = getString(R.string.temperature_units, if (preferences.fahrenheit) "°F" else "°C")
    }

    private fun searchLocation() {
        val input = EditText(this).apply {
            hint = getString(R.string.location_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isFocusableInTouchMode = true
            tag = "location_input"
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.location_search).setView(input)
            .setPositiveButton(R.string.search) { _, _ ->
                val query = input.text.toString().trim()
                if (query.length < 2) { status.setText(R.string.location_too_short); return@setPositiveButton }
                val token = ++generation
                pending?.cancel(true)
                client.cancel()
                status.setText(R.string.searching)
                pending = executor.submit {
                    val result = runCatching { client.search(query) }
                    handler.post {
                        if (!active || token != generation) return@post
                        result.fold(onSuccess = { places ->
                            status.text = ""
                            if (places.isEmpty()) { status.setText(R.string.location_not_found); return@fold }
                            AlertDialog.Builder(this).setTitle(R.string.choose_location)
                                .setItems(places.map { it.name }.toTypedArray()) { _, index ->
                                    preferences.location = places[index]
                                    updateLabels()
                                    status.setText(R.string.location_saved)
                                }.setNegativeButton(R.string.cancel, null).show()
                        }, onFailure = { status.setText(R.string.weather_search_failed) })
                    }
                }
            }.setNegativeButton(R.string.cancel, null).create()
        fun showKeyboard() {
            input.requestFocus()
            input.post {
                if (dialog.isShowing) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        input.setOnClickListener { showKeyboard() }
        input.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.action == KeyEvent.ACTION_UP) showKeyboard()
                true
            } else false
        }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                true
            } else false
        }
        dialog.setOnShowListener {
            dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            showKeyboard()
        }
        dialog.show()
    }
}
