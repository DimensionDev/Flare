package dev.dimension.flare.ui.route

import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Route : NavKey {
    @Serializable
    sealed interface WithAccountType : Route {
        val accountType: AccountType
    }

    @Serializable
    sealed interface Status : Route {
        @Serializable
        data class Detail(
            val statusKey: MicroBlogKey,
            override val accountType: AccountType,
        ) : Status,
            WithAccountType

        @Serializable
        data class VVOComment(
            val commentKey: MicroBlogKey,
            override val accountType: AccountType,
        ) : Status,
            WithAccountType

        @Serializable
        data class VVOStatus(
            val statusKey: MicroBlogKey,
            override val accountType: AccountType,
        ) : Status,
            WithAccountType

        @Serializable
        data class AddReaction(
            val statusKey: MicroBlogKey,
            override val accountType: AccountType,
        ) : Status,
            WithAccountType

        @Serializable
        data class AltText(
            val text: String,
        ) : Status

        @Serializable
        data class BlueskyReport(
            val statusKey: MicroBlogKey,
            override val accountType: AccountType,
        ) : Status,
            WithAccountType

        @Serializable
        data class DeleteConfirm(
            val statusKey: MicroBlogKey,
            override val accountType: AccountType,
        ) : Status,
            WithAccountType

        @Serializable
        data class MastodonReport(
            val userKey: MicroBlogKey,
            val statusKey: MicroBlogKey?,
            override val accountType: AccountType,
        ) : Status,
            WithAccountType

        @Serializable
        data class MisskeyReport(
            val userKey: MicroBlogKey,
            val statusKey: MicroBlogKey?,
            override val accountType: AccountType,
        ) : Status,
            WithAccountType
    }

    @Serializable
    sealed interface Settings : Route {
        @Serializable
        data object Main : Settings

        @Serializable
        data object Accounts : Settings

        @Serializable
        data object Appearance : Settings

        @Serializable
        data object Storage : Settings

        @Serializable
        data object About : Settings

        @Serializable
        data object TabCustomization : Settings

        @Serializable
        data object LocalFilter : Settings

        @Serializable
        data object GuestSetting : Settings

        @Serializable
        data object LocalHistory : Settings

        @Serializable
        data object AiConfig : Settings

        @Serializable
        data object ColorPicker : Settings

        @Serializable
        data object AppLogging : Settings

        @Serializable
        data class LocalFilterEdit(
            val keyword: String?,
        ) : Settings

        @Serializable
        data object ColorSpace : Settings
    }

    @Serializable
    sealed interface Rss : Route {
        @Serializable
        data object Sources : Rss

        @Serializable
        data class Timeline(
            val id: Int,
            val url: String,
            val title: String?,
        ) : Rss

        @Serializable
        data class Detail(
            val url: String,
        ) : Rss

        @Serializable
        data object Create : Rss

        @Serializable
        data class Edit(
            val id: Int,
        ) : Rss
    }

    @Serializable
    data class Home(
        override val accountType: AccountType,
    ) : Route,
        WithAccountType

    @Serializable
    data class Timeline(
        override val accountType: AccountType,
        val tabItem: TimelineTabItem,
    ) : Route,
        WithAccountType

    @Serializable
    sealed interface ServiceSelect : Route {
        @Serializable
        data object Selection : ServiceSelect

        @Serializable
        data object VVOLogin : ServiceSelect

        @Serializable
        data object XQTLogin : ServiceSelect
    }

    @Serializable
    data object TabSettings : Route

    @Serializable
    data class Discover(
        override val accountType: AccountType,
    ) : Route,
        WithAccountType

    @Serializable
    sealed interface Profile : Route {
        @Serializable
        data class User(
            override val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile,
            WithAccountType

        @Serializable
        data class UserNameWithHost(
            override val accountType: AccountType,
            val name: String,
            val host: String,
        ) : Profile,
            WithAccountType

        @Serializable
        data class Following(
            override val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile,
            WithAccountType

        @Serializable
        data class Fans(
            override val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile,
            WithAccountType

        @Serializable
        data class Me(
            override val accountType: AccountType,
        ) : Profile,
            WithAccountType
    }

    @Serializable
    data class Notification(
        override val accountType: AccountType,
    ) : Route,
        WithAccountType

    @Serializable
    data class Search(
        override val accountType: AccountType,
        val query: String,
    ) : Route,
        WithAccountType

    @Serializable
    sealed interface Lists : Route {
        @Serializable
        data class List(
            override val accountType: AccountType,
        ) : Lists,
            WithAccountType

        @Serializable
        data class Detail(
            override val accountType: AccountType,
            val listId: String,
            val title: String,
        ) : Lists,
            WithAccountType

        @Serializable
        data class Create(
            override val accountType: AccountType,
        ) : Lists,
            WithAccountType

        @Serializable
        data class Edit(
            override val accountType: AccountType,
            val listId: String,
        ) : Lists,
            WithAccountType

        @Serializable
        data class EditMember(
            override val accountType: AccountType,
            val listId: String,
        ) : Lists,
            WithAccountType

        @Serializable
        data class EditAccountList(
            override val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Lists,
            WithAccountType

        @Serializable
        data class Delete(
            override val accountType: AccountType,
            val listId: String,
            val title: String?,
        ) : Lists,
            WithAccountType
    }

    @Serializable
    sealed interface DM : Route {
        @Serializable
        data class List(
            override val accountType: AccountType,
        ) : DM,
            WithAccountType

        @Serializable
        data class Conversation(
            override val accountType: AccountType,
            val roomKey: MicroBlogKey,
        ) : DM,
            WithAccountType

        @Serializable
        data class UserConversation(
            override val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : DM,
            WithAccountType
    }

    @Serializable
    sealed interface Bluesky : Route {
        @Serializable
        data class Feed(
            override val accountType: AccountType,
        ) : Bluesky,
            WithAccountType

        @Serializable
        data class FeedDetail(
            override val accountType: AccountType,
            val feedId: String,
        ) : Bluesky,
            WithAccountType
    }

    @Serializable
    sealed interface Misskey : Route {
        @Serializable
        data class AntennasList(
            override val accountType: AccountType,
        ) : Misskey,
            WithAccountType

        @Serializable
        data class AntennaTimeline(
            override val accountType: AccountType,
            val antennaId: String,
            val title: String,
        ) : Misskey,
            WithAccountType
    }

    @Serializable
    sealed interface Compose : Route {
        @Serializable
        data class New(
            override val accountType: AccountType,
        ) : Compose,
            WithAccountType

        @Serializable
        data class Reply(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose

        @Serializable
        data class Quote(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose

        @Serializable
        data class VVOReplyComment(
            val accountKey: MicroBlogKey,
            val replyTo: MicroBlogKey,
            val rootId: String,
        ) : Compose
    }

    @Serializable
    sealed interface Media : Route {
        @Serializable
        data class Image(
            val uri: String,
            val previewUrl: String?,
        ) : Media

        @Serializable
        data class StatusMedia(
            val statusKey: MicroBlogKey,
            override val accountType: AccountType,
            val index: Int,
            val preview: String?,
        ) : Media,
            WithAccountType

        @Serializable
        data class Podcast(
            override val accountType: AccountType,
            val id: String,
        ) : Media,
            WithAccountType
    }

    @Serializable
    data object AccountSelection : Route

    companion object {
        public fun parse(url: String): Route? {
            val deeplinkRoute = DeeplinkRoute.parse(url) ?: return null
            return when (deeplinkRoute) {
                is DeeplinkRoute.Login -> Route.ServiceSelect.Selection
                is DeeplinkRoute.Callback -> null
                is DeeplinkRoute.Compose.New ->
                    Route.Compose.New(accountType = deeplinkRoute.accountType)
                is DeeplinkRoute.Compose.Quote ->
                    Route.Compose.Quote(
                        accountKey = deeplinkRoute.accountKey,
                        statusKey = deeplinkRoute.statusKey,
                    )
                is DeeplinkRoute.Compose.Reply ->
                    Route.Compose.Reply(
                        accountKey = deeplinkRoute.accountKey,
                        statusKey = deeplinkRoute.statusKey,
                    )
                is DeeplinkRoute.Compose.VVOReplyComment ->
                    Route.Compose.VVOReplyComment(
                        accountKey = deeplinkRoute.accountKey,
                        replyTo = deeplinkRoute.replyTo,
                        rootId = deeplinkRoute.rootId,
                    )
                is DeeplinkRoute.Media.Image ->
                    Route.Media.Image(
                        uri = deeplinkRoute.uri,
                        previewUrl = deeplinkRoute.previewUrl,
                    )
                is DeeplinkRoute.Media.Podcast ->
                    Route.Media.Podcast(
                        accountType = deeplinkRoute.accountType,
                        id = deeplinkRoute.id,
                    )
                is DeeplinkRoute.Media.StatusMedia ->
                    Route.Media.StatusMedia(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                        index = deeplinkRoute.index,
                        preview = deeplinkRoute.preview,
                    )
                is DeeplinkRoute.Profile.User ->
                    Route.Profile.User(
                        accountType = deeplinkRoute.accountType,
                        userKey = deeplinkRoute.userKey,
                    )
                is DeeplinkRoute.Profile.UserNameWithHost ->
                    Route.Profile.UserNameWithHost(
                        accountType = deeplinkRoute.accountType,
                        name = deeplinkRoute.userName,
                        host = deeplinkRoute.host,
                    )
                is DeeplinkRoute.Rss.Detail ->
                    Route.Rss.Detail(
                        url = deeplinkRoute.url,
                    )
                is DeeplinkRoute.Search ->
                    Route.Search(
                        accountType = deeplinkRoute.accountType,
                        query = deeplinkRoute.query,
                    )
                is DeeplinkRoute.Status.AddReaction ->
                    Route.Status.AddReaction(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                is DeeplinkRoute.Status.AltText ->
                    Route.Status.AltText(
                        text = deeplinkRoute.text,
                    )
                is DeeplinkRoute.Status.BlueskyReport ->
                    Route.Status.BlueskyReport(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                is DeeplinkRoute.Status.DeleteConfirm ->
                    Route.Status.DeleteConfirm(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                is DeeplinkRoute.Status.Detail ->
                    Route.Status.Detail(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                is DeeplinkRoute.Status.MastodonReport ->
                    Route.Status.MastodonReport(
                        userKey = deeplinkRoute.userKey,
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                is DeeplinkRoute.Status.MisskeyReport ->
                    Route.Status.MisskeyReport(
                        userKey = deeplinkRoute.userKey,
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                is DeeplinkRoute.Status.VVOComment ->
                    Route.Status.VVOComment(
                        commentKey = deeplinkRoute.commentKey,
                        accountType = deeplinkRoute.accountType,
                    )
                is DeeplinkRoute.Status.VVOStatus ->
                    Route.Status.VVOStatus(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
            }
        }
    }
}

internal fun Route.accountTypeOr(default: AccountType): AccountType =
    when (this) {
        is Route.WithAccountType -> this.accountType
        else -> default
    }
