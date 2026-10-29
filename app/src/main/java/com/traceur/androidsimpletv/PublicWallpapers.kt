package com.traceur.androidsimpletv

import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

internal enum class WallpaperSource(val label: String) { COMMONS("Wikimedia Commons"), NASA("NASA") }
internal data class WallpaperConfig(val sources: Set<WallpaperSource>) {
    val sourceKey: String get() = sources.sortedBy { it.name }.joinToString("+") { it.name }
}
internal data class WallpaperPhoto(val id: String, val title: String, val credit: String, val imageUrl: String, val pageUrl: String, val license: String, val licenseUrl: String) {
    fun json() = JSONObject().put("id", id).put("title", title).put("credit", credit).put("image", imageUrl)
        .put("page", pageUrl).put("license", license).put("licenseUrl", licenseUrl)
    companion object {
        fun fromJson(o: JSONObject) = WallpaperPhoto(o.getString("id"), o.getString("title"), o.getString("credit"), o.getString("image"), o.getString("page"), o.getString("license"), o.getString("licenseUrl"))
        fun id(source: String) = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    }
}

internal object PublicListings {
    @Suppress("DEPRECATION")
    private fun plain(value: String) = Html.fromHtml(value).toString().replace("\uFFFC", "").replace(Regex("\\s+"), " ").trim()
    fun trusted(url: String, hosts: Set<String>): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host in hosts && uri.userInfo == null && uri.port == -1
    }.getOrDefault(false)
    fun imageAllowed(url: String) = trusted(url, setOf("upload.wikimedia.org", "thumb.wikimedia.org", "images-assets.nasa.gov"))
    fun pageAllowed(url: String) = trusted(url, setOf("commons.wikimedia.org", "images.nasa.gov"))
    fun licenseAllowed(url: String) = trusted(url, setOf("creativecommons.org", "www.nasa.gov"))

    fun commons(body: String): List<WallpaperPhoto> {
        val pages = JSONObject(body).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        return (0 until pages.length()).mapNotNull { index -> runCatching {
            val page = pages.getJSONObject(index)
            val info = page.getJSONArray("imageinfo").getJSONObject(0)
            val metadata = info.getJSONObject("extmetadata")
            fun value(key: String) = plain(metadata.optJSONObject(key)?.optString("value") ?: "")
            val license = value("LicenseShortName")
            if (!Regex("(CC BY(-SA)? [1-4]\\.0|CC0|Public domain)").matches(license)) return@runCatching null
            val author = value("Artist")
            val credit = value("Credit")
            // Do not silently truncate attribution. Unusually complex entries are skipped.
            if (author.isBlank() || author.length > 350 || credit.length > 350) return@runCatching null
            val width = info.optInt("thumbwidth"); val height = info.optInt("thumbheight")
            if (width < 1280 || height < 720 || width <= height || info.optString("mime") !in listOf("image/jpeg", "image/png")) return@runCatching null
            val image = info.getString("thumburl")
            val pageUrl = info.getString("descriptionurl")
            val licenseUrl = value("LicenseUrl").ifBlank {
                if (license == "CC0") "https://creativecommons.org/publicdomain/zero/1.0/" else "https://creativecommons.org/publicdomain/mark/1.0/"
            }.replaceFirst("http://", "https://")
            if (!imageAllowed(image) || !pageAllowed(pageUrl) || !licenseAllowed(licenseUrl)) return@runCatching null
            val credits = listOf(author, credit.takeUnless { it.equals("Own work", true) || it == author }.orEmpty(), "Wikimedia Commons").filter { it.isNotBlank() }.joinToString(" · ")
            WallpaperPhoto(WallpaperPhoto.id(pageUrl), page.getString("title").removePrefix("File:").take(200), credits, image, pageUrl, license, licenseUrl)
        }.getOrNull() }.distinctBy { it.id }
    }

    fun nasa(body: String): List<WallpaperPhoto> {
        val items = JSONObject(body).getJSONObject("collection").getJSONArray("items")
        return (0 until items.length()).mapNotNull { index -> runCatching {
            val item = items.getJSONObject(index)
            val data = item.getJSONArray("data").getJSONObject(0)
            if (data.optString("media_type") != "image") return@runCatching null
            val description = plain(data.optString("description"))
            if (description.contains("copyright", true) || description.contains("©")) return@runCatching null
            val links = item.getJSONArray("links")
            val choices = (0 until links.length()).map { links.getJSONObject(it) }.filter {
                it.optString("render") == "image" && imageAllowed(it.optString("href")) &&
                    it.optInt("width") >= 1280 && it.optInt("height") >= 720 && it.optInt("width") > it.optInt("height") &&
                    it.optLong("size", Long.MAX_VALUE) in 1..PublicWallpaperClient.IMAGE_LIMIT.toLong()
            }
            val image = choices.minByOrNull { it.getLong("size") }?.getString("href") ?: return@runCatching null
            val id = data.getString("nasa_id")
            val captionCredit = Regex("(?:Image [Cc]redit|[Cc]redits?):\\s*([^\\n<]+)").find(data.optString("description"))
                ?.groupValues?.get(1)?.substringBefore("----")?.substringBefore("Read more")?.trim().orEmpty()
            val credit = plain(data.optString("secondary_creator").ifBlank { data.optString("photographer").ifBlank { captionCredit.ifBlank { "NASA / ${data.optString("center")}" } } })
            if (credit.length > 500) return@runCatching null
            val page = "https://images.nasa.gov/details/" + URLEncoder.encode(id, "UTF-8")
            WallpaperPhoto(WallpaperPhoto.id(page), plain(data.getString("title")).take(200), credit, image, page,
                "NASA media guidelines", "https://www.nasa.gov/nasa-brand-center/images-and-media/")
        }.getOrNull() }.distinctBy { it.id }
    }
}

internal class WallpaperHttpException(val code: Int, val retryMs: Long = 3_600_000L) : IOException("Wallpaper request failed ($code)")
internal class PublicWallpaperClient {
    @Volatile private var connection: HttpURLConnection? = null
    private var remaining = TRANSFER_LIMIT
    private var requests = 0
    fun cancel() { connection?.disconnect() }
    fun photos(config: WallpaperConfig, now: Long = System.currentTimeMillis()): List<WallpaperPhoto> {
        remaining = TRANSFER_LIMIT; requests = 0
        val slot = ((now / 1_800_000L) % 4).toInt().coerceAtLeast(0)
        val lists = config.sources.sortedBy { it.name }.map { source ->
            try {
                when (source) {
                    WallpaperSource.COMMONS -> {
                        val query = listOf("mountain landscape", "forest landscape", "ocean coastline", "desert landscape")[slot]
                        val args = linkedMapOf("action" to "query", "format" to "json", "formatversion" to "2", "generator" to "search",
                            "gsrsearch" to "filetype:bitmap $query", "gsrnamespace" to "6", "gsrlimit" to "50", "gsroffset" to (((now / 1_800_000L / 4) % 10) * 50).toString(), "prop" to "imageinfo",
                            "iiprop" to "url|size|mime|extmetadata", "iiurlwidth" to "1920", "uselang" to "en",
                            "iiextmetadatafilter" to "Artist|Credit|LicenseShortName|LicenseUrl|Copyrighted|AttributionRequired")
                        PublicListings.commons(String(request("https://commons.wikimedia.org/w/api.php?" + args.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }, JSON_LIMIT), Charsets.UTF_8))
                    }
                    WallpaperSource.NASA -> {
                        val query = listOf("nebula", "earth from space", "galaxy", "saturn")[slot]
                        PublicListings.nasa(String(request("https://images-api.nasa.gov/search?media_type=image&page_size=50&page=${((now / 1_800_000L / 4) % 10) + 1}&q=" + URLEncoder.encode(query, "UTF-8"), JSON_LIMIT), Charsets.UTF_8))
                    }
                }.shuffled(java.util.Random(now / 1_800_000L))
            } catch (e: java.io.InterruptedIOException) { throw e }
            catch (e: WallpaperHttpException) { if (e.code == 429) throw e else emptyList() }
            catch (_: IOException) { emptyList() }
            catch (_: org.json.JSONException) { emptyList() }
        }
        // Alternate sources so one provider cannot fill every slot before the other is tried.
        return (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }.distinctBy { it.id }
    }
    fun image(photo: WallpaperPhoto): ByteArray { require(PublicListings.imageAllowed(photo.imageUrl)); return request(photo.imageUrl, IMAGE_LIMIT) }

    private fun request(url: String, limit: Int): ByteArray {
        checkInterrupted()
        if (++requests > 32 || remaining <= 0) throw IOException("Refresh budget reached")
        val c = URL(url).openConnection() as HttpURLConnection
        connection = c
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "AndroidSimpleTV/0.6.0 (https://github.com/Traceur10/AndroidSimpleTV)")
            c.setRequestProperty("Accept-Encoding", "identity")
            if (c.responseCode != 200) throw WallpaperHttpException(c.responseCode,
                (c.getHeaderField("Retry-After")?.toLongOrNull() ?: 3600L).coerceIn(3600L, 86400L) * 1000)
            if (c.contentLength > minOf(limit, remaining)) throw IOException("Response exceeds budget")
            return c.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    checkInterrupted()
                    // Read at most one byte beyond the cap to distinguish EOF from an oversized body.
                    val count = input.read(buffer, 0, minOf(buffer.size, limit - out.size() + 1, remaining + 1))
                    if (count < 0) break
                    remaining -= count
                    if (remaining < 0 || out.size() + count > limit) throw IOException("Response exceeds budget")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        } finally { c.disconnect(); if (connection === c) connection = null }
    }
    companion object { const val IMAGE_LIMIT = 3 * 1024 * 1024; const val JSON_LIMIT = 1024 * 1024; const val TRANSFER_LIMIT = 48 * 1024 * 1024 }
}
internal fun checkInterrupted() { if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException() }
