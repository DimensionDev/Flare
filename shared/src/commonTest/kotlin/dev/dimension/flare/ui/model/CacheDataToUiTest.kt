package dev.dimension.flare.ui.model

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.combineLatestFlowLists
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheDataToUiTest {
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
