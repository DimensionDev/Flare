package dev.dimension.flare.data.network.tumblr

import dev.dimension.flare.data.platform.TUMBLR_HOST
import dev.dimension.flare.model.PlatformType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TumblrPlatformDetectorTest {
    @Test
    fun detectsRootAndBlogHostsAsTumblr() =
        runTest {
            listOf(TUMBLR_HOST, "www.tumblr.com", "Staff.tumblr.com").forEach { host ->
                val result = TumblrPlatformDetector.detect(host)

                assertEquals(TUMBLR_HOST, result?.host)
                assertEquals(PlatformType.Tumblr, result?.platformType)
            }
        }

    @Test
    fun rejectsLookalikeAndNestedHosts() =
        runTest {
            assertNull(TumblrPlatformDetector.detect("tumblr.com.example.org"))
            assertNull(TumblrPlatformDetector.detect("not-tumblr.com"))
            assertNull(TumblrPlatformDetector.detect("foo.bar.tumblr.com"))
        }
}
