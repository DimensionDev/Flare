package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.datasource.microblog.timeline.CommonTimelineSpecs
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.toTimelineTabDescriptor
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.GroupTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import dev.dimension.flare.ui.model.UiIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupConfigPresenterTest {
    private val timelinePersistenceMapper =
        TimelinePersistenceMapper(
            TimelineCatalog(defaultSocialPlatformRegistry.specs.flatMap { it.timelineSpecs }),
        )

    @Test
    fun upsertGroupConfigReplacesDuplicateKeyInsteadOfAppending() {
        val accountKey = MicroBlogKey("1872639344760254464", "x.com")
        val homeTab =
            timelinePersistenceMapper.toTabItem(
                CommonTimelineSpecs.home.toTimelineTabDescriptor(TimelineSpec.AccountBasedData(accountKey)),
            )
        val listTab =
            timelinePersistenceMapper.toTabItem(
                CommonTimelineSpecs
                    .list
                    .toTimelineTabDescriptor(TimelineSpec.AccountResourceData(accountKey, "1681353064253640704")),
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
                    timelinePersistenceMapper = timelinePersistenceMapper,
                ).homeSlots
                .single()
                .let(timelinePersistenceMapper::toTabItem)
        val existingGroupItem = assertIs<GroupTimelineTabItemV2>(existingGroup)

        val updated =
            TabSettingsV2(homeSlots = listOf(timelinePersistenceMapper.toSlot(existingGroupItem)))
                .upsertGroupConfig(
                    initialItem = null,
                    name = "엑스",
                    icon = IconType.Material(UiIcon.Rss),
                    appearancePatch = null,
                    enabled = true,
                    tabs = listOf(homeTab, homeTab, listTab),
                    mergePolicy = TimelineMergePolicy.Staggered,
                    defaultGroupName = "Group",
                    timelinePersistenceMapper = timelinePersistenceMapper,
                )

        assertEquals(1, updated.homeSlots.size)
        assertEquals(existingGroupItem.id, updated.homeSlots.single().id)
        val updatedGroup = assertIs<GroupTimelineTabItemV2>(timelinePersistenceMapper.toTabItem(updated.homeSlots.single()))
        assertEquals(
            listOf(homeTab.id, listTab.id),
            updatedGroup.children.map { it.id },
        )
        assertEquals(TimelineMergePolicy.Staggered, updatedGroup.mergePolicy)
    }
}
