package com.traceur.androidsimpletv

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperActivityTest {
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun waitFor(condition: () -> Boolean) {
        repeat(300) { shadowOf(Looper.getMainLooper()).idle(); if (condition()) return; Thread.sleep(10) }
        fail("Background result was not delivered")
    }
    private fun seed() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.RGB_565)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        bitmap.recycle()
        WallpaperStore(context).replace("earthporn", listOf(RedditPhoto("test1", "Test", "artist", "earthporn", "https://i.redd.it/test1.jpg", "https://www.reddit.com/r/earthporn/comments/test1/test/"))) { bytes }
    }
    @Test fun `offline photos display with attribution and disabling restores bundled images`() {
        seed()
        val prefs = LauncherPreferences(RuntimeEnvironment.getApplication())
        assertFalse(prefs.redditEnabled)
        prefs.redditEnabled = true
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val credit = controller.get().window.decorView.findViewWithTag<TextView>("photo_credit")
        waitFor { credit.text.contains("u/artist") }
        controller.pause()
        val elapsed = controller.get().timeline.elapsedMs
        controller.resume()
        assertEquals(elapsed, controller.get().timeline.elapsedMs)
        controller.pause(); prefs.redditEnabled = false; controller.resume()
        assertEquals("", credit.text.toString())
        controller.pause().stop().destroy()
    }
    @Test fun `settings previews cached photos without credentials and gates downloads on setup`() {
        seed()
        val controller = Robolectric.buildActivity(WallpaperSettingsActivity::class.java).setup()
        fun button(text: String) = views(controller.get().window.decorView).filterIsInstance<Button>().first { it.text.toString() == text }
        waitFor { button("Preview downloaded photos").isEnabled }
        button("Preview downloaded photos").performClick()
        waitFor { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        var dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("u/artist") })
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        button("Refresh photos").performClick()
        dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("approved installed-app") })
        assertFalse(LauncherPreferences(controller.get()).redditConfig.ready)
        assertFalse(LauncherPreferences(controller.get()).redditEnabled)
        controller.pause().stop().destroy()
        assertFalse(dialog.isShowing)
    }
}
