package dev.dimension.flare.data.repository

import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MxgaRepositoryTest {
    @Test
    fun metadataAndWhitelistRejectUntrustedShapes() {
        val meta =
            MxgaDocumentParser.parseMeta(
                """{"version":"v-test","count":1000,"artifacts":{"lite":"/v1/artifacts/lite-v-test.json"}}""",
            )
        val whitelist =
            MxgaDocumentParser.parseWhitelist(
                """{"count":2,"list":[{"x_user_id":"42","handle":"Allowed"},{"x_user_id":null,"handle":"HandleOnly"}]}""",
            )

        assertEquals("/v1/artifacts/lite-v-test.json", meta.litePath)
        assertContentEquals(longArrayOf(42L), whitelist.ids)
        assertEquals(listOf("allowed", "handleonly"), whitelist.handles)
        assertFails {
            MxgaDocumentParser.parseMeta(
                """{"version":"v-test","count":1000,"artifacts":{"lite":"https://example.com/list.json"}}""",
            )
        }
    }

    @Test
    fun parserValidatesAndCompilesOfficialLiteShape() {
        val entries =
            (1..1_000).joinToString(separator = ",") { index ->
                "[\"$index\",\"u$index\",\"ppa\"]"
            }
        val snapshot =
            MxgaDocumentParser.parseLite(
                value =
                    """{"schema":2,"version":"v-test","count":1000,"rules":[["Blocked","t","sm"]],"entries":[$entries]}""",
                expectedVersion = "v-test",
                expectedCount = 1_000,
            )

        assertEquals(1_000, snapshot.blockedIds.size)
        assertTrue(snapshot.matches(MxgaSignals("42", "safe", "safe", "safe", "safe")))
        assertTrue(snapshot.matches(MxgaSignals("2000", "safe", "safe", "safe", "BLOCKED text")))
        val restored = snapshot.encodeProtobuf().decodeProtobuf<MxgaSnapshot>()
        assertContentEquals(snapshot.blockedIds, restored.blockedIds)
        assertEquals(snapshot.rules, restored.rules)
        assertFails {
            MxgaDocumentParser.parseLite(
                value =
                    """{"schema":2,"version":"v-test","count":999,"rules":[],"entries":[$entries]}""",
                expectedVersion = "v-test",
                expectedCount = 1_000,
            )
        }
    }

    @Test
    fun whitelistWinsBeforeAccountAndOrderedTextRules() {
        val snapshot =
            MxgaSnapshot(
                blockedIds = longArrayOf(42L),
                blockedHandles = listOf("fallback"),
                whitelistIds = longArrayOf(7L),
                whitelistHandles = listOf("allowed"),
                rules =
                    listOf(
                        MxgaRule("handle-hit", 'h'),
                        MxgaRule("name-hit", 'd'),
                        MxgaRule("bio-hit", 'b'),
                        MxgaRule("text-hit", 't'),
                        MxgaRule("any-hit", 'a'),
                    ),
            )

        assertFalse(snapshot.matches(MxgaSignals("42", "allowed", "", "", "")))
        assertFalse(snapshot.matches(MxgaSignals("7", "other", "text-hit", "", "")))
        assertTrue(snapshot.matches(MxgaSignals("42", "other", "", "", "")))
        assertTrue(snapshot.matches(MxgaSignals("", "fallback", "", "", "")))
        assertTrue(snapshot.matches(MxgaSignals("100", "HANDLE-HIT", "", "", "")))
        assertTrue(snapshot.matches(MxgaSignals("100", "", "NAME-HIT", "", "")))
        assertTrue(snapshot.matches(MxgaSignals("100", "", "", "BIO-HIT", "")))
        assertTrue(snapshot.matches(MxgaSignals("100", "", "", "", "TEXT-HIT")))
        assertTrue(snapshot.matches(MxgaSignals("100", "", "", "", "prefix ANY-HIT suffix")))
    }

    @Test
    fun timelineMatcherUsesDisplayedRepostOriginalTextOnly() {
        val user =
            createSampleUser().copy(
                key = MicroBlogKey("100", "x.com"),
                handle = UiHandle("safe", "x.com"),
                platformType = PlatformType.xQt,
            )
        val base = createSampleStatus(user)
        val snapshot =
            MxgaSnapshot(
                rules = listOf(MxgaRule("blocked-word", 't')),
            )
        val translatedOnly =
            base.copy(
                content =
                    UiTranslatableText(
                        original = "safe original".toUiPlainText(),
                        translation = "blocked-word".toUiPlainText(),
                    ),
            )
        val blocked =
            base.copy(
                statusKey = base.statusKey.copy(id = "blocked"),
                content = UiTranslatableText("blocked-word".toUiPlainText()),
            )

        assertFalse(translatedOnly.isMxgaMatch(snapshot))
        assertTrue(blocked.isMxgaMatch(snapshot))
        assertTrue(
            UiTimelineV2
                .TimelinePostItem(
                    post = translatedOnly,
                    presentation = UiTimelineV2.PostPresentation(repost = blocked),
                ).isMxgaMatch(snapshot),
        )
        assertFalse(
            UiTimelineV2
                .TimelinePostItem(
                    post = translatedOnly,
                    presentation = UiTimelineV2.PostPresentation(quotes = persistentListOf(blocked)),
                ).isMxgaMatch(snapshot),
        )
    }
}
