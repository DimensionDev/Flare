package dev.dimension.flare.common.deeplink

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

    public fun <Account : Any> matches(
        url: String,
        mapping: ImmutableMap<Account, ImmutableList<DeepLinkPattern<out Type>>>,
    ): ImmutableMap<Account, Type> {
        val request = DeepLinkRequest(Url(url))

        val resultBuilder = persistentMapOf<Account, Type>().builder()

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
