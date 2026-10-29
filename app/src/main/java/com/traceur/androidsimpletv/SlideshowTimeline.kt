package com.traceur.androidsimpletv

/** Foreground time only. Online albums stop at the last photo instead of repeating. */
internal class SlideshowTimeline(private val imageCount: Int, elapsed: Long = 0L, private val loop: Boolean = true) {
    init { require(imageCount > 0) }
    private val loopMs = CYCLE_MS * imageCount
    private fun normalize(value: Long) = if (loop) value.coerceAtLeast(0L) % loopMs else value.coerceIn(0L, loopMs)
    var elapsedMs: Long = normalize(elapsed)
        private set
    val imageIndex: Int get() = (elapsedMs / CYCLE_MS).toInt().coerceAtMost(imageCount - 1)
    val nextImageIndex: Int get() = if (loop) (imageIndex + 1) % imageCount else (imageIndex + 1).coerceAtMost(imageCount - 1)
    private val phaseMs: Long get() = elapsedMs % CYCLE_MS
    val fadeFraction: Float get() = if (imageIndex == nextImageIndex) 0f else ((phaseMs - STATIC_MS).coerceAtLeast(0L).toFloat() / FADE_MS)
    val nextFrameDelayMs: Long get() = if (!loop && imageIndex == imageCount - 1) 60_000L else if (phaseMs < STATIC_MS) STATIC_MS - phaseMs else 16L

    fun advance(deltaMs: Long) {
        require(deltaMs >= 0L)
        elapsedMs = if (loop) normalize(elapsedMs + deltaMs % loopMs) else elapsedMs + deltaMs.coerceAtMost(loopMs - elapsedMs)
    }

    companion object {
        const val STATIC_MS = 115_000L
        const val FADE_MS = 5_000L
        const val CYCLE_MS = STATIC_MS + FADE_MS
    }
}
