package dev.dimension.flare.data.model.tab

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AllRssTimelineTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.LegacySubscriptionType
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
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
public suspend fun migrateTabSettingsV1ToV2(
    pathProducer: PlatformPathProducer,
    tabSettingsV2Store: DataStore<TabSettingsV2>,
) {
    if (!legacyTabSettingsExists(pathProducer)) return
    if (tabSettingsV2Store.data
            .first()
            .homeSlots
            .isNotEmpty()
    ) {
        deleteLegacyTabSettings(pathProducer)
        return
    }

    val v1 = readLegacyTabSettings(pathProducer)
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
    deleteLegacyTabSettings(pathProducer)
}

internal expect suspend fun legacyTabSettingsExists(pathProducer: PlatformPathProducer): Boolean

internal expect suspend fun readLegacyTabSettings(pathProducer: PlatformPathProducer): TabSettings?

internal expect suspend fun deleteLegacyTabSettings(pathProducer: PlatformPathProducer)

public fun TabSettings.toTabSettingsV2(): TabSettingsV2 =
    TabSettingsV2(
        homeSlots = mainTabs.toTimelineSlots().withLegacySystemHomeMixedTimeline(enableMixedTimeline),
    )

public fun List<TimelineTabItem>.toTimelineSlots(): List<TimelineSlot> =
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

public fun TimelineTabItem.toTimelineSlotOrNull(): TimelineSlot? =
    when (this) {
        is HomeTimelineTabItem -> {
            account.specificAccountKey()?.let { accountKey ->
                LegacyTimelineSpecs.home
                    .target(
                        data = LegacyAccountBasedData(accountKey),
                        title = metaData.title.toUiText(UiStrings.Home.asText()),
                        icon = metaData.icon,
                    ).toSlot(presentation = metaData.toPresentation())
            }
        }

        is ListTimelineTabItem -> {
            account.specificAccountKey()?.let { accountKey ->
                LegacyTimelineSpecs.list
                    .target(
                        data = LegacyAccountResourceData(accountKey, listId),
                        title = metaData.title.toUiText(UiStrings.List.asText()),
                        icon = metaData.icon,
                    ).toSlot(presentation = metaData.toPresentation())
            }
        }

        is Mastodon.LocalTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.mastodonLocal,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonLocal,
            )
        }

        is Mastodon.PublicTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.mastodonPublic,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonPublic,
            )
        }

        is Mastodon.BookmarkTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.mastodonBookmark,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )
        }

        is Mastodon.FavouriteTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.mastodonFavourite,
                metaData = metaData,
                fallbackTitle = UiStrings.Favourite,
            )
        }

        is Misskey.LocalTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.misskeyLocal,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonLocal,
            )
        }

        is Misskey.GlobalTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.misskeyGlobal,
                metaData = metaData,
                fallbackTitle = UiStrings.MastodonPublic,
            )
        }

        is Misskey.HybridTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.misskeyHybrid,
                metaData = metaData,
                fallbackTitle = UiStrings.Social,
            )
        }

        is Misskey.FavouriteTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.misskeyFavourite,
                metaData = metaData,
                fallbackTitle = UiStrings.Favourite,
            )
        }

        is Misskey.AntennasTimelineTabItem -> {
            account.toAccountResourceSlot(
                spec = LegacyTimelineSpecs.misskeyAntenna,
                resourceId = antennasId,
                metaData = metaData,
                fallbackTitle = UiStrings.Antenna,
            )
        }

        is Misskey.ChannelTimelineTabItem -> {
            account.toAccountResourceSlot(
                spec = LegacyTimelineSpecs.misskeyChannel,
                resourceId = channelId,
                metaData = metaData,
                fallbackTitle = UiStrings.Channel,
            )
        }

        is XQT.FeaturedTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.xqtFeatured,
                metaData = metaData,
                fallbackTitle = UiStrings.Featured,
            )
        }

        is XQT.BookmarkTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.xqtBookmark,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )
        }

        is XQT.DeviceFollowTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.xqtDeviceFollow,
                metaData = metaData,
                fallbackTitle = UiStrings.Posts,
            )
        }

        is Bluesky.FeedTabItem -> {
            account.toAccountResourceSlot(
                spec = LegacyTimelineSpecs.blueskyFeed,
                resourceId = uri,
                metaData = metaData,
                fallbackTitle = UiStrings.Feeds,
            )
        }

        is Bluesky.BookmarkTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.blueskyBookmark,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )
        }

        is VVo.FeaturedTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.discover,
                metaData = metaData,
                fallbackTitle = UiStrings.Featured,
            )
        }

        is VVo.FavoriteTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.vvoFavorite,
                metaData = metaData,
                fallbackTitle = UiStrings.Bookmark,
            )
        }

        is VVo.LikedTimelineTabItem -> {
            account.toAccountBasedSlot(
                spec = LegacyTimelineSpecs.vvoLiked,
                metaData = metaData,
                fallbackTitle = UiStrings.Liked,
            )
        }

        is RssTimelineTabItem -> {
            LegacyTimelineSpecs.rss
                .target(
                    data = LegacyRssData(feedUrl),
                    title = metaData.title.toUiText(UiStrings.Rss.asText()),
                    icon = metaData.icon,
                ).toSlot(presentation = metaData.toPresentation())
        }

        is AllRssTimelineTabItem -> {
            LegacyTimelineSpecs.allRss
                .target(
                    data = LegacyAllRssData,
                    title = metaData.title.toUiText(UiStrings.AllRssFeeds.asText()),
                    icon = metaData.icon,
                ).toSlot(presentation = metaData.toPresentation())
        }

        is SubscriptionTimelineTabItem -> {
            LegacyTimelineSpecs.subscription
                .target(
                    data =
                        LegacySubscriptionData(
                            subscriptionUrl = subscriptionUrl,
                            subscriptionType = subscriptionType,
                        ),
                    title = metaData.title.toUiText(UiStrings.Rss.asText()),
                    icon = metaData.icon,
                ).toSlot(presentation = metaData.toPresentation())
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
    spec: LegacyTimelineSpec<LegacyAccountBasedData>,
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot? =
    specificAccountKey()?.let { accountKey ->
        spec
            .target(
                data = LegacyAccountBasedData(accountKey),
                title = metaData.title.toUiText(fallbackTitle.asText()),
                icon = metaData.icon,
            ).toSlot(presentation = metaData.toPresentation())
    }

private fun AccountType.toAccountResourceSlot(
    spec: LegacyTimelineSpec<LegacyAccountResourceData>,
    resourceId: String,
    metaData: TabMetaData,
    fallbackTitle: UiStrings,
): TimelineSlot? =
    specificAccountKey()?.let { accountKey ->
        spec
            .target(
                data = LegacyAccountResourceData(accountKey, resourceId),
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

private data class LegacyTimelineSpec<T>(
    val id: String,
    val title: UiStrings,
    val icon: dev.dimension.flare.data.model.IconType,
    val serializer: KSerializer<T>,
    val targetId: (data: T) -> String,
) {
    @OptIn(ExperimentalSerializationApi::class)
    fun target(
        data: T,
        title: UiText = this.title.asText(),
        icon: dev.dimension.flare.data.model.IconType = this.icon,
    ): TimelineSourceRef =
        TimelineSourceRef(
            id = "$id:${targetId(data)}",
            specId = id,
            title = title,
            icon = icon,
            data = ProtoBuf.encodeToHexString(serializer, data),
        )
}

@Serializable
private data class LegacyAccountBasedData(
    val accountKey: MicroBlogKey,
)

@Serializable
private data class LegacyAccountResourceData(
    val accountKey: MicroBlogKey,
    val resourceId: String,
)

@Serializable
private data class LegacyRssData(
    val feedUrl: String,
)

@Serializable
private data object LegacyAllRssData

@Serializable
private data class LegacySubscriptionData(
    val subscriptionUrl: String,
    val subscriptionType: LegacySubscriptionType,
)

private object LegacyTimelineSpecs {
    val home =
        LegacyTimelineSpec(
            id = "common.home",
            title = UiStrings.Home,
            icon =
                dev.dimension.flare.data.model.IconType
                    .Material(UiIcon.Home),
            serializer = LegacyAccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
        )

    val discover =
        LegacyTimelineSpec(
            id = "common.discover",
            title = UiStrings.Discover,
            icon =
                dev.dimension.flare.data.model.IconType
                    .Material(UiIcon.Search),
            serializer = LegacyAccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
        )

    val list =
        LegacyTimelineSpec(
            id = "common.list",
            title = UiStrings.List,
            icon =
                dev.dimension.flare.data.model.IconType
                    .Material(UiIcon.List),
            serializer = LegacyAccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
        )

    val mastodonLocal = accountBased("mastodon.local", UiStrings.MastodonLocal, UiIcon.Local)
    val mastodonPublic = accountBased("mastodon.public", UiStrings.MastodonPublic, UiIcon.World)
    val mastodonBookmark = accountBased("mastodon.bookmark", UiStrings.Bookmark, UiIcon.Bookmark)
    val mastodonFavourite = accountBased("mastodon.favourite", UiStrings.Favourite, UiIcon.Favourite)
    val misskeyFavourite = accountBased("misskey.favourite", UiStrings.Favourite, UiIcon.Favourite)
    val misskeyHybrid = accountBased("misskey.hybrid", UiStrings.Social, UiIcon.Featured)
    val misskeyLocal = accountBased("misskey.local", UiStrings.MastodonLocal, UiIcon.Local)
    val misskeyGlobal = accountBased("misskey.global", UiStrings.MastodonPublic, UiIcon.World)
    val misskeyAntenna = accountResource("misskey.antenna", UiStrings.Antenna, UiIcon.Rss)
    val misskeyChannel = accountResource("misskey.channel", UiStrings.Channel, UiIcon.Channel)
    val xqtFeatured = accountBased("xqt.featured", UiStrings.Featured, UiIcon.Featured)
    val xqtBookmark = accountBased("xqt.bookmark", UiStrings.Bookmark, UiIcon.Bookmark)
    val xqtDeviceFollow = accountBased("xqt.device_follow", UiStrings.Posts, UiIcon.List)
    val blueskyBookmark = accountBased("bluesky.bookmark", UiStrings.Bookmark, UiIcon.Bookmark)
    val blueskyFeed = accountResource("bluesky.feed", UiStrings.Feeds, UiIcon.Feeds)
    val vvoFavorite = accountBased("vvo.favorite", UiStrings.Bookmark, UiIcon.Bookmark)
    val vvoLiked = accountBased("vvo.liked", UiStrings.Liked, UiIcon.Heart)

    val rss =
        LegacyTimelineSpec(
            id = "rss.feed",
            title = UiStrings.Rss,
            icon =
                dev.dimension.flare.data.model.IconType
                    .Material(UiIcon.Rss),
            serializer = LegacyRssData.serializer(),
            targetId = { it.feedUrl },
        )

    val allRss =
        LegacyTimelineSpec(
            id = "rss.all",
            title = UiStrings.AllRssFeeds,
            icon =
                dev.dimension.flare.data.model.IconType
                    .Material(UiIcon.Rss),
            serializer = LegacyAllRssData.serializer(),
            targetId = { "all" },
        )

    val subscription =
        LegacyTimelineSpec(
            id = "rss.subscription",
            title = UiStrings.Rss,
            icon =
                dev.dimension.flare.data.model.IconType
                    .Material(UiIcon.Rss),
            serializer = LegacySubscriptionData.serializer(),
            targetId = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
        )

    private fun accountBased(
        id: String,
        title: UiStrings,
        icon: UiIcon,
    ): LegacyTimelineSpec<LegacyAccountBasedData> =
        LegacyTimelineSpec(
            id = id,
            title = title,
            icon =
                dev.dimension.flare.data.model.IconType
                    .Material(icon),
            serializer = LegacyAccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
        )

    private fun accountResource(
        id: String,
        title: UiStrings,
        icon: UiIcon,
    ): LegacyTimelineSpec<LegacyAccountResourceData> =
        LegacyTimelineSpec(
            id = id,
            title = title,
            icon =
                dev.dimension.flare.data.model.IconType
                    .Material(icon),
            serializer = LegacyAccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
        )
}
