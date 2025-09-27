package dev.dimension.flare.ui.route

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.Url
import kotlinx.serialization.Serializable

public sealed class DeeplinkRoute {
    @Serializable
    public data object Login : DeeplinkRoute()

    @Serializable
    public sealed class Callback : DeeplinkRoute() {
        @Serializable
        public object Mastodon : Callback()

        @Serializable
        public object Misskey : Callback()

        @Serializable
        public object Bluesky : Callback()
    }

    @Serializable
    public sealed class Status : DeeplinkRoute() {
        @Serializable
        public data class Detail(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class VVOComment(
            val commentKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class VVOStatus(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class AddReaction(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class AltText(
            val text: String,
        ) : Status()

        @Serializable
        public data class BlueskyReport(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class DeleteConfirm(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class MastodonReport(
            val userKey: MicroBlogKey,
            val statusKey: MicroBlogKey?,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class MisskeyReport(
            val userKey: MicroBlogKey,
            val statusKey: MicroBlogKey?,
            val accountType: AccountType,
        ) : Status()
    }

    @Serializable
    public sealed class Rss : DeeplinkRoute() {
        @Serializable
        public data class Detail(
            val url: String,
        ) : Rss()
    }

    @Serializable
    public data class Search(
        val accountType: AccountType,
        val query: String,
    ) : DeeplinkRoute()

    @Serializable
    public sealed class Compose : DeeplinkRoute() {
        @Serializable
        public data class New(
            val accountType: AccountType,
        ) : Compose()

        @Serializable
        public data class Reply(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose()

        @Serializable
        public data class Quote(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose()

        @Serializable
        public data class VVOReplyComment(
            val accountKey: MicroBlogKey,
            val replyTo: MicroBlogKey,
            val rootId: String,
        ) : Compose()
    }

    @Serializable
    public sealed class Media : DeeplinkRoute() {
        @Serializable
        public data class Image(
            val uri: String,
            val previewUrl: String?,
        ) : Media()

        @Serializable
        public data class StatusMedia(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val index: Int,
            val preview: String?,
        ) : Media()

        @Serializable
        public data class Podcast(
            val accountType: AccountType,
            val id: String,
        ) : Media()
    }

    @Serializable
    public sealed class Profile : DeeplinkRoute() {
        @Serializable
        public data class User(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile()

        @Serializable
        public data class UserNameWithHost(
            val accountType: AccountType,
            val userName: String,
            val host: String,
        ) : Profile()
    }

    public companion object Companion {
        public fun parse(url: String): DeeplinkRoute? {
            val data = Url(url)
            return when (data.host) {
                "Login" -> Login
                "Callback" ->
                    when (data.segments.getOrNull(0)) {
                        "SignIn" ->
                            when (data.segments.getOrNull(1)) {
                                "Mastodon" -> Callback.Mastodon
                                "Misskey" -> Callback.Misskey
                                "Bluesky" -> Callback.Bluesky
                                else -> null
                            }

                        else -> null
                    }

                "Search" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val keyword = data.segments.getOrNull(0) ?: return null
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Search(accountType, keyword)
                }

                "Profile" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Profile.User(accountType, userKey)
                }

                "ProfileWithNameAndHost" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val userName = data.segments.getOrNull(0) ?: return null
                    val host = data.segments.getOrNull(1) ?: return null
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Profile.UserNameWithHost(accountType, userName, host)
                }

                "StatusDetail" -> {
                    val accountKey = data.parameters["accountKey"]?.let { MicroBlogKey.valueOf(it) }
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
                    Status.Detail(statusKey, accountType)
                }

                "Compose" ->
                    when (data.segments.getOrNull(0)) {
                        "Reply" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Compose.Reply(accountKey, statusKey)
                        }

                        "Quote" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Compose.Quote(accountKey, statusKey)
                        }

                        "New" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            Compose.New(AccountType.Specific(accountKey))
                        }

                        else -> null
                    }

                "RawImage" -> {
                    val rawImage = data.segments.getOrNull(0) ?: return null
                    Media.Image(rawImage, previewUrl = null)
                }

                "VVO" ->
                    when (data.segments.getOrNull(0)) {
                        "StatusDetail" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Status.VVOStatus(statusKey, AccountType.Specific(accountKey))
                        }

                        "CommentDetail" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Status.VVOComment(statusKey, AccountType.Specific(accountKey))
                        }

                        "ReplyToComment" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val replyTo = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            val rootId = data.segments.getOrNull(3) ?: return null
                            Compose.VVOReplyComment(accountKey, replyTo, rootId)
                        }

                        else -> null
                    }

                "DeleteStatus" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                    Status.DeleteConfirm(statusKey, AccountType.Specific(accountKey))
                }

                "AddReaction" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                    Status.AddReaction(statusKey, AccountType.Specific(accountKey))
                }

                "Bluesky" ->
                    when (data.segments.getOrNull(0)) {
                        "ReportStatus" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            Status.BlueskyReport(statusKey, AccountType.Specific(accountKey))
                        }

                        else -> null
                    }

                "Mastodon" ->
                    when (data.segments.getOrNull(0)) {
                        "ReportStatus" -> {
                            val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(1) ?: return null)
                            val statusKey = MicroBlogKey.valueOf(data.segments.getOrNull(2) ?: return null)
                            val userKey = MicroBlogKey.valueOf(data.segments.getOrNull(3) ?: return null)
                            Status.MastodonReport(
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
                            Status.MisskeyReport(
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
                    val preview = data.parameters["preview"]
                    Media.StatusMedia(accountType = accountType, statusKey = statusKey, index = index, preview = preview)
                }

                "Podcast" -> {
                    val accountKey = MicroBlogKey.valueOf(data.segments.getOrNull(0) ?: return null)
                    val id = data.segments.getOrNull(1) ?: return null
                    val accountType = accountKey.let { AccountType.Specific(it) }
                    Media.Podcast(accountType = accountType, id = id)
                }

                "AltText" -> {
                    val text = data.segments.getOrNull(0) ?: return null
                    Status.AltText(text)
                }

                "RSS" -> {
                    val feedUrl = data.segments.getOrNull(0) ?: return null
                    Rss.Detail(feedUrl)
                }

                else -> null
            }
        }
    }
}
