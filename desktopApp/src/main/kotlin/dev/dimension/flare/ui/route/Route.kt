package dev.dimension.flare.ui.route

import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Route {
    sealed interface FloatingRoute : Route

    sealed interface ScreenRoute : Route

    sealed interface WindowRoute : Route

    sealed interface UrlRoute : Route {
        val url: String
    }

    @Serializable
    data class Timeline(
        val tabItem: TimelineTabItem,
    ) : ScreenRoute

    @Serializable
    data class Home(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class Discover(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class Notification(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data object Settings : ScreenRoute

    @Serializable
    data class Profile(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    @Serializable
    data class ProfileWithNameAndHost(
        val accountType: AccountType,
        val userName: String,
        val host: String,
    ) : ScreenRoute

    @Serializable
    data class MeRoute(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data object ServiceSelect : ScreenRoute

    @Serializable
    data class AllLists(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class BlueskyFeeds(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class StatusDetail(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : ScreenRoute

    data object VVO {
        @Serializable
        data class StatusDetail(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : ScreenRoute

        @Serializable
        data class CommentDetail(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : ScreenRoute
    }

    @Serializable
    data class AddReaction(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class DeleteStatus(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class BlueskyReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class MastodonReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class MisskeyReport(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : FloatingRoute

    @Serializable
    data class AltText(
        val text: String,
    ) : FloatingRoute

    @Serializable
    data class Search(
        val accountType: AccountType,
        val keyword: String,
    ) : ScreenRoute

    @Serializable
    data class StatusMedia(
        val accountType: AccountType,
        val statusKey: MicroBlogKey,
        val index: Int,
        val preview: String? = null,
    ) : WindowRoute

    @Serializable
    data class RawImage(
        val rawImage: String,
    ) : WindowRoute

    data object Compose {
        @Serializable
        data class Reply(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : FloatingRoute

        @Serializable
        data class Quote(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : FloatingRoute

        @Serializable
        data class New(
            val accountType: AccountType,
        ) : FloatingRoute

        @Serializable
        data class VVOReplyComment(
            val accountKey: MicroBlogKey,
            val replyTo: MicroBlogKey,
            val rootId: String,
        ) : FloatingRoute
    }

    @Serializable
    data object RssList : ScreenRoute

    @Serializable
    data class RssTimeline(
        val id: Int,
        val url: String,
        val title: String?,
    ) : ScreenRoute

    @Serializable
    data class EditRssSource(
        val id: Int,
    ) : FloatingRoute

    @Serializable
    data object CreateRssSource : FloatingRoute

    @Serializable
    data class RssDetail(
        override val url: String,
    ) : UrlRoute

    @Serializable
    data class DmList(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data class DmConversation(
        val accountType: AccountType,
        val roomKey: MicroBlogKey,
    ) : ScreenRoute

    @Serializable
    data class DmUserConversation(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    @Serializable
    data class MisskeyAntennas(
        val accountType: AccountType,
    ) : ScreenRoute

    @Serializable
    data object TabSetting : ScreenRoute

    @Serializable
    data object LocalCache : ScreenRoute

    @Serializable
    data object StorageUsage : ScreenRoute

    @Serializable
    data class Following(
        val accountType: AccountType,
        val userKey: MicroBlogKey,
    ) : ScreenRoute

    @Serializable
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
