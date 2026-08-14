package dev.dimension.flare.data.network.bluesky

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlueskyLinkCardResolverTest {
    @Test
    fun parsesOpenGraphMetadataAndResolvesRelativeImageUrl() {
        val html =
            """
            <html>
              <head>
                <title>HTML title</title>
                <meta name="twitter:title" content="Twitter title">
                <meta property="og:title" content="  Open   Graph title  ">
                <meta property="og:description" content=" First
                 description ">
                <meta property="og:image" content="../images/card.png">
              </head>
            </html>
            """.trimIndent()

        val card =
            parseOpenGraph(
                html = html,
                baseUrl = "https://cdn.example.com/articles/entry/",
                cardUrl = "https://example.com/original",
            )

        assertEquals("https://example.com/original", card?.uri)
        assertEquals("Open Graph title", card?.title)
        assertEquals("First description", card?.description)
        assertEquals("https://cdn.example.com/articles/images/card.png", card?.imageUrl)
    }

    @Test
    fun fallsBackToDocumentMetadataAndNormalizedUrl() {
        val card =
            parseOpenGraph(
                html =
                    """
                    <html>
                      <head>
                        <title>Document title</title>
                        <meta name="description" content="Document description">
                      </head>
                    </html>
                    """.trimIndent(),
                baseUrl = "https://example.com/article",
            )

        assertEquals("https://example.com/article", card?.uri)
        assertEquals("Document title", card?.title)
        assertEquals("Document description", card?.description)
        assertNull(card?.imageUrl)
    }

    @Test
    fun parsesCardybResponseWithoutTrustingItsUrl() {
        val card =
            parseCardybResponse(
                value =
                    """
                    {
                      "url": "https://unexpected.example.com",
                      "title": " Card title ",
                      "description": "Card description",
                      "image": "https://cdn.example.com/card.jpg"
                    }
                    """.trimIndent(),
                cardUrl = "example.com/article",
            )

        assertEquals("https://example.com/article", card?.uri)
        assertEquals("Card title", card?.title)
        assertEquals("Card description", card?.description)
        assertEquals("https://cdn.example.com/card.jpg", card?.imageUrl)
    }

    @Test
    fun normalizesOnlyHttpUrls() {
        assertEquals("https://example.com/path", normalizeHttpUrl("example.com/path"))
        assertEquals("http://example.com/path", normalizeHttpUrl("http://example.com/path"))
        assertNull(normalizeHttpUrl("ftp://example.com/file"))
        assertNull(normalizeHttpUrl("mailto:user@example.com"))
    }

    @Test
    fun directResolverSendsHtmlHeadersAndParsesResponse() =
        runTest {
            var acceptHeader: String? = null
            var userAgent: String? = null
            val client =
                HttpClient(
                    MockEngine { request ->
                        acceptHeader = request.headers[HttpHeaders.Accept]
                        userAgent = request.headers[HttpHeaders.UserAgent]
                        respond(
                            content =
                                """
                                <meta property="og:title" content="Direct title">
                                <meta property="og:description" content="Direct description">
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                        )
                    },
                )

            try {
                val card =
                    BlueskyLinkCardResolver(
                        client = client,
                        metadataSource = LinkCardMetadataSource.Direct,
                        metadataTimeoutMillis = null,
                    ).resolve("https://example.com/article")

                assertEquals("Direct title", card?.title)
                assertEquals("Direct description", card?.description)
                assertEquals("text/html,application/xhtml+xml", acceptHeader)
                assertEquals("Flare/1.0 (+https://flareapp.moe)", userAgent)
            } finally {
                client.close()
            }
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
                )

            try {
                val card =
                    BlueskyLinkCardResolver(
                        client = client,
                        metadataSource = LinkCardMetadataSource.Cardyb,
                        metadataTimeoutMillis = null,
                    ).resolve("example.com/article")

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
                        if (request.url.encodedPath.endsWith(".png")) {
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
                )
            val resolver =
                BlueskyLinkCardResolver(
                    client = client,
                    metadataSource = LinkCardMetadataSource.Direct,
                    imageTimeoutMillis = null,
                )

            try {
                assertContentEquals(imageBytes, resolver.fetchImage("https://example.com/card.png"))
                assertNull(resolver.fetchImage("https://example.com/card.txt"))
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
