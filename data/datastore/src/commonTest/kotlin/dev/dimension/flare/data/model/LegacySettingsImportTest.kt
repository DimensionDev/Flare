package dev.dimension.flare.data.model

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.LegacyAppearanceSettings
import dev.dimension.flare.data.model.appearance.LegacyTheme
import dev.dimension.flare.data.model.tab.SYSTEM_HOME_MIXED_TIMELINE_ID
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacySettingsImportTest {
    private val accountKey = MicroBlogKey(id = "alice", host = "example.com")
    private val account = AccountType.Specific(accountKey)

    @Test
    fun legacyAppearanceSettingsExportDecodesToAppearanceBag() {
        val slot = groupSlot("manual")
        val export =
            LegacyAppearanceSettingsExport(
                appearanceSettings =
                    LegacyAppearanceSettings(
                        theme = LegacyTheme.DARK,
                        showNumbers = false,
                    ),
                appSettings = AppSettings(version = "legacy-appearance"),
                tabSettingsV2 = TabSettingsV2(homeSlots = listOf(slot)),
            )

        val decoded = decodeLegacyAppearanceSettingsExport(export.encodeJson(LegacyAppearanceSettingsExport.serializer()))

        assertEquals("legacy-appearance", decoded.appSettings.version)
        assertEquals(listOf(slot.id), decoded.tabSettingsV2.homeSlots.map { it.id })
        assertTrue(decoded.appearanceBag.entries.containsKey("app.theme"))
        assertTrue(decoded.appearanceBag.entries.containsKey("timeline.show_numbers"))
    }

    @Test
    fun legacySettingsExportDecodesTabsToV2() {
        val export =
            LegacySettingsExport(
                appearanceBag = AppearanceBag(),
                appSettings = AppSettings(version = "v1"),
                tabSettings =
                    TabSettings(
                        enableMixedTimeline = true,
                        mainTabs =
                            listOf(
                                HomeTimelineTabItem(account),
                                Mastodon.LocalTimelineTabItem(
                                    account = account,
                                    metaData = localMetaData(),
                                ),
                                RssTimelineTabItem(
                                    feedUrl = "https://example.com/rss.xml",
                                    metaData = rssMetaData(),
                                ),
                            ),
                    ),
            )

        val decoded = decodeLegacySettingsExport(export.encodeJson(LegacySettingsExport.serializer()))

        assertEquals("v1", decoded.appSettings.version)
        assertEquals(
            listOf(
                SYSTEM_HOME_MIXED_TIMELINE_ID,
                "common.home:$accountKey",
                "mastodon.local:$accountKey",
                "rss.feed:https://example.com/rss.xml",
            ),
            decoded.tabSettingsV2.homeSlots.map { it.id },
        )
    }

    @Test
    fun legacyAppearanceSettingsAndTabsExportDecodesAppearanceAndTabs() {
        val export =
            LegacyAppearanceSettingsAndTabsExport(
                appearanceSettings = LegacyAppearanceSettings(theme = LegacyTheme.DARK),
                appSettings = AppSettings(version = "v1-with-appearance"),
                tabSettings =
                    TabSettings(
                        enableMixedTimeline = false,
                        mainTabs =
                            listOf(
                                Mastodon.LocalTimelineTabItem(
                                    account = account,
                                    metaData = localMetaData(),
                                ),
                            ),
                    ),
            )

        val decoded =
            decodeLegacyAppearanceSettingsAndTabsExport(
                export.encodeJson(LegacyAppearanceSettingsAndTabsExport.serializer()),
            )

        assertEquals("v1-with-appearance", decoded.appSettings.version)
        assertEquals(listOf("mastodon.local:$accountKey"), decoded.tabSettingsV2.homeSlots.map { it.id })
        assertTrue(decoded.appearanceBag.entries.containsKey("app.theme"))
    }

    private fun groupSlot(id: String) =
        TimelineSlot(
            id = id,
            content = TimelineSlotContent.Group(),
        )

    private fun localMetaData() =
        TabMetaData(
            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonLocal),
            icon = IconType.Material(UiIcon.Local),
        )

    private fun rssMetaData() =
        TabMetaData(
            title = TitleType.Text("RSS"),
            icon = IconType.Material(UiIcon.Rss),
        )
}
