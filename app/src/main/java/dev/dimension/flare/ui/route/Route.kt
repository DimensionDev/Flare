package dev.dimension.flare.ui.route

import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Route : NavKey {
    @Serializable
    sealed interface Status : Route {
        @Serializable
        data class Detail(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class VVOComment(
            val commentKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class VVOStatus(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class AddReaction(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class AltText(
            val text: String,
        ) : Status

        @Serializable
        data class BlueskyReport(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class DeleteConfirm(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class MastodonReport(
            val userKey: MicroBlogKey,
            val statusKey: MicroBlogKey?,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class MisskeyReport(
            val userKey: MicroBlogKey,
            val statusKey: MicroBlogKey?,
            val accountType: AccountType,
        ) : Status
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
    }

    @Serializable
    sealed interface Rss : Route {
        @Serializable
        data object Sources : Rss

        @Serializable
        data class Timeline(
            val id: Int,
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
        val accountType: AccountType,
    ) : Route

    @Serializable
    data class Timeline(
        val accountType: AccountType,
        val tabItem: TimelineTabItem,
    ) : Route

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
    data class TabSettings(
        val accountType: AccountType,
    ) : Route

    @Serializable
    data class Discover(
        val accountType: AccountType,
    ) : Route

    @Serializable
    sealed interface Profile : Route {
        @Serializable
        data class User(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile

        @Serializable
        data class UserNameWithHost(
            val accountType: AccountType,
            val name: String,
            val host: String,
        ) : Profile

        @Serializable
        data class Following(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile

        @Serializable
        data class Fans(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile

        @Serializable
        data class Me(
            val accountType: AccountType,
        ) : Profile
    }

    @Serializable
    data class Notification(
        val accountType: AccountType,
    ) : Route

    @Serializable
    data class Search(
        val accountType: AccountType,
        val query: String,
    ) : Route

    @Serializable
    sealed interface Lists : Route {
        @Serializable
        data class List(
            val accountType: AccountType,
        ) : Lists

        @Serializable
        data class Create(
            val accountType: AccountType,
        ) : Lists

        @Serializable
        data class Edit(
            val accountType: AccountType,
            val listId: String,
        ) : Lists

        @Serializable
        data class EditMember(
            val accountType: AccountType,
            val listId: String,
        ) : Lists

        @Serializable
        data class EditAccountList(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Lists

        @Serializable
        data class Delete(
            val accountType: AccountType,
            val listId: String,
            val title: String?,
        ) : Lists
    }

    @Serializable
    sealed interface DM : Route {
        @Serializable
        data class List(
            val accountType: AccountType,
        ) : DM

        @Serializable
        data class Conversation(
            val accountType: AccountType,
            val roomKey: MicroBlogKey,
        ) : DM

        @Serializable
        data class UserConversation(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : DM
    }

    @Serializable
    sealed interface Bluesky : Route {
        @Serializable
        data class Feed(
            val accountType: AccountType,
        ) : Bluesky

        @Serializable
        data class FeedDetail(
            val accountType: AccountType,
            val feedId: String,
        ) : Bluesky
    }

    @Serializable
    sealed interface Compose : Route {
        @Serializable
        data class New(
            val accountType: AccountType,
        ) : Compose

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
            val accountType: AccountType,
            val index: Int,
            val preview: String?,
        ) : Media

        @Serializable
        data class Podcast(
            val accountType: AccountType,
            val id: String,
        ) : Media
    }

    companion object {
        public fun parse(url: String): Route? {
            val data = Url(url)
            return when (data.host) {
                "Callback" ->
                    when (data.segments.getOrNull(0)) {
                        "SignIn" ->
                            when (data.segments.getOrNull(1)) {
//                                "Mastodon" -> Route.Callback.Mastodon
//                                "Misskey" -> Route.Callback.Misskey
                                else -> null
                            }

                        else -> null
                    }

                "Search" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val keyword = data.segments.getOrNull(0) ?: return null
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.Search(accountType, keyword)
                }

                "Profile" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.Profile.User(accountType, userKey)
                }

                "ProfileWithNameAndHost" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val userName = data.segments.getOrNull(0) ?: return null
                    val host = data.segments.getOrNull(1) ?: return null
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.Profile.UserNameWithHost(accountType, userName, host)
                }

                "StatusDetail" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.Status.Detail(statusKey, accountType)
                }

                "Compose" ->
                    when (data.segments.getOrNull(0)) {
                        "Reply" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Route.Compose.Reply(accountKey, statusKey)
                        }

                        "Quote" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Route.Compose.Quote(accountKey, statusKey)
                        }

                        "New" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            Route.Compose.New(AccountType.Specific(accountKey))
                        }

                        else -> null
                    }

                "RawImage" -> {
                    val rawImage = data.segments.getOrNull(0) ?: return null
                    Route.Media.Image(rawImage, previewUrl = null)
                }

                "VVO" ->
                    when (data.segments.getOrNull(0)) {
                        "StatusDetail" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Route.Status.VVOStatus(statusKey, AccountType.Specific(accountKey))
                        }

                        "CommentDetail" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Route.Status.VVOComment(statusKey, AccountType.Specific(accountKey))
                        }

                        "ReplyToComment" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val replyTo = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            val rootId = data.segments.getOrNull(3) ?: return null
                            Route.Compose.VVOReplyComment(accountKey, replyTo, rootId)
                        }

                        else -> null
                    }

                "DeleteStatus" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                    Route.Status.DeleteConfirm(statusKey, AccountType.Specific(accountKey))
                }

                "AddReaction" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                    Route.Status.AddReaction(statusKey, AccountType.Specific(accountKey))
                }

                "Bluesky" ->
                    when (data.segments.getOrNull(0)) {
                        "ReportStatus" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Route.Status.BlueskyReport(statusKey, AccountType.Specific(accountKey))
                        }

                        else -> null
                    }

                "Mastodon" ->
                    when (data.segments.getOrNull(0)) {
                        "ReportStatus" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(3) ?: return null)
                            Route.Status.MastodonReport(
                                statusKey = statusKey,
                                userKey = userKey,
                                accountType = AccountType.Specific(accountKey),
                            )
                        }

                        else -> null
                    }

                "Misskey" ->
                    when (data.segments.getOrNull(0)) {
                        "ReportStatus" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(3) ?: return null)
                            Route.Status.MisskeyReport(
                                accountType = AccountType.Specific(accountKey),
                                statusKey = statusKey,
                                userKey = userKey,
                            )
                        }

                        else -> null
                    }

                "StatusMedia" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val index = data.segments.getOrNull(1)?.toIntOrNull() ?: return null
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Route.Media.StatusMedia(accountType = accountType, statusKey = statusKey, index = index, preview = null)
                }

                "Podcast" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val id = data.segments.getOrNull(1) ?: return null
                    val accountType = accountKey.let { AccountType.Specific(it) }
                    Route.Media.Podcast(accountType = accountType, id = id)
                }

                "AltText" -> {
                    val text = data.segments.getOrNull(0) ?: return null
                    Route.Status.AltText(text)
                }

                else -> null
            }
        }
    }
}
