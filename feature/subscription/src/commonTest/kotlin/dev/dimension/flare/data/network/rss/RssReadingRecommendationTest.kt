package dev.dimension.flare.data.network.rss

import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.network.rss.model.Feed
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RssReadingRecommendationTest {
    @Test
    fun samplesAtMostTwoDistinctArticlesAndStopsOnSuccess() =
        runTest {
            val visited = mutableListOf<String>()
            val feed = feed(listOf("http://example.com/1", "http://example.com/1", "http://example.com/2", "http://example.com/3"))
            val result =
                recommendRssDisplayMode(feed) {
                    visited += it
                    if (it.endsWith("1")) throw ArticleExtractionException()
                    document()
                }
            assertEquals(RssDisplayMode.FULL_CONTENT, result)
            assertEquals(listOf("http://example.com/1", "http://example.com/2"), visited)
            visited.clear()
            recommendRssDisplayMode(feed) {
                visited += it
                document()
            }
            assertEquals(1, visited.size)
        }

    @Test
    fun permanentFailureChoosesFeedOrBrowserAndTransientFailureIsUnknown() =
        runTest {
            for (status in listOf(301, 403, 404, 408, 429, 500, 503)) {
                val client = mockClient { respond("<h1>Error</h1>", HttpStatusCode.fromValue(status)) }
                try {
                    val permanent = status in listOf(301, 403, 404)
                    assertEquals(
                        if (permanent) RssDisplayMode.DESCRIPTION_ONLY else null,
                        recommendRssDisplayMode(feed(), Readability(client, StandardTestDispatcher(testScheduler))::extract),
                    )
                    assertEquals(
                        if (permanent) RssDisplayMode.OPEN_IN_BROWSER else null,
                        recommendRssDisplayMode(feed(html = null), Readability(client, StandardTestDispatcher(testScheduler))::extract),
                    )
                } finally {
                    client.close()
                }
            }
            assertEquals(null, recommendRssDisplayMode(feed()) { throw IllegalStateException("Offline") })
            assertEquals(RssDisplayMode.DESCRIPTION_ONLY, recommendRssDisplayMode(feed()) { throw ArticleExtractionException() })
        }

    @Test
    fun emptyFeedsTimeoutAndCancellationDoNotProduceFalseRecommendations() =
        runTest {
            assertEquals(null, recommendRssDisplayMode(feed(emptyList())) { error("No request expected") })
            assertEquals(null, recommendRssDisplayMode(feed(listOf("mailto:person@example.com"))) { error("No request expected") })
            val start = testScheduler.currentTime
            assertEquals(
                null,
                recommendRssDisplayMode(feed()) {
                    delay(10_000)
                    document()
                },
            )
            assertEquals(6_000, testScheduler.currentTime - start)
            assertFailsWith<CancellationException> { recommendRssDisplayMode(feed()) { throw CancellationException("New URL") } }
        }

    private fun feed(
        urls: List<String> = listOf("https://example.com/1"),
        html: String? = "<p>Feed article</p>",
    ): Feed =
        Feed.Rss20(
            "2.0",
            Feed.Rss20.Channel("Feed", "https://example.com", items = urls.map { Feed.Rss20.Item(link = it, description = html) }),
        )

    private fun document() = DocumentData("Title", "<p>Body</p>", "Body", null, null, null, null, null, null, null)
}

private fun TestScope.mockClient(handler: MockRequestHandler): HttpClient =
    HttpClient(
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            },
        ),
    )
