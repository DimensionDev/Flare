package dev.dimension.flare.ui.route

import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.route.Route.Compose.New
import dev.dimension.flare.ui.route.Route.Compose.Quote
import dev.dimension.flare.ui.route.Route.Compose.Reply
import dev.dimension.flare.ui.route.Route.Compose.VVOReplyComment
import dev.dimension.flare.ui.route.Route.VVO.CommentDetail
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable

internal sealed interface Route : NavKey {
    sealed interface FloatingRoute : Route

    sealed interface ScreenRoute : Route

    sealed interface WindowRoute : Route

    sealed interface UrlRoute : Route {
        val url: String
    }

    data class Timeline(
        val tabItem: TimelineTabItem,
    ) : ScreenRoute

    data class Home(
        val accountType: AccountType,
    ) : ScreenRoute

    data object Discover : ScreenRoute

    data object Notification : ScreenRoute

    data object Settings : ScreenRoute

    data class Profile(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    data class ProfileWithNameAndHost(
        val accountType: AccountType,
        val userName: String,
        val host: String,
    ) : ScreenRoute

    data class MeRoute(
        val accountType: AccountType,
    ) : ScreenRoute

    data object ServiceSelect : ScreenRoute

    data object AppLogging : ScreenRoute

    data class AllLists(
        val accountType: AccountType,
    ) : ScreenRoute

    data class BlueskyFeeds(
        val accountType: AccountType,
    ) : ScreenRoute

    data class StatusDetail(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : ScreenRoute

    data object VVO {
        data class StatusDetail(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : ScreenRoute

        data class CommentDetail(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : ScreenRoute
    }

    data class AddReaction(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    data class DeleteStatus(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    data class BlueskyReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    data class MastodonReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    data class MisskeyReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    data class AltText(
        val text: String,
    ) : FloatingRoute

    data class Search(
        val accountType: AccountType,
        val keyword: String,
    ) : ScreenRoute

    data class StatusMedia(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val index: Int,
        val preview: String? = null,
    ) : WindowRoute

    data class RawImage(
        val rawImage: String,
    ) : WindowRoute

    data object Compose {
        data class Reply(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : FloatingRoute

        data class Quote(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : FloatingRoute

        data class New(
            val accountType: AccountType,
        ) : FloatingRoute

        data class VVOReplyComment(
            val accountKey: MicroBlogKey,
            val replyTo: MicroBlogKey,
            val rootId: String,
        ) : FloatingRoute
    }

    data object RssList : ScreenRoute

    data class RssTimeline(
        val data: UiRssSource,
    ) : ScreenRoute

    data class EditRssSource(
        val id: Int,
    ) : FloatingRoute

    data object CreateRssSource : FloatingRoute

    data class ImportOPML(
        val filePath: String,
    ) : FloatingRoute

    data class RssDetail(
        override val url: String,
    ) : UrlRoute

    data class DmList(
        val accountType: AccountType,
    ) : ScreenRoute

    data class DmConversation(
        val accountType: AccountType,
        val roomKey: MicroBlogKey,
    ) : ScreenRoute

    data class DmUserConversation(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    data class MisskeyAntennas(
        val accountType: AccountType,
    ) : ScreenRoute

    data object TabSetting : ScreenRoute

    data class TabGroupConfig(
        val item: dev.dimension.flare.data.model.MixedTimelineTabItem? = null,
    ) : ScreenRoute

    data object LocalCache : ScreenRoute

    data class Following(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    data class Fans(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    data class WebViewLogin(
        val url: String,
        val cookieCallback: ((cookies: String?) -> Unit)?,
    ) : WindowRoute

    data class DeepLinkAccountPicker(
        val originalUrl: String,
        val data: ImmutableMap<MicroBlogKey, Route>,
    ) : FloatingRoute

    @Serializable
    data class BlockUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class MuteUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class ReportUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    companion object {
        public fun parse(url: String): Route? {
            val deeplinkRoute = DeeplinkRoute.parse(url) ?: return null
            return from(deeplinkRoute)
        }

        public fun from(deeplinkRoute: DeeplinkRoute): Route? {
            return when (deeplinkRoute) {
                is DeeplinkRoute.OpenLinkDirectly -> null
                is DeeplinkRoute.DeepLinkAccountPicker ->
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
                is DeeplinkRoute.Login -> ServiceSelect
                is DeeplinkRoute.Compose.New -> New(deeplinkRoute.accountType)
                is DeeplinkRoute.Compose.Quote ->
                    Quote(
                        deeplinkRoute.accountKey,
                        deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.Compose.Reply ->
                    Reply(
                        deeplinkRoute.accountKey,
                        deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.Compose.VVOReplyComment ->
                    VVOReplyComment(
                        deeplinkRoute.accountKey,
                        deeplinkRoute.replyTo,
                        deeplinkRoute.rootId,
                    )

                is DeeplinkRoute.Media.Image -> RawImage(deeplinkRoute.uri)
                is DeeplinkRoute.Media.Podcast -> null
                is DeeplinkRoute.Media.StatusMedia ->
                    StatusMedia(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey,
                        index = deeplinkRoute.index,
                        preview = deeplinkRoute.preview,
                    )

                is DeeplinkRoute.Profile.User ->
                    Profile(
                        accountType = deeplinkRoute.accountType,
                        userKey = deeplinkRoute.userKey,
                    )

                is DeeplinkRoute.Profile.UserNameWithHost ->
                    ProfileWithNameAndHost(
                        accountType = deeplinkRoute.accountType,
                        userName = deeplinkRoute.userName,
                        host = deeplinkRoute.host,
                    )

                is DeeplinkRoute.Rss.Detail -> RssDetail(url = deeplinkRoute.url)
                is DeeplinkRoute.Search ->
                    Search(
                        accountType = deeplinkRoute.accountType,
                        keyword = deeplinkRoute.query,
                    )

                is DeeplinkRoute.Status.AddReaction ->
                    AddReaction(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.Status.AltText -> AltText(text = deeplinkRoute.text)
                is DeeplinkRoute.Status.BlueskyReport ->
                    BlueskyReport(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.Status.DeleteConfirm ->
                    DeleteStatus(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.Status.Detail ->
                    StatusDetail(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.Status.MastodonReport ->
                    MastodonReport(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey ?: return null,
                        userKey = deeplinkRoute.userKey,
                    )

                is DeeplinkRoute.Status.MisskeyReport ->
                    MisskeyReport(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey ?: return null,
                        userKey = deeplinkRoute.userKey,
                    )

                is DeeplinkRoute.Status.VVOComment ->
                    CommentDetail(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.commentKey,
                    )

                is DeeplinkRoute.Status.VVOStatus ->
                    VVO.StatusDetail(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.DirectMessage ->
                    DmUserConversation(
                        accountType = AccountType.Specific(deeplinkRoute.accountKey),
                        userKey = deeplinkRoute.userKey,
                    )
                is DeeplinkRoute.EditUserList -> null
                is DeeplinkRoute.MuteUser ->
                    Route.MuteUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                is DeeplinkRoute.ReportUser ->
                    Route.ReportUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )

                is DeeplinkRoute.BlockUser ->
                    Route.BlockUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
            }
        }
    }
}
