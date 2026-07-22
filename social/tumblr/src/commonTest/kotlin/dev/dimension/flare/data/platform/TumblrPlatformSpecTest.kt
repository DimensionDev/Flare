package dev.dimension.flare.data.platform

import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertEquals

class TumblrPlatformSpecTest {
    @Test
    fun deepLinksIncludePostUrlsWithAndWithoutSlugs() {
        val patterns =
            TumblrPlatformSpec
                .deepLinks(MicroBlogKey(id = "staff", host = TUMBLR_HOST))
                .map { it.uriPattern }

        assertEquals(
            listOf(
                "https://www.tumblr.com/{blogName}/{id}",
                "https://www.tumblr.com/{blogName}/{id}/{slug}",
                "https://{blogName}.tumblr.com/post/{id}",
                "https://{blogName}.tumblr.com/post/{id}/{slug}",
                "https://www.tumblr.com/{blogName}",
                "https://{blogName}.tumblr.com",
            ),
            patterns,
        )
    }
}
