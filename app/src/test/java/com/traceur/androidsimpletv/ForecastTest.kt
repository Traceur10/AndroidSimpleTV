package com.traceur.androidsimpletv

import android.view.View
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForecastTest {
    @Test fun `daily parsing units and invalid data`() {
        val daily = JSONObject("""{"time":["2026-10-27"],"temperature_2m_max":[20],"temperature_2m_min":[10],"weather_code":[61]}""")
        val day = ForecastDay.parseDaily(daily).single()
        assertEquals("Rain", day.condition)
        assertTrue(day.label(true).contains("68°F"))
        assertTrue(day.label(false).contains("10°C"))
        assertTrue(runCatching { ForecastDay("2026-02-30", 20.0, 10.0, 0) }.isFailure)
        assertTrue(runCatching { ForecastDay("2026-10-27", 10.0, 20.0, 0) }.isFailure)
        assertEquals("1969-12-31", ForecastDay.today("America/Chicago", 0))
    }
    @Test fun `cached forecast persists toggles on resume and clears with location`() {
        val prefs = LauncherPreferences(RuntimeEnvironment.getApplication())
        assertFalse(prefs.showForecast)
        prefs.location = WeatherLocation("Test", 30.0, -97.0)
        val now = System.currentTimeMillis()
        val dates = (0..4).map { ForecastDay.today("UTC", now + it * 86_400_000L) }
        prefs.weather = WeatherReading(20.0, now, dates.map { ForecastDay(it, 22.0, 12.0, 0) })
        assertEquals(5, LauncherPreferences(RuntimeEnvironment.getApplication()).weather!!.forecast.size)
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val view = c.get().window.decorView.findViewWithTag<TextView>("weather_forecast")
        assertEquals(View.GONE, view.visibility)
        c.pause(); prefs.showForecast = true; c.resume()
        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(view.text.contains("72°F"))
        c.pause(); prefs.fahrenheit = false; c.resume()
        assertTrue(view.text.contains("22°C"))
        c.pause(); prefs.location = null; c.resume()
        assertNull(prefs.weather)
        assertEquals(View.GONE, view.visibility)
        c.pause().stop().destroy()
    }
}
