package com.traceur.androidsimpletv

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class ForecastDay(val date: String, val high: Double, val low: Double, val code: Int, val precipitationProbability: Int = 0) {
    init {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date))
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        require(parser.parse(date) != null)
        require(high.isFinite() && low.isFinite() && high in -100.0..70.0 && low in -100.0..70.0 && low <= high && precipitationProbability in 0..100)
    }
    fun json() = JSONObject().put("date", date).put("high", high).put("low", low).put("code", code).put("precipitationProbability", precipitationProbability)
    fun label(fahrenheit: Boolean): String {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val day = SimpleDateFormat("EEE", Locale.getDefault()).format(parser.parse(date)!!)
        val unit = if (fahrenheit) "°F" else "°C"
        return "$day\n$condition\nHigh ${WeatherReading(high, 0).temperature(fahrenheit)}$unit\nLow ${WeatherReading(low, 0).temperature(fahrenheit)}$unit"
    }
    val condition: String get() = when (code) {
        0 -> "Clear"; 1, 2 -> "Partly cloudy"; 3 -> "Cloudy"; 45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"; 56, 57, 66, 67 -> "Freezing rain"
        61, 63, 65, 80, 81, 82 -> "Rain"; 71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorms"; else -> "Forecast"
    }
    companion object {
        fun today(timezone: String, now: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone(timezone) }.format(Date(now))
        fun fromArray(array: JSONArray?): List<ForecastDay> = if (array == null) emptyList() else
            (0 until minOf(5, array.length())).map { i -> array.getJSONObject(i).let { ForecastDay(it.getString("date"), it.getDouble("high"), it.getDouble("low"), it.getInt("code"), it.optInt("precipitationProbability", 0)) } }
        fun parseDaily(daily: JSONObject): List<ForecastDay> = (0 until minOf(5, daily.getJSONArray("time").length())).map { i ->
            ForecastDay(daily.getJSONArray("time").getString(i), daily.getJSONArray("temperature_2m_max").getDouble(i), daily.getJSONArray("temperature_2m_min").getDouble(i), daily.getJSONArray("weather_code").getInt(i), daily.optJSONArray("precipitation_probability_max")?.optInt(i, 0) ?: 0)
        }
    }
}
