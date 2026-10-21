package com.traceur.androidsimpletv

import android.app.Activity
import android.app.AlertDialog
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

internal object DockSettingsDialogs {
    fun visibility(activity: Activity, apps: List<AppEntry>, preferences: LauncherPreferences): AlertDialog {
        val byId = apps.associateBy { it.component.flattenToString() }
        val ids = preferences.ordered(apps.map { it.component.flattenToString() })
        return AlertDialog.Builder(activity)
            .setTitle(R.string.show_hide_apps)
            .setMultiChoiceItems(ids.map { byId.getValue(it).label }.toTypedArray(),
                ids.map { !preferences.isHidden(it) }.toBooleanArray()) { _, index, checked ->
                preferences.setHidden(ids[index], !checked)
            }
            .setPositiveButton(R.string.done, null)
            .create().apply { show(); listView.requestFocus() }
    }

    fun reorder(activity: Activity, apps: List<AppEntry>, preferences: LauncherPreferences): AlertDialog {
        val byId = apps.associateBy { it.component.flattenToString() }
        val editor = DockOrderEditor(preferences.ordered(apps.map { it.component.flattenToString() }))
        val density = activity.resources.displayMetrics.density
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
        }
        val hint = TextView(activity).apply { textSize = 16f; setPadding(0, 0, 0, (12 * density).toInt()) }
        val rows = ArrayList<String>()
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_activated_1, rows)
        val list = ListView(activity).apply {
            tag = "move_apps_list"
            choiceMode = ListView.CHOICE_MODE_SINGLE
            this.adapter = adapter
        }
        column.addView(hint)
        column.addView(list, LinearLayout.LayoutParams(-1, (280 * density).toInt()))
        fun refresh(selection: Int) {
            rows.clear()
            editor.order.forEachIndexed { index, id ->
                val name = byId.getValue(id).label
                val label = if (preferences.isHidden(id)) activity.getString(R.string.app_hidden, name) else name
                rows.add(activity.getString(if (id == editor.picked) R.string.moving_app_row else R.string.app_order_row, index + 1, label))
            }
            adapter.notifyDataSetChanged()
            hint.text = if (editor.picked != null) activity.getString(R.string.move_apps_active, byId.getValue(editor.picked!!).label)
                else activity.getString(R.string.move_apps_help)
            list.clearChoices()
            if (editor.picked != null) list.setItemChecked(editor.pickedIndex, true)
            list.setSelection(selection)
        }
        list.setOnItemClickListener { _, _, index, _ ->
            if (editor.picked == null) editor.pick(index) else {
                editor.move(index - editor.pickedIndex)
                preferences.saveOrder(editor.place())
            }
            refresh(index)
        }
        list.setOnKeyListener { _, keyCode, event ->
            if (editor.picked != null && keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    editor.move(if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    refresh(editor.pickedIndex)
                }
                true
            } else false
        }
        var consumingBack = false
        return AlertDialog.Builder(activity).setTitle(R.string.move_apps).setView(column)
            .setPositiveButton(R.string.done, null).create().apply {
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && (editor.picked != null || consumingBack)) {
                        if (event.action == KeyEvent.ACTION_DOWN && !consumingBack) {
                            val id = editor.picked
                            editor.cancel()
                            refresh(editor.order.indexOf(id).coerceAtLeast(0))
                            consumingBack = true
                        }
                        if (event.action == KeyEvent.ACTION_UP) consumingBack = false
                        true
                    } else false
                }
                setOnDismissListener { editor.cancel() }
                setOnShowListener { refresh(0); list.requestFocus() }
                show()
            }
    }
}
