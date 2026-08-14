package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.platform.XQTCredential
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class XQTTimelineConfigurationTest {
    private val dataSource =
        XQTDataSource(
            accountKey = MicroBlogKey(id = "account", host = "x.com"),
            sourceCredentialFlow = flowOf(XQTCredential(chocolate = "")),
        )

    @Test
    fun newTimelinesAreAvailableInSidebar() {
        val shortcuts = dataSource.shortcuts

        assertContains(shortcuts.map { it.title }, UiStrings.ForYou)
        assertContains(shortcuts.map { it.title }, UiStrings.Popular)
        assertContains(shortcuts.map { it.title }, UiStrings.Liked)

        val timelineSpecIds =
            shortcuts.mapNotNull {
                (it.target as? ShortcutSpec.Target.Timeline)
                    ?.candidate
                    ?.target
                    ?.spec
                    ?.id
            }
        assertContains(timelineSpecIds, XqtPlatformSpec.featuredTimelineSpec.id)
        assertContains(timelineSpecIds, XqtPlatformSpec.popularTimelineSpec.id)
        assertContains(timelineSpecIds, XqtPlatformSpec.likedTimelineSpec.id)
    }

    @Test
    fun primaryTimelineCandidatesUseXFavIcon() {
        val expected = IconType.FavIcon("x.com")

        assertEquals(List(3) { expected }, dataSource.defaultTabs.map { it.icon })
        assertEquals(List(3) { expected }, dataSource.builtInTimelineTabs.take(3).map { it.icon })
    }

    @Test
    fun tagTimelineSpecIsRegistered() {
        assertContains(
            XqtPlatformSpec.timelineSpecs.map { it.id },
            XqtPlatformSpec.tagTimelineSpec.id,
        )
    }
}
