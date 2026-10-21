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
        val saved = store.replace("COMMONS", (1..20).map { photo("abc$it") }) { bytes }
        assertEquals(15, saved.photos.size)
        assertNull(store.load("different"))
        val loaded = WallpaperStore(context).load("COMMONS")!!
        assertEquals(15, loaded.images.size)
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
        store.replace("COMMONS", (1..15).map { photo("old$it") }) { bytes }
        val root = java.io.File(context.filesDir, "public-wallpapers")
        store.replace("COMMONS", (1..15).map { photo("new$it") }) {
            assertTrue(root.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= WallpaperStore.MAX_DISK_BYTES)
            bytes
        }
        assertTrue(root.listFiles()!!.filter { it.extension == "jpg" }.all { it.length() <= WallpaperStore.MAX_FILE_BYTES })
        assertEquals(15, root.listFiles()!!.count { it.extension == "jpg" })
        java.io.File(root, "new1.jpg").writeBytes(ByteArray(WallpaperStore.MAX_FILE_BYTES.toInt() + 1))
        assertNull(store.read("COMMONS"))
    }
    @Test fun `recent photos are skipped across store recreation and failed refresh preserves history`() {
        val store = WallpaperStore(context); store.clear()
        store.replace("COMMONS", listOf(photo("old1"))) { image() }
        val loadedIds = mutableListOf<String>()
        WallpaperStore(context).replace("COMMONS", listOf(photo("old1"), photo("new1"))) { loadedIds.add(it.id); image() }
        assertEquals(listOf("new1"), loadedIds)
        assertTrue(runCatching { store.replace("COMMONS", listOf(photo("old1"), photo("new1"))) { error("Repeated photo download") } }.isFailure)
        assertEquals("new1", store.read("COMMONS")!!.photos.single().id)
        store.clear()
        store.replace("COMMONS", listOf(photo("old1"))) { image() }
        assertEquals("old1", store.read("COMMONS")!!.photos.single().id)
    }
    @Test fun `history remains bounded while retaining the newest photo IDs`() {
        val store = WallpaperStore(context); store.clear()
        store.replace("COMMONS", listOf(photo("start1"))) { image() }
        val manifest = java.io.File(context.filesDir, "public-wallpapers/album.json")
        val json = org.json.JSONObject(manifest.readText()).put("history", org.json.JSONArray((1..WallpaperStore.HISTORY_LIMIT).map { "old$it" }))
        manifest.writeText(json.toString())
        store.replace("COMMONS", listOf(photo("latest1"))) { image() }
        val history = org.json.JSONObject(manifest.readText()).getJSONArray("history")
        assertEquals(WallpaperStore.HISTORY_LIMIT, history.length())
        assertEquals("latest1", history.getString(history.length() - 1))
        assertNotEquals("old1", history.getString(0))
        assertTrue(manifest.length() < 512 * 1024)
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
