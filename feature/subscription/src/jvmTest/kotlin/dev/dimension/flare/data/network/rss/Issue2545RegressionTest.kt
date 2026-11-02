package dev.dimension.flare.data.network.rss

import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Issue2545RegressionTest {
    @Test
    fun fullContentPreservesOriginalHttpUrl() {
        assertEquals("http://legacy.example/article", item().url)
    }

    @Test
    fun fullContentRetainsEmbeddedHtmlForFallback() {
        val feed = item()
        val route = DeeplinkRoute.parse((feed.clickEvent as ClickEvent.Deeplink).url) as DeeplinkRoute.Rss.Detail
        assertEquals(feed.descriptionHtml, route.fallbackHtml)
    }

    @Test
    fun unresolvedRedirectIsNotAnArticle() =
        runBlocking {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content =
                                """
                                <html><head><title>301 Moved Permanently</title></head>
                                <body><center><h1>301 Moved Permanently</h1></center><hr><center>nginx</center></body></html>
                                """.trimIndent(),
                            status = HttpStatusCode.MovedPermanently,
                            headers = headersOf(HttpHeaders.Location, "http://legacy.example/article"),
                        )
                    },
                )
            val parser = Readability(client)
            try {
                val result = runCatching { parser.extract("https://legacy.example/article") }
                assertTrue(result.isFailure, "301 must fail extraction; actual result: $result")
            } finally {
                client.close()
            }
        }

    @Test
    fun responseValidationRejectsTheSameRedirect() =
        runBlocking {
            withClient(
                HttpClient(
                    MockEngine {
                        respond(
                            "<html><head><title>301 Moved Permanently</title></head><body><h1>301 Moved Permanently</h1><p>nginx</p></body></html>",
                            HttpStatusCode.MovedPermanently,
                            headersOf(HttpHeaders.Location, "http://legacy.example/article"),
                        )
                    },
                ) { expectSuccess = true },
            ) { parser ->
                assertTrue(runCatching { parser.extract("https://legacy.example/article") }.isFailure)
            }
        }

    @Test
    fun clientPreservesHttpWhenMapperIsBypassed() =
        runBlocking {
            withClient(
                HttpClient(
                    MockEngine { request ->
                        assertEquals("http://legacy.example/article", request.url.toString())
                        respond(
                            """
                            <html><head><title>Article</title></head><body><article><p>
                            ${"A readable article with enough text for extraction. ".repeat(30)}
                            </p></article></body></html>
                            """.trimIndent(),
                        )
                    },
                ),
            ) { parser ->
                assertTrue(runCatching { parser.extract("http://legacy.example/article") }.isSuccess)
            }
        }

    @Test
    fun descriptionOnlyKeepsEmbeddedHtml() {
        val feed =
            Feed.Rss20
                .Item(
                    title = "Article",
                    link = "http://legacy.example/article",
                    description = "<p>Embedded article remains readable.</p>",
                ).render("Test feed", null, RssDisplayMode.DESCRIPTION_ONLY) as UiTimelineV2.Feed
        val route = DeeplinkRoute.parse((feed.clickEvent as ClickEvent.Deeplink).url) as DeeplinkRoute.Rss.Detail
        assertEquals(feed.descriptionHtml, route.descriptionHtml)
    }

    @Test
    fun tlsFailurePropagatesAsFailedExtraction() =
        runBlocking {
            withClient(HttpClient(MockEngine { throw SSLHandshakeException("certificate expired") })) { parser ->
                val result = runCatching { parser.extract("https://legacy.example/article") }
                assertTrue(result.exceptionOrNull() is SSLHandshakeException)
            }
        }

    @Test
    fun securityPolicyFailuresRecommendEmbeddedContent() =
        runBlocking {
            val feed =
                Feed.Rss20(
                    "2.0",
                    Feed.Rss20.Channel(
                        "Feed",
                        "https://example.com",
                        items = listOf(Feed.Rss20.Item(link = "http://example.com/article", description = "<p>Feed body</p>")),
                    ),
                )
            for (failure in listOf(
                SSLHandshakeException("certificate expired"),
                UnknownServiceException("CLEARTEXT communication to example.com not permitted by network security policy"),
            )) {
                assertEquals(RssDisplayMode.DESCRIPTION_ONLY, recommendRssDisplayMode(feed) { throw failure })
            }
            assertEquals(null, recommendRssDisplayMode(feed) { throw UnknownServiceException("Unknown protocol") })
        }

    private suspend fun withClient(
        client: HttpClient,
        block: suspend (Readability) -> Unit,
    ) {
        val parser = Readability(client)
        try {
            block(parser)
        } finally {
            client.close()
        }
    }

    private fun item(): UiTimelineV2.Feed =
        Feed.Rss20
            .Item(
                title = "Legacy article",
                link = "http://legacy.example/article",
                description = "<p>Embedded article remains readable.</p>",
            ).render("Test feed", null, RssDisplayMode.FULL_CONTENT) as UiTimelineV2.Feed
}
