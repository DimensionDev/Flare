package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupConfigPresenterTest {
    @Test
    fun upsertGroupConfigReplacesDuplicateKeyInsteadOfAppending() {
        val accountKey = MicroBlogKey("1872639344760254464", "x.com")
        val accountType = AccountType.Specific(accountKey)
        val homeTab = HomeTimelineTabItem(accountType)
        val listTab =
            ListTimelineTabItem(
                account = accountType,
                listId = "1681353064253640704",
                metaData =
                    TabMetaData(
                        title = TitleType.Text("list"),
                        icon = IconType.Material(UiIcon.List),
                    ),
            )
        val existingGroup =
            MixedTimelineTabItem(
                subTimelineTabItem = listOf(homeTab, listTab),
                metaData =
                    TabMetaData(
                        title = TitleType.Text("엑스"),
                        icon = IconType.Material(UiIcon.Rss),
                    ),
            )

        val updated =
            TabSettings(mainTabs = listOf(existingGroup))
                .upsertGroupConfig(
                    initialItem = null,
                    name = "엑스",
                    icon = IconType.Material(UiIcon.Rss),
                    tabs = listOf(homeTab, homeTab, listTab),
                    defaultGroupName = "Group",
                )

        assertEquals(1, updated.mainTabs.size)
        assertEquals(existingGroup.key, updated.mainTabs.single().key)
        assertEquals(
            listOf(homeTab.key, listTab.key),
            (updated.mainTabs.single() as MixedTimelineTabItem).subTimelineTabItem.map { it.key },
        )
    }
}
