package dev.dimension.flare.data.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.presenter.home.DiscoverStatusTimelinePresenter
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import dev.dimension.flare.ui.presenter.home.MixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.mastodon.MastodonBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.mastodon.MastodonFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.mastodon.MastodonLocalTimelinePresenter
import dev.dimension.flare.ui.presenter.home.mastodon.MastodonPublicTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MissKeyLocalTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MissKeyPublicTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyHybridTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.AllRssTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.RssTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.SubscriptionTimelinePresenter
import dev.dimension.flare.ui.presenter.home.vvo.VVOFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.vvo.VVOLikeTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTDeviceFollowTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTFeaturedTimelinePresenter
import dev.dimension.flare.ui.presenter.list.AntennasTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ChannelTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ListTimelinePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

@Immutable
@Serializable
internal data class TabSettings(
    val secondaryItems: List<TabItem>? = null,
    val enableMixedTimeline: Boolean = true,
    val mainTabs: List<TimelineTabItem> = listOf(),
)

@Immutable
@Serializable
internal sealed class TabItem {
    // for iOS
    internal val id: String by lazy {
        key
    }
    internal abstract val metaData: TabMetaData
    internal abstract val key: String

    internal abstract fun update(metaData: TabMetaData = this.metaData): TabItem
}

internal interface WithAccountTabItem {
    val account: AccountType
}

@Immutable
@Serializable
internal sealed class AccountTabItem :
    TabItem(),
    WithAccountTabItem {
    abstract override val account: AccountType
}

@Immutable
@Serializable
internal data class TabMetaData(
    val title: TitleType,
    val icon: IconType,
)

@Serializable
internal sealed class TitleType {
    @Immutable
    @Serializable
    internal data class Text(
        val content: String,
    ) : TitleType()

    @Immutable
    @Serializable
    internal data class Localized(
        val key: LocalizedKey,
    ) : TitleType() {
        @Serializable
        internal enum class LocalizedKey {
            Home,
            Notifications,
            Discover,
            Me,
            Settings,
            MastodonLocal,
            MastodonPublic,
            Featured,
            Bookmark,
            Favourite,
            List,
            Feeds,
            DirectMessage,
            Rss,
            Antenna,
            MixedTimeline,
            Social,
            Liked,
            AllRssFeeds,
            Posts,
            Channel,
        }
    }
}

@Serializable
internal data object AllNotificationTabItem : TabItem() {
    override val metaData: TabMetaData =
        TabMetaData(
            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Notifications),
            icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Notification),
        )
    override val key: String = "all_notification"

    override fun update(metaData: TabMetaData): TabItem = this
}

// keep this here for compatibility
@Immutable
@Serializable
internal data class NotificationTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : AccountTabItem() {
    override val key: String = "notification_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Immutable
@Serializable
internal sealed class TimelineTabItem : TabItem() {
    internal abstract fun createPresenter(): TimelinePresenter

    internal companion object {
        internal val default: ImmutableList<TabItem> =
            persistentListOf(
                HomeTabItem,
                AllNotificationTabItem,
                DiscoverTabItem,
            )

        internal val guest: ImmutableList<TabItem> =
            persistentListOf(
                HomeTabItem,
                DiscoverTabItem,
            )
    }
}

@Immutable
@Serializable
internal data object HomeTabItem : TabItem() {
    override val metaData: TabMetaData =
        TabMetaData(
            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
            icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Home),
        )
    override val key: String = "home"

    override fun update(metaData: TabMetaData): TabItem = this
}

@Immutable
@Serializable
internal data class HomeTimelineTabItem(
    override val metaData: TabMetaData,
    override val account: AccountType,
) : TimelineTabItem(),
    WithAccountTabItem {
    override val key: String = "home_$account"

    override fun createPresenter(): TimelinePresenter = HomeTimelinePresenter(account)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)

    internal constructor(accountType: AccountType) :
        this(
            account = accountType,
            metaData =
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                    icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Home),
                ),
        )

    internal constructor(
        accountKey: MicroBlogKey,
        title: String,
        icon: IconType = IconType.FavIcon(accountKey.host),
    ) :
        this(
            account = AccountType.Specific(accountKey),
            metaData =
                TabMetaData(
                    title = TitleType.Text(title),
                    icon = icon,
                ),
        )
}

@Immutable
@Serializable
internal data class MixedTimelineTabItem(
    val subTimelineTabItem: List<TimelineTabItem>,
    override val metaData: TabMetaData =
        TabMetaData(
            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MixedTimeline),
            icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Rss),
        ),
) : TimelineTabItem() {
    override fun createPresenter(): TimelinePresenter = MixedTimelinePresenter(subTimelineTabItem.map { it.createPresenter() })

    override val key: String
        get() =
            buildString {
                append("mixed_timeline")
                append(metaData.title.toString())
                subTimelineTabItem.forEach { item ->
                    append(item.key)
                }
            }

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Immutable
@Serializable
internal data class ListTimelineTabItem(
    override val account: AccountType,
    val listId: String,
    override val metaData: TabMetaData,
) : TimelineTabItem(),
    WithAccountTabItem {
    internal constructor(accountKey: MicroBlogKey, data: UiList) : this(
        listId = data.id,
        account = AccountType.Specific(accountKey),
        metaData =
            TabMetaData(
                title = TitleType.Text(data.title),
                icon =
                    IconType.Mixed(
                        icon = dev.dimension.flare.ui.model.UiIcon.List,
                        accountKey = accountKey,
                    ),
            ),
    )

    override val key: String = "list_${account}_$listId"

    override fun createPresenter(): TimelinePresenter = ListTimelinePresenter(account, listId)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Immutable
@Serializable
internal data class AllListTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : AccountTabItem() {
    override val key: String = "list_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

internal object Mastodon {
    @Immutable
    @Serializable
    internal data class LocalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "local_$account"

        override fun createPresenter(): TimelinePresenter = MastodonLocalTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class PublicTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "internal_$account"

        override fun createPresenter(): TimelinePresenter = MastodonPublicTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter = MastodonBookmarkTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class FavouriteTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "favourite_$account"

        override fun createPresenter(): TimelinePresenter = MastodonFavouriteTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

internal object Misskey {
    @Immutable
    @Serializable
    internal data class LocalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "local_$account"

        override fun createPresenter(): TimelinePresenter = MissKeyLocalTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class GlobalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "global_$account"

        override fun createPresenter(): TimelinePresenter = MissKeyPublicTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class HybridTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "hybrid_$account"

        override fun createPresenter(): TimelinePresenter = MisskeyHybridTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class FavouriteTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "favourite_$account"

        override fun createPresenter(): TimelinePresenter = MisskeyFavouriteTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class AntennasListTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : AccountTabItem() {
        override val key: String = "antennas_$account"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class AntennasTimelineTabItem(
        val antennasId: String,
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        internal constructor(accountKey: MicroBlogKey, data: UiList) : this(
            antennasId = data.id,
            account = AccountType.Specific(accountKey),
            metaData =
                TabMetaData(
                    title = TitleType.Text(data.title),
                    icon =
                        IconType.Mixed(
                            icon = dev.dimension.flare.ui.model.UiIcon.Rss,
                            accountKey = accountKey,
                        ),
                ),
        )

        override val key: String = "antennas_${account}_$antennasId"

        override fun createPresenter(): TimelinePresenter = AntennasTimelinePresenter(account, antennasId)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class ChannelTimelineTabItem(
        val channelId: String,
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        internal constructor(accountKey: MicroBlogKey, data: UiList) : this(
            channelId = data.id,
            account = AccountType.Specific(accountKey),
            metaData =
                TabMetaData(
                    title = TitleType.Text(data.title),
                    icon =
                        IconType.Mixed(
                            icon = dev.dimension.flare.ui.model.UiIcon.List,
                            accountKey = accountKey,
                        ),
                ),
        )

        override val key: String = "channel_${account}_$channelId"

        override fun createPresenter(): TimelinePresenter = ChannelTimelinePresenter(account, channelId)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class ChannelListTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : AccountTabItem() {
        override val key: String = "channels_$account"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

internal object XQT {
    @Immutable
    @Serializable
    internal data class FeaturedTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "featured_$account"

        override fun createPresenter(): TimelinePresenter = XQTFeaturedTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter = XQTBookmarkTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class DeviceFollowTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData =
            TabMetaData(
                title = TitleType.Localized(TitleType.Localized.LocalizedKey.Posts),
                icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.List),
            ),
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "device_follow_$account"

        override fun createPresenter(): TimelinePresenter = XQTDeviceFollowTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

internal object Bluesky {
    @Immutable
    @Serializable
    internal data class FeedsTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : AccountTabItem() {
        override val key: String = "feeds_$account"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class FeedTabItem(
        override val account: AccountType,
        val uri: String,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        internal constructor(accountKey: MicroBlogKey, data: UiList) : this(
            uri = data.id,
            account = AccountType.Specific(accountKey),
            metaData =
                TabMetaData(
                    title = TitleType.Text(data.title),
                    icon =
                        IconType.Mixed(
                            icon = dev.dimension.flare.ui.model.UiIcon.Feeds,
                            accountKey = accountKey,
                        ),
                ),
        )

        override fun createPresenter(): TimelinePresenter = BlueskyFeedTimelinePresenter(account, uri)

        override val key: String = "feed_${account}_$uri"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter = BlueskyBookmarkTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)

        internal constructor(accountType: AccountType) :
            this(
                account = accountType,
                metaData =
                    TabMetaData(
                        title = TitleType.Localized(TitleType.Localized.LocalizedKey.Bookmark),
                        icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Bookmark),
                    ),
            )
    }
}

internal object VVo {
    @Immutable
    @Serializable
    internal data class FeaturedTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "featured_$account"

        override fun createPresenter(): TimelinePresenter = DiscoverStatusTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class FavoriteTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "favorite_$account"

        override fun createPresenter(): TimelinePresenter = VVOFavouriteTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Immutable
    @Serializable
    internal data class LikedTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem(),
        WithAccountTabItem {
        override val key: String = "liked_$account"

        override fun createPresenter(): TimelinePresenter = VVOLikeTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

@Immutable
@Serializable
internal data class RssTimelineTabItem(
    val feedUrl: String,
    override val metaData: TabMetaData,
    val favIcon: String? = null,
) : TimelineTabItem() {
    override val key: String = "rss_$feedUrl"

    override fun createPresenter(): TimelinePresenter = RssTimelinePresenter(feedUrl)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)

    internal constructor(data: UiRssSource) : this(
        data.url,
        favIcon = data.favIcon,
        metaData =
            TabMetaData(
                title = TitleType.Text(data.title ?: data.url),
                icon =
                    data.favIcon?.let {
                        IconType.Url(it)
                    } ?: IconType.Material(dev.dimension.flare.ui.model.UiIcon.Rss),
            ),
    )
}

@Immutable
@Serializable
internal data class AllRssTimelineTabItem(
    override val metaData: TabMetaData =
        TabMetaData(
            title = TitleType.Localized(TitleType.Localized.LocalizedKey.AllRssFeeds),
            icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Rss),
        ),
) : TimelineTabItem() {
    override val key: String = "all_rss"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)

    override fun createPresenter(): TimelinePresenter = AllRssTimelinePresenter()
}

@Immutable
@Serializable
internal data class SubscriptionTimelineTabItem(
    val subscriptionUrl: String,
    val subscriptionType: SubscriptionType,
    override val metaData: TabMetaData,
    val favIcon: String? = null,
) : TimelineTabItem() {
    override val key: String = "subscription_${subscriptionType.name}_$subscriptionUrl"

    override fun createPresenter(): TimelinePresenter = SubscriptionTimelinePresenter(subscriptionType, subscriptionUrl)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)

    internal constructor(data: UiRssSource) : this(
        subscriptionUrl = data.url,
        subscriptionType = data.type,
        favIcon = data.favIcon,
        metaData =
            TabMetaData(
                title = TitleType.Text(data.title ?: data.url),
                icon =
                    data.favIcon?.let {
                        IconType.Url(it)
                    } ?: when (data.type) {
                        SubscriptionType.RSS -> IconType.Material(dev.dimension.flare.ui.model.UiIcon.Rss)
                        else -> IconType.FavIcon(data.host)
                    },
            ),
    )
}

@Immutable
@Serializable
internal data class ProfileTabItem(
    override val account: AccountType,
    val userKey: AccountType,
    override val metaData: TabMetaData,
) : AccountTabItem() {
    internal constructor(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ) : this(
        account = AccountType.Specific(accountKey),
        userKey = AccountType.Specific(userKey),
        metaData =
            TabMetaData(
                title = TitleType.Localized(TitleType.Localized.LocalizedKey.Me),
                icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Profile, accountKey),
            ),
    )

    override val key: String = "profile_${account}_$userKey"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Immutable
@Serializable
internal data object DiscoverTabItem : TabItem() {
    override val metaData: TabMetaData
        get() =
            TabMetaData(
                title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Search),
            )
    override val key: String = "discover"

    override fun update(metaData: TabMetaData): TabItem = this
}

@Serializable
internal data object SettingsTabItem : TabItem() {
    override val key: String
        get() = "settings"
    override val metaData: TabMetaData
        get() =
            TabMetaData(
                title = TitleType.Localized(TitleType.Localized.LocalizedKey.Settings),
                icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Settings),
            )

    override fun update(metaData: TabMetaData): TabItem = this
}

@Immutable
@Serializable
internal data class DirectMessageTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : AccountTabItem() {
    override val key: String = "dm_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Immutable
@Serializable
internal data object RssTabItem : TabItem() {
    override val key: String = "rss"
    override val metaData: TabMetaData
        get() =
            TabMetaData(
                title = TitleType.Localized(TitleType.Localized.LocalizedKey.Rss),
                icon = IconType.Material(dev.dimension.flare.ui.model.UiIcon.Rss),
            )

    override fun update(metaData: TabMetaData): TabItem = this
}
