package dev.dimension.flare.data.model.tab

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.AllRssTimelineTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.Mastodon
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.SubscriptionTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testTimelineSpecs
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TabSettingsMigrationTest {
    private val accountKey = MicroBlogKey(id = "alice", host = "example.com")
    private val account = AccountType.Specific(accountKey)

    @Test
    fun accountTimelineMigratesToExpectedSource() {
        val slot =
            Mastodon
                .LocalTimelineTabItem(
                    account = account,
                    metaData =
                        TabMetaData(
                            title = TitleType.Text("Local custom"),
                            icon = IconType.FavIcon("example.com"),
                        ),
                ).toTimelineSlotOrNull()

        assertNotNull(slot)
        val source = assertIs<TimelineSlotContent.Source>(slot.content).source
        assertEquals(TimelineSpecIds.MASTODON_LOCAL, source.specId)
        assertEquals("${TimelineSpecIds.MASTODON_LOCAL}:$accountKey", slot.id)
        assertEquals(IconType.FavIcon("example.com"), slot.icon)
        assertEquals("Local custom", assertIs<dev.dimension.flare.ui.model.UiText.Raw>(slot.title).string)
    }

    @Test
    fun resourceTimelineMigratesWithResourceId() {
        val slot =
            ListTimelineTabItem(
                account = account,
                listId = "list-1",
                metaData =
                    TabMetaData(
                        title = TitleType.Text("Friends"),
                        icon = IconType.Material(UiIcon.List),
                    ),
            ).toTimelineSlotOrNull()

        assertNotNull(slot)
        val source = assertIs<TimelineSlotContent.Source>(slot.content).source
        assertEquals(TimelineSpecIds.COMMON_LIST, source.specId)
        assertEquals("${TimelineSpecIds.COMMON_LIST}:$accountKey:list-1", slot.id)
    }

    @Test
    fun mixedTimelineMigratesChildrenAndUsesTimePerPagePolicy() {
        val slot =
            MixedTimelineTabItem(
                subTimelineTabItem =
                    listOf(
                        Bluesky.BookmarkTimelineTabItem(account),
                        Mastodon.PublicTimelineTabItem(
                            account = account,
                            metaData =
                                TabMetaData(
                                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonPublic),
                                    icon = IconType.Material(UiIcon.World),
                                ),
                        ),
                    ),
                metaData =
                    TabMetaData(
                        title = TitleType.Text("Merged"),
                        icon = IconType.Material(UiIcon.Rss),
                    ),
            ).toTimelineSlotOrNull()

        assertNotNull(slot)
        val group = assertIs<TimelineSlotContent.Group>(slot.content)
        assertEquals(TimelineMergePolicy.TimePerPage, group.mergePolicy)
        assertEquals(GroupSource.Manual, group.source)
        assertEquals(2, group.children.size)
        assertEquals("Merged", assertIs<dev.dimension.flare.ui.model.UiText.Raw>(slot.title).string)
    }

    @Test
    fun enabledLegacyMixedTimelineMigratesToSystemHomeGroup() {
        val settings =
            TabSettings(
                enableMixedTimeline = true,
                mainTabs =
                    listOf(
                        Mastodon.LocalTimelineTabItem(
                            account = account,
                            metaData =
                                TabMetaData(
                                    title = TitleType.Text("Local"),
                                    icon = IconType.Material(UiIcon.Local),
                                ),
                        ),
                        RssTimelineTabItem(
                            feedUrl = "https://example.com/rss.xml",
                            metaData =
                                TabMetaData(
                                    title = TitleType.Text("RSS"),
                                    icon = IconType.Material(UiIcon.Rss),
                                ),
                        ),
                    ),
            )

        val migrated = settings.toTabSettingsV2()

        assertEquals(3, migrated.homeSlots.size)
        val systemHomeGroup = migrated.homeSlots.first()
        val group = assertIs<TimelineSlotContent.Group>(systemHomeGroup.content)
        assertEquals(GroupSource.SystemHome, group.source)
        assertEquals(TimelineMergePolicy.TimePerPage, group.mergePolicy)
        assertEquals(
            listOf(
                "${TimelineSpecIds.MASTODON_LOCAL}:$accountKey",
                "${TimelineSpecIds.RSS_FEED}:https://example.com/rss.xml",
            ),
            group.children.map { it.id },
        )
    }

    @Test
    fun rssTimelineTypesMigrateWithoutLoss() {
        val slots =
            listOf(
                RssTimelineTabItem(
                    feedUrl = "https://example.com/feed.xml",
                    favIcon = "https://example.com/favicon.ico",
                    metaData =
                        TabMetaData(
                            title = TitleType.Text("Feed"),
                            icon = IconType.Url("https://example.com/favicon.ico"),
                        ),
                ),
                AllRssTimelineTabItem(),
                SubscriptionTimelineTabItem(
                    subscriptionUrl = "https://mastodon.example/public",
                    subscriptionType = SubscriptionType.MASTODON_PUBLIC,
                    favIcon = null,
                    metaData =
                        TabMetaData(
                            title = TitleType.Text("Public"),
                            icon = IconType.FavIcon("mastodon.example"),
                        ),
                ),
            ).toTimelineSlots()

        assertEquals(
            listOf(
                TimelineSpecIds.RSS_FEED,
                TimelineSpecIds.RSS_ALL,
                TimelineSpecIds.RSS_SUBSCRIPTION,
            ),
            slots.map { assertIs<TimelineSlotContent.Source>(it.content).source.specId },
        )
    }

    @Test
    fun duplicateAndUnsupportedTabsAreFiltered() {
        val duplicate =
            Mastodon.FavouriteTimelineTabItem(
                account = account,
                metaData =
                    TabMetaData(
                        title = TitleType.Localized(TitleType.Localized.LocalizedKey.Favourite),
                        icon = IconType.Material(UiIcon.Favourite),
                    ),
            )
        val unsupported =
            Mastodon.FavouriteTimelineTabItem(
                account = AccountType.Guest,
                metaData = duplicate.metaData,
            )

        val slots = listOf(duplicate, duplicate.copy(), unsupported).toTimelineSlots()

        assertEquals(1, slots.size)
        assertEquals("${TimelineSpecIds.MASTODON_FAVOURITE}:$accountKey", slots.single().id)
        assertNull(unsupported.toTimelineSlotOrNull())
    }

    @Test
    fun legacyMigrationIdsResolveAgainstRegisteredTimelineSpecs() {
        val runtimeIds =
            testTimelineSpecs().map { it.id }.toSet() +
                setOf(
                    TimelineSpecIds.RSS_FEED,
                    TimelineSpecIds.RSS_ALL,
                    TimelineSpecIds.RSS_SUBSCRIPTION,
                    TimelineSpecIds.BLUESKY_BOOKMARK,
                    TimelineSpecIds.BLUESKY_FEED,
                    TimelineSpecIds.XQT_FEATURED,
                    TimelineSpecIds.XQT_BOOKMARK,
                    TimelineSpecIds.XQT_DEVICE_FOLLOW,
                    TimelineSpecIds.VVO_FAVORITE,
                    TimelineSpecIds.VVO_LIKED,
                    TimelineSpecIds.PIXIV_FOLLOWING,
                    TimelineSpecIds.PIXIV_BOOKMARK,
                )

        assertEquals(emptySet(), TimelineSpecIds.legacyMigrationIds - runtimeIds)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun v1FileMigratesToV2StoreAndDeletesOldFile() =
        runTest {
            val root = "/tmp/flare-tab-settings-${Random.nextLong()}".toPath()
            val fs = createTestFileSystem()
            fs.createDirectories(root)
            val fileStorage = OkioFileStorage(fs, root)
            val oldPath = fileStorage.dataStoreFile("tab_settings.pb")
            fs.write(oldPath) {
                write(
                    ProtoBuf.encodeToByteArray(
                        TabSettings(
                            mainTabs =
                                listOf(
                                    Mastodon.LocalTimelineTabItem(
                                        account = account,
                                        metaData =
                                            TabMetaData(
                                                title = TitleType.Text("Local"),
                                                icon = IconType.Material(UiIcon.Local),
                                            ),
                                    ),
                                    RssTimelineTabItem(
                                        feedUrl = "https://example.com/rss.xml",
                                        metaData =
                                            TabMetaData(
                                                title = TitleType.Text("RSS"),
                                                icon = IconType.Material(UiIcon.Rss),
                                            ),
                                    ),
                                ),
                        ),
                    ),
                )
            }
            val store =
                DataStoreFactory.create(
                    storage =
                        OkioStorage(
                            fileSystem = fs,
                            serializer = protobufSerializer(TabSettingsV2()),
                            producePath = { fileStorage.dataStoreFile("tab_settings_v2.pb") },
                        ),
                )

            migrateTabSettingsV1ToV2(fileStorage, store)

            val settings = store.data.first()
            assertEquals(
                listOf(
                    SYSTEM_HOME_MIXED_TIMELINE_ID,
                    "${TimelineSpecIds.MASTODON_LOCAL}:$accountKey",
                    "${TimelineSpecIds.RSS_FEED}:https://example.com/rss.xml",
                ),
                settings.homeSlots.map { it.id },
            )
            assertFalse(fs.exists(oldPath))
            fs.deleteRecursively(root)
        }
}
