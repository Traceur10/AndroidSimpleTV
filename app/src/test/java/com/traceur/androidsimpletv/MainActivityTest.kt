package com.traceur.androidsimpletv

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityTest {
    private fun key(activity: MainActivity, code: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    @Test fun `keep awake is opt in and released on pause`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        assertEquals(0, activity.window.attributes.flags and flag)
        controller.pause()
        LauncherPreferences(activity).keepSlideshowAwake = true
        controller.resume()
        assertEquals(flag, activity.window.attributes.flags and flag)
        controller.pause()
        assertEquals(0, activity.window.attributes.flags and flag)
        LauncherPreferences(activity).keepSlideshowAwake = false
        controller.resume()
        assertEquals(0, activity.window.attributes.flags and flag)
        controller.pause().stop().destroy()
    }

    @Test fun `remote reveals and hides dock without exiting`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val dock = activity.window.decorView.findViewWithTag<View>("app_dock")
        assertEquals(View.GONE, dock.visibility)
        key(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(View.VISIBLE, dock.visibility)
        assertNull(shadowOf(activity).nextStartedActivity)
        key(activity, KeyEvent.KEYCODE_BACK)
        assertEquals(View.GONE, dock.visibility)
        assertFalse(activity.isFinishing)
        key(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        key(activity, KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(View.GONE, dock.visibility)
        controller.pause().stop().destroy()
    }

    @Test fun `held reveal key does not immediately hide dock`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        activity.dispatchKeyEvent(KeyEvent(0L, 1L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 1))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(View.VISIBLE, activity.window.decorView.findViewWithTag<View>("app_dock").visibility)
        controller.pause().stop().destroy()
    }

    @Test fun `recreation restores wallpaper progress and visible dock`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        key(controller.get(), KeyEvent.KEYCODE_DPAD_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(56))
        controller.pause()
        val frozen = controller.get().timeline.elapsedMs
        val state = android.os.Bundle()
        controller.saveInstanceState(state).stop().destroy()
        val restored = Robolectric.buildActivity(MainActivity::class.java).create(state).start().restoreInstanceState(state)
        assertEquals(frozen, restored.get().timeline.elapsedMs)
        // Once resumed, rendering may legitimately advance foreground time.
        restored.resume().visible()
        assertEquals(View.VISIBLE, restored.get().window.decorView.findViewWithTag<View>("app_dock").visibility)
        restored.pause().stop().destroy()
    }

    @Test fun `background time does not advance slideshow and resume preserves fade`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(57_500))
        controller.pause()
        val frozen = activity.timeline.elapsedMs
        // Activity setup may consume a few milliseconds before the explicit idle.
        assertTrue(frozen in 57_500L..57_516L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
        assertEquals(frozen, activity.timeline.elapsedMs)
        controller.resume()
        assertEquals(frozen, activity.timeline.elapsedMs)
        assertEquals(0.5f, activity.timeline.fadeFraction, 0.001f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500))
        controller.pause()
        assertEquals(1, activity.timeline.imageIndex)
        controller.stop().destroy()
    }
}
