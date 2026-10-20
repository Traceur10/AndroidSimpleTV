package com.traceur.androidsimpletv

/** Foreground time only. The owner stops advancing this timeline while paused. */
internal class SlideshowTimeline(private val imageCount: Int, elapsed: Long = 0L) {
    init { require(imageCount > 0) }
    private val loopMs = CYCLE_MS * imageCount
    var elapsedMs: Long = elapsed.coerceAtLeast(0L) % loopMs
        private set
    val imageIndex: Int get() = (elapsedMs / CYCLE_MS).toInt()
    val nextImageIndex: Int get() = (imageIndex + 1) % imageCount
    private val phaseMs: Long get() = elapsedMs % CYCLE_MS
    val fadeFraction: Float get() = ((phaseMs - STATIC_MS).coerceAtLeast(0L).toFloat() / FADE_MS)
    val nextFrameDelayMs: Long get() = if (phaseMs < STATIC_MS) STATIC_MS - phaseMs else 16L

    fun advance(deltaMs: Long) {
        require(deltaMs >= 0L)
        elapsedMs = (elapsedMs + deltaMs % loopMs) % loopMs
    }

    companion object {
        const val STATIC_MS = 55_000L
        const val FADE_MS = 5_000L
        const val CYCLE_MS = STATIC_MS + FADE_MS
    }
}
