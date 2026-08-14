package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.nullableFallbackJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlueskyLinkCardResolverTest {
    @Test
    fun normalizesOnlyHttpUrls() {
        assertEquals("https://example.com/path", normalizeHttpUrl("example.com/path"))
        assertEquals("http://example.com/path", normalizeHttpUrl("http://example.com/path"))
        assertNull(normalizeHttpUrl("ftp://example.com/file"))
        assertNull(normalizeHttpUrl("mailto:user@example.com"))
    }

    @Test
    fun acceptsOnlyCardybImageProxyUrls() {
        val proxyUrl = "https://cardyb.bsky.app/v1/image?url=https%3A%2F%2Fexample.com%2Fcard.png"

        assertEquals(proxyUrl, normalizeCardybImageUrl(proxyUrl))
        listOf(
            "http://127.0.0.1/private.png",
            "https://192.168.1.1/private.png",
            "http://cardyb.bsky.app/v1/image?url=https%3A%2F%2Fexample.com%2Fcard.png",
            "https://user:password@cardyb.bsky.app/v1/image?url=https%3A%2F%2Fexample.com%2Fcard.png",
            "https://cardyb.bsky.app.evil.test/v1/image?url=https%3A%2F%2Fexample.com%2Fcard.png",
            "https://cardyb.bsky.app:444/v1/image?url=https%3A%2F%2Fexample.com%2Fcard.png",
            "https://cardyb.bsky.app/v1/other?url=https%3A%2F%2Fexample.com%2Fcard.png",
        ).forEach { assertNull(normalizeCardybImageUrl(it)) }
    }

    @Test
    fun cardybResolverPassesTheOriginalUrlAsQueryParameter() =
        runTest {
            var requestedUrl: String? = null
            val client =
                HttpClient(
                    MockEngine { request ->
                        requestedUrl = request.url.parameters["url"]
                        respond(
                            content = """{"title":"Proxy title","description":"Proxy description"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                ) {
                    install(ContentNegotiation) {
                        nullableFallbackJson(JSON)
                    }
                }

            try {
                val card =
                    withContext(Dispatchers.Default) {
                        BlueskyLinkCardResolver(client).resolve("example.com/article")
                    }

                assertEquals("https://example.com/article", requestedUrl)
                assertEquals("Proxy title", card?.title)
            } finally {
                client.close()
            }
        }

    @Test
    fun imageFetchAcceptsImagesAndRejectsOtherContentTypes() =
        runTest {
            val imageBytes = byteArrayOf(1, 2, 3, 4)
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.url.parameters["url"]?.endsWith(".png") == true) {
                            respond(
                                content = ByteReadChannel(imageBytes),
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
                            )
                        } else {
                            respond(
                                content = "not an image",
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                            )
                        }
                    },
                ) {
                    followRedirects = false
                }
            val resolver = BlueskyLinkCardResolver(client)

            try {
                withContext(Dispatchers.Default) {
                    assertContentEquals(
                        imageBytes,
                        resolver.fetchImage(
                            "https://cardyb.bsky.app/v1/image?url=https%3A%2F%2Fexample.com%2Fcard.png",
                        ),
                    )
                    assertNull(
                        resolver.fetchImage(
                            "https://cardyb.bsky.app/v1/image?url=https%3A%2F%2Fexample.com%2Fcard.txt",
                        ),
                    )
                    assertNull(resolver.fetchImage("http://127.0.0.1/private.png"))
                }
            } finally {
                client.close()
            }
        }

    @Test
    fun imageFetchDoesNotFollowProxyRedirects() =
        runTest {
            var requestCount = 0
            val client =
                HttpClient(
                    MockEngine {
                        requestCount++
                        respond(
                            content = "",
                            status = HttpStatusCode.Found,
                            headers = headersOf(HttpHeaders.Location, "http://127.0.0.1/private.png"),
                        )
                    },
                ) {
                    followRedirects = false
                }

            try {
                val image =
                    withContext(Dispatchers.Default) {
                        BlueskyLinkCardResolver(client).fetchImage(
                            "https://cardyb.bsky.app/v1/image?url=https%3A%2F%2Fexample.com%2Fcard.png",
                        )
                    }

                assertNull(image)
                assertEquals(1, requestCount)
            } finally {
                client.close()
            }
        }

    @Test
    fun createsTheBlueskyExternalEmbedPayload() {
        val embed =
            BlueskyLinkCard(
                uri = "https://example.com/article",
                title = "Article title",
                description = "Article description",
                imageUrl = null,
            ).toExternalEmbed(thumb = null)

        assertEquals("https://example.com/article", embed.value.external.uri.uri)
        assertEquals("Article title", embed.value.external.title)
        assertEquals("Article description", embed.value.external.description)
        assertNull(embed.value.external.thumb)
    }
}
