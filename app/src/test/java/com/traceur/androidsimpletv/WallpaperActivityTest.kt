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
        WallpaperStore(context).replace("COMMONS+NASA", listOf(WallpaperPhoto("test1", "Test", "artist", "https://thumb.wikimedia.org/test1.jpg", "https://commons.wikimedia.org/wiki/File:test1.jpg", "CC0", "https://creativecommons.org/publicdomain/zero/1.0/"))) { bytes }
    }
    @Test fun `offline photos display with attribution and disabling restores bundled images`() {
        seed()
        val prefs = LauncherPreferences(RuntimeEnvironment.getApplication())
        assertFalse(prefs.publicWallpapersEnabled)
        prefs.publicWallpapersEnabled = true
        prefs.wallpaperRetryAt = System.currentTimeMillis() + LauncherPreferences.WALLPAPER_INTERVAL_MS
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val credit = controller.get().window.decorView.findViewWithTag<TextView>("photo_credit")
        waitFor { credit.text.contains("artist") }
        controller.pause()
        val elapsed = controller.get().timeline.elapsedMs
        controller.resume()
        assertEquals(elapsed, controller.get().timeline.elapsedMs)
        controller.pause(); prefs.publicWallpapersEnabled = false; controller.resume()
        assertEquals("", credit.text.toString())
        controller.pause().stop().destroy()
    }
    @Test fun `settings previews cached photos and respects hourly refresh gate`() {
        seed()
        val controller = Robolectric.buildActivity(WallpaperSettingsActivity::class.java).setup()
        fun button(text: String) = views(controller.get().window.decorView).filterIsInstance<Button>().first { it.text.toString() == text }
        waitFor { button("Preview downloaded photos").isEnabled }
        button("Preview downloaded photos").performClick()
        waitFor { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        var dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("artist") })
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        LauncherPreferences(controller.get()).wallpaperRetryAt = System.currentTimeMillis() + LauncherPreferences.WALLPAPER_INTERVAL_MS
        button("Refresh photos").performClick()
        waitFor { views(controller.get().window.decorView).filterIsInstance<TextView>().any { it.text.contains("Next refresh allowed") } }
        assertFalse(LauncherPreferences(controller.get()).publicWallpapersEnabled)
        controller.pause().stop().destroy()
        assertFalse(dialog.isShowing)
    }
}
