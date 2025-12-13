package dev.dimension.flare.common.deeplink

import io.ktor.http.URLBuilder
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeepLinkPatternTest {
    @Test
    fun pathAndQueryParsersFollowSerializerDescriptor() {
        val url =
            URLBuilder("https://example.com")
                .apply {
                    path("profile", "{userId}", "{section}")
                    parameters.append("includeHistory", "{includeHistory}")
                    parameters.append("page", "{page}")
                }.build()

        val pattern = DeepLinkPattern(ProfileKey.serializer(), url)

        val segments = pattern.pathSegments.filter { it.stringValue.isNotEmpty() }
        assertEquals(3, segments.size)

        val staticSegment = segments[0]
        assertEquals("profile", staticSegment.stringValue)
        assertFalse(staticSegment.isParamArg)
        assertEquals("profile", staticSegment.typeParser("profile"))

        val userIdSegment = segments[1]
        assertEquals("userId", userIdSegment.stringValue)
        assertTrue(userIdSegment.isParamArg)
        assertEquals(123, userIdSegment.typeParser("123"))

        val sectionSegment = segments[2]
        assertEquals("section", sectionSegment.stringValue)
        assertTrue(sectionSegment.isParamArg)
        assertEquals("news", sectionSegment.typeParser("news"))

        val includeHistoryParser = requireNotNull(pattern.queryValueParsers["includeHistory"])
        assertEquals(true, includeHistoryParser("true"))

        val pageParser = requireNotNull(pattern.queryValueParsers["page"])
        assertEquals(5L, pageParser("5"))
    }

    @Test
    fun unsupportedSerialKindThrows() {
        val url =
            URLBuilder("https://example.com")
                .apply {
                    path("item", "{metadata}")
                }.build()

        assertFailsWith<IllegalArgumentException> {
            DeepLinkPattern(ComplexKey.serializer(), url)
        }
    }

    @Serializable
    private data class ProfileKey(
        val userId: Int,
        val section: String,
        val includeHistory: Boolean,
        val page: Long,
    )

    @Serializable
    private data class ComplexKey(
        val metadata: Nested,
    )

    @Serializable
    private data class Nested(
        val value: String,
    )
}
