package dev.dimension.flare.data.model.tab

import androidx.datastore.core.DataStore
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.model.AllRssTimelineTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
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
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
internal suspend fun migrateTabSettingsV1ToV2(
    fileStorage: FileStorage,
    tabSettingsV2Store: DataStore<TabSettingsV2>,
) {
    withContext(PlatformDispatchers.IO) {
        val v1Path = fileStorage.dataStoreFile("tab_settings.pb")
        if (!fileStorage.exists(v1Path)) return@withContext

        if (tabSettingsV2Store.data
                .first()
                .homeSlots
                .isNotEmpty()
        ) {
            runCatching { fileStorage.delete(v1Path) }
            return@withContext
        }

        val v1 =
            runCatching {
                ProtoBuf.decodeFromByteArray<TabSettings>(fileStorage.read(v1Path))
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
        runCatching { fileStorage.delete(v1Path) }
    }
}

internal fun TabSettings.toTabSettingsV2(): TabSettingsV2 =
    TabSettingsV2(
        homeSlots = mainTabs.toTimelineSlots().withLegacySystemHomeMixedTimeline(enableMixedTimeline),
    )

internal fun List<TimelineTabItem>.toTimelineSlots(): List<TimelineSlot> =
    mapNotNull { it.toTimelineSlotOrNull() }
        .distinctBy { it.id }

private fun List<TimelineSlot>.withLegacySystemHomeMixedTimeline(enabled: Boolean): List<TimelineSlot> {
    if (!enabled || size < 2 || any { it.id == SYSTEM_HOME_MIXED_TIMELINE_ID }) {
        return this
    }
    val systemHomeGroup =
        TimelineSlot(
            id = SYSTEM_HOME_MIXED_TIMELINE_ID,
            content =
                TimelineSlotContent.Group(
                    children = this,
                    source = GroupSource.SystemHome,
                    mergePolicy = TimelineMergePolicy.TimePerPage,
                ),
        )
    return listOf(systemHomeGroup) + this
}

internal fun TimelineTabItem.toTimelineSlotOrNull(): TimelineSlot? =
    when (this) {
        is HomeTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.COMMON_HOME,
                metaData = metaData,
                fallbackTitle = UiStrings.Home,
            )
        }

        is ListTimelineTabItem -> {
            account.toAccountResourceSlot(
                specId = TimelineSpecIds.COMMON_LIST,
                resourceId = listId,
                metaData = metaData,
                fallbackTitle = UiStrings.List,
            )
        }

        is Mastodon.LocalTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.MASTODON_LOCAL,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonLocal,
            )
        }

        is Mastodon.PublicTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.MASTODON_PUBLIC,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonPublic,
            )
        }

        is Mastodon.BookmarkTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.MASTODON_BOOKMARK,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )
        }

        is Mastodon.FavouriteTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.MASTODON_FAVOURITE,
                metaData = metaData,
                fallbackTitle = UiStrings.Favourite,
            )
        }

        is Misskey.LocalTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.MISSKEY_LOCAL,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonLocal,
            )
        }

        is Misskey.GlobalTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.MISSKEY_GLOBAL,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonPublic,
            )
        }

        is Misskey.HybridTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.MISSKEY_HYBRID,
                metaData = metaData,
                fallbackTitle = UiStrings.Social,
            )
        }

        is Misskey.FavouriteTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.MISSKEY_FAVOURITE,
                metaData = metaData,
                fallbackTitle = UiStrings.Favourite,
            )
        }

        is Misskey.AntennasTimelineTabItem -> {
            account.toAccountResourceSlot(
                specId = TimelineSpecIds.MISSKEY_ANTENNA,
                resourceId = antennasId,
                metaData = metaData,
                fallbackTitle = UiStrings.Antenna,
            )
        }

        is Misskey.ChannelTimelineTabItem -> {
            account.toAccountResourceSlot(
                specId = TimelineSpecIds.MISSKEY_CHANNEL,
                resourceId = channelId,
                metaData = metaData,
                fallbackTitle = UiStrings.Channel,
            )
        }

        is XQT.FeaturedTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.XQT_FEATURED,
                metaData = metaData,
                fallbackTitle = UiStrings.Featured,
            )
        }

        is XQT.BookmarkTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.XQT_BOOKMARK,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )
        }

        is XQT.DeviceFollowTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.XQT_DEVICE_FOLLOW,
                metaData = metaData,
                fallbackTitle = UiStrings.Posts,
            )
        }

        is Bluesky.FeedTabItem -> {
            account.toAccountResourceSlot(
                specId = TimelineSpecIds.BLUESKY_FEED,
                resourceId = uri,
                metaData = metaData,
                fallbackTitle = UiStrings.Feeds,
            )
        }

        is Bluesky.BookmarkTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.BLUESKY_BOOKMARK,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )
        }

        is VVo.FeaturedTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.COMMON_DISCOVER,
                metaData = metaData,
                fallbackTitle = UiStrings.Featured,
            )
        }

        is VVo.FavoriteTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.VVO_FAVORITE,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )
        }

        is VVo.LikedTimelineTabItem -> {
            account.toAccountBasedSlot(
                specId = TimelineSpecIds.VVO_LIKED,
                metaData = metaData,
                fallbackTitle = UiStrings.Liked,
            )
        }

        is RssTimelineTabItem -> {
            rssSlot(
                feedUrl = feedUrl,
                metaData = metaData,
                fallbackTitle = UiStrings.Rss,
            )
        }

        is AllRssTimelineTabItem -> {
            allRssSlot(
                metaData = metaData,
                fallbackTitle = UiStrings.AllRssFeeds,
            )
        }

        is SubscriptionTimelineTabItem -> {
            subscriptionSlot(
                subscriptionUrl = subscriptionUrl,
                subscriptionType = subscriptionType,
                metaData = metaData,
                fallbackTitle = UiStrings.Rss,
            )
        }

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
    specId: String,
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot? =
    specificAccountKey()?.let { accountKey ->
        accountBasedSource(
            specId = specId,
            accountKey = accountKey,
            title = metaData.title.toUiText(fallbackTitle.asText()),
            icon = metaData.icon,
        ).toSlot(presentation = metaData.toPresentation())
    }

private fun AccountType.toAccountResourceSlot(
    specId: String,
    resourceId: String,
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot? =
    specificAccountKey()?.let { accountKey ->
        accountResourceSource(
            specId = specId,
            accountKey = accountKey,
            resourceId = resourceId,
            title = metaData.title.toUiText(fallbackTitle.asText()),
            icon = metaData.icon,
        ).toSlot(presentation = metaData.toPresentation())
    }

@OptIn(ExperimentalSerializationApi::class)
private fun accountBasedSource(
    specId: String,
    accountKey: MicroBlogKey,
    title: UiText,
    icon: IconType,
): TimelineSourceRef =
    TimelineSourceRef(
        id = "$specId:$accountKey",
        specId = specId,
        title = title,
        icon = icon,
        data =
            ProtoBuf.encodeToHexString(
                TimelineSpec.AccountBasedData.serializer(),
                TimelineSpec.AccountBasedData(accountKey),
            ),
    )

@OptIn(ExperimentalSerializationApi::class)
private fun accountResourceSource(
    specId: String,
    accountKey: MicroBlogKey,
    resourceId: String,
    title: UiText,
    icon: IconType,
): TimelineSourceRef =
    TimelineSourceRef(
        id = "$specId:$accountKey:$resourceId",
        specId = specId,
        title = title,
        icon = icon,
        data =
            ProtoBuf.encodeToHexString(
                TimelineSpec.AccountResourceData.serializer(),
                TimelineSpec.AccountResourceData(accountKey, resourceId),
            ),
    )

@OptIn(ExperimentalSerializationApi::class)
private fun rssSlot(
    feedUrl: String,
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot =
    TimelineSourceRef(
        id = "${TimelineSpecIds.RSS_FEED}:$feedUrl",
        specId = TimelineSpecIds.RSS_FEED,
        title = metaData.title.toUiText(fallbackTitle.asText()),
        icon = metaData.icon,
        data =
            ProtoBuf.encodeToHexString(
                RssTimelineData.serializer(),
                RssTimelineData(feedUrl),
            ),
    ).toSlot(presentation = metaData.toPresentation())

@OptIn(ExperimentalSerializationApi::class)
private fun allRssSlot(
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot =
    TimelineSourceRef(
        id = "${TimelineSpecIds.RSS_ALL}:all",
        specId = TimelineSpecIds.RSS_ALL,
        title = metaData.title.toUiText(fallbackTitle.asText()),
        icon = metaData.icon,
        data =
            ProtoBuf.encodeToHexString(
                AllRssTimelineData.serializer(),
                AllRssTimelineData,
            ),
    ).toSlot(presentation = metaData.toPresentation())

@OptIn(ExperimentalSerializationApi::class)
private fun subscriptionSlot(
    subscriptionUrl: String,
    subscriptionType: SubscriptionType,
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot =
    TimelineSourceRef(
        id = "${TimelineSpecIds.RSS_SUBSCRIPTION}:${subscriptionType.name}:$subscriptionUrl",
        specId = TimelineSpecIds.RSS_SUBSCRIPTION,
        title = metaData.title.toUiText(fallbackTitle.asText()),
        icon = metaData.icon,
        data =
            ProtoBuf.encodeToHexString(
                SubscriptionTimelineData.serializer(),
                SubscriptionTimelineData(
                    subscriptionUrl = subscriptionUrl,
                    subscriptionType = subscriptionType,
                ),
            ),
    ).toSlot(presentation = metaData.toPresentation())

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
