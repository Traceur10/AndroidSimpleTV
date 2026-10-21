package com.traceur.androidsimpletv

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

internal data class RedditConfig(val subreddits: List<String>, val clientId: String, val contact: String) {
    val sourceKey: String get() = subreddits.joinToString("+")
    val ready: Boolean get() = Regex("[A-Za-z0-9_-]{8,100}").matches(clientId) && Regex("[A-Za-z0-9_-]{3,20}").matches(contact)
    companion object {
        fun subreddits(input: String): List<String> {
            val names = input.split(Regex("[,\\s+]+" )).filter { it.isNotBlank() }
                .map { it.removePrefix("r/").lowercase(java.util.Locale.ROOT) }.distinct()
            require(names.size in 1..5 && names.all { Regex("[a-z0-9_]{2,21}").matches(it) })
            return names
        }
    }
}

internal data class RedditPhoto(val id: String, val title: String, val author: String, val subreddit: String, val imageUrl: String, val permalink: String) {
    fun json() = JSONObject().put("id", id).put("title", title).put("author", author).put("subreddit", subreddit).put("image", imageUrl).put("permalink", permalink)
    val credit: String get() = "r/$subreddit · u/$author"
    companion object {
        fun fromJson(o: JSONObject) = RedditPhoto(o.getString("id"), o.getString("title"), o.getString("author"), o.getString("subreddit"), o.getString("image"), o.getString("permalink"))
    }
}

/** Only direct static Reddit-hosted images; never follow arbitrary post links or redirects. */
internal object RedditListing {
    fun imageUrlAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "i.redd.it" && uri.userInfo == null && uri.port == -1 &&
            Regex("/[A-Za-z0-9_-]+\\.(jpg|jpeg|png)", RegexOption.IGNORE_CASE).matches(uri.path)
    }.getOrDefault(false)

    fun parse(body: String): List<RedditPhoto> {
        val children = JSONObject(body).getJSONObject("data").getJSONArray("children")
        return (0 until children.length()).mapNotNull { index ->
            runCatching {
                val item = children.getJSONObject(index)
                if (item.optString("kind") != "t3") return@runCatching null
                val d = item.getJSONObject("data")
                if (d.optBoolean("over_18", true) || d.optBoolean("spoiler") || d.optBoolean("is_video") ||
                    d.optBoolean("is_self") || d.optBoolean("is_gallery") || d.optBoolean("stickied") ||
                    !d.isNull("removed_by_category")) return@runCatching null
                val id = d.getString("id")
                val author = d.getString("author")
                val sub = d.getString("subreddit")
                val link = d.getString("permalink")
                val url = d.optString("url_overridden_by_dest", d.optString("url"))
                if (!Regex("[a-z0-9]{1,16}").matches(id) || !Regex("[A-Za-z0-9_-]{3,20}").matches(author) ||
                    !Regex("[A-Za-z0-9_]{2,21}").matches(sub) || !link.startsWith("/r/$sub/comments/$id/") ||
                    !imageUrlAllowed(url)) return@runCatching null
                RedditPhoto(id, d.getString("title").take(200), author, sub, url, "https://www.reddit.com$link")
            }.getOrNull()
        }.distinctBy { it.id }
    }
}

internal class RedditAccessException(val code: Int, val retryMs: Long = 60_000L) : IOException("Reddit request failed ($code)")

internal class RedditClient {
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { connection?.disconnect() }

    fun photos(config: RedditConfig): List<RedditPhoto> {
        require(config.ready)
        val agent = "android:com.traceur.androidsimpletv:v0.4.0 (by /u/${config.contact})"
        val basic = Base64.encodeToString("${config.clientId}:".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val form = "grant_type=" + URLEncoder.encode("https://oauth.reddit.com/grants/installed_client", "UTF-8") + "&device_id=DO_NOT_TRACK_THIS_DEVICE"
        val tokenJson = JSONObject(String(request("https://www.reddit.com/api/v1/access_token", agent, "Basic $basic", form, 64 * 1024), Charsets.UTF_8))
        val token = tokenJson.optString("access_token")
        if (token.isBlank() || !tokenJson.optString("token_type").equals("bearer", true)) throw RedditAccessException(401)
        val body = request("https://oauth.reddit.com/r/${config.sourceKey}/top?t=week&limit=50&raw_json=1", agent, "bearer $token", null, 2 * 1024 * 1024)
        return RedditListing.parse(String(body, Charsets.UTF_8)).filter { it.subreddit.lowercase(java.util.Locale.ROOT) in config.subreddits }
    }

    fun image(photo: RedditPhoto): ByteArray {
        require(RedditListing.imageUrlAllowed(photo.imageUrl))
        return request(photo.imageUrl, "AndroidSimpleTV/0.4.0", null, null, 8 * 1024 * 1024)
    }

    private fun request(url: String, agent: String, authorization: String?, form: String?, limit: Int): ByteArray {
        checkInterrupted()
        val c = URL(url).openConnection() as HttpURLConnection
        connection = c
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", agent)
            authorization?.let { c.setRequestProperty("Authorization", it) }
            if (form != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode != 200) throw RedditAccessException(c.responseCode,
                (c.getHeaderField("Retry-After")?.toLongOrNull() ?: 60L).coerceIn(60L, 86_400L) * 1000L)
            if (c.contentLength > limit) throw IOException("Response too large")
            return c.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    checkInterrupted()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > limit) throw IOException("Response too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { c.disconnect(); if (connection === c) connection = null }
    }
}
internal fun checkInterrupted() { if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException() }
