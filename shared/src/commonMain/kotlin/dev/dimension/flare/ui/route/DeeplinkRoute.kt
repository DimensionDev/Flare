package dev.dimension.flare.ui.route

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

public const val APPSCHEMA: String = "flare"

@Serializable
public sealed class DeeplinkRoute {
    @Serializable
    public data object Login : DeeplinkRoute()

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

    @Serializable
    public data class DeepLinkAccountPicker(
        val originalUrl: String,
        val data: Map<MicroBlogKey, DeeplinkRoute>,
    ) : DeeplinkRoute()

    @Serializable
    public data class OpenLinkDirectly(
        val url: String,
    ) : DeeplinkRoute()

    @Serializable
    public data class EditUserList(
        val accountKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class DirectMessage(
        val accountKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class BlockUser(
        val accountKey: MicroBlogKey?,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class MuteUser(
        val accountKey: MicroBlogKey?,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class ReportUser(
        val accountKey: MicroBlogKey?,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    public companion object Companion {
        public object Callback {
            public const val MASTODON: String = "$APPSCHEMA://Callback/SignIn/Mastodon"
            public const val MISSKEY: String = "$APPSCHEMA://Callback/SignIn/Misskey"
            public const val BLUESKY: String = "$APPSCHEMA://Callback/SignIn/Bluesky"
        }

        @OptIn(ExperimentalSerializationApi::class)
        public fun parse(uri: String): DeeplinkRoute? =
            runCatching {
                ProtoBuf.decodeFromHexString<RoutePackage>(uri.removePrefix("$APPSCHEMA://")).route
            }.getOrNull()
    }
}

@OptIn(ExperimentalSerializationApi::class)
public fun DeeplinkRoute.toUri(): String {
    val protobuf = ProtoBuf.encodeToHexString(RoutePackage(this))
    return "$APPSCHEMA://$protobuf"
}

@Serializable
private data class RoutePackage(
    val route: DeeplinkRoute,
)
