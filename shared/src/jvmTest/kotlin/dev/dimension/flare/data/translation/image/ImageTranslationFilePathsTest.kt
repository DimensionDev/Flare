package dev.dimension.flare.data.translation.image

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageTranslationFilePathsTest {
    @Test
    fun windowsDriveUsesAnEmptyAuthorityAndPreservesItsDrive() {
        val url = imageTranslationFileUrl("C:\\Temp\\translated 日本語.png")
        assertEquals("file", URI(url).scheme)
        assertEquals(null, URI(url).authority)
        assertEquals("/C:/Temp/translated 日本語.png", URI(url).path)
        assertEquals("C:/Temp/translated 日本語.png", imageTranslationFilePath(url))
    }

    @Test
    fun unixPathAndReservedCharactersRoundTrip() {
        val path = "/tmp/translated 50%+#日本語.png"
        val url = imageTranslationFileUrl(path)
        assertEquals(path, URI(url).path)
        assertEquals(path, imageTranslationFilePath(url))
    }

    @Test
    fun windowsUncPathKeepsItsNetworkRoot() {
        val url = imageTranslationFileUrl("\\\\server\\share\\image.png")
        assertEquals("//server/share/image.png", imageTranslationFilePath(url))
    }
}
