package com.traceur.androidsimpletv

import org.junit.Assert.*
import org.junit.Test

class SlideshowTimelineTest {
    @Test fun `holds for 55 seconds then crossfades for five`() {
        val timeline = SlideshowTimeline(3)
        timeline.advance(54_999)
        assertEquals(0f, timeline.fadeFraction, 0f)
        assertEquals(1L, timeline.nextFrameDelayMs)
        timeline.advance(1)
        assertEquals(0f, timeline.fadeFraction, 0f)
        assertEquals(16L, timeline.nextFrameDelayMs)
        timeline.advance(2_500)
        assertEquals(0.5f, timeline.fadeFraction, 0.0001f)
        timeline.advance(2_499)
        assertEquals(0, timeline.imageIndex)
        assertTrue(timeline.fadeFraction > 0.99f)
        timeline.advance(1)
        assertEquals(1, timeline.imageIndex)
        assertEquals(0f, timeline.fadeFraction, 0f)
        assertEquals(55_000L, timeline.nextFrameDelayMs)
    }

    @Test fun `wraps and restores a partially completed fade`() {
        val timeline = SlideshowTimeline(3, 177_500)
        assertEquals(2, timeline.imageIndex)
        assertEquals(0, timeline.nextImageIndex)
        assertEquals(0.5f, timeline.fadeFraction, 0f)
        timeline.advance(2_500)
        assertEquals(0, timeline.imageIndex)
        assertEquals(0L, timeline.elapsedMs)
        timeline.advance(360_000L + 57_500L)
        assertEquals(0.5f, timeline.fadeFraction, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty wallpaper list`() { SlideshowTimeline(0) }
}
