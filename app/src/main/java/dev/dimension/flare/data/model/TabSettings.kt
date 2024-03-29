package dev.dimension.flare.data.model

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class TabSettings(
    val items: List<TabItem> = TimelineTabItem.default,
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
    data class Localized(val resId: Int) : TitleType
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
            Settings, ;

            fun toIcon(): ImageVector {
                return when (this) {
                    Home -> Icons.Default.Home
                    Notification -> Icons.Default.Notifications
                    Search -> Icons.Default.Search
                    Profile -> Icons.Default.AccountCircle
                    Settings -> Icons.Default.Settings
                }
            }
        }
    }

    @Serializable
    data class Mixed(val icon: Material.MaterialIcon, val userKey: MicroBlogKey) : IconType
}

@Serializable
data class TimelineTabItem(
    override val account: AccountType,
    val type: Type,
    override val metaData: TabMetaData,
) : TabItem {
    override val key: String = "timeline_${account}_$type"

    @Serializable
    sealed interface Type {
        @Serializable
        data object Home : Type

        @Serializable
        data object Notifications : Type
    }

    companion object {
        val default =
            persistentListOf(
                TimelineTabItem(
                    account = AccountType.Active,
                    type = Type.Home,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_home_title),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Home),
                        ),
                ),
                TimelineTabItem(
                    account = AccountType.Active,
                    type = Type.Notifications,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_notifications_title),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Notification),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_discover_title),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Search),
                        ),
                ),
                ProfileTabItem(
                    account = AccountType.Active,
                    userKey = AccountType.Active,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_me_title),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Profile),
                        ),
                ),
            )
        val guest =
            persistentListOf(
                TimelineTabItem(
                    account = AccountType.Guest,
                    type = Type.Home,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_home_title),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Home),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Guest,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_discover_title),
                            icon = IconType.Material(IconType.Material.MaterialIcon.Search),
                        ),
                ),
                SettingsTabItem,
            )

        fun mastodon(accountKey: MicroBlogKey) =
            persistentListOf(
                TimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    type = Type.Home,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_home_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                TimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    type = Type.Notifications,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_notifications_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Notification, accountKey),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_discover_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
//            TimelineTabItem(
//                account = AccountType.Specific(accountKey),
//                type = "local",
//                metaData = TabMetaData(
//                    title = TitleType.Localized(R.string.home_tab_local_title),
//                    icon = IconType.Material(IconType.Material.MaterialIcon.Search),
//                ),
//            ),
//            TimelineTabItem(
//                account = AccountType.Specific(accountKey),
//                type = "federated",
//                metaData = TabMetaData(
//                    title = TitleType.Localized(R.string.home_tab_federated_title),
//                    icon = IconType.Material(IconType.Material.MaterialIcon.Search),
//                ),
//            ),
                ProfileTabItem(
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_me_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
                ),
            )

        fun misskey(accountKey: MicroBlogKey) =
            persistentListOf(
                TimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    type = Type.Home,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_home_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                TimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    type = Type.Notifications,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_notifications_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Notification, accountKey),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_discover_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
                ProfileTabItem(
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_me_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
                ),
            )

        fun bluesky(accountKey: MicroBlogKey) =
            persistentListOf(
                TimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    type = Type.Home,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_home_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                TimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    type = Type.Notifications,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_notifications_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Notification, accountKey),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_discover_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
                ProfileTabItem(
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_me_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
                ),
            )

        fun xqt(accountKey: MicroBlogKey) =
            listOf(
                TimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    type = Type.Home,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_home_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Home, accountKey),
                        ),
                ),
                TimelineTabItem(
                    account = AccountType.Specific(accountKey),
                    type = Type.Notifications,
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_notifications_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Notification, accountKey),
                        ),
                ),
                DiscoverTabItem(
                    account = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_discover_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Search, accountKey),
                        ),
                ),
                ProfileTabItem(
                    account = AccountType.Specific(accountKey),
                    userKey = AccountType.Specific(accountKey),
                    metaData =
                        TabMetaData(
                            title = TitleType.Localized(R.string.home_tab_me_title),
                            icon = IconType.Mixed(IconType.Material.MaterialIcon.Profile, accountKey),
                        ),
                ),
            )
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
                title = TitleType.Localized(R.string.settings_title),
                icon = IconType.Material(IconType.Material.MaterialIcon.Settings),
            )
}

@OptIn(ExperimentalSerializationApi::class)
private object TabSettingsSerializer : Serializer<TabSettings> {
    override suspend fun readFrom(input: InputStream): TabSettings {
        return ProtoBuf.decodeFromByteArray(input.readBytes())
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
)
