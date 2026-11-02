package dev.dimension.flare.ui.presenter.home.rss

import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CheckRssSourceConcurrencyTest {
    @Test
    fun concurrentDiscoveryCachesEveryFeedBeforePublishingSources() =
        runBlocking {
            Executors.newFixedThreadPool(8).asCoroutineDispatcher().use { dispatcher ->
                withTimeout(15_000) {
                    repeat(4) {
                        val urls = List(256) { "https://example.com/feed/$it" }
                        val fetched = CyclicBarrier(8)
                        val presenter =
                            CheckRssSourcePresenter(
                                "https://example.com/discover",
                                fetchFeed = { url ->
                                    if (url.endsWith("discover")) error("Not XML")
                                    fetched.await(5, TimeUnit.SECONDS)
                                    Feed.Rss20("2.0", Feed.Rss20.Channel(url, url))
                                },
                                fetchIcon = { null },
                                detectLinkSources = { urls },
                            )
                        val state = withContext(dispatcher) { presenter.flow.first { it !is UiState.Loading } }
                        val sources = assertIs<CheckRssSourcePresenter.State.RssState.RssSources>(assertIs<UiState.Success<*>>(state).data)
                        assertEquals(urls, sources.sources.map { it.url })
                        assertEquals(urls.toSet(), presenter.feeds.keys)
                        urls.forEach { url ->
                            assertEquals(url, assertIs<Feed.Rss20>(presenter.feeds[url]).channel.link)
                        }
                    }
                }
            }
        }
}
