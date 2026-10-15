package dev.dimension.flare.data.network.rss

import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.fromRss
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RssArticleLoadingTest {
    @Test
    fun httpErrorsFallBackButSuccessfulExtractionWins() =
        runTest {
            for (status in listOf(301, 404, 500)) {
                val client = mockClient { respond("<h1>Error page</h1>", HttpStatusCode.fromValue(status)) }
                try {
                    val result =
                        Readability(
                            client,
                            StandardTestDispatcher(testScheduler),
                        ).load("https://example.com/a", title = "Feed title", fallbackHtml = "<p>Feed <b>content</b></p>")
                    assertEquals("Feed title", result.title)
                    assertEquals("Feed content", result.textContent)
                } finally {
                    client.close()
                }
            }
            val client = mockClient { respond(ARTICLE) }
            try {
                val result =
                    Readability(
                        client,
                        StandardTestDispatcher(testScheduler),
                    ).load("https://example.com/a", fallbackHtml = "<p>Feed content</p>")
                assertEquals("Extracted title", result.title)
                assertTrue(result.textContent.contains("Actual article"))
            } finally {
                client.close()
            }
        }

    @Test
    fun descriptionModeDoesNotFetchAndImageOnlyFeedIsUsable() =
        runTest {
            val client = mockClient { error("Description mode must not fetch") }
            try {
                val reader = Readability(client, StandardTestDispatcher(testScheduler))
                assertEquals("Feed content", reader.load("https://example.com", descriptionHtml = "<p>Feed content</p>").textContent)
                assertTrue(
                    reader.load("https://example.com", descriptionHtml = "<img src='https://example.com/a.jpg'>").content.contains("img"),
                )
                assertFailsWith<ArticleExtractionException> { reader.load("https://example.com", descriptionHtml = "<p> </p>") }
            } finally {
                client.close()
            }
        }

    @Test
    fun timeoutFallsBackAndCancellationDoesNot() =
        runTest {
            val slowClient =
                mockClient {
                    delay(20_000)
                    respond(ARTICLE)
                }
            try {
                assertEquals(
                    "Feed",
                    Readability(
                        slowClient,
                        StandardTestDispatcher(testScheduler),
                    ).load("https://example.com", fallbackHtml = "Feed").textContent,
                )
            } finally {
                slowClient.close()
            }
            val cancelledClient = mockClient { throw CancellationException("Leaving article") }
            try {
                assertFailsWith<CancellationException> {
                    Readability(cancelledClient, StandardTestDispatcher(testScheduler)).load("https://example.com", fallbackHtml = "Feed")
                }
            } finally {
                cancelledClient.close()
            }
        }

    @Test
    fun emptyFallbackPreservesOriginalFailure() =
        runTest {
            val failure = IllegalStateException("Network unavailable")
            val client = mockClient { throw failure }
            try {
                val result =
                    runCatching {
                        Readability(
                            client,
                            StandardTestDispatcher(testScheduler),
                        ).load("https://example.com", fallbackHtml = "<script>oops()</script><p> </p>")
                    }
                assertTrue(result.exceptionOrNull() is IllegalStateException)
                assertEquals(failure.message, result.exceptionOrNull()?.message)
            } finally {
                client.close()
            }
        }

    @Test
    fun extractionUsesFinalUrlAfterRedirects() =
        runTest {
            val client =
                mockClient { request ->
                    if (request.url.encodedPath == "/old") {
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.com/new/article"))
                    } else {
                        respond(ARTICLE.replace("</p>", "<img src='image.jpg'></p>"))
                    }
                }
            try {
                assertTrue(
                    Readability(
                        client,
                        StandardTestDispatcher(testScheduler),
                    ).extract("https://example.com/old").content.contains("https://example.com/new/image.jpg"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun encodedContentAndOriginalProtocolSurviveNavigation() {
        val feed =
            parseFeedText(
                """
                <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
                  <channel><title>Feed</title><link>http://example.com</link>
                    <item><title>Title</title><link>http://example.com/a</link><description>Summary</description>
                      <content:encoded><![CDATA[<p>Full feed article</p>]]></content:encoded>
                    </item>
                  </channel>
                </rss>
                """.trimIndent(),
            ) as Feed.Rss20
        val item =
            feed.channel.items
                .single()
                .render("Feed", null, RssDisplayMode.FULL_CONTENT) as UiTimelineV2.Feed
        val route = DeeplinkRoute.parse((item.clickEvent as ClickEvent.Deeplink).url) as DeeplinkRoute.Rss.Detail
        assertEquals("http://example.com/a", route.url)
        assertEquals("<p>Full feed article</p>", route.fallbackHtml)
        assertEquals(null, route.descriptionHtml)
        assertEquals(MicroBlogKey.fromRss("https://example.com/a"), item.statusKey)
        assertEquals("http://example.com/feed", "http://example.com/feed".toRssWebUrl())
    }

    @Test
    fun atomAndRdfAlsoPreserveOriginalProtocol() {
        val atom =
            parseFeedText(
                """<feed><id>feed</id><title>Feed</title><updated>2025-01-01T00:00:00Z</updated>
          <entry><id>entry</id><title>Title</title><updated>2025-01-01T00:00:00Z</updated>
            <link rel="self" href="https://example.com/api"/><link rel="alternate" href="http://example.com/article"/>
            <content type="html">&lt;p&gt;Article&lt;/p&gt;</content>
          </entry></feed>""",
            ) as Feed.Atom
        val atomItem = atom.entries.single().render("Feed", null, RssDisplayMode.FULL_CONTENT) as UiTimelineV2.Feed
        val rdfItem =
            Feed.RDF
                .Item(
                    "Title",
                    "http://example.com/article",
                    "Content",
                ).render("Feed", null, RssDisplayMode.OPEN_IN_BROWSER) as UiTimelineV2.Feed
        assertEquals("http://example.com/article", atomItem.url)
        assertEquals("http://example.com/article", rdfItem.url)
    }
}

internal val ARTICLE =
    """
    <html><head><title>Extracted title</title></head><body><article><p>
    ${"Actual article with enough readable content. ".repeat(30)}
    </p></article></body></html>
    """.trimIndent()

private fun TestScope.mockClient(handler: MockRequestHandler): HttpClient =
    HttpClient(
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            },
        ),
    )
