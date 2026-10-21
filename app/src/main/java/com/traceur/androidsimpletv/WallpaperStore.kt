package com.traceur.androidsimpletv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal data class WallpaperAlbum(val source: String, val fetched: Long, val photos: List<WallpaperPhoto>) {
    fun usable(now: Long) = now >= fetched
}
internal data class LoadedWallpaper(val photo: WallpaperPhoto, val bitmap: Bitmap)
internal data class LoadedAlbum(val album: WallpaperAlbum, val images: List<LoadedWallpaper>)

/** Small app-private cache; committed metadata switches only after all new files are ready. */
internal class WallpaperStore(context: Context) {
    private val root = File(context.filesDir, "public-wallpapers")
    private val manifest get() = File(root, "album.json")

    fun read(source: String, now: Long = System.currentTimeMillis()): WallpaperAlbum? = synchronized(lock) {
        File(root.parentFile, "reddit-wallpapers").deleteRecursively()
        val album = readManifest() ?: run { root.deleteRecursively(); return@synchronized null }
        val keep = album.photos.map { "${it.id}.jpg" }.toSet() + "album.json"
        root.listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
        if (!album.usable(now)) { root.deleteRecursively(); return@synchronized null }
        if (root.walkTopDown().filter { it.isFile }.sumOf { it.length() } > MAX_DISK_BYTES ||
            album.photos.any { File(root, "${it.id}.jpg").length() > MAX_FILE_BYTES }) {
            root.deleteRecursively(); return@synchronized null
        }
        album.takeIf { it.source == source }
    }
    private fun readManifest(): WallpaperAlbum? = runCatching {
        if (!manifest.exists() || manifest.length() > 64 * 1024) return null
        val o = JSONObject(manifest.readText())
        val a = o.getJSONArray("photos")
        require(a.length() in 1..MAX_PHOTOS)
        val photos = (0 until a.length()).map { WallpaperPhoto.fromJson(a.getJSONObject(it)) }
        require(photos.all { Regex("[a-z0-9]{1,32}").matches(it.id) })
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

    fun refresh(config: WallpaperConfig, client: PublicWallpaperClient): WallpaperAlbum {
        val old = read(config.sourceKey)?.photos?.map { it.id }?.toSet().orEmpty()
        val candidates = client.photos(config)
        return replace(config.sourceKey, candidates.filter { it.id !in old } + candidates.filter { it.id in old }, client::image)
    }

    internal fun replace(source: String, candidates: List<WallpaperPhoto>, download: (WallpaperPhoto) -> ByteArray): WallpaperAlbum = synchronized(lock) {
        checkInterrupted()
        read(source) // Purge old Reddit data, incomplete writes and invalid cache before staging.
        root.mkdirs()
        if (root.usableSpace < MAX_DISK_BYTES + 3L * 1024 * 1024) throw IOException("Not enough free TV storage")
        val previous = readManifest()?.photos?.associateBy { it.id }.orEmpty()
        val staging = File(root, "pending").apply { deleteRecursively(); mkdirs() }
        try {
            val accepted = mutableListOf<WallpaperPhoto>()
            for (photo in candidates.distinctBy { it.id }.take(8)) {
                checkInterrupted()
                if (accepted.size == MAX_PHOTOS) break
                try {
                    val existing = File(root, "${photo.id}.jpg")
                    if (previous[photo.id]?.imageUrl == photo.imageUrl && existing.length() in 1..MAX_FILE_BYTES) {
                        val bytes = existing.readBytes()
                        val checked = decode(bytes)
                        if (checked != null) {
                            checked.recycle()
                            File(staging, "${photo.id}.jpg").writeBytes(bytes)
                            accepted.add(photo)
                            continue
                        }
                    }
                    val bitmap = decode(download(photo)) ?: continue
                    try {
                        var encoded: ByteArray? = null
                        for (quality in listOf(80, 65, 50, 35)) {
                            val buffer = java.io.ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
                            if (buffer.size() <= MAX_FILE_BYTES) { encoded = buffer.toByteArray(); break }
                        }
                        if (encoded != null) {
                            val used = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                            if (used + encoded.size + 64 * 1024 > MAX_DISK_BYTES) throw IOException("Wallpaper cache is full")
                            File(staging, "${photo.id}.jpg").writeBytes(encoded)
                            accepted.add(photo)
                        }
                    } finally { bitmap.recycle() }
                } catch (e: java.io.InterruptedIOException) { throw e }
                catch (e: WallpaperHttpException) { if (e.code == 429) throw e }
                catch (_: IOException) { /* A bad image does not discard other usable images. */ }
            }
            checkInterrupted()
            if (accepted.isEmpty()) {
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
        const val MAX_PHOTOS = 4
        const val MAX_FILE_BYTES = 512L * 1024
        const val MAX_DISK_BYTES = 5L * 1024 * 1024
        internal fun decode(bytes: ByteArray): Bitmap? {
            if (bytes.size > PublicWallpaperClient.IMAGE_LIMIT) return null
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
