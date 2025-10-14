package dev.dimension.flare.ui.route

import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRssSource

internal sealed interface Route {
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

    data class Discover(
        val accountType: AccountType,
    ) : ScreenRoute

    data class Notification(
        val accountType: AccountType,
    ) : ScreenRoute

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

    data object LocalCache : ScreenRoute

    data object StorageUsage : ScreenRoute

    data class Following(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    data class Fans(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    companion object {
        public fun parse(url: String): Route? {
            val deeplinkRoute = DeeplinkRoute.parse(url) ?: return null
            return when (deeplinkRoute) {
                is DeeplinkRoute.Login -> ServiceSelect
                is DeeplinkRoute.Callback -> null
                is DeeplinkRoute.Compose.New -> Compose.New(deeplinkRoute.accountType)
                is DeeplinkRoute.Compose.Quote ->
                    Compose.Quote(
                        deeplinkRoute.accountKey,
                        deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.Compose.Reply ->
                    Compose.Reply(
                        deeplinkRoute.accountKey,
                        deeplinkRoute.statusKey,
                    )

                is DeeplinkRoute.Compose.VVOReplyComment ->
                    Compose.VVOReplyComment(
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
                    VVO.CommentDetail(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.commentKey,
                    )

                is DeeplinkRoute.Status.VVOStatus ->
                    VVO.StatusDetail(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey,
                    )
            }
        }
    }
}
