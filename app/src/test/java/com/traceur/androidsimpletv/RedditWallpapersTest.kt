package com.traceur.androidsimpletv

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RedditWallpapersTest {
    private fun post() = JSONObject().put("id", "abc123").put("title", "Mountains").put("author", "photographer")
        .put("subreddit", "EarthPorn").put("permalink", "/r/EarthPorn/comments/abc123/mountains/")
        .put("url", "https://i.redd.it/landscape.jpg").put("over_18", false)
    private fun listing(vararg posts: JSONObject) = JSONObject().put("data", JSONObject().put("children",
        JSONArray(posts.map { JSONObject().put("kind", "t3").put("data", it) }))).toString()

    @Test fun `normalizes sources and rejects malformed or excessive inputs`() {
        assertEquals(listOf("earthporn", "spaceporn"), RedditConfig.subreddits("r/EarthPorn, earthporn + SpacePorn"))
        for (bad in listOf("", "../secret", "https://reddit.com/r/test", "a,b,c,d,e,f", "earthporn?limit=100")) {
            assertTrue(bad, runCatching { RedditConfig.subreddits(bad) }.isFailure)
        }
        assertFalse(RedditConfig(listOf("earthporn"), "", "").ready)
    }
    @Test fun `accepts safe direct images with credits and removes duplicates`() {
        val photos = RedditListing.parse(listing(post(), post()))
        assertEquals(1, photos.size)
        assertEquals("r/EarthPorn · u/photographer", photos.single().credit)
        assertEquals("https://www.reddit.com/r/EarthPorn/comments/abc123/mountains/", photos.single().permalink)
    }
    @Test fun `filters marked adult spoiler removed deleted and non static posts`() {
        for (flag in listOf("over_18", "spoiler", "is_video", "is_self", "is_gallery", "stickied")) {
            assertTrue(flag, RedditListing.parse(listing(post().put(flag, true))).isEmpty())
        }
        assertTrue(RedditListing.parse(listing(post().put("removed_by_category", "moderator"))).isEmpty())
        assertTrue(RedditListing.parse(listing(post().put("author", "[deleted]"))).isEmpty())
        val missingRating = post().apply { remove("over_18") }
        assertTrue(RedditListing.parse(listing(missingRating)).isEmpty())
    }
    @Test fun `image allowlist rejects local hosts lookalikes credentials redirects and animation`() {
        for (url in listOf("http://i.redd.it/a.jpg", "https://i.redd.it.evil.test/a.jpg", "https://localhost/a.jpg",
            "https://i.redd.it@evil.test/a.jpg", "https://i.redd.it:8443/a.jpg", "https://i.redd.it/a.gif", "https://i.redd.it/../a.jpg")) {
            assertFalse(url, RedditListing.imageUrlAllowed(url))
        }
        assertTrue(RedditListing.imageUrlAllowed("https://i.redd.it/abc.png"))
    }
}
