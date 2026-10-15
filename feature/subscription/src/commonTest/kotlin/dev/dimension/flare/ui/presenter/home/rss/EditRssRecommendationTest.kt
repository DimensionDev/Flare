package dev.dimension.flare.ui.presenter.home.rss

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.network.rss.ArticleExtractionException
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.di.startKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditRssRecommendationTest {
    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var persistenceScope: TestScope

    @BeforeTest
    fun setUp() {
        val koin = startKoin().koin
        repository = koin.get()
        persistenceScope = koin.get<CoroutineScope>() as TestScope
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun recommendationDoesNotResetFeedStateOrOverrideManualChoice() =
        runTest {
            var fetches = 0
            val observed =
                observe(
                    presenter(fetch = {
                        fetches++
                        feed(it)
                    }) {
                        delay(1_000)
                        throw ArticleExtractionException()
                    },
                )
            observed.state.checkUrl("http://example.com/feed")
            advanceTimeBy(667)
            runCurrent()
            val checked = observed.state.checkState
            assertTrue(observed.state.canSave)
            assertTrue(
                observed.state.readingModes
                    .single()
                    .checking,
            )
            observed.state.setDisplayMode(RssDisplayMode.OPEN_IN_BROWSER)
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(checked, observed.state.checkState)
            assertEquals(
                RssDisplayMode.DESCRIPTION_ONLY,
                observed.state.readingModes
                    .single()
                    .recommended,
            )
            assertEquals(RssDisplayMode.OPEN_IN_BROWSER, observed.state.displayMode)
            assertFalse(
                observed.state.readingModes
                    .single()
                    .automatic,
            )
            assertEquals(1, fetches)
        }

    @Test
    fun lateResultForPreviousUrlCannotChangeNewRecommendation() =
        runTest {
            val observed =
                observe(
                    presenter {
                        if (it.contains("first")) {
                            withContext(NonCancellable) { delay(2_000) }
                            throw ArticleExtractionException()
                        }
                        document()
                    },
                )
            observed.state.checkUrl("https://first.example/feed")
            advanceTimeBy(667)
            runCurrent()
            observed.state.checkUrl("https://second.example/feed")
            advanceTimeBy(667)
            runCurrent()
            assertEquals(RssDisplayMode.FULL_CONTENT, observed.state.displayMode)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(
                "https://second.example/feed",
                observed.state.readingModes
                    .single()
                    .url,
            )
            assertEquals(RssDisplayMode.FULL_CONTENT, observed.state.displayMode)
        }

    @Test
    fun existingAndExplicitInitialChoicesAreNotProbed() =
        runTest {
            val saved =
                repository.upsert(
                    SubscriptionSourceInput(
                        url = "https://example.com/feed",
                        title = "Feed",
                        icon = null,
                        displayMode = RssDisplayMode.DESCRIPTION_ONLY,
                    ),
                )
            val existing = observe(presenter(id = saved.id) { error("Existing source must not be probed") })
            existing.state.checkUrl(saved.url)
            advanceTimeBy(667)
            runCurrent()
            assertEquals(RssDisplayMode.DESCRIPTION_ONLY, existing.state.displayMode)
            assertFalse(
                existing.state.readingModes
                    .single()
                    .automatic,
            )

            val explicit = observe(presenter(initial = RssDisplayMode.OPEN_IN_BROWSER) { error("Explicit choice must not be probed") })
            explicit.state.checkUrl(saved.url)
            advanceTimeBy(667)
            runCurrent()
            assertEquals(RssDisplayMode.OPEN_IN_BROWSER, explicit.state.displayMode)
        }

    @Test
    fun savingBeforeRecommendationCompletesUsesCurrentChoice() =
        runTest {
            val observed =
                observe(
                    presenter {
                        delay(2_000)
                        throw ArticleExtractionException()
                    },
                )
            observed.state.checkUrl("https://example.com/feed")
            advanceTimeBy(667)
            runCurrent()
            observed.state.saveRssFeed("Feed", observed.state.displayMode)
            persistenceScope.testScheduler.advanceUntilIdle()
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(RssDisplayMode.FULL_CONTENT, repository.currentSources.single().displayMode)
            assertEquals(RssDisplayMode.DESCRIPTION_ONLY, observed.state.displayMode)
        }

    @Test
    fun selectedDiscoveredSourcesKeepSeparateChoicesAndSaveSnapshots() =
        runTest {
            val observed =
                observe(
                    presenter(
                        fetch = {
                            if (it.endsWith(
                                    "discover",
                                )
                            ) {
                                error("Not XML")
                            } else {
                                feed(it, html = if (it.contains("second")) null else "Body")
                            }
                        },
                    ) {
                        delay(1_000)
                        throw ArticleExtractionException()
                    },
                )
            observed.state.checkUrl("https://example.com/discover")
            advanceTimeBy(667)
            runCurrent()
            assertFalse(observed.state.readingModes.any { it.checking })
            observed.state.selectSource("https://first.example/feed", true)
            observed.state.selectSource("https://second.example/feed", true)
            runCurrent()
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(
                listOf(RssDisplayMode.DESCRIPTION_ONLY, RssDisplayMode.OPEN_IN_BROWSER),
                observed.state.readingModes.map { it.displayMode },
            )
            observed.state.setSourceDisplayMode("https://second.example/feed", RssDisplayMode.FULL_CONTENT)
            runCurrent()
            observed.state.saveSelectedSources()
            observed.state.setSourceDisplayMode("https://first.example/feed", RssDisplayMode.OPEN_IN_BROWSER)
            persistenceScope.testScheduler.advanceUntilIdle()
            assertEquals(
                listOf(RssDisplayMode.DESCRIPTION_ONLY, RssDisplayMode.FULL_CONTENT),
                repository.currentSources.map { it.displayMode },
            )
        }

    @Test
    fun commaContainingSourcesCanBeSelectedRecommendedAndSaved() =
        runTest {
            val urls = listOf("https://example.com/feed?tags=a,b", "https://example.com/feed?tags=c,d")
            val visited = mutableListOf<String>()
            val observed =
                observe(
                    presenter(
                        fetch = { if (it.endsWith("discover")) error("Not XML") else feed(it) },
                        sources = urls,
                    ) {
                        visited += it
                        throw ArticleExtractionException()
                    },
                )
            observed.state.checkUrl("https://example.com/discover")
            advanceTimeBy(667)
            runCurrent()
            urls.forEach { observed.state.selectSource(it, true) }
            runCurrent()
            assertEquals(urls.map { "$it/article" }, visited)
            assertTrue(observed.state.readingModes.all { it.displayMode == RssDisplayMode.DESCRIPTION_ONLY && !it.checking })

            observed.state.selectSource(urls.last(), false)
            observed.state.saveSelectedSources()
            observed.state.clearSelectedSources()
            persistenceScope.testScheduler.advanceUntilIdle()
            assertEquals(listOf(urls.first()), repository.currentSources.map { it.url })
            assertEquals(RssDisplayMode.DESCRIPTION_ONLY, repository.currentSources.single().displayMode)

            observed.state.saveSelectedSources()
            persistenceScope.testScheduler.advanceUntilIdle()
            assertEquals(1, repository.currentSources.size)
        }

    @Test
    fun rssHubRecommendationsUseResolvedFeedAndCancelWhenServerChanges() =
        runTest {
            val observed = observe(presenter { if (it.contains("first")) throw ArticleExtractionException() else document() })
            observed.state.checkUrl("rsshub://test/path")
            advanceTimeBy(667)
            runCurrent()
            observed.state.checkRssHubServer("https://first.example")
            runCurrent()
            advanceTimeBy(667)
            runCurrent()
            assertEquals(RssDisplayMode.DESCRIPTION_ONLY, observed.state.displayMode)
            observed.state.checkRssHubServer("https://second.example")
            runCurrent()
            advanceTimeBy(667)
            runCurrent()
            assertEquals(RssDisplayMode.FULL_CONTENT, observed.state.displayMode)
            assertTrue(
                observed.state.readingModes
                    .single()
                    .url
                    .contains("second.example"),
            )
        }

    private fun presenter(
        id: Int? = null,
        initial: RssDisplayMode? = null,
        fetch: suspend (String) -> Feed = { feed(it) },
        sources: List<String> = listOf("https://first.example/feed", "https://second.example/feed"),
        extract: suspend (String) -> DocumentData,
    ) = EditRssSourcePresenter(id, initial, { url ->
        CheckRssSourcePresenter(url, fetch, { null }, { sources })
    }, extract)

    private fun feed(
        url: String,
        html: String? = "Feed article",
    ) = Feed.Rss20("2.0", Feed.Rss20.Channel("Feed", url, items = listOf(Feed.Rss20.Item(link = "$url/article", description = html))))

    private fun document() = DocumentData("Title", "<p>Article</p>", "Article", null, null, null, null, null, null, null)

    private fun TestScope.observe(presenter: EditRssSourcePresenter): Observed {
        val observed = Observed()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { observed.state = it }
        }
        runCurrent()
        return observed
    }

    private class Observed {
        lateinit var state: EditRssSourcePresenter.State
    }
}
