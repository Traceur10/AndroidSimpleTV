package com.traceur.androidsimpletv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
        button(getString(R.string.manage_apps)) { showAppManager() }
        button(getString(R.string.sort_apps)) {
            preferences.sortAlphabetically()
            status.setText(R.string.sorted_apps)
        }
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

    private fun showAppManager(selected: String? = null) {
        if (apps.isEmpty()) { status.setText(R.string.apps_loading); return }
        val byId = apps.associateBy { it.component.flattenToString() }
        val ids = preferences.ordered(apps.map { it.component.flattenToString() })
        val labels = ids.map { id -> getString(if (preferences.isHidden(id)) R.string.app_hidden else R.string.app_shown, byId.getValue(id).label) }.toTypedArray()
        val dialog = AlertDialog.Builder(this).setTitle(R.string.manage_apps)
            .setItems(labels) { _, index -> appOptions(ids[index], byId.getValue(ids[index]).label) }
            .setNegativeButton(R.string.done, null).create()
        dialog.setOnShowListener { dialog.listView.setSelection(ids.indexOf(selected).coerceAtLeast(0)) }
        dialog.show()
    }

    private fun appOptions(id: String, name: String) {
        val options = arrayOf(getString(if (preferences.isHidden(id)) R.string.show_app else R.string.hide_app),
            getString(R.string.move_earlier), getString(R.string.move_later))
        AlertDialog.Builder(this).setTitle(name).setItems(options) { _, action ->
            when (action) {
                0 -> preferences.setHidden(id, !preferences.isHidden(id))
                1 -> preferences.move(id, -1, apps.map { it.component.flattenToString() })
                2 -> preferences.move(id, 1, apps.map { it.component.flattenToString() })
            }
            showAppManager(id)
        }.setNegativeButton(R.string.cancel) { _, _ -> showAppManager(id) }.show()
    }

    private fun searchLocation() {
        val input = EditText(this).apply {
            hint = getString(R.string.location_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        AlertDialog.Builder(this).setTitle(R.string.location_search).setView(input)
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
            }.setNegativeButton(R.string.cancel, null).show()
    }
}
