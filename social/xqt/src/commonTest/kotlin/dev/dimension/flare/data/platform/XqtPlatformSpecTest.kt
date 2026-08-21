package dev.dimension.flare.data.platform

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
import kotlin.test.Test
import kotlin.test.assertEquals

class XqtPlatformSpecTest {
    @Test
    fun twitterPostDeepLinksUseStatusRoute() {
        val deepLinks = XqtPlatformSpec.deepLinks(MicroBlogKey(id = "1", host = xqtHost))

        assertEquals(
            "https://$xqtOldHost/{handle}/status/{id}",
            deepLinks[5].uriPattern,
        )
        assertEquals(
            "https://www.$xqtOldHost/{handle}/status/{id}",
            deepLinks[7].uriPattern,
        )
    }
}
