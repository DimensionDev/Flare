package dev.dimension.flare.data.model

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Bluesky
import compose.icons.fontawesomeicons.brands.Mastodon
import compose.icons.fontawesomeicons.brands.Twitter
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.BookBookmark
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.RectangleList
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.Star
import compose.icons.fontawesomeicons.solid.Users
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.icons.Misskey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.list.ListTimelinePresenter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class TabSettings(
    val items: List<TabItem> = TimelineTabItem.default,
    val secondaryItems: List<TabItem>? = null,
    val homeTabs: Map<MicroBlogKey, List<TimelineTabItem>> = mapOf(),
)

@Serializable
sealed interface TabItem {
    val metaData: TabMetaData
    val account: AccountType
    val key: String

    fun update(metaData: TabMetaData = this.metaData): TabItem
}

@Serializable
data class TabMetaData(
    val title: TitleType,
    val icon: IconType,
)

@Serializable
sealed interface TitleType {
    @Serializable
    data class Text(
        val content: String,
    ) : TitleType

    @Serializable
    data class Localized(
        val key: LocalizedKey,
    ) : TitleType {
        val resId: Int
            get() = key.toId()

        @Serializable
        enum class LocalizedKey {
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
            Feeds, ;

            fun toId(): Int =
                when (this) {
                    Home -> R.string.home_tab_home_title
                    Notifications -> R.string.home_tab_notifications_title
                    Discover -> R.string.home_tab_discover_title
                    Me -> R.string.home_tab_me_title
                    Settings -> R.string.settings_title
                    MastodonLocal -> R.string.mastodon_tab_local_title
                    MastodonPublic -> R.string.mastodon_tab_public_title
                    Featured -> R.string.home_tab_featured_title
                    Bookmark -> R.string.home_tab_bookmarks_title
                    Favourite -> R.string.home_tab_favorite_title
                    List -> R.string.home_tab_list_title
                    Feeds -> R.string.home_tab_feeds_title
                }
        }
    }
}

@Serializable
sealed interface IconType {
    @Serializable
    data class Avatar(
        val userKey: MicroBlogKey,
    ) : IconType

    @Serializable
    data class Material(
        val icon: MaterialIcon,
    ) : IconType {
        @Serializable
        enum class MaterialIcon {
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
            ;

            fun toIcon(): ImageVector =
                when (this) {
                    Home -> FontAwesomeIcons.Solid.House
                    Notification -> FontAwesomeIcons.Solid.Bell
                    Search -> FontAwesomeIcons.Solid.MagnifyingGlass
                    Profile -> FontAwesomeIcons.Solid.CircleUser
                    Settings -> FontAwesomeIcons.Solid.Gear
                    Local -> FontAwesomeIcons.Solid.Users
                    World -> FontAwesomeIcons.Solid.Globe
                    Featured -> FontAwesomeIcons.Solid.RectangleList
                    Bookmark -> FontAwesomeIcons.Solid.BookBookmark
                    Heart -> FontAwesomeIcons.Solid.Star
                    Twitter -> FontAwesomeIcons.Brands.Twitter
                    Mastodon -> FontAwesomeIcons.Brands.Mastodon
                    Misskey -> FontAwesomeIcons.Brands.Misskey
                    Bluesky -> FontAwesomeIcons.Brands.Bluesky
                    List -> FontAwesomeIcons.Solid.List
                    Feeds -> FontAwesomeIcons.Solid.Rss
                }
        }
    }

    @Serializable
    data class Mixed(
        val icon: Material.MaterialIcon,
        val userKey: MicroBlogKey,
    ) : IconType
}

@Serializable
data class NotificationTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem {
    override val key: String = "notification_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
sealed interface TimelineTabItem : TabItem {
    fun createPresenter(): TimelinePresenter

    companion object {
        val default =
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
                ProfileTabItem(
                    account = AccountType.Active,
                    userKey = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Me),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Profile),
                        ),
                ),
            )
        val guest =
            persistentListOf(
                HomeTimelineTabItem(
                    account = AccountType.Guest,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Home),
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

        fun defaultPrimary(user: UiUserV2) =
            when (user.platformType) {
                PlatformType.Mastodon -> mastodon(user.key)
                PlatformType.Misskey -> misskey(user.key)
                PlatformType.Bluesky -> bluesky(user.key)
                PlatformType.xQt -> xqt(user.key)
                PlatformType.VVo -> vvo(user.key)
            }

        fun defaultSecondary(user: UiUserV2) =
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
                ProfileTabItem(
                    accountKey = accountKey,
                    userKey = accountKey,
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
                    account = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Feeds),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Feeds, accountKey),
                        ),
                ),
            )

        private fun xqt(accountKey: MicroBlogKey) =
            listOf(
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
            listOf(
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
            )

        private fun vvo(accountKey: MicroBlogKey) =
            listOf(
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

        private fun defaultVVOSecondaryItems(accountKey: MicroBlogKey) = emptyList<TimelineTabItem>()
    }
}

@Serializable
data class HomeTimelineTabItem(
    override val metaData: TabMetaData,
    override val account: AccountType,
) : TimelineTabItem {
    override val key: String = "home_$account"

    override fun createPresenter(): TimelinePresenter = HomeTimelinePresenter(account)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    constructor(accountType: AccountType) :
        this(
            account = accountType,
            metaData =
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Home),
                    icon = IconType.Material(IconType.Material.MaterialIcon.Home),
                ),
        )
}

@Serializable
data class ListTimelineTabItem(
    override val account: AccountType,
    val listId: String,
    override val metaData: TabMetaData,
) : TimelineTabItem {
    override val key: String = "list_${account}_$listId"

    override fun createPresenter(): TimelinePresenter = ListTimelinePresenter(account, listId)

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
data class AllListTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem {
    override val key: String = "list_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

object Mastodon {
    @Serializable
    data class LocalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "local_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.mastodon
                .LocalTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    data class PublicTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "public_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.mastodon
                .PublicTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.mastodon
                .BookmarkTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    data class FavouriteTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "favourite_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.mastodon
                .FavouriteTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

object Misskey {
    @Serializable
    data class LocalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "local_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.misskey
                .LocalTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    data class GlobalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "global_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.misskey
                .PublicTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

object XQT {
    @Serializable
    data class FeaturedTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "featured_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.xqt
                .FeaturedTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.xqt
                .BookmarkTimelinePresenter(account)

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

object Bluesky {
    @Serializable
    data class FeedsTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TabItem {
        override val key: String = "feeds_$account"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }

    @Serializable
    data class FeedTabItem(
        override val account: AccountType,
        val uri: String,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override fun createPresenter(): TimelinePresenter =
            dev.dimension.flare.ui.presenter.home.bluesky
                .BlueskyFeedTimelinePresenter(account, uri)

        override val key: String = "feed_${account}_$uri"

        override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
    }
}

@Serializable
data class ProfileTabItem(
    override val account: AccountType,
    val userKey: AccountType,
    override val metaData: TabMetaData,
) : TabItem {
    constructor(
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
data class DiscoverTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem {
    override val key: String = "discover_$account"

    override fun update(metaData: TabMetaData): TabItem = copy(metaData = metaData)
}

@Serializable
data object SettingsTabItem : TabItem {
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

@OptIn(ExperimentalSerializationApi::class)
private object TabSettingsSerializer : Serializer<TabSettings> {
    override suspend fun readFrom(input: InputStream): TabSettings =
        try {
            ProtoBuf.decodeFromByteArray(input.readBytes())
        } catch (e: SerializationException) {
            throw androidx.datastore.core.CorruptionException("Cannot read proto.", e)
        }

    override suspend fun writeTo(
        t: TabSettings,
        output: OutputStream,
    ) = withContext(Dispatchers.IO) {
        output.write(ProtoBuf.encodeToByteArray(t))
    }

    override val defaultValue: TabSettings
        get() = TabSettings()
}

internal val Context.tabSettings: DataStore<TabSettings> by dataStore(
    fileName = "tab_settings.pb",
    serializer = TabSettingsSerializer,
    corruptionHandler =
        ReplaceFileCorruptionHandler {
            TabSettingsSerializer.defaultValue
        },
)
