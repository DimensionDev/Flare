package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TimelineSlotNormalizationTest {
    @Test
    fun normalizeSystemHomeMixedTimelineAddsSystemMixedWhenSecondDefaultTabIsAdded() {
        val firstSlot = sourceSlot("common.home:1872639344760254464@x.com")
        val secondSlot = sourceSlot("common.home:1711111111111111111@mastodon.social")

        val normalized =
            listOf(
                firstSlot,
                secondSlot,
            ).normalizeSystemHomeMixedTimeline(enabled = true)

        assertEquals(
            listOf(
                SYSTEM_HOME_MIXED_TIMELINE_ID,
                firstSlot.id,
                secondSlot.id,
            ),
            normalized.map { it.id },
        )
    }

    @Test
    fun normalizeSystemHomeMixedTimelineDoesNotReAddSystemMixedAfterItWasRemoved() {
        val firstSlot = sourceSlot("common.home:1872639344760254464@x.com")
        val secondSlot = sourceSlot("common.home:1711111111111111111@mastodon.social")

        val normalized =
            listOf(
                firstSlot,
                secondSlot,
            ).normalizeSystemHomeMixedTimeline(enabled = true)
                .filterNot { it.id == SYSTEM_HOME_MIXED_TIMELINE_ID }
                .normalizeSystemHomeMixedTimeline(enabled = false)

        assertEquals(
            listOf(
                firstSlot.id,
                secondSlot.id,
            ),
            normalized.map { it.id },
        )
    }

    @Test
    fun normalizeSystemHomeMixedTimelinePreservesDisabledChildrenInManualGroup() {
        val enabledChild = sourceSlot("common.home:1872639344760254464@x.com")
        val disabledChild =
            sourceSlot("common.home:1711111111111111111@mastodon.social")
                .copy(presentation = TimelinePresentation(enabled = false))
        val manualGroup =
            TimelineSlot(
                id = "manual_group",
                content =
                    TimelineSlotContent.Group(
                        children = listOf(enabledChild, disabledChild),
                        source = GroupSource.Manual,
                    ),
            )

        val normalized =
            listOf(
                enabledChild,
                sourceSlot("common.home:1999999999999999999@mastodon.cloud"),
                manualGroup,
            ).normalizeSystemHomeMixedTimeline(enabled = true)

        val preservedGroup = assertNotNull(normalized.firstOrNull { it.id == manualGroup.id })
        val groupContent = assertIs<TimelineSlotContent.Group>(preservedGroup.content)
        assertEquals(listOf(enabledChild.id, disabledChild.id), groupContent.children.map { it.id })
        assertFalse(groupContent.children[1].presentation.enabled)
    }

    private fun sourceSlot(id: String): TimelineSlot =
        TimelineSourceRef(
            id = id,
            specId = "common.home",
            title = UiStrings.Home.asText(),
            icon = IconType.Material(UiIcon.Home),
            data = id,
        ).toSlot()
}
