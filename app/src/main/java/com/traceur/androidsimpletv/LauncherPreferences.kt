package com.traceur.androidsimpletv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local settings. Component names survive label changes and duplicate app names. */
internal class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    var redditRetryAt: Long
        get() = prefs.getLong("redditRetryAt", 0L)
        set(value) { prefs.edit().putLong("redditRetryAt", value).apply() }
    var redditEnabled: Boolean
        get() = prefs.getBoolean("redditEnabled", false)
        set(value) { prefs.edit().putBoolean("redditEnabled", value).apply() }
    var redditConfig: RedditConfig
        get() = RedditConfig(
            runCatching { RedditConfig.subreddits(prefs.getString("subreddits", "earthporn")!!) }.getOrDefault(listOf("earthporn")),
            prefs.getString("redditClientId", "")!!, prefs.getString("redditContact", "")!!)
        set(value) { prefs.edit().putString("subreddits", value.subreddits.joinToString(","))
            .putString("redditClientId", value.clientId).putString("redditContact", value.contact).apply() }
    var keepSlideshowAwake: Boolean
        get() = prefs.getBoolean("keepSlideshowAwake", false)
        set(value) { prefs.edit().putBoolean("keepSlideshowAwake", value).apply() }
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
            WeatherReading(json.getDouble("celsius"), json.getLong("fetched"))
        }.getOrNull()
        set(value) { prefs.edit().putString("weather", value?.let { JSONObject().put("celsius", it.celsius).put("fetched", it.fetchedAt).toString() }).apply() }

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
internal data class WeatherReading(val celsius: Double, val fetchedAt: Long) {
    fun temperature(fahrenheit: Boolean): Int = kotlin.math.round(if (fahrenheit) celsius * 9 / 5 + 32 else celsius).toInt()
    fun isFresh(now: Long) = now >= fetchedAt && now - fetchedAt < 30 * 60_000L
}
