package dev.dimension.flare.common.deeplink

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.Serializable

public object DeepLinkMapping {
    public sealed interface Type {
        @Serializable
        public data class Profile(
            val handle: String,
        ) : Type

        @Serializable
        public data class Post(
            val handle: String? = null,
            val id: String,
        ) : Type

        @Serializable
        public data class BlueskyPost(
            val handle: String,
            val id: String,
        ) : Type

        @Serializable
        public data class PostMedia(
            val handle: String,
            val id: String,
            val index: Int,
        ) : Type
    }

    public fun matches(
        url: String,
        mapping: ImmutableMap<MicroBlogKey, ImmutableList<DeepLinkPattern<out Type>>>,
    ): ImmutableMap<MicroBlogKey, DeeplinkRoute> {
        val request = DeepLinkRequest(Url(url))

        val resultBuilder = persistentMapOf<MicroBlogKey, DeeplinkRoute>().builder()

        mapping.forEach { (accountKey, patterns) ->
            val matchType =
                patterns.firstNotNullOfOrNull { pattern ->
                    DeepLinkMatcher(request, pattern).match()?.let { match ->
                        KeyDecoder(match.args).decodeSerializableValue(match.serializer)
                    }
                }

            if (matchType != null) {
                resultBuilder[accountKey] = matchType.toDeeplinkRoute(accountKey)
            }
        }

        return resultBuilder.build()
    }
}

private fun DeepLinkMapping.Type.toDeeplinkRoute(accountKey: MicroBlogKey): DeeplinkRoute =
    when (this) {
        is DeepLinkMapping.Type.BlueskyPost -> {
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey =
                    MicroBlogKey(
                        "at://$handle/app.bsky.feed.post/$id",
                        accountKey.host,
                    ),
            )
        }

        is DeepLinkMapping.Type.Post -> {
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = MicroBlogKey(id, accountKey.host),
            )
        }

        is DeepLinkMapping.Type.PostMedia -> {
            DeeplinkRoute.Media.StatusMedia(
                accountType = AccountType.Specific(accountKey),
                statusKey = MicroBlogKey(id, accountKey.host),
                index = index,
                preview = null,
            )
        }

        is DeepLinkMapping.Type.Profile -> {
            val (userName, host) =
                if (handle.contains('@')) {
                    MicroBlogKey.valueOf(handle)
                } else {
                    MicroBlogKey(handle, accountKey.host)
                }
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(accountKey),
                userName = userName,
                host = host,
            )
        }
    }
