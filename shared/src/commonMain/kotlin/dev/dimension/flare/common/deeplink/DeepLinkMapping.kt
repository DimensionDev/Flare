package dev.dimension.flare.common.deeplink

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.Serializable

internal object DeepLinkMapping {
    sealed interface Type {
        fun deepLink(accountKey: MicroBlogKey): DeeplinkRoute

        @Serializable
        data class Profile(
            val handle: String,
        ) : Type {
            override fun deepLink(accountKey: MicroBlogKey): DeeplinkRoute {
                if (handle.contains('@')) {
                    val (name, host) = MicroBlogKey.valueOf(handle)
                    return DeeplinkRoute.Profile.UserNameWithHost(
                        accountType = AccountType.Specific(accountKey),
                        userName = name,
                        host = host,
                    )
                } else {
                    return DeeplinkRoute.Profile.UserNameWithHost(
                        accountType = AccountType.Specific(accountKey),
                        userName = handle,
                        host = accountKey.host,
                    )
                }
            }
        }

        @Serializable
        data class Post(
            val handle: String? = null,
            val id: String,
        ) : Type {
            override fun deepLink(accountKey: MicroBlogKey): DeeplinkRoute =
                DeeplinkRoute.Status.Detail(
                    accountType = AccountType.Specific(accountKey),
                    statusKey = MicroBlogKey(id, accountKey.host),
                )
        }

        @Serializable
        data class BlueskyPost(
            val handle: String,
            val id: String,
        ) : Type {
            override fun deepLink(accountKey: MicroBlogKey): DeeplinkRoute =
                DeeplinkRoute.Status.Detail(
                    accountType = AccountType.Specific(accountKey),
                    statusKey =
                        MicroBlogKey(
                            "at://$handle/app.bsky.feed.post/$id",
                            accountKey.host,
                        ),
                )
        }

        @Serializable
        data class PostMedia(
            val handle: String,
            val id: String,
            val index: Int,
        ) : Type {
            override fun deepLink(accountKey: MicroBlogKey): DeeplinkRoute =
                DeeplinkRoute.Media.StatusMedia(
                    accountType = AccountType.Specific(accountKey),
                    statusKey = MicroBlogKey(id, accountKey.host),
                    index = index,
                    preview = null,
                )
        }
    }

    fun generatePattern(
        platformType: PlatformType,
        host: String,
    ): List<DeepLinkPattern<out Type>> =
        when (platformType) {
            PlatformType.Mastodon -> {
                listOf(
                    DeepLinkPattern(
                        Type.Profile.serializer(),
                        Url("https://$host/@{handle}"),
                    ),
                    DeepLinkPattern(
                        Type.Post.serializer(),
                        Url("https://$host/@{handle}/{id}"),
                    ),
                )
            }

            PlatformType.Misskey -> {
                listOf(
                    DeepLinkPattern(
                        Type.Profile.serializer(),
                        Url("https://$host/@{handle}"),
                    ),
                    DeepLinkPattern(
                        Type.Post.serializer(),
                        Url("https://$host/notes/{id}"),
                    ),
                )
            }

            PlatformType.Bluesky -> {
                listOf(
                    DeepLinkPattern(
                        Type.Profile.serializer(),
                        Url("https://$host/profile/{handle}"),
                    ),
                    DeepLinkPattern(
                        Type.BlueskyPost.serializer(),
                        Url("https://$host/profile/{handle}/post/{id}"),
                    ),
                ) +
                    if (host == "bsky.social") {
                        listOf(
                            DeepLinkPattern(
                                Type.Profile.serializer(),
                                Url("https://bsky.app/profile/{handle}"),
                            ),
                            DeepLinkPattern(
                                Type.BlueskyPost.serializer(),
                                Url("https://bsky.app/profile/{handle}/post/{id}"),
                            ),
                        )
                    } else {
                        emptyList()
                    }
            }

            PlatformType.xQt -> {
                val profile =
                    listOf(
                        "https://$xqtHost/{handle}",
                        "https://$xqtOldHost/{handle}",
                        "https://www.$xqtHost/{handle}",
                        "https://www.$xqtOldHost/{handle}",
                    )
                val post =
                    listOf(
                        "https://$xqtHost/{handle}/status/{id}",
                        "https://$xqtOldHost/{handle}/",
                        "https://www.$xqtHost/{handle}/status/{id}",
                        "https://www.$xqtOldHost/{handle}/",
                    )
                val media =
                    listOf(
                        "https://$xqtHost/{handle}/status/{id}/photo/{index}",
                        "https://$xqtOldHost/{handle}/status/{id}/photo/{index}",
                        "https://www.$xqtHost/{handle}/status/{id}/photo/{index}",
                        "https://www.$xqtOldHost/{handle}/status/{id}/photo/{index}",
                    )
                profile.map {
                    DeepLinkPattern(
                        Type.Profile.serializer(),
                        Url(it),
                    )
                } +
                    post.map {
                        DeepLinkPattern(
                            Type.Post.serializer(),
                            Url(it),
                        )
                    } +
                    media.map {
                        DeepLinkPattern(
                            Type.PostMedia.serializer(),
                            Url(it),
                        )
                    }
            }

            PlatformType.VVo -> emptyList()
        }

    fun matches(
        url: String,
        mapping: ImmutableMap<UiAccount, ImmutableList<DeepLinkPattern<out Type>>>,
    ): ImmutableMap<UiAccount, Type> {
        val request = DeepLinkRequest(Url(url))

        val resultBuilder = persistentMapOf<UiAccount, Type>().builder()

        mapping.forEach { (account, patterns) ->
            val matchType =
                patterns.firstNotNullOfOrNull { pattern ->
                    DeepLinkMatcher(request, pattern).match()?.let { match ->
                        KeyDecoder(match.args).decodeSerializableValue(match.serializer)
                    }
                }

            if (matchType != null) {
                resultBuilder[account] = matchType
            }
        }

        return resultBuilder.build()
    }
}
