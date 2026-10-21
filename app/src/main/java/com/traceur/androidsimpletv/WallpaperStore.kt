package com.traceur.androidsimpletv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal data class WallpaperAlbum(val source: String, val fetched: Long, val photos: List<RedditPhoto>) {
    fun usable(now: Long) = now >= fetched && now - fetched < MAX_AGE_MS
    companion object { const val MAX_AGE_MS = 48 * 60 * 60_000L }
}
internal data class LoadedWallpaper(val photo: RedditPhoto, val bitmap: Bitmap)
internal data class LoadedAlbum(val album: WallpaperAlbum, val images: List<LoadedWallpaper>)

/** Small app-private cache; committed metadata switches only after all new files are ready. */
internal class WallpaperStore(context: Context) {
    private val root = File(context.filesDir, "reddit-wallpapers")
    private val manifest get() = File(root, "album.json")

    fun read(source: String, now: Long = System.currentTimeMillis()): WallpaperAlbum? = synchronized(lock) {
        val album = readManifest() ?: run { root.deleteRecursively(); return@synchronized null }
        val keep = album.photos.map { "${it.id}.jpg" }.toSet() + "album.json"
        root.listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
        if (!album.usable(now)) { root.deleteRecursively(); return@synchronized null }
        album.takeIf { it.source == source }
    }
    private fun readManifest(): WallpaperAlbum? = runCatching {
        if (!manifest.exists() || manifest.length() > 64 * 1024) return null
        val o = JSONObject(manifest.readText())
        val a = o.getJSONArray("photos")
        require(a.length() in 1..MAX_PHOTOS)
        val photos = (0 until a.length()).map { RedditPhoto.fromJson(a.getJSONObject(it)) }
        require(photos.all { Regex("[a-z0-9]{1,16}").matches(it.id) })
        WallpaperAlbum(o.getString("source"), o.getLong("fetched"), photos)
    }.getOrNull()

    fun load(source: String, now: Long = System.currentTimeMillis()): LoadedAlbum? = synchronized(lock) {
        val album = read(source, now) ?: return@synchronized null
        val images = album.photos.mapNotNull { p ->
            checkInterrupted()
            val file = File(root, "${p.id}.jpg")
            val bitmap = if (file.length() in 1..MAX_FILE_BYTES) decode(file.readBytes()) else null
            bitmap?.let { LoadedWallpaper(p, it) }
        }
        if (images.isEmpty()) null else LoadedAlbum(album, images)
    }

    fun clear() = synchronized(lock) { root.deleteRecursively(); Unit }

    fun refresh(config: RedditConfig, client: RedditClient): WallpaperAlbum = replace(config.sourceKey, client.photos(config), client::image)

    internal fun replace(source: String, candidates: List<RedditPhoto>, download: (RedditPhoto) -> ByteArray): WallpaperAlbum = synchronized(lock) {
        checkInterrupted()
        root.mkdirs()
        val staging = File(root, "pending").apply { deleteRecursively(); mkdirs() }
        try {
            val accepted = mutableListOf<RedditPhoto>()
            for (photo in candidates.take(24)) {
                checkInterrupted()
                if (accepted.size == MAX_PHOTOS) break
                try {
                    val bitmap = decode(download(photo)) ?: continue
                    try {
                        File(staging, "${photo.id}.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        accepted.add(photo)
                    } finally { bitmap.recycle() }
                } catch (e: java.io.InterruptedIOException) { throw e }
                catch (e: RedditAccessException) { if (e.code == 429) throw e }
                catch (_: IOException) { /* A bad image does not discard other usable images. */ }
            }
            checkInterrupted()
            if (accepted.isEmpty()) {
                if (candidates.isEmpty()) root.deleteRecursively()
                throw IOException("No usable landscape photos")
            }
            val album = WallpaperAlbum(source, System.currentTimeMillis(), accepted)
            // AtomicFile preserves the previous manifest on a failed write. Image files are valid before it switches.
            accepted.forEach { p ->
                val target = File(root, "${p.id}.jpg")
                if (!File(staging, "${p.id}.jpg").renameTo(target)) throw IOException("Could not save photo")
            }
            val atomic = android.util.AtomicFile(manifest)
            val stream = atomic.startWrite()
            try {
                val json = JSONObject().put("source", source).put("fetched", album.fetched)
                    .put("photos", JSONArray(accepted.map { it.json() }))
                stream.write(json.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream)
            } catch (e: Exception) { atomic.failWrite(stream); throw e }
            val keep = accepted.map { "${it.id}.jpg" }.toSet() + "album.json"
            root.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
            album
        } finally {
            staging.deleteRecursively()
            val keep = (readManifest()?.photos?.map { "${it.id}.jpg" } ?: emptyList()).toSet() + "album.json" + "album.json.bak"
            root.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
        }
    }

    companion object {
        private val lock = Any()
        const val MAX_PHOTOS = 6
        private const val MAX_FILE_BYTES = 8L * 1024 * 1024
        internal fun decode(bytes: ByteArray): Bitmap? {
            if (bytes.size > MAX_FILE_BYTES) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth; val h = bounds.outHeight
            if (w < 1280 || h < 720 || w <= h || w > 20000 || h > 20000 || w.toLong() * h > 100_000_000L) return null
            var sample = 1
            while (w / sample > 3840 || h / sample > 2160) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565
            }) ?: return null
            // Normalize to the TV frame before caching, including very wide panoramas.
            val cropWidth = minOf(decoded.width, decoded.height * 16 / 9)
            val cropHeight = minOf(decoded.height, decoded.width * 9 / 16)
            val cropped = Bitmap.createBitmap(decoded, (decoded.width - cropWidth) / 2, (decoded.height - cropHeight) / 2, cropWidth, cropHeight)
            if (cropped !== decoded) decoded.recycle()
            val width = minOf(1920, cropWidth)
            val scaled = Bitmap.createScaledBitmap(cropped, width, width * 9 / 16, true)
            if (scaled !== cropped) cropped.recycle()
            return scaled
        }
    }
}
