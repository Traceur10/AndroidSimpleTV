package com.traceur.androidsimpletv

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PublicWallpapersTest {
    private fun commons(license: String = "CC BY-SA 4.0", host: String = "thumb.wikimedia.org", width: Int = 1920): String {
        val metadata = JSONObject()
        mapOf("Artist" to "<b>Artist Name</b>", "Credit" to "Own work", "LicenseShortName" to license,
            "LicenseUrl" to "https://creativecommons.org/licenses/by-sa/4.0/").forEach { (k, v) -> metadata.put(k, JSONObject().put("value", v)) }
        val info = JSONObject().put("thumburl", "https://$host/photo.jpg").put("thumbwidth", width).put("thumbheight", 1080)
            .put("descriptionurl", "https://commons.wikimedia.org/wiki/File:Landscape.jpg").put("mime", "image/jpeg").put("extmetadata", metadata)
        return JSONObject().put("query", JSONObject().put("pages", JSONArray().put(JSONObject().put("title", "File:Landscape.jpg").put("imageinfo", JSONArray().put(info))))).toString()
    }
    @Test fun `Commons retains plain attribution and license and rejects unknown licenses or unsafe URLs`() {
        val photo = PublicListings.commons(commons()).single()
        assertEquals("Artist Name · Wikimedia Commons", photo.credit)
        assertEquals("CC BY-SA 4.0", photo.license)
        assertTrue(photo.licenseUrl.contains("creativecommons.org"))
        assertTrue(PublicListings.commons(commons("All rights reserved")).isEmpty())
        assertTrue(PublicListings.commons(commons(host = "localhost")).isEmpty())
        assertTrue(PublicListings.commons(commons(width = 800)).isEmpty())
    }
    @Test fun `NASA selects smallest usable size and excludes explicit copyright entries`() {
        fun body(description: String): String {
            val data = JSONObject().put("media_type", "image").put("nasa_id", "test1").put("title", "Space").put("secondary_creator", "NASA/Test team").put("description", description)
            val links = JSONArray()
            for ((name, size) in listOf("orig" to 9_000_000, "large" to 2_000_000, "medium" to 500_000)) {
                links.put(JSONObject().put("render", "image").put("href", "https://images-assets.nasa.gov/image/test1/$name.jpg")
                    .put("width", 1920).put("height", 1080).put("size", size))
            }
            return JSONObject().put("collection", JSONObject().put("items", JSONArray().put(JSONObject().put("data", JSONArray().put(data)).put("links", links)))).toString()
        }
        val photo = PublicListings.nasa(body("Space scene")).single()
        assertTrue(photo.imageUrl.endsWith("medium.jpg")); assertEquals("NASA/Test team", photo.credit)
        assertTrue(PublicListings.nasa(body("Copyright external photographer")).isEmpty())
    }
    @Test fun `download URLs reject lookalikes local hosts credentials and insecure transport`() {
        for (url in listOf("http://upload.wikimedia.org/a.jpg", "https://thumb.wikimedia.org.evil.test/a.jpg", "https://localhost/a.jpg",
            "https://upload.wikimedia.org@evil.test/a.jpg", "https://images-assets.nasa.gov:444/a.jpg")) assertFalse(url, PublicListings.imageAllowed(url))
        assertTrue(PublicListings.imageAllowed("https://thumb.wikimedia.org/a.jpg"))
        assertTrue(PublicListings.imageAllowed("https://images-assets.nasa.gov/a.jpg"))
    }
    @Test fun `half-hour lease survives recreation failures source changes and clock rollback`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = LauncherPreferences(context)
        assertTrue(prefs.claimWallpaperRefresh(1000))
        assertFalse(LauncherPreferences(context).claimWallpaperRefresh(2000))
        prefs.wallpaperConfig = WallpaperConfig(setOf(WallpaperSource.NASA))
        assertFalse(prefs.claimWallpaperRefresh(900))
        assertFalse(prefs.claimWallpaperRefresh(1000 + LauncherPreferences.WALLPAPER_INTERVAL_MS - 1))
        assertTrue(prefs.claimWallpaperRefresh(1000 + LauncherPreferences.WALLPAPER_INTERVAL_MS))
        assertFalse(prefs.publicWallpapersEnabled)
    }
}
