package dev.dimension.flare.data.datasource.microblog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ComposeConfigTextTest {
    @Test
    fun `merged text retains invalidity with a positive remaining counter`() =
        runTest {
            val invalid = ComposeConfig.Text.withValidation(300) { _, _ -> ComposeConfig.Text.Check(299, isValid = false) }
            val valid = ComposeConfig.Text(5000)
            for (rule in listOf(invalid.merge(valid), valid.merge(invalid))) {
                val result = rule.validate("text")
                assertEquals(299, result.remainingLength)
                assertFalse(result.isValid)
                assertFalse(rule.check("text").first().isValid)
            }
        }

    @Test
    fun `an unlimited platform retains other platform constraints`() =
        runTest {
            val limited = ComposeConfig(text = ComposeConfig.Text(280))
            val unlimited = ComposeConfig()
            for (config in listOf(limited.merge(unlimited), unlimited.merge(limited))) {
                val text = assertNotNull(config.text)
                assertFalse(text.validate("a".repeat(281)).isValid)
            }
        }

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

    @Test
    fun `merged text uses the strictest length rule`() =
        runTest {
            val merged =
                ComposeConfig.Text
                    .withLength(280) { if (it.startsWith("https://")) 23 else it.length * 2 }
                    .merge(ComposeConfig.Text(500))

            assertEquals(-2, merged.remainingLength("あ".repeat(141)).first())
            assertEquals(200, merged.remainingLength("https://" + "a".repeat(292)).first())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cross posting follows the minimum remaining count as platform limits change`() =
        runTest {
            val limit = MutableStateFlow(500)
            val configs =
                listOf(
                    ComposeConfig(text = ComposeConfig.Text.withLength(280) { it.length * 2 }),
                    ComposeConfig(text = ComposeConfig.Text(300)),
                    ComposeConfig(
                        text =
                            ComposeConfig.Text.withValidation(
                                maxLength = limit,
                                check = { text, cw, _ -> limit.map { ComposeConfig.Text.Check(it - text.length - cw.orEmpty().length) } },
                            ),
                    ),
                    ComposeConfig(),
                )
            for (accounts in listOf(configs, configs.reversed())) {
                limit.value = 500
                val merged = assertNotNull(accounts.reduce { current, other -> current.merge(other) }.text)
                val emissions = mutableListOf<ComposeConfig.Text.Check>()
                val collection = merged.check("a".repeat(100), "cw").onEach(emissions::add).launchIn(backgroundScope)
                runCurrent()
                limit.value = 150
                runCurrent()
                limit.value = 90
                runCurrent()

                assertEquals(listOf(80, 48, -12), emissions.map { it.remainingLength })
                assertFalse(emissions.last().isValid)
                assertEquals(emissions.last(), merged.validate("a".repeat(100), "cw"))
                collection.cancel()
            }
        }

    @Test
    fun `merged validation refreshes every platform before calculating the minimum`() =
        runTest {
            val refreshed = mutableListOf<Int>()
            val merged =
                listOf(500 to 100, 1000 to 50)
                    .map { (cached, resolved) ->
                        ComposeConfig.Text.withValidation(
                            maxLength = flowOf(cached),
                            check = { text, _, refresh ->
                                flow {
                                    if (refresh) refreshed.add(resolved)
                                    emit(ComposeConfig.Text.Check((if (refresh) resolved else cached) - text.length))
                                }
                            },
                        )
                    }.reduce { current, other -> current.merge(other) }

            assertEquals(496, merged.check("text").first().remainingLength)
            assertEquals(emptyList(), refreshed)
            assertEquals(46, merged.validate("text").remainingLength)
            assertEquals(listOf(50, 100), refreshed.sorted())
        }
}
