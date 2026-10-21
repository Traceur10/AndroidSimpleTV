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
    private fun photo(id: String) = RedditPhoto(id, "Test landscape", "artist", "earthporn", "https://i.redd.it/$id.jpg", "https://www.reddit.com/r/earthporn/comments/$id/test/")
    private fun image(w: Int = 1280, h: Int = 720): ByteArray {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out); bitmap.recycle(); out.toByteArray() }
    }
    @Test fun `cache persists offline with bounded size and source isolation`() {
        val store = WallpaperStore(context); store.clear()
        val bytes = image()
        val saved = store.replace("earthporn", (1..10).map { photo("abc$it") }) { bytes }
        assertEquals(6, saved.photos.size)
        assertNull(store.load("different"))
        val loaded = WallpaperStore(context).load("earthporn")!!
        assertEquals(6, loaded.images.size)
        assertEquals(1280, loaded.images.first().bitmap.width)
        assertTrue(java.io.File(context.filesDir, "reddit-wallpapers").walkTopDown().filter { it.isFile }.sumOf { it.length() } < 48L * 1024 * 1024)
        store.clear(); assertNull(store.read("earthporn"))
    }
    @Test fun `failed refresh keeps last good album and expiry purges it`() {
        val store = WallpaperStore(context); store.clear()
        val saved = store.replace("earthporn", listOf(photo("old1"))) { image() }
        assertTrue(runCatching { store.replace("earthporn", listOf(photo("new1"))) { throw IOException("offline") } }.isFailure)
        assertEquals(saved, store.read("earthporn"))
        assertNotNull(store.read("earthporn", saved.fetched + WallpaperAlbum.MAX_AGE_MS - 1))
        assertNull(store.read("earthporn", saved.fetched + WallpaperAlbum.MAX_AGE_MS))
        assertFalse(java.io.File(context.filesDir, "reddit-wallpapers").exists())
    }
    @Test fun `invalid images never replace the cache and panoramic photos can reload`() {
        val store = WallpaperStore(context); store.clear()
        val saved = store.replace("earthporn", listOf(photo("wide1"))) { image(3840, 720) }
        assertEquals(1280, store.load("earthporn")!!.images.single().bitmap.width)
        assertTrue(runCatching { store.replace("earthporn", listOf(photo("bad1"))) { byteArrayOf(1, 2, 3) } }.isFailure)
        assertEquals(saved, store.read("earthporn"))
        assertNull(WallpaperStore.decode(image(720, 1280)))
        assertNull(WallpaperStore.decode(image(100, 100)))
    }
    @Test fun `empty successful listing clears obsolete cached photos`() {
        val store = WallpaperStore(context); store.clear()
        store.replace("earthporn", listOf(photo("old1"))) { image() }
        assertTrue(runCatching { store.replace("earthporn", emptyList()) { image() } }.isFailure)
        assertNull(store.read("earthporn"))
    }
    @Test fun `interrupted refresh cannot publish changes`() {
        val store = WallpaperStore(context); store.clear()
        val saved = store.replace("earthporn", listOf(photo("old1"))) { image() }
        try {
            Thread.currentThread().interrupt()
            assertTrue(runCatching { store.replace("earthporn", listOf(photo("new1"))) { image() } }.isFailure)
        } finally { Thread.interrupted() }
        assertEquals(saved, store.read("earthporn"))
    }
}
