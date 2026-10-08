package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.graphemeCount
import dev.dimension.flare.common.normalizeNfc
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeTextRulesTest {
    @Test
    fun unicodeUnitsAndBlueskyLimits() =
        runTest {
            for (text in listOf("😀", "👍🏽", "🇨🇳", "👨‍👩‍👧‍👦", "e\u0301")) {
                assertEquals(1, text.graphemeCount(), text)
                assertEquals(299, ComposeTextRules.bluesky().validate(text).remainingLength, text)
            }
            assertEquals("é", "e\u0301".normalizeNfc())
            val rule = ComposeTextRules.bluesky()
            assertTrue(rule.validate("a".repeat(299) + "👍🏽").isValid)
            assertFalse(rule.validate("a".repeat(300) + "👍🏽").isValid)
            assertTrue(rule.validate("a" + "\u0301".repeat(1499)).isValid)
            val byteOverflow = "a" + "\u0301".repeat(1500)
            assertEquals(299, rule.validate(byteOverflow).remainingLength)
            assertFalse(rule.validate(byteOverflow).isValid)
            assertFalse(rule.merge(ComposeConfig.Text(5000)).validate(byteOverflow).isValid)
            assertFalse(
                ComposeConfig
                    .Text(5000)
                    .merge(rule)
                    .validate(byteOverflow)
                    .isValid,
            )
        }

    @Test
    fun misskeyCountsCodePointsAndChecksCWIndependently() {
        assertTrue(ComposeTextRules.misskeyCheck("a".repeat(2998) + "👍🏽", null, 3000).isValid)
        assertFalse(ComposeTextRules.misskeyCheck("a".repeat(2999) + "👍🏽", null, 3000).isValid)
        assertTrue(ComposeTextRules.misskeyCheck("a".repeat(3000), "a".repeat(100), 3000).isValid)
        assertFalse(ComposeTextRules.misskeyCheck("x", "a".repeat(101), 3000).isValid)
        assertEquals(2993, ComposeTextRules.misskeyCheck("👨‍👩‍👧‍👦", null, 3000).remainingLength)
    }

    @Test
    fun publishingChecksFinalInputBeforeInvokingPlatform() =
        runTest {
            val source = TestComposeSource(ComposeTextRules.bluesky())
            assertFailsWith<IllegalArgumentException> { source.compose(ComposeData("a".repeat(301))) {} }
            assertFailsWith<IllegalArgumentException> { source.compose(ComposeData("a" + "\u0301".repeat(1500))) {} }
            assertEquals(0, source.published)
            source.compose(ComposeData("a".repeat(299) + "👍🏽")) {}
            assertEquals(1, source.published)
            val misskey =
                TestComposeSource(
                    ComposeConfig.Text.withValidation(3000) { content, cw ->
                        ComposeTextRules.misskeyCheck(content, cw, 3000)
                    },
                )
            assertFailsWith<IllegalArgumentException> { misskey.compose(ComposeData("x", spoilerText = "a".repeat(101))) {} }
            assertEquals(0, misskey.published)
            misskey.compose(ComposeData("x", spoilerText = " ".repeat(101))) {}
            assertEquals(1, misskey.published)
        }

    @Test
    fun publishingUsesResolvedLimitRatherThanFirstFallbackEmission() =
        runTest {
            val limits =
                flow {
                    emit(500)
                    emit(4000)
                }
            val rule =
                ComposeConfig.Text.withValidation(
                    maxLength = limits,
                    check = { content, _ -> flowOf(ComposeConfig.Text.Check(500 - content.length)) },
                    validate = { content, _ -> ComposeConfig.Text.Check(4000 - content.length) },
                )
            assertFalse(rule.check("a".repeat(3000)).first().isValid)
            val source = TestComposeSource(rule)
            source.compose(ComposeData("a".repeat(3000))) {}
            assertEquals(1, source.published)
        }
}

private class TestComposeSource(
    private val rule: ComposeConfig.Text,
) : ComposeDataSource {
    override val accountKey = MicroBlogKey("test", "example.com")
    var published = 0

    override suspend fun publish(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        published++
    }

    override fun composeConfig(type: ComposeType) = ComposeConfig(text = rule)

    override fun homeTimeline(): Nothing = error("unused")

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): Nothing = error("unused")

    override fun context(statusKey: MicroBlogKey): Nothing = error("unused")

    override fun searchStatus(query: String): Nothing = error("unused")

    override fun searchUser(query: String): Nothing = error("unused")

    override fun discoverUsers(): Nothing = error("unused")

    override fun discoverStatuses(): Nothing = error("unused")

    override fun discoverHashtags(): Nothing = error("unused")

    override fun following(userKey: MicroBlogKey): Nothing = error("unused")

    override fun fans(userKey: MicroBlogKey): Nothing = error("unused")

    override suspend fun profileTabs(userKey: MicroBlogKey): Nothing = error("unused")
}
