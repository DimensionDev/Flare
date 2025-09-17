package dev.dimension.flare.data.model

import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import dev.dimension.flare.ui.presenter.home.MixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.list.ListTimelinePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSink
import okio.BufferedSource

@Serializable
public data class TabSettings(
    val secondaryItems: List<TabItem>? = null,
    val enableMixedTimeline: Boolean = true,
    val mainTabs: List<TimelineTabItem> = listOf(),
)

@Serializable
public sealed class TabItem {
    public abstract val metaData: TabMetaData
    public abstract val account: AccountType
    public abstract val key: String

    public abstract fun update(metaData: TabMetaData = this.metaData): TabItem
}

@Serializable
public data class TabMetaData(
    val title: TitleType,
    val icon: IconType,
)

@Serializable
public sealed class TitleType {
    @Serializable
    public data class Text(
        val content: String,
    ) : TitleType()

    @Serializable
    public data class Localized(
        val key: LocalizedKey,
    ) : TitleType() {
        @Serializable
        public enum class LocalizedKey {
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
        }
    }
}

@Serializable
public sealed class IconType {
    @Serializable
    public data class Avatar(
        val userKey: MicroBlogKey,
    ) : IconType()

    @Serializable
    public data class Url(
        val url: String,
    ) : IconType()

    @Serializable
    public data class Material(
        val icon: MaterialIcon,
    ) : IconType() {
        @Serializable
        public enum class MaterialIcon {
            Home,
            Notification,
            Search,
            Profile,
            Settings,
            Local,
            World,
            Featured,
            Bookmark,
            Heart,
            Twitter,
            Mastodon,
            Misskey,
            Bluesky,
            List,
            Feeds,
            Messages,
            Rss,
        }
    }

    @Serializable
    public data class Mixed(
        val icon: Material.MaterialIcon,
        val userKey: MicroBlogKey,
    ) : IconType()
}

@Serializable
public data class NotificationTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem() {
    override val key: String = "notification_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
public sealed class TimelineTabItem : TabItem() {
    public abstract fun createPresenter(): TimelinePresenter

    public companion object {
        public val default: ImmutableList<TabItem> =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Home),
                        ),
                ),
                NotificationTabItem(
                    account = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Notifications),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Notification),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Search),
                        ),
                ),
            )
        public val mainSidePanel: ImmutableList<TabItem> =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Home),
                        ),
                ),
                NotificationTabItem(
                    account = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Notifications),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Notification),
                        ),
                ),
                RssTabItem(
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Rss),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Rss),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Search),
                        ),
                ),
            )
        public val guest: ImmutableList<TabItem> =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Guest,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Home),
                        ),
                ),
                RssTabItem(
                    account = AccountType.Guest,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Rss),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Rss),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Guest,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Search),
                        ),
                ),
                SettingsTabItem,
            )

        public fun defaultPrimary(user: UiUserV2): ImmutableList<TabItem> =
            when (user.platformType) {
                PlatformType.Mastodon -> mastodon(user.key)
                PlatformType.Misskey -> misskey(user.key)
                PlatformType.Bluesky -> bluesky(user.key)
                PlatformType.xQt -> xqt(user.key)
                PlatformType.VVo -> vvo(user.key)
            }

        public fun defaultSecondary(user: UiUserV2): ImmutableList<TabItem> {
            val result =
                listOf(
                    RssTabItem(
                        account = AccountType.Guest,
                        metaData =
                            TabMetaData(
                                title = TitleType.Localized(TitleType.Localized.LocalizedKey.Rss),
                                icon = IconType.Material(IconType.Material.MaterialIcon.Rss),
                            ),
                    ),
                ) +
                    when (user.platformType) {
                        PlatformType.Mastodon -> defaultMastodonSecondaryItems(user.key)
                        PlatformType.Misskey -> defaultMisskeySecondaryItems(user.key)
                        PlatformType.Bluesky -> defaultBlueskySecondaryItems(user.key)
                        PlatformType.xQt -> defaultXqtSecondaryItems(user.key)
                        PlatformType.VVo -> defaultVVOSecondaryItems(user.key)
                    }
            return result.toImmutableList()
        }

        public fun secondaryFor(user: UiUserV2): ImmutableList<TabItem> =
            when (user.platformType) {
                PlatformType.Mastodon -> defaultMastodonSecondaryItems(user.key)
                PlatformType.Misskey -> defaultMisskeySecondaryItems(user.key)
                PlatformType.Bluesky -> defaultBlueskySecondaryItems(user.key)
                PlatformType.xQt -> defaultXqtSecondaryItems(user.key)
                PlatformType.VVo -> defaultVVOSecondaryItems(user.key)
            }

        private fun mastodon(accountKey: MicroBlogKey) =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                NotificationTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Notifications),
                            icon =
                                IconType.Mixed(
                                    IconType.Material.MaterialIcon.Notification,
                                    accountKey,
                                ),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
                ProfileTabItem(
                    accountKey = accountKey,
                    userKey = accountKey,
                ),
            )

        private fun defaultMastodonSecondaryItems(accountKey: MicroBlogKey) =
            persistentListOf(
                Mastodon.LocalTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonLocal),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Local, accountKey),
                        ),
                ),
                Mastodon.PublicTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonPublic),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.World, accountKey),
                        ),
                ),
                Mastodon.BookmarkTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Bookmark),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Bookmark, accountKey),
                        ),
                ),
                Mastodon.FavouriteTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Favourite),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Heart, accountKey),
                        ),
                ),
                AllListTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.List),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.List, accountKey),
                        ),
                ),
            )

        private fun misskey(accountKey: MicroBlogKey) =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                NotificationTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Notifications),
                            icon =
                                IconType.Mixed(
                                    IconType.Material.MaterialIcon.Notification,
                                    accountKey,
                                ),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
                ProfileTabItem(
                    accountKey = accountKey,
                    userKey = accountKey,
                ),
            )

        private fun defaultMisskeySecondaryItems(accountKey: MicroBlogKey) =
            persistentListOf(
                Misskey.FavouriteTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Favourite),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Heart, accountKey),
                        ),
                ),
                AllListTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.List),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.List, accountKey),
                        ),
                ),
                Misskey.HybridTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Social),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Featured, accountKey),
                        ),
                ),
                Misskey.LocalTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonLocal),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Local, accountKey),
                        ),
                ),
                Misskey.GlobalTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonPublic),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.World, accountKey),
                        ),
                ),
                Misskey.AntennasListTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Antenna),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Rss, accountKey),
                        ),
                ),
            )

        private fun bluesky(accountKey: MicroBlogKey) =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                NotificationTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Notifications),
                            icon =
                                IconType.Mixed(
                                    IconType.Material.MaterialIcon.Notification,
                                    accountKey,
                                ),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
            )

        private fun defaultBlueskySecondaryItems(accountKey: MicroBlogKey) =
            persistentListOf(
                AllListTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.List),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.List, accountKey),
                        ),
                ),
                Bluesky.FeedsTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Feeds),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Feeds, accountKey),
                        ),
                ),
                Bluesky.BookmarkTimelineTabItem(
                    accountType = AccountType.Specific(accountKey),
                ),
                DirectMessageTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.DirectMessage),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Messages, accountKey),
                        ),
                ),
            )

        private fun xqt(accountKey: MicroBlogKey) =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                NotificationTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Notifications),
                            icon =
                                IconType.Mixed(
                                    IconType.Material.MaterialIcon.Notification,
                                    accountKey,
                                ),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
                ProfileTabItem(
                    accountKey = accountKey,
                    userKey = accountKey,
                ),
            )

        private fun defaultXqtSecondaryItems(accountKey: MicroBlogKey) =
            persistentListOf(
                XQT.FeaturedTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Featured),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Featured, accountKey),
                        ),
                ),
                XQT.BookmarkTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Bookmark),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Bookmark, accountKey),
                        ),
                ),
                AllListTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.List),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.List, accountKey),
                        ),
                ),
                DirectMessageTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.DirectMessage),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Messages, accountKey),
                        ),
                ),
            )

        private fun vvo(accountKey: MicroBlogKey) =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                NotificationTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Notifications),
                            icon =
                                IconType.Mixed(
                                    IconType.Material.MaterialIcon.Notification,
                                    accountKey,
                                ),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Discover),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
                ProfileTabItem(
                    accountKey = accountKey,
                    userKey = accountKey,
                ),
            )

        private fun defaultVVOSecondaryItems(accountKey: MicroBlogKey) = persistentListOf<TimelineTabItem>()
    }
}

@Serializable
public data class HomeTimelineTabItem(
    override val metaData: TabMetaData,
    override val account: AccountType,
) : TimelineTabItem() {
    override val key: String = "home_$account"

    override fun createPresenter(): TimelinePresenter = HomeTimelinePresenter(account)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    public constructor(accountType: AccountType) :
        this(
            account = accountType,
            metaData =
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                    icon = IconType.Material(IconType.Material.MaterialIcon.Home),
                ),
        )

    public constructor(accountKey: MicroBlogKey, icon: String, title: String) :
        this(
            account = AccountType.Specific(accountKey),
            metaData =
                TabMetaData(
                    title = TitleType.Text(title),
                    icon = IconType.Url(icon),
                ),
        )
}

@Serializable
public data class MixedTimelineTabItem(
    val subTimelineTabItem: List<TimelineTabItem>,
    override val metaData: TabMetaData =
        TabMetaData(
            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MixedTimeline),
            icon = IconType.Material(IconType.Material.MaterialIcon.Rss),
        ),
) : TimelineTabItem() {
    override fun createPresenter(): TimelinePresenter = MixedTimelinePresenter(subTimelineTabItem.map { it.createPresenter() })

    override val account: AccountType
        get() = AccountType.Guest
    override val key: String
        get() =
            buildString {
                append("mixed_timeline")
                subTimelineTabItem.forEach { item ->
                    append(item.key)
                }
            }

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
public data class ListTimelineTabItem(
    override val account: AccountType,
    val listId: String,
    override val metaData: TabMetaData,
) : TimelineTabItem() {
    override val key: String = "list_${account}_$listId"

    override fun createPresenter(): TimelinePresenter = ListTimelinePresenter(account, listId)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
public data class AllListTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem() {
    override val key: String = "list_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

public object Mastodon {
    @Serializable
    public data class LocalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "local_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.mastodon
                .MastodonLocalTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class PublicTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "public_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.mastodon
                .MastodonPublicTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.mastodon
                .MastodonBookmarkTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class FavouriteTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "favourite_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.mastodon
                .MastodonFavouriteTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

public object Misskey {
    @Serializable
    public data class LocalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "local_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.misskey
                .MissKeyLocalTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class GlobalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "global_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.misskey
                .MissKeyPublicTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class HybridTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "hybrid_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.misskey
                .MisskeyHybridTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class FavouriteTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "favourite_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.misskey
                .MisskeyFavouriteTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class AntennasListTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TabItem() {
        override val key: String = "antennas_$account"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class AntennasTimelineTabItem(
        val id: String,
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "antennas_${account}_$id"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.list
                .AntennasTimelinePresenter(account, id)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

public object XQT {
    @Serializable
    public data class FeaturedTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "featured_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.xqt
                .XQTFeaturedTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.xqt
                .XQTBookmarkTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

public object Bluesky {
    @Serializable
    public data class FeedsTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TabItem() {
        override val key: String = "feeds_$account"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class FeedTabItem(
        override val account: AccountType,
        val uri: String,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.bluesky
                .BlueskyFeedTimelinePresenter(account, uri)

        override val key: String = "feed_${account}_$uri"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    public data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem() {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.bluesky
                .BlueskyBookmarkTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)

        public constructor(accountType: AccountType) :
            this(
                account = accountType,
                metaData =
                    TabMetaData(
                        title = TitleType.Localized(TitleType.Localized.LocalizedKey.Bookmark),
                        icon = IconType.Material(IconType.Material.MaterialIcon.Bookmark),
                    ),
            )
    }
}

@Serializable
public data class RssTimelineTabItem(
    val feedUrl: String,
    override val metaData: TabMetaData,
) : TimelineTabItem() {
    // This is a special case for RSS feeds, which are not tied to a specific account.
    override val account: AccountType = AccountType.Guest
    override val key: String = "rss_$feedUrl"

    override fun createPresenter(): TimelinePresenter =
        dev.dimension.flare.ui.presenter.home.rss
            .RssTimelinePresenter(feedUrl)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)

    public constructor(data: UiRssSource) : this(
        data.url,
        metaData =
            TabMetaData(
                title = TitleType.Text(data.title ?: data.url),
                icon = IconType.Url(data.favIcon),
            ),
    )

    public constructor(
        feedUrl: String,
        title: String,
    ) : this(
        feedUrl,
        metaData =
            TabMetaData(
                title = TitleType.Text(title),
                icon = IconType.Url(UiRssSource.favIconUrl(feedUrl)),
            ),
    )
}

@Serializable
public data class ProfileTabItem(
    override val account: AccountType,
    val userKey: AccountType,
    override val metaData: TabMetaData,
) : TabItem() {
    public constructor(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ) : this(
        account = AccountType.Specific(accountKey),
        userKey = AccountType.Specific(userKey),
        metaData =
            TabMetaData(
                title = TitleType.Localized(TitleType.Localized.LocalizedKey.Me),
                icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
            ),
    )

    override val key: String = "profile_${account}_$userKey"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
public data class DiscoverTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem() {
    override val key: String = "discover_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
public data object SettingsTabItem : TabItem() {
    override val account: AccountType
        get() = AccountType.Active
    override val key: String
        get() = "settings"
    override val metaData: TabMetaData
        get() =
            TabMetaData(
                title = TitleType.Localized(TitleType.Localized.LocalizedKey.Settings),
                icon = IconType.Material(IconType.Material.MaterialIcon.Settings),
            )

    override fun update(metaData: TabMetaData): TabItem = this
}

@Serializable
public data class DirectMessageTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem() {
    override val key: String = "dm_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
public data class RssTabItem(
    override val metaData: TabMetaData,
    override val account: AccountType = AccountType.Active,
) : TabItem() {
    override val key: String = "rss"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@OptIn(ExperimentalSerializationApi::class)
public object TabSettingsSerializer : OkioSerializer<TabSettings> {
    override val defaultValue: TabSettings
        get() = TabSettings()

    override suspend fun readFrom(source: BufferedSource): TabSettings =
        try {
            withContext(Dispatchers.IO) {
                ProtoBuf.decodeFromByteArray(source.readByteArray())
            }
        } catch (e: SerializationException) {
            throw androidx.datastore.core.CorruptionException("Cannot read proto.", e)
        }

    override suspend fun writeTo(
        t: TabSettings,
        sink: BufferedSink,
    ) {
        withContext(Dispatchers.IO) {
            sink.write(ProtoBuf.encodeToByteArray(t))
        }
    }
}
