package com.traceur.androidsimpletv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local settings. Component names survive label changes and duplicate app names. */
internal class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    var showPhotoInformation: Boolean
        get() = prefs.getBoolean("showPhotoInformation", true)
        set(value) { prefs.edit().putBoolean("showPhotoInformation", value).apply() }
    var publicWallpapersEnabled: Boolean
        get() = prefs.getBoolean("publicWallpapersEnabled", false)
        set(value) { prefs.edit().putBoolean("publicWallpapersEnabled", value).apply() }
    var wallpaperConfig: WallpaperConfig
        get() = WallpaperConfig(prefs.getStringSet("wallpaperSources", setOf("COMMONS", "NASA"))!!
            .mapNotNull { runCatching { WallpaperSource.valueOf(it) }.getOrNull() }.toSet().ifEmpty { setOf(WallpaperSource.COMMONS, WallpaperSource.NASA) })
        set(value) { require(value.sources.isNotEmpty()); prefs.edit().putStringSet("wallpaperSources", value.sources.map { it.name }.toSet()).apply() }
    var wallpaperRetryAt: Long
        get() = prefs.getLong("wallpaperRetryAt", 0)
        set(value) { prefs.edit().putLong("wallpaperRetryAt", value).commit() }

    /** One persisted lease shared by auto/manual refresh, including failure and cancellation. */
    fun claimWallpaperRefresh(now: Long = System.currentTimeMillis()): Boolean = synchronized(refreshLock) {
        if (now < wallpaperRetryAt) return@synchronized false
        prefs.edit().putLong("wallpaperRetryAt", now + WALLPAPER_INTERVAL_MS).commit()
    }
    companion object { const val WALLPAPER_INTERVAL_MS = 1_800_000L; private val refreshLock = Any() }
    fun saveWallpaperPosition(key: String, elapsed: Long) {
        prefs.edit().putString("wallpaperPositionKey", key).putLong("wallpaperPositionMs", elapsed).apply()
    }
    fun wallpaperPosition(key: String): Long = if (prefs.getString("wallpaperPositionKey", null) == key) prefs.getLong("wallpaperPositionMs", 0) else 0
    var keepSlideshowAwake: Boolean
        get() = prefs.getBoolean("keepSlideshowAwake", false)
        set(value) { prefs.edit().putBoolean("keepSlideshowAwake", value).apply() }
    var showForecast: Boolean
        get() = prefs.getBoolean("showForecast", false)
        set(value) { prefs.edit().putBoolean("showForecast", value).apply() }
    var fahrenheit: Boolean
        get() = prefs.getBoolean("fahrenheit", true)
        set(value) { prefs.edit().putBoolean("fahrenheit", value).apply() }
    var location: WeatherLocation?
        get() = runCatching {
            val json = JSONObject(prefs.getString("location", null) ?: return null)
            WeatherLocation(json.getString("name"), json.getDouble("lat"), json.getDouble("lon"))
        }.getOrNull()
        set(value) {
            prefs.edit().putString("location", value?.let { JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude).toString() })
                .remove("weather").apply()
        }
    var weather: WeatherReading?
        get() = runCatching {
            val json = JSONObject(prefs.getString("weather", null) ?: return null)
            WeatherReading(json.getDouble("celsius"), json.getLong("fetched"), ForecastDay.fromArray(json.optJSONArray("forecast")), json.optString("timezone", "UTC"), json.optInt("forecastVersion", 0), json.optInt("currentCode", -1))
        }.getOrNull()
        set(value) { prefs.edit().putString("weather", value?.let { JSONObject().put("celsius", it.celsius).put("fetched", it.fetchedAt).put("forecast", JSONArray(it.forecast.map { day -> day.json() })).put("timezone", it.timezone).put("forecastVersion", it.forecastVersion).put("currentCode", it.currentCode).toString() }).apply() }

    fun isHidden(component: String) = prefs.getStringSet("hidden", emptySet())!!.contains(component)
    fun setHidden(component: String, hidden: Boolean) {
        val values = prefs.getStringSet("hidden", emptySet())!!.toMutableSet()
        if (hidden) values.add(component) else values.remove(component)
        prefs.edit().putStringSet("hidden", values).apply()
    }
    fun ordered(available: List<String>): List<String> {
        val stored = runCatching {
            val array = JSONArray(prefs.getString("order", "[]"))
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        // Missing/uninstalled apps are omitted; newly installed apps append alphabetically.
        return (stored.filter { it in available } + available).distinct()
    }
    fun saveOrder(order: List<String>) { prefs.edit().putString("order", JSONArray(order.distinct()).toString()).apply() }
    fun sortAlphabetically() { prefs.edit().remove("order").apply() }
}

internal data class WeatherLocation(val name: String, val latitude: Double, val longitude: Double)
internal data class WeatherReading(val celsius: Double, val fetchedAt: Long, val forecast: List<ForecastDay> = emptyList(), val timezone: String = "UTC", val forecastVersion: Int = 0, val currentCode: Int = -1) {
    fun temperature(fahrenheit: Boolean): Int = kotlin.math.round(if (fahrenheit) celsius * 9 / 5 + 32 else celsius).toInt()
    fun isFresh(now: Long) = now >= fetchedAt && now - fetchedAt < 30 * 60_000L
}
