package dev.dimension.flare.ui.route

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.XQT
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable

@Immutable
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

        @Serializable
        data class ShareSheet(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val shareUrl: String,
            val fxShareUrl: String? = null,
            val fixvxShareUrl: String? = null,
        ) : Status
    }

    @Serializable
    sealed interface Settings : Route {
        @Serializable
        data object Main : Settings

        @Serializable
        data object Accounts : Settings

        @Serializable
        data object AppearanceTheme : Settings

        @Serializable
        data object AppearanceLayout : Settings

        @Serializable
        data object AppearanceDisplay : Settings

        @Serializable
        data object AppearanceMedia : Settings

        @Serializable
        data object Storage : Settings

        @Serializable
        data object About : Settings

        @Serializable
        data object LocalFilter : Settings

        @Serializable
        data object LocalHistory : Settings

        @Serializable
        data object AiConfig : Settings

        @Serializable
        data object TranslationConfig : Settings

        @Serializable
        data object ColorPicker : Settings

        @Serializable
        data object AppLogging : Settings

        @Serializable
        data class NostrRelays(
            val accountKey: MicroBlogKey,
        ) : Settings

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
            val favIcon: String?,
        ) : Rss

        @Serializable
        data class Detail(
            val url: String,
            val descriptionHtml: String? = null,
            val title: String? = null,
        ) : Rss

        @Serializable
        data object Create : Rss

        @Serializable
        data class OPMLImport(
            val url: String,
        ) : Rss

        @Serializable
        data class Edit(
            val id: Int,
        ) : Rss
    }

    @Serializable
    data object Home : Route

    @Serializable
    data class Timeline(
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
    data object TabSettings : Route

    @Serializable
    data class TabGroupConfig(
        val item: MixedTimelineTabItem? = null,
    ) : Route

    @Serializable
    data object Discover : Route

    @Serializable
    data object DraftBox : Route

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
    data object Notification : Route

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
        data class Detail(
            val accountType: AccountType,
            val listId: String,
            val title: String,
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
    sealed interface Misskey : Route {
        @Serializable
        data class AntennasList(
            val accountType: AccountType,
        ) : Misskey

        @Serializable
        data class AntennaTimeline(
            val accountType: AccountType,
            val antennaId: String,
            val title: String,
        ) : Misskey

        @Serializable
        data class ChannelList(
            val accountType: AccountType,
        ) : Misskey

        @Serializable
        data class ChannelTimeline(
            val accountType: AccountType,
            val channelId: String,
            val title: String,
        ) : Misskey
    }

    @Serializable
    sealed interface Compose : Route {
        @Serializable
        data object New : Compose

        @Serializable
        data class Draft(
            val draftGroupId: String,
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

    @Serializable
    data object AccountSelection : Route

    @Serializable
    data class DeepLinkAccountPicker(
        val originalUrl: String,
        val data: ImmutableMap<MicroBlogKey, Route>,
    ) : Route

    @Serializable
    data class TwitterArticle(
        val accountType: AccountType,
        val tweetId: String,
        val articleId: String? = null,
    ) : Route

    @Serializable
    data class BlockUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    public data class UnblockUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    public data class MuteUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    public data class UnmuteUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    public data class ReportUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    companion object {
        public fun parse(url: String): Route? {
            val deeplinkRoute = DeeplinkRoute.parse(url) ?: return null
            return from(deeplinkRoute)
        }

        public fun from(deeplinkRoute: DeeplinkRoute): Route? {
            return when (deeplinkRoute) {
                is DeeplinkRoute.Timeline.XQTDeviceFollow -> {
                    Route.Timeline(
                        tabItem =
                            XQT.DeviceFollowTimelineTabItem(
                                account = deeplinkRoute.accountType,
                            ),
                    )
                }

                is DeeplinkRoute.OpenLinkDirectly -> {
                    null
                }

                is DeeplinkRoute.DeepLinkAccountPicker -> {
                    DeepLinkAccountPicker(
                        originalUrl = deeplinkRoute.originalUrl,
                        data =
                            deeplinkRoute.data
                                .mapNotNull { (key, value) ->
                                    val route = Route.from(value) ?: return@mapNotNull null
                                    key to route
                                }.toMap()
                                .toImmutableMap(),
                    )
                }

                is DeeplinkRoute.Login -> {
                    ServiceSelect.Selection
                }

                is DeeplinkRoute.Compose.New -> {
                    Compose.New
                }

                is DeeplinkRoute.Compose.Quote -> {
                    Compose.Quote(
                        accountKey = deeplinkRoute.accountKey,
                        statusKey = deeplinkRoute.statusKey,
                    )
                }

                is DeeplinkRoute.Compose.Reply -> {
                    Compose.Reply(
                        accountKey = deeplinkRoute.accountKey,
                        statusKey = deeplinkRoute.statusKey,
                    )
                }

                is DeeplinkRoute.Compose.VVOReplyComment -> {
                    Compose.VVOReplyComment(
                        accountKey = deeplinkRoute.accountKey,
                        replyTo = deeplinkRoute.replyTo,
                        rootId = deeplinkRoute.rootId,
                    )
                }

                is DeeplinkRoute.Media.Image -> {
                    Media.Image(
                        uri = deeplinkRoute.uri,
                        previewUrl = deeplinkRoute.previewUrl,
                    )
                }

                is DeeplinkRoute.Media.Podcast -> {
                    Media.Podcast(
                        accountType = deeplinkRoute.accountType,
                        id = deeplinkRoute.id,
                    )
                }

                is DeeplinkRoute.Media.StatusMedia -> {
                    Media.StatusMedia(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                        index = deeplinkRoute.index,
                        preview = deeplinkRoute.preview,
                    )
                }

                is DeeplinkRoute.Profile.User -> {
                    Profile.User(
                        accountType = deeplinkRoute.accountType,
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.Profile.UserNameWithHost -> {
                    Profile.UserNameWithHost(
                        accountType = deeplinkRoute.accountType,
                        name = deeplinkRoute.userName,
                        host = deeplinkRoute.host,
                    )
                }

                is DeeplinkRoute.Rss.Detail -> {
                    Rss.Detail(
                        url = deeplinkRoute.url,
                        descriptionHtml = deeplinkRoute.descriptionHtml,
                        title = deeplinkRoute.title,
                    )
                }

                is DeeplinkRoute.TwitterArticle -> {
                    TwitterArticle(
                        accountType = deeplinkRoute.accountType,
                        tweetId = deeplinkRoute.tweetId,
                        articleId = deeplinkRoute.articleId,
                    )
                }

                is DeeplinkRoute.Search -> {
                    Search(
                        accountType = deeplinkRoute.accountType,
                        query = deeplinkRoute.query,
                    )
                }

                is DeeplinkRoute.Status.AddReaction -> {
                    Status.AddReaction(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.AltText -> {
                    Status.AltText(
                        text = deeplinkRoute.text,
                    )
                }

                is DeeplinkRoute.Status.BlueskyReport -> {
                    Status.BlueskyReport(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.DeleteConfirm -> {
                    Status.DeleteConfirm(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.Detail -> {
                    Status.Detail(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.MastodonReport -> {
                    Status.MastodonReport(
                        userKey = deeplinkRoute.userKey,
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.MisskeyReport -> {
                    Status.MisskeyReport(
                        userKey = deeplinkRoute.userKey,
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.ShareSheet -> {
                    Status.ShareSheet(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                        shareUrl = deeplinkRoute.shareUrl,
                        fxShareUrl = deeplinkRoute.fxShareUrl,
                        fixvxShareUrl = deeplinkRoute.fixvxShareUrl,
                    )
                }

                is DeeplinkRoute.Status.VVOComment -> {
                    Status.VVOComment(
                        commentKey = deeplinkRoute.commentKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.VVOStatus -> {
                    Status.VVOStatus(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.BlockUser -> {
                    Route.BlockUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.UnblockUser -> {
                    Route.UnblockUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.DirectMessage -> {
                    DM.UserConversation(
                        accountType = AccountType.Specific(deeplinkRoute.accountKey),
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.EditUserList -> {
                    Lists.EditAccountList(
                        accountType = AccountType.Specific(deeplinkRoute.accountKey),
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.MuteUser -> {
                    Route.MuteUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.UnmuteUser -> {
                    Route.UnmuteUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.ReportUser -> {
                    Route.ReportUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }
            }
        }
    }
}
