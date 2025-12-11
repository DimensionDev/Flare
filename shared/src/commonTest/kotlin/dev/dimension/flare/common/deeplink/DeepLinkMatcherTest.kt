package dev.dimension.flare.common.deeplink

import io.ktor.http.URLBuilder
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeepLinkMatcherTest {
    @Test
    fun exactMatchWithoutArgsReturnsSerializerAndEmptyArgs() {
        val url = URLBuilder("https://example.com").apply { path("home") }.build()
        val pattern = DeepLinkPattern(HomeKey.serializer(), url)
        val request = DeepLinkRequest(url)

        val match = DeepLinkMatcher(request, pattern).match()

        assertNotNull(match)
        assertEquals(HomeKey.serializer(), match.serializer)
        assertEquals(emptyMap(), match.args)
    }

    @Test
    fun parsesPathAndQueryArgs() {
        val patternUrl =
            URLBuilder("https://example.com")
                .apply {
                    path("profile", "{userId}")
                    parameters.append("includeHistory", "{includeHistory}")
                    parameters.append("page", "{page}")
                }.build()
        val requestUrl =
            URLBuilder("https://example.com")
                .apply {
                    path("profile", "42")
                    parameters.append("includeHistory", "true")
                    parameters.append("page", "2")
                }.build()
        val pattern = DeepLinkPattern(ProfileKey.serializer(), patternUrl)
        val request = DeepLinkRequest(requestUrl)

        val match = DeepLinkMatcher(request, pattern).match()

        assertNotNull(match)
        assertEquals(ProfileKey.serializer(), match.serializer)
        assertEquals(
            mapOf("userId" to 42, "includeHistory" to true, "page" to 2L),
            match.args,
        )
    }

    @Test
    fun returnsNullWhenStaticSegmentsDoNotMatch() {
        val patternUrl =
            URLBuilder("https://example.com")
                .apply { path("profile", "{userId}") }
                .build()
        val requestUrl =
            URLBuilder("https://example.com")
                .apply { path("settings", "42") }
                .build()
        val pattern = DeepLinkPattern(ProfileKey.serializer(), patternUrl)
        val request = DeepLinkRequest(requestUrl)

        val match = DeepLinkMatcher(request, pattern).match()

        assertNull(match)
    }

    @Test
    fun returnsNullWhenPathParsingFails() {
        val patternUrl =
            URLBuilder("https://example.com")
                .apply { path("profile", "{userId}") }
                .build()
        val requestUrl =
            URLBuilder("https://example.com")
                .apply { path("profile", "not-a-number") }
                .build()
        val pattern = DeepLinkPattern(ProfileKey.serializer(), patternUrl)
        val request = DeepLinkRequest(requestUrl)

        val match = DeepLinkMatcher(request, pattern).match()

        assertNull(match)
    }

    @Test
    fun returnsNullWhenQueryParsingFails() {
        val patternUrl =
            URLBuilder("https://example.com")
                .apply {
                    path("profile", "{userId}")
                    parameters.append("page", "{page}")
                }.build()
        val requestUrl =
            URLBuilder("https://example.com")
                .apply {
                    path("profile", "42")
                    parameters.append("page", "abc")
                }.build()
        val pattern = DeepLinkPattern(ProfileKey.serializer(), patternUrl)
        val request = DeepLinkRequest(requestUrl)

        val match = DeepLinkMatcher(request, pattern).match()

        assertNull(match)
    }

    @Serializable
    private data class HomeKey(
        val value: String = "",
    )

    @Serializable
    private data class ProfileKey(
        val userId: Int,
        val includeHistory: Boolean = false,
        val page: Long = 0,
    )
}
