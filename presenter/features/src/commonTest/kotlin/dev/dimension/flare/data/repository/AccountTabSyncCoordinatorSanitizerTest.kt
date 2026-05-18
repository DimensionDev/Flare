package dev.dimension.flare.data.repository

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
import kotlin.test.assertIs

class AccountTabSyncCoordinatorSanitizerTest {
    @Test
    fun sanitizeDuplicateTabKeysRemovesDuplicateMixedTabsAndSubTabs() {
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
        val mixedTab =
            MixedTimelineTabItem(
                subTimelineTabItem = listOf(homeTab, homeTab, listTab),
                metaData =
                    TabMetaData(
                        title = TitleType.Text("엑스"),
                        icon = IconType.Material(UiIcon.Rss),
                    ),
            )

        val sanitized =
            TabSettings(
                mainTabs = listOf(mixedTab, mixedTab),
            ).sanitizeDuplicateTabKeys()

        assertEquals(1, sanitized.mainTabs.size)
        val sanitizedMixedTab = assertIs<MixedTimelineTabItem>(sanitized.mainTabs.single())
        assertEquals(listOf(homeTab.key, listTab.key), sanitizedMixedTab.subTimelineTabItem.map { it.key })
    }
}
