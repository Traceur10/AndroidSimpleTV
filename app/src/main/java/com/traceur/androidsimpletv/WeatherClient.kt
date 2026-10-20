package com.traceur.androidsimpletv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** No device location permission: only explicitly configured city coordinates are sent. */
internal class WeatherClient {
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { connection?.disconnect() }

    fun search(query: String): List<WeatherLocation> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val results = get("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=en&format=json")
            .optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { index ->
            val place = results.getJSONObject(index)
            WeatherLocation(listOf(place.getString("name"), place.optString("admin1"), place.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString(", "), place.getDouble("latitude"), place.getDouble("longitude"))
        }
    }

    fun current(location: WeatherLocation): WeatherReading {
        val json = get("https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m&temperature_unit=celsius")
        val temperature = json.getJSONObject("current").getDouble("temperature_2m")
        require(temperature.isFinite() && temperature in -100.0..70.0)
        return WeatherReading(temperature, System.currentTimeMillis())
    }

    private fun get(url: String): JSONObject {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val request = URL(url).openConnection() as HttpURLConnection
        connection = request
        try {
            request.connectTimeout = 8_000
            request.readTimeout = 8_000
            request.setRequestProperty("User-Agent", "AndroidSimpleTV/0.2")
            check(request.responseCode == 200) { "Weather service unavailable" }
            val text = request.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(64_000)
                val result = StringBuilder()
                var count = reader.read(buffer)
                while (count >= 0) {
                    result.append(buffer, 0, count)
                    check(result.length <= 256_000)
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    count = reader.read(buffer)
                }
                result.toString()
            }
            return JSONObject(text)
        } finally {
            request.disconnect()
            if (connection === request) connection = null
        }
    }
}
