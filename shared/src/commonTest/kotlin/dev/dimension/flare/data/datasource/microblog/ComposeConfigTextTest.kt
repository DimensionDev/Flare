package dev.dimension.flare.data.datasource.microblog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeConfigTextTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `merged text limit follows the strictest flow`() =
        runTest {
            val first = MutableStateFlow(500)
            val second = MutableStateFlow(1_000)
            val merged = ComposeConfig.Text(first).merge(ComposeConfig.Text(second))
            val emissions = mutableListOf<Int>()
            val collection =
                merged.maxLength
                    .onEach(emissions::add)
                    .launchIn(backgroundScope)

            runCurrent()
            second.value = 400
            runCurrent()
            first.value = 300
            runCurrent()

            assertEquals(listOf(500, 400, 300), emissions)
            collection.cancel()
        }
}
