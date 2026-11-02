package com.traceur.androidsimpletv

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocationInputTest {
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Test fun `location dialog focuses text and remote select reopens keyboard`() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        views(controller.get().window.decorView).filterIsInstance<Button>().first { it.text.startsWith("Weather location:") }.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val input = dialog.window!!.decorView.findViewWithTag<EditText>("location_input")
        shadowOf(Looper.getMainLooper()).idle()
        val ime = controller.get().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        assertTrue(input.hasFocus())
        assertTrue(shadowOf(ime).isSoftInputVisible)
        ime.hideSoftInputFromWindow(input.windowToken, 0)
        input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(ime).isSoftInputVisible)
        dialog.dismiss()
        controller.pause().stop().destroy()
    }
}
