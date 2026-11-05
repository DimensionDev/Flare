package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaHeadersV1Test {
    private val media = MediaV1("image", MediaTypeV1.Image, "https://cdn.example/image.jpg")

    @Test
    fun acceptsExplicitCredentialsAndProviderHeadersWithoutChangingValues() {
        val value =
            media.copy(
                customHeaders =
                    mapOf(
                        "Referer" to "https://www.bilibili.com/",
                        "User-Agent" to "Provider/1.0",
                        "Cookie" to "session=one; csrf=two",
                        "Authorization" to "identify_v1 token",
                        "X-Media-Token" to "opaque-token",
                    ),
            )
        value.requireValid()
        assertEquals(value, PluginJsonV1.decodeFromString<MediaV1>(PluginJsonV1.encodeToString(MediaV1.serializer(), value)))
        assertEquals(
            emptyMap(),
            PluginJsonV1.decodeFromString<MediaV1>("""{"id":"image","type":"image","url":"https://cdn.example/image.jpg"}""").customHeaders,
        )
    }

    @Test
    fun rejectsHeaderInjectionDuplicatesAndTransportOverrides() {
        val invalid =
            listOf(
                mapOf("Referer" to "safe\r\nX-Injected: value"),
                mapOf("Cookie" to "secret\u0000value"),
                mapOf("X-Token" to "secret\tvalue"),
                mapOf("Bad Name" to "value"),
                mapOf("Referer" to "one", "referer" to "two"),
            ) +
                listOf(
                    "Host",
                    "Connection",
                    "Content-Length",
                    "Transfer-Encoding",
                    "Range",
                    "If-Range",
                    "Accept-Encoding",
                    "Proxy-Authorization",
                    "Sec-Fetch-Site",
                ).map { mapOf(it to "value") }
        invalid.forEach { headers ->
            assertFailsWith<IllegalArgumentException> { media.copy(customHeaders = headers).requireValid() }
        }
    }

    @Test
    fun boundsHeaderCountIndividualValuesAndUtf8Bytes() {
        listOf(
            (1..33).associate { "X-$it" to "value" },
            mapOf("X-Token" to "a".repeat(8_193)),
            (1..8).associate { "X-$it" to "a".repeat(8_192) },
            (1..4).associate { "X-$it" to "界".repeat(8_192) },
        ).forEach { headers ->
            assertFailsWith<IllegalArgumentException> { media.copy(customHeaders = headers).requireValid() }
        }
        media.copy(customHeaders = (1..32).associate { "X-$it" to "value" }).requireValid()
    }
}
