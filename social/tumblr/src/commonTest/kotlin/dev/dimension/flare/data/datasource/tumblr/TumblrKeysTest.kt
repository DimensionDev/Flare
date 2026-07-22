package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.platform.TUMBLR_HOST
import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertEquals

class TumblrKeysTest {
    @Test
    fun blogNamesAreNormalizedForUserKeys() {
        assertEquals(
            MicroBlogKey(id = "staff", host = TUMBLR_HOST),
            tumblrUserKey("https://www.Staff.tumblr.com/post/123"),
        )
        assertEquals(
            MicroBlogKey(id = "staff", host = TUMBLR_HOST),
            tumblrUserKey("@Staff"),
        )
        assertEquals(
            MicroBlogKey(id = "staff", host = TUMBLR_HOST),
            tumblrUserKey("https://www.tumblr.com/staff"),
        )
        assertEquals(
            MicroBlogKey(id = "staff", host = TUMBLR_HOST),
            tumblrUserKey("https://www.tumblr.com/@Staff"),
        )
        assertEquals(
            MicroBlogKey(id = "www.davidslog.com", host = TUMBLR_HOST),
            tumblrUserKey("https://www.davidslog.com/archive"),
        )
    }

    @Test
    fun postKeysRoundTripBlogAndPostId() {
        val key = tumblrPostKey(blogName = "Staff", postId = "1234567890")

        assertEquals(MicroBlogKey(id = "staff:1234567890", host = TUMBLR_HOST), key)
        assertEquals(
            TumblrPostKeyParts(blogName = "staff", postId = "1234567890"),
            key.toTumblrPostKeyParts(),
        )
    }

    @Test
    fun blogUrlUsesTumblrSubdomain() {
        assertEquals(
            "https://staff.tumblr.com/",
            tumblrBlogUrl("https://www.Staff.tumblr.com/archive"),
        )
    }
}
