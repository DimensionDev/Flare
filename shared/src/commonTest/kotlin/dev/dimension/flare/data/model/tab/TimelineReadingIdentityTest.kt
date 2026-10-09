package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TimelineReadingIdentityTest {
    @Test
    fun renamingAndReorderingTabsPreserveTheirReadingIdentityIncludingSystemMixed() {
        val first = source("first.example")
        val second = source("second.example")
        val before = listOf(first, second).withSystemHomeMixedTimelineEnabled(true).readingSources()
        val after =
            listOf(second, first.withPresentationOverrides("Renamed", first.icon))
                .withSystemHomeMixedTimelineEnabled(true)
                .readingSources()
        assertEquals(before, after)
    }

    @Test
    fun changingSourceOrMergePolicyStartsAnotherSession() {
        assertNotEquals(source("first.example").readingSourceKey, source("second.example").readingSourceKey)
        val sources = listOf(source("first.example"), source("second.example"))
        assertNotEquals(
            sources.withSystemHomeMixedTimelineEnabled(true, TimelineMergePolicy.Time).readingSources()[SYSTEM_HOME_MIXED_TIMELINE_ID],
            sources.withSystemHomeMixedTimelineEnabled(true, TimelineMergePolicy.Staggered).readingSources()[SYSTEM_HOME_MIXED_TIMELINE_ID],
        )
    }

    private fun source(host: String): UiTimelineTabItem =
        CommonTimelineSpecs.guestHome
            .candidate(
                data = CommonTimelineSpecs.GuestHomeData(host),
                title = UiStrings.Home.asText(),
                icon = IconType.Material(UiIcon.Home),
            ).toUiTimelineTabItem()
}
