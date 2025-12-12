package dev.dimension.flare.common.deeplink

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
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
                        Type.Post.serializer(),
                        Url("https://$host/profile/{handle}/post/{id}"),
                    ),
                )
            }

            PlatformType.xQt -> {
                listOf(
                    DeepLinkPattern(
                        Type.Profile.serializer(),
                        Url("https://$host/{handle}"),
                    ),
                    DeepLinkPattern(
                        Type.Post.serializer(),
                        Url("https://$host/{handle}/status/{id}"),
                    ),
                )
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
