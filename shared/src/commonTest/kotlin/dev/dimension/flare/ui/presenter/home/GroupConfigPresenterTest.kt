package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.tab.GroupTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.toTimelineSlotOrNull
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testTimelineSpecs
import dev.dimension.flare.ui.model.UiIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupConfigPresenterTest {
    @Test
    fun upsertGroupConfigReplacesDuplicateKeyInsteadOfAppending() {
        val timelineResolver = TimelineResolver(testTimelineSpecs())
        val accountKey = MicroBlogKey("1872639344760254464", "x.com")
        val accountType = AccountType.Specific(accountKey)
        val homeTab = timelineResolver.toTabItem(HomeTimelineTabItem(accountType).toTimelineSlotOrNull()!!)
        val listTab =
            timelineResolver.toTabItem(
                ListTimelineTabItem(
                    account = accountType,
                    listId = "1681353064253640704",
                    metaData =
                        TabMetaData(
                            title = TitleType.Text("list"),
                            icon = IconType.Material(UiIcon.List),
                        ),
                ).toTimelineSlotOrNull()!!,
            )
        val existingGroup =
            TabSettingsV2()
                .upsertGroupConfig(
                    initialItem = null,
                    name = "엑스",
                    icon = IconType.Material(UiIcon.Rss),
                    appearancePatch = null,
                    enabled = true,
                    tabs = listOf(homeTab, listTab),
                    defaultGroupName = "Group",
                    timelineResolver = timelineResolver,
                ).homeSlots
                .single()
                .let(timelineResolver::toTabItem)
        val existingGroupItem = assertIs<GroupTimelineTabItemV2>(existingGroup)

        val updated =
            TabSettingsV2(homeSlots = listOf(timelineResolver.toSlot(existingGroupItem)))
                .upsertGroupConfig(
                    initialItem = null,
                    name = "엑스",
                    icon = IconType.Material(UiIcon.Rss),
                    appearancePatch = null,
                    enabled = true,
                    tabs = listOf(homeTab, homeTab, listTab),
                    mergePolicy = TimelineMergePolicy.Staggered,
                    defaultGroupName = "Group",
                    timelineResolver = timelineResolver,
                )

        assertEquals(1, updated.homeSlots.size)
        assertEquals(existingGroupItem.id, updated.homeSlots.single().id)
        val updatedGroup = assertIs<GroupTimelineTabItemV2>(timelineResolver.toTabItem(updated.homeSlots.single()))
        assertEquals(
            listOf(homeTab.id, listTab.id),
            updatedGroup.children.map { it.id },
        )
        assertEquals(TimelineMergePolicy.Staggered, updatedGroup.mergePolicy)
    }
}
