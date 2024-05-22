package dev.dimension.flare.data.model

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
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
)

@Serializable
sealed interface TabItem {
    val metaData: TabMetaData
    val account: AccountType
    val key: String
}

@Serializable
data class TabMetaData(
    val title: TitleType,
    val icon: IconType,
)

@Serializable
sealed interface TitleType {
    @Serializable
    data class Text(val content: String) : TitleType

    @Serializable
    data class Localized(val key: LocalizedKey) : TitleType {
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
            Favourite, ;

            fun toId(): Int {
                return when (this) {
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
                }
            }
        }
    }
}

@Serializable
sealed interface IconType {
    @Serializable
    data class Avatar(val userKey: MicroBlogKey) : IconType

    @Serializable
    data class Material(val icon: MaterialIcon) : IconType {
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
            Heart, ;

            fun toIcon(): ImageVector {
                return when (this) {
                    Home -> Icons.Default.Home
                    Notification -> Icons.Default.Notifications
                    Search -> Icons.Default.Search
                    Profile -> Icons.Default.AccountCircle
                    Settings -> Icons.Default.Settings
                    Local -> Icons.Default.Groups
                    World -> Icons.Default.Public
                    Featured -> Icons.AutoMirrored.Filled.FeaturedPlayList
                    Bookmark -> Icons.Default.Bookmarks
                    Heart -> Icons.Default.Favorite
                }
            }
        }
    }

    @Serializable
    data class Mixed(val icon: Material.MaterialIcon, val userKey: MicroBlogKey) : IconType
}

@Serializable
data class NotificationTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem {
    override val key: String = "notification_$account"
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

        fun defaultPrimary(user: UiUser) =
            when (user) {
                is UiUser.Mastodon -> mastodon(user.userKey)
                is UiUser.Misskey -> misskey(user.userKey)
                is UiUser.Bluesky -> bluesky(user.userKey)
                is UiUser.XQT -> xqt(user.userKey)
                is UiUser.VVO -> vvo(user.userKey)
            }

        fun defaultSecondary(user: UiUser) =
            when (user) {
                is UiUser.Mastodon -> defaultMastodonSecondaryItems(user.userKey)
                is UiUser.Misskey -> defaultMisskeySecondaryItems(user.userKey)
                is UiUser.Bluesky -> defaultBlueskySecondaryItems(user.userKey)
                is UiUser.XQT -> defaultXqtSecondaryItems(user.userKey)
                is UiUser.VVO -> defaultVVOSecondaryItems(user.userKey)
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
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Me),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
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
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Featured, accountKey),
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
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Me),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
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
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Me),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
                ),
            )

        private fun defaultBlueskySecondaryItems(
            @Suppress("UNUSED_PARAMETER")
            accountKey: MicroBlogKey,
        ) = persistentListOf<TabItem>()

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
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Me),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
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
                ProfileTabItem(
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(TitleType.Localized.LocalizedKey.Me),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
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

    override fun createPresenter(): TimelinePresenter {
        return HomeTimelinePresenter(account)
    }
}

object Mastodon {
    @Serializable
    data class LocalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "local_$account"

        override fun createPresenter(): TimelinePresenter {
            return dev.dimension.flare.ui.presenter.home.mastodon.LocalTimelinePresenter(account)
        }
    }

    @Serializable
    data class PublicTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "public_$account"

        override fun createPresenter(): TimelinePresenter {
            return dev.dimension.flare.ui.presenter.home.mastodon.PublicTimelinePresenter(account)
        }
    }

    @Serializable
    data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter {
            return dev.dimension.flare.ui.presenter.home.mastodon.BookmarkTimelinePresenter(account)
        }
    }

    @Serializable
    data class FavouriteTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "favourite_$account"

        override fun createPresenter(): TimelinePresenter {
            return dev.dimension.flare.ui.presenter.home.mastodon.FavouriteTimelinePresenter(account)
        }
    }
}

object Misskey {
    @Serializable
    data class LocalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "local_$account"

        override fun createPresenter(): TimelinePresenter {
            return dev.dimension.flare.ui.presenter.home.misskey.LocalTimelinePresenter(account)
        }
    }

    @Serializable
    data class GlobalTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "global_$account"

        override fun createPresenter(): TimelinePresenter {
            return dev.dimension.flare.ui.presenter.home.misskey.PublicTimelinePresenter(account)
        }
    }
}

object XQT {
    @Serializable
    data class FeaturedTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "featured_$account"

        override fun createPresenter(): TimelinePresenter {
            return dev.dimension.flare.ui.presenter.home.xqt.FeaturedTimelinePresenter(account)
        }
    }

    @Serializable
    data class BookmarkTimelineTabItem(
        override val account: AccountType,
        override val metaData: TabMetaData,
    ) : TimelineTabItem {
        override val key: String = "bookmark_$account"

        override fun createPresenter(): TimelinePresenter {
            return dev.dimension.flare.ui.presenter.home.xqt.BookmarkTimelinePresenter(account)
        }
    }
}

@Serializable
data class ProfileTabItem(
    override val account: AccountType,
    val userKey: AccountType,
    override val metaData: TabMetaData,
) : TabItem {
    override val key: String = "profile_${account}_$userKey"
}

@Serializable
data class DiscoverTabItem(
    override val account: AccountType,
    override val metaData: TabMetaData,
) : TabItem {
    override val key: String = "discover_$account"
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
}

@OptIn(ExperimentalSerializationApi::class)
private object TabSettingsSerializer : Serializer<TabSettings> {
    override suspend fun readFrom(input: InputStream): TabSettings {
        return try {
            ProtoBuf.decodeFromByteArray(input.readBytes())
        } catch (e: SerializationException) {
            throw androidx.datastore.core.CorruptionException("Cannot read proto.", e)
        }
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
