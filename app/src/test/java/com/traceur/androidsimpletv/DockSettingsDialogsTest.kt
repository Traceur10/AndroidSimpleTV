package com.traceur.androidsimpletv

import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.widget.ListView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DockSettingsDialogsTest {
    private val apps = listOf("Alpha", "Beta", "Gamma").map {
        AppEntry(ComponentName("test.$it", "Main"), it, ColorDrawable())
    }
    private val ids = apps.map { it.component.flattenToString() }

    @Test fun `visibility list toggles multiple apps without closing`() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val prefs = LauncherPreferences(controller.get())
        val dialog = DockSettingsDialogs.visibility(controller.get(), apps, prefs)
        shadowOf(Looper.getMainLooper()).idle()
        val list = dialog.listView
        list.performItemClick(null, 0, 0)
        list.performItemClick(null, 1, 1)
        assertTrue(prefs.isHidden(ids[0]))
        assertTrue(prefs.isHidden(ids[1]))
        assertTrue(dialog.isShowing)
        list.performItemClick(null, 0, 0)
        assertFalse(prefs.isHidden(ids[0]))
        assertTrue(prefs.isHidden(ids[1]))
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun `remote moves preview until placed and Back cancels without closing`() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val prefs = LauncherPreferences(controller.get())
        val dialog = DockSettingsDialogs.reorder(controller.get(), apps, prefs)
        shadowOf(Looper.getMainLooper()).idle()
        val list = dialog.findViewById<android.view.View>(android.R.id.content).findViewWithTag<ListView>("move_apps_list")
        list.performItemClick(null, 0, 0)
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            list.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_DPAD_DOWN))
        }
        assertTrue((0 until list.count).map { list.adapter.getItem(it) }.toString(), list.adapter.getItem(1).toString().contains("Alpha"))
        assertEquals(ids, prefs.ordered(ids))
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            dialog.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_BACK))
        }
        assertTrue(dialog.isShowing)
        assertTrue(list.adapter.getItem(0).toString().contains("Alpha"))
        list.performItemClick(null, 0, 0)
        list.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        list.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
        list.performItemClick(null, 1, 1)
        assertEquals(listOf(ids[1], ids[0], ids[2]), prefs.ordered(ids))
        assertTrue(dialog.isShowing)
        dialog.dismiss()
        controller.pause().stop().destroy()
    }
}
