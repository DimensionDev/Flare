package dev.dimension.flare.ui.model

import androidx.paging.LoadState
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.combineLatestFlowLists
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CacheDataToUiTest : RobolectricTest() {
    @Test
    fun flowWaitsForCacheAfterSuccessfulFetchWithoutPublishingAnError() =
        runTest {
            val finishFetch = CompletableDeferred<Unit>()
            val publishCache = CompletableDeferred<Unit>()
            val data =
                Cacheable(
                    fetchSource = { finishFetch.await() },
                    cacheSource = {
                        flow {
                            publishCache.await()
                            emit("user")
                        }
                    },
                )
            val states = Channel<UiState<String>>(Channel.UNLIMITED)
            backgroundScope.launch { data.toUi().collect { states.send(it) } }

            assertIs<UiState.Loading<String>>(states.receive())
            finishFetch.complete(Unit)
            assertIs<UiState.Loading<String>>(states.receive())
            publishCache.complete(Unit)
            assertEquals(UiState.Success("user"), states.receive())
        }

    @Test
    fun composeWaitsForCacheAfterSuccessfulFetchWithoutPublishingAnError() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val publishCache = CompletableDeferred<Unit>()
            val data =
                Cacheable(
                    fetchSource = {},
                    cacheSource = {
                        flow {
                            publishCache.await()
                            emit("user")
                        }
                    },
                )
            val states = Channel<Pair<LoadState, UiState<String>>>(Channel.UNLIMITED)
            val job =
                launch {
                    moleculeFlow(RecompositionMode.Immediate) {
                        val state = data.collectAsState()
                        state.refreshState to state.toUi()
                    }.collect { states.send(it) }
                }
            try {
                val fetched = states.receiveAsFlow().first { it.first is LoadState.NotLoading }
                assertIs<UiState.Loading<String>>(fetched.second)

                publishCache.complete(Unit)
                val published = states.receiveAsFlow().first { it.second !is UiState.Loading }
                assertEquals(UiState.Success("user"), published.second)
            } finally {
                job.cancelAndJoin()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun fetchFailuresRemainErrorsWithoutCachedData() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val failure = IllegalStateException("User not found")
                val data =
                    Cacheable(
                        fetchSource = { throw failure },
                        cacheSource = { MutableStateFlow<String?>(null).filterNotNull() },
                    )

                val flowResult = data.toUi().first { it !is UiState.Loading }
                assertEquals(
                    failure.message,
                    assertIs<IllegalStateException>(assertIs<UiState.Error<String>>(flowResult).throwable).message,
                )
                val composeResult =
                    moleculeFlow(RecompositionMode.Immediate) {
                        data.collectAsState().toUi()
                    }.first { it !is UiState.Loading }
                assertEquals(
                    failure.message,
                    assertIs<IllegalStateException>(assertIs<UiState.Error<String>>(composeResult).throwable).message,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `toUi does not fetch until collected`() =
        runTest {
            var fetchCount = 0
            val cache = MutableStateFlow<String?>(null)
            val data =
                Cacheable(
                    fetchSource = {
                        fetchCount++
                        cache.value = "user"
                    },
                    cacheSource = {
                        cache.filterNotNull()
                    },
                )

            val uiFlow = data.toUi()

            assertEquals(0, fetchCount)

            val result = uiFlow.first { it is UiState.Success }

            assertEquals(1, fetchCount)
            assertEquals(UiState.Success("user"), result)
        }

    @Test
    fun `combining toUi flows triggers fetch for each inner cache once collected`() =
        runTest {
            val fetchCounts = mutableMapOf("a" to 0, "b" to 0)
            val cacheA = MutableStateFlow<String?>(null)
            val cacheB = MutableStateFlow<String?>(null)
            val flows =
                MutableStateFlow(
                    listOf(
                        Cacheable(
                            fetchSource = {
                                fetchCounts["a"] = fetchCounts.getValue("a") + 1
                                cacheA.value = "alice"
                            },
                            cacheSource = { cacheA.filterNotNull() },
                        ).toUi(),
                        Cacheable(
                            fetchSource = {
                                fetchCounts["b"] = fetchCounts.getValue("b") + 1
                                cacheB.value = "bob"
                            },
                            cacheSource = { cacheB.filterNotNull() },
                        ).toUi(),
                    ),
                )

            val combined = flows.combineLatestFlowLists().map { states -> states.filterIsInstance<UiState.Success<String>>() }

            assertEquals(0, fetchCounts.getValue("a"))
            assertEquals(0, fetchCounts.getValue("b"))

            val result = combined.first { it.size == 2 }

            assertEquals(1, fetchCounts.getValue("a"))
            assertEquals(1, fetchCounts.getValue("b"))
            assertEquals(listOf("alice", "bob"), result.map { it.data })
        }
}
