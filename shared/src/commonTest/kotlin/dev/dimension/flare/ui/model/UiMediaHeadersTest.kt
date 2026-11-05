package dev.dimension.flare.ui.model

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class UiMediaHeadersTest {
    @Test
    fun customTokenCachesArePartitionedByHeadersAndViewingAccount() {
        val image =
            UiMedia.Image(
                "https://cdn.example/image",
                "https://cdn.example/preview",
                null,
                1f,
                1f,
                false,
                customHeaders = persistentMapOf("X-Media-Token" to "fixture-token", "Referer" to "https://example.com/"),
                proxy = ProxyRef.Account(MicroBlogKey("alice", "example.com")),
            )
        assertNotEquals(image.cacheKey, image.copy(proxy = ProxyRef.Account(MicroBlogKey("bob", "example.com"))).cacheKey)
        assertNotEquals(image.cacheKey, image.copy(customHeaders = persistentMapOf("X-Media-Token" to "other-token")).cacheKey)
        assertEquals(
            image.cacheKey,
            image
                .copy(
                    customHeaders =
                        persistentMapOf(
                            "referer" to "https://example.com/",
                            "x-media-token" to "fixture-token",
                        ),
                ).cacheKey,
        )
        assertFalse(image.cacheKey.contains("fixture-token"))
        assertEquals(image.customHeaders, assertNotNull(image.preview).customHeaders)
    }
}
