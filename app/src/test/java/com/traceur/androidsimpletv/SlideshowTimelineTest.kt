package com.traceur.androidsimpletv

import org.junit.Assert.*
import org.junit.Test

class SlideshowTimelineTest {
    @Test fun `holds for 115 seconds then crossfades for five`() {
        val timeline = SlideshowTimeline(3)
        timeline.advance(114_999)
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
        assertEquals(115_000L, timeline.nextFrameDelayMs)
    }
    @Test fun `bundled landscapes wrap and restore a partially completed fade`() {
        val timeline = SlideshowTimeline(3, 357_500)
        assertEquals(2, timeline.imageIndex)
        assertEquals(0, timeline.nextImageIndex)
        assertEquals(0.5f, timeline.fadeFraction, 0f)
        timeline.advance(2_500)
        assertEquals(0, timeline.imageIndex)
        assertEquals(0L, timeline.elapsedMs)
        timeline.advance(720_000L + 117_500L)
        assertEquals(0.5f, timeline.fadeFraction, 0f)
    }
    @Test fun `online batch never wraps even after hours offline`() {
        val timeline = SlideshowTimeline(15, loop = false)
        for (index in 0 until 15) {
            assertEquals(index, timeline.imageIndex)
            timeline.advance(120_000)
        }
        timeline.advance(24 * 60 * 60_000L)
        assertEquals(14, timeline.imageIndex)
        assertEquals(14, timeline.nextImageIndex)
        assertEquals(0f, timeline.fadeFraction, 0f)
        assertEquals(1_800_000L, timeline.elapsedMs)
        assertEquals(14, SlideshowTimeline(15, timeline.elapsedMs, loop = false).imageIndex)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty wallpaper list`() { SlideshowTimeline(0) }
}
