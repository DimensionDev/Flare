package dev.dimension.flare.ui.presenter.home.rss

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.di.startKoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RssSourcesPresenterTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun emptyStateWaitsForInitialReadAndReturnsAfterLastDeletion() =
        runTest {
            val repository = startKoin().koin.get<FakeSubscriptionRepository>()
            val presenter = RssSourcesPresenter()
            val states = mutableListOf<RssSourcesPresenter.State>()
            val job =
                launch {
                    moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect(states::add)
                }

            try {
                advanceUntilIdle()
                assertTrue(states.first().loading)
                assertFalse(states.last().loading)
                assertTrue(states.last().sources.isEmpty())

                val source =
                    repository.upsert(
                        SubscriptionSourceInput(
                            url = "https://example.com/feed.xml",
                            title = "Example",
                            icon = null,
                            lastUpdateMillis = 0,
                        ),
                    )
                advanceUntilIdle()
                assertFalse(states.last().loading)
                assertEquals(listOf(source), states.last().sources)

                repository.delete(source.id)
                advanceUntilIdle()
                assertFalse(states.last().loading)
                assertTrue(states.last().sources.isEmpty())
            } finally {
                job.cancel()
            }
        }
}
