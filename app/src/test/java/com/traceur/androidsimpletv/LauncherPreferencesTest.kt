package com.traceur.androidsimpletv

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherPreferencesTest {
    private fun settings() = LauncherPreferences(RuntimeEnvironment.getApplication())

    @Test fun `hidden choices and custom order survive a new settings instance`() {
        val prefs = settings()
        val apps = listOf("a/main", "b/main", "c/main")
        prefs.setHidden("b/main", true)
        prefs.saveOrder(listOf("c/main", "a/main", "b/main"))
        val restored = settings()
        assertEquals(listOf("c/main", "a/main", "b/main"), restored.ordered(apps))
        assertTrue(restored.isHidden("b/main"))
        restored.setHidden("b/main", false)
        assertFalse(settings().isHidden("b/main"))
    }

    @Test fun `sorting preserves hidden choices and missing apps do not break order`() {
        val prefs = settings()
        prefs.saveOrder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "d"), prefs.ordered(listOf("a", "c", "d")))
        prefs.setHidden("a", true)
        prefs.sortAlphabetically()
        assertEquals(listOf("a", "c", "d"), prefs.ordered(listOf("a", "c", "d")))
        assertTrue(prefs.isHidden("a"))
        assertEquals(listOf("a", "c", "d"), prefs.ordered(listOf("a", "c", "d")))
    }

    @Test fun `weather defaults to Fahrenheit and no location and changing city clears cache`() {
        val prefs = settings()
        assertTrue(prefs.fahrenheit)
        assertNull(prefs.location)
        prefs.location = WeatherLocation("Test city", 30.0, -97.0)
        prefs.weather = WeatherReading(20.0, 1000)
        assertEquals(68, settings().weather!!.temperature(true))
        assertEquals("Test city", settings().location!!.name)
        prefs.fahrenheit = false
        assertFalse(settings().fahrenheit)
        prefs.location = WeatherLocation("Another city", 40.0, -75.0)
        assertNull(prefs.weather)
        prefs.location = null
        assertNull(settings().location)
    }
}
