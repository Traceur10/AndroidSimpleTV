package com.traceur.androidsimpletv

import org.junit.Assert.*
import org.junit.Test

class WeatherReadingTest {
    @Test fun `converts units including freezing and negative temperatures`() {
        assertEquals(32, WeatherReading(0.0, 0).temperature(true))
        assertEquals(-40, WeatherReading(-40.0, 0).temperature(true))
        assertEquals(21, WeatherReading(20.6, 0).temperature(false))
    }
    @Test fun `cached readings expire at thirty minutes and reject clock rollback`() {
        val reading = WeatherReading(20.0, 1000)
        assertTrue(reading.isFresh(1000 + 30 * 60_000L - 1))
        assertFalse(reading.isFresh(1000 + 30 * 60_000L))
        assertFalse(reading.isFresh(999))
    }
}
