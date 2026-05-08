package dev.dimension.flare.data.model.tab

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.model.AllRssTimelineTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.Mastodon
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.SubscriptionTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.VVo
import dev.dimension.flare.data.model.XQT
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.FileSystem
import okio.SYSTEM

@OptIn(ExperimentalSerializationApi::class)
internal suspend fun migrateTabSettingsV1ToV2(
    pathProducer: PlatformPathProducer,
    tabSettingsV2Store: DataStore<TabSettingsV2>,
) {
    withContext(Dispatchers.IO) {
        val v1Path = pathProducer.dataStoreFile("tab_settings.pb")
        val fs = FileSystem.SYSTEM
        if (!fs.exists(v1Path)) return@withContext

        if (tabSettingsV2Store.data.first().homeSlots.isNotEmpty()) {
            runCatching { fs.delete(v1Path) }
            return@withContext
        }

        val v1 =
            runCatching {
                fs.read(v1Path) {
                    ProtoBuf.decodeFromByteArray<TabSettings>(readByteArray())
                }
            }.getOrNull()

        if (v1 != null) {
            val migratedSlots = v1.toTabSettingsV2().homeSlots
            if (migratedSlots.isNotEmpty()) {
                tabSettingsV2Store.updateData { current ->
                    if (current.homeSlots.isEmpty()) {
                        current.copy(homeSlots = migratedSlots)
                    } else {
                        current
                    }
                }
            }
        }
        runCatching { fs.delete(v1Path) }
    }
}

internal fun TabSettings.toTabSettingsV2(): TabSettingsV2 =
    TabSettingsV2(
        homeSlots = mainTabs.toTimelineSlots(),
    )

internal fun List<TimelineTabItem>.toTimelineSlots(): List<TimelineSlot> =
    mapNotNull { it.toTimelineSlotOrNull() }
        .distinctBy { it.id }

internal fun TimelineTabItem.toTimelineSlotOrNull(): TimelineSlot? =
    when (this) {
        is HomeTimelineTabItem ->
            account.specificAccountKey()?.let { accountKey ->
                CommonTimelineSpecs.home
                    .target(
                        data = TimelineSpec.AccountBasedData(accountKey),
                        title = metaData.title.toUiText(UiStrings.Home.asText()),
                        icon = metaData.icon,
                    ).toSlot(presentation = metaData.toPresentation())
            }

        is ListTimelineTabItem ->
            account.specificAccountKey()?.let { accountKey ->
                CommonTimelineSpecs.list
                    .target(
                        data = TimelineSpec.AccountResourceData(accountKey, listId),
                        title = metaData.title.toUiText(UiStrings.List.asText()),
                        icon = metaData.icon,
                    ).toSlot(presentation = metaData.toPresentation())
            }

        is Mastodon.LocalTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = MastodonPlatformSpec.localTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonLocal,
            )

        is Mastodon.PublicTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = MastodonPlatformSpec.publicTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonPublic,
            )

        is Mastodon.BookmarkTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = MastodonPlatformSpec.bookmarkTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )

        is Mastodon.FavouriteTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = MastodonPlatformSpec.favouriteTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Favourite,
            )

        is Misskey.LocalTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = MisskeyPlatformSpec.localTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonLocal,
            )

        is Misskey.GlobalTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = MisskeyPlatformSpec.globalTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonPublic,
            )

        is Misskey.HybridTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = MisskeyPlatformSpec.hybridTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Social,
            )

        is Misskey.FavouriteTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = MisskeyPlatformSpec.favouriteTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Favourite,
            )

        is Misskey.AntennasTimelineTabItem ->
            account.toAccountResourceSlot(
                spec = MisskeyPlatformSpec.antennaTimelineSpec,
                resourceId = antennasId,
                metaData = metaData,
                fallbackTitle = UiStrings.Antenna,
            )

        is Misskey.ChannelTimelineTabItem ->
            account.toAccountResourceSlot(
                spec = MisskeyPlatformSpec.channelTimelineSpec,
                resourceId = channelId,
                metaData = metaData,
                fallbackTitle = UiStrings.Channel,
            )

        is XQT.FeaturedTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = XqtPlatformSpec.featuredTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Featured,
            )

        is XQT.BookmarkTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = XqtPlatformSpec.bookmarkTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )

        is XQT.DeviceFollowTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = XqtPlatformSpec.deviceFollowTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Posts,
            )

        is Bluesky.FeedTabItem ->
            account.toAccountResourceSlot(
                spec = BlueskyPlatformSpec.feedTimelineSpec,
                resourceId = uri,
                metaData = metaData,
                fallbackTitle = UiStrings.Feeds,
            )

        is Bluesky.BookmarkTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = BlueskyPlatformSpec.bookmarkTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )

        is VVo.FeaturedTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = VvoPlatformSpec.featuredTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Featured,
            )

        is VVo.FavoriteTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = VvoPlatformSpec.favoriteTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )

        is VVo.LikedTimelineTabItem ->
            account.toAccountBasedSlot(
                spec = VvoPlatformSpec.likedTimelineSpec,
                metaData = metaData,
                fallbackTitle = UiStrings.Liked,
            )

        is RssTimelineTabItem ->
            RssTimelineSpecs.rss
                .target(
                    data = RssTimelineSpecs.RssData(feedUrl),
                    title = metaData.title.toUiText(UiStrings.Rss.asText()),
                    icon = metaData.icon,
                ).toSlot(presentation = metaData.toPresentation())

        is AllRssTimelineTabItem ->
            RssTimelineSpecs.allRss
                .target(
                    data = RssTimelineSpecs.AllRssData,
                    title = metaData.title.toUiText(UiStrings.AllRssFeeds.asText()),
                    icon = metaData.icon,
                ).toSlot(presentation = metaData.toPresentation())

        is SubscriptionTimelineTabItem ->
            RssTimelineSpecs.subscription
                .target(
                    data =
                        RssTimelineSpecs.SubscriptionData(
                            subscriptionUrl = subscriptionUrl,
                            subscriptionType = subscriptionType,
                        ),
                    title = metaData.title.toUiText(UiStrings.Rss.asText()),
                    icon = metaData.icon,
                ).toSlot(presentation = metaData.toPresentation())

        is MixedTimelineTabItem -> {
            val children = subTimelineTabItem.toTimelineSlots()
            if (children.isEmpty()) {
                null
            } else {
                TimelineSlot(
                    id = key,
                    content =
                        TimelineSlotContent.Group(
                            children = children,
                            source = GroupSource.Manual,
                            mergePolicy = TimelineMergePolicy.TimePerPage,
                        ),
                    presentation = metaData.toPresentation(),
                )
            }
        }
    }

private fun AccountType.toAccountBasedSlot(
    spec: TimelineSpec<TimelineSpec.AccountBasedData>,
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot? =
    specificAccountKey()?.let { accountKey ->
        spec
            .target(
                data = TimelineSpec.AccountBasedData(accountKey),
                title = metaData.title.toUiText(fallbackTitle.asText()),
                icon = metaData.icon,
            ).toSlot(presentation = metaData.toPresentation())
    }

private fun AccountType.toAccountResourceSlot(
    spec: TimelineSpec<TimelineSpec.AccountResourceData>,
    resourceId: String,
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot? =
    specificAccountKey()?.let { accountKey ->
        spec
            .target(
                data = TimelineSpec.AccountResourceData(accountKey, resourceId),
                title = metaData.title.toUiText(fallbackTitle.asText()),
                icon = metaData.icon,
            ).toSlot(presentation = metaData.toPresentation())
    }

private fun AccountType.specificAccountKey(): MicroBlogKey? = (this as? AccountType.Specific)?.accountKey

private fun TabMetaData.toPresentation(): TimelinePresentation =
    TimelinePresentation(
        titleOverride = (title as? TitleType.Text)?.content,
        iconOverride = icon,
    )

private fun TitleType.toUiText(fallback: UiText): UiText =
    when (this) {
        is TitleType.Text -> UiText.Raw(content)
        is TitleType.Localized -> runCatching { UiStrings.valueOf(key.name).asText() }.getOrDefault(fallback)
    }
