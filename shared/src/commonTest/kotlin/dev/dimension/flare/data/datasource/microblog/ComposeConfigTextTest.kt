package dev.dimension.flare.data.datasource.microblog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
                val result = rule.remainingLength("text").first()
                assertEquals(299, result.remainingLength)
                assertFalse(result.isValid)
                assertFalse(rule.remainingLength("text").first().isValid)
            }
        }

    @Test
    fun `an unlimited platform retains other platform constraints`() =
        runTest {
            val limited = ComposeConfig(text = ComposeConfig.Text(280))
            val unlimited = ComposeConfig()
            for (config in listOf(limited.merge(unlimited), unlimited.merge(limited))) {
                val text = assertNotNull(config.text)
                assertFalse(text.remainingLength("a".repeat(281)).first().isValid)
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

            assertEquals(-2, merged.remainingLength("あ".repeat(141)).first().remainingLength)
            assertEquals(200, merged.remainingLength("https://" + "a".repeat(292)).first().remainingLength)
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
                                check = { text, cw -> limit.map { ComposeConfig.Text.Check(it - text.length - cw.orEmpty().length) } },
                            ),
                    ),
                    ComposeConfig(),
                )
            for (accounts in listOf(configs, configs.reversed())) {
                limit.value = 500
                val merged = assertNotNull(accounts.reduce { current, other -> current.merge(other) }.text)
                val emissions = mutableListOf<ComposeConfig.Text.Check>()
                val collection = merged.remainingLength("a".repeat(100), "cw").onEach(emissions::add).launchIn(backgroundScope)
                runCurrent()
                limit.value = 150
                runCurrent()
                limit.value = 90
                runCurrent()

                assertEquals(listOf(80, 48, -12), emissions.map { it.remainingLength })
                assertFalse(emissions.last().isValid)
                assertEquals(emissions.last(), merged.remainingLength("a".repeat(100), "cw").first())
                collection.cancel()
            }
        }
}
