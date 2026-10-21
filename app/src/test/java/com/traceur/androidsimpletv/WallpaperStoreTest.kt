package com.traceur.androidsimpletv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun photo(id: String) = WallpaperPhoto(id, "Test landscape", "artist · Commons", "https://thumb.wikimedia.org/$id.jpg", "https://commons.wikimedia.org/wiki/File:$id.jpg", "CC0", "https://creativecommons.org/publicdomain/zero/1.0/")
    private fun image(w: Int = 1280, h: Int = 720): ByteArray {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out); bitmap.recycle(); out.toByteArray() }
    }
    @Test fun `cache persists offline with bounded size and source isolation`() {
        val store = WallpaperStore(context); store.clear()
        val bytes = image()
        val saved = store.replace("COMMONS", (1..10).map { photo("abc$it") }) { bytes }
        assertEquals(4, saved.photos.size)
        assertNull(store.load("different"))
        val loaded = WallpaperStore(context).load("COMMONS")!!
        assertEquals(4, loaded.images.size)
        assertEquals(1280, loaded.images.first().bitmap.width)
        assertTrue(java.io.File(context.filesDir, "public-wallpapers").walkTopDown().filter { it.isFile }.sumOf { it.length() } <= WallpaperStore.MAX_DISK_BYTES)
        store.clear(); assertNull(store.read("COMMONS"))
    }
    @Test fun `failed refresh and empty responses keep last good offline album`() {
        val store = WallpaperStore(context); store.clear()
        val saved = store.replace("COMMONS", listOf(photo("old1"))) { image() }
        assertTrue(runCatching { store.replace("COMMONS", listOf(photo("new1"))) { throw IOException("offline") } }.isFailure)
        assertEquals(saved, store.read("COMMONS"))
        assertNotNull(store.read("COMMONS", saved.fetched + 30L * 24 * 60 * 60_000))
        assertTrue(runCatching { store.replace("COMMONS", emptyList()) { image() } }.isFailure)
        assertEquals(saved, store.read("COMMONS"))
    }
    @Test fun `invalid images never replace the cache and panoramic photos can reload`() {
        val store = WallpaperStore(context); store.clear()
        val saved = store.replace("COMMONS", listOf(photo("wide1"))) { image(3840, 720) }
        assertEquals(1280, store.load("COMMONS")!!.images.single().bitmap.width)
        assertTrue(runCatching { store.replace("COMMONS", listOf(photo("bad1"))) { byteArrayOf(1, 2, 3) } }.isFailure)
        assertEquals(saved, store.read("COMMONS"))
        assertNull(WallpaperStore.decode(image(720, 1280)))
        assertNull(WallpaperStore.decode(image(100, 100)))
    }
    @Test fun `refresh staging stays below disk cap and oversize existing files are purged`() {
        val store = WallpaperStore(context); store.clear()
        val bytes = image()
        store.replace("COMMONS", (1..4).map { photo("old$it") }) { bytes }
        val root = java.io.File(context.filesDir, "public-wallpapers")
        store.replace("COMMONS", (1..4).map { photo("new$it") }) {
            assertTrue(root.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= WallpaperStore.MAX_DISK_BYTES)
            bytes
        }
        assertTrue(root.listFiles()!!.filter { it.extension == "jpg" }.all { it.length() <= WallpaperStore.MAX_FILE_BYTES })
        assertEquals(4, root.listFiles()!!.count { it.extension == "jpg" })
        java.io.File(root, "new1.jpg").writeBytes(ByteArray(WallpaperStore.MAX_FILE_BYTES.toInt() + 1))
        assertNull(store.read("COMMONS"))
    }
    @Test fun `unchanged photos are reused without downloading or recompressing`() {
        val store = WallpaperStore(context); store.clear()
        store.replace("COMMONS", listOf(photo("same1"))) { image() }
        val file = java.io.File(context.filesDir, "public-wallpapers/same1.jpg")
        val original = file.readBytes()
        store.replace("COMMONS", listOf(photo("same1"))) { error("Unexpected image download") }
        assertArrayEquals(original, file.readBytes())
    }
    @Test fun `interrupted refresh cannot publish changes`() {
        val store = WallpaperStore(context); store.clear()
        val saved = store.replace("COMMONS", listOf(photo("old1"))) { image() }
        try {
            Thread.currentThread().interrupt()
            assertTrue(runCatching { store.replace("COMMONS", listOf(photo("new1"))) { image() } }.isFailure)
        } finally { Thread.interrupted() }
        assertEquals(saved, store.read("COMMONS"))
    }
}
