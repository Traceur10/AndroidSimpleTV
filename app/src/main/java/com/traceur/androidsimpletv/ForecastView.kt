package com.traceur.androidsimpletv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** A deliberately fixed, compact TV dashboard. Drawing it directly avoids text wrapping surprises. */
internal class ForecastView(context: Context) : View(context) {
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL) }
    private var days = emptyList<ForecastDay>()
    private var fahrenheit = true
    private var message: String? = null
    private var location = ""
    private var current = ""
    private var currentCode = -1
    private val weatherIcons: Map<Int, Bitmap> by lazy {
        mapOf(
            R.drawable.weather_clear to BitmapFactory.decodeResource(resources, R.drawable.weather_clear),
            R.drawable.weather_partly_cloudy to BitmapFactory.decodeResource(resources, R.drawable.weather_partly_cloudy),
            R.drawable.weather_cloudy to BitmapFactory.decodeResource(resources, R.drawable.weather_cloudy),
            R.drawable.weather_rain to BitmapFactory.decodeResource(resources, R.drawable.weather_rain),
            R.drawable.weather_snow to BitmapFactory.decodeResource(resources, R.drawable.weather_snow),
            R.drawable.weather_storm to BitmapFactory.decodeResource(resources, R.drawable.weather_storm)
        )
    }

    init { tag = "weather_forecast_container"; setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

    fun show(days: List<ForecastDay>, fahrenheit: Boolean, message: String? = null, location: String = "", current: String = "", currentCode: Int = -1) {
        this.days = days.take(5); this.fahrenheit = fahrenheit; this.message = message; this.location = location; this.current = current; this.currentCode = currentCode; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); if (w <= 0f || h <= 0f) return
        panel.color = Color.argb(72, 0, 0, 0); canvas.drawRoundRect(RectF(0f, 0f, w, h), 18f, 18f, panel)
        if (message != null) { drawText(canvas, message!!, w / 2, h / 2, 14f, true); return }
        if (days.isEmpty()) return
        drawText(canvas, location, 18f, 25f, 14f, true, Color.WHITE, Paint.Align.LEFT)
        drawIcon(canvas, 0f + 40f, 18f, currentCode, 18f)
        drawText(canvas, current, w - 18f, 25f, 14f, true, Color.WHITE, Paint.Align.RIGHT)
        val gap = 8f; val left = 10f; val top = 40f; val bottom = h - 10f
        val cardW = (w - left * 2 - gap * (days.size - 1)) / days.size
        days.forEachIndexed { i, day ->
            val x = left + i * (cardW + gap); val r = RectF(x, top, x + cardW, bottom)
            card.color = if (i == 0) Color.argb(58, 255, 255, 255) else Color.argb(48, 0, 0, 0)
            card.style = Paint.Style.FILL; canvas.drawRoundRect(r, 14f, 14f, card)
            card.color = if (i == 0) Color.rgb(92, 170, 235) else Color.argb(130, 100, 120, 145)
            card.style = Paint.Style.STROKE; card.strokeWidth = if (i == 0) 2f else 1f; canvas.drawRoundRect(r, 14f, 14f, card)
            drawText(canvas, day.label(fahrenheit).substringBefore('\n'), r.centerX(), r.top + 31f, 14f, true)
            drawText(canvas, day.shortDate(), r.centerX(), r.top + 53f, 11f, false, Color.rgb(165, 184, 214))
            drawIcon(canvas, r.centerX(), r.top + 75f, day.code, 18f)
            // Keep the temperature pair compact like the reference card; the unit is
            // already visible in the current-weather header.
            drawText(canvas, day.highText(fahrenheit), r.centerX() - 20f, r.top + 126f, 18f, true)
            drawText(canvas, day.lowText(fahrenheit), r.centerX() + 27f, r.top + 126f, 12f, false, Color.rgb(155, 190, 230))
            drawText(canvas, day.condition, r.centerX(), r.top + 150f, 10f, false, Color.rgb(185, 204, 228))
            drawText(canvas, "☂ ${day.precipitationProbability}%", r.centerX(), r.bottom - 16f, 11f, false, Color.rgb(145, 200, 245))
        }
    }

    private fun drawText(c: Canvas, value: String, x: Float, y: Float, size: Float, bold: Boolean, color: Int = Color.WHITE, align: Paint.Align = Paint.Align.CENTER) {
        text.color = color; text.textSize = size * resources.displayMetrics.density; text.textAlign = Paint.Align.CENTER
        text.textAlign = align
        text.typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        c.drawText(value, x, y, text)
    }

    private fun drawIcon(c: Canvas, x: Float, y: Float, code: Int, size: Float) {
        val bitmap = weatherIcons[iconForCode(code)]
        if (bitmap != null) {
            val half = size * resources.displayMetrics.density
            val dst = RectF(x - half, y - half, x + half, y + half)
            c.drawBitmap(bitmap, null, dst, null)
            return
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (code in 51..99) Color.rgb(120, 195, 245) else Color.rgb(255, 205, 70); style = Paint.Style.FILL }
        if (code == 0 || code == 1 || code == 2) c.drawCircle(x, y, size * .45f, p)
        if (code >= 3 || code in 1..2) { p.color = Color.rgb(220, 230, 242); c.drawOval(RectF(x-size*.7f,y-size*.05f,x+size*.7f,y+size*.5f),p) }
        if (code in 51..67 || code in 80..82 || code in 95..99) { p.color=Color.rgb(110,190,245); c.drawCircle(x-size*.35f,y+size*.7f,size*.1f,p); c.drawCircle(x,y+size*.82f,size*.1f,p); c.drawCircle(x+size*.35f,y+size*.7f,size*.1f,p) }
    }

    private fun iconForCode(code: Int): Int = when (code) {
        0 -> R.drawable.weather_clear
        1, 2 -> R.drawable.weather_partly_cloudy
        3 -> R.drawable.weather_cloudy
        in 51..67, in 80..82 -> R.drawable.weather_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.weather_snow
        in 95..99 -> R.drawable.weather_storm
        else -> R.drawable.weather_cloudy
    }
}

private fun ForecastDay.highText(fahrenheit: Boolean) = "${WeatherReading(high, 0).temperature(fahrenheit)}°"
private fun ForecastDay.lowText(fahrenheit: Boolean) = "${WeatherReading(low, 0).temperature(fahrenheit)}°"
