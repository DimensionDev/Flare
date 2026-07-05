package dev.dimension.flare.data.platform

import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxPlatformSpecTest {
    @Test
    fun deepLinksIncludeSubdomainUrls() {
        val deepLinks = FanboxPlatformSpec.deepLinks(MicroBlogKey(id = "1", host = FANBOX_HOST))

        assertEquals(
            "https://{creatorid}.fanbox.cc/posts/{id}",
            deepLinks[1].uriPattern,
        )
        assertEquals(
            "https://{creatorid}.fanbox.cc",
            deepLinks[3].uriPattern,
        )
    }
}
