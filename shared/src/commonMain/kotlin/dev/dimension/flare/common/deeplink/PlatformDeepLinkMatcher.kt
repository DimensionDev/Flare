package dev.dimension.flare.common.deeplink

import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

internal object PlatformDeepLinkMatcher {
    fun matches(
        url: String,
        mapping: Map<UiAccount, List<PlatformDeepLink<*>>>,
    ): ImmutableMap<UiAccount, Match> {
        val request = DeepLinkRequest(Url(url))
        val resultBuilder = persistentMapOf<UiAccount, Match>().builder()

        mapping.forEach { (account, deepLinks) ->
            val match =
                deepLinks.firstNotNullOfOrNull { deepLink ->
                    match(request, deepLink)
                }

            if (match != null) {
                resultBuilder[account] = match
            }
        }

        return resultBuilder.build()
    }

    private fun <T> match(
        request: DeepLinkRequest,
        deepLink: PlatformDeepLink<T>,
    ): Match? {
        val uriPattern = Url(deepLink.uriPattern)
        val pattern =
            DeepLinkPattern(
                serializer = deepLink.serializer,
                uriPattern = uriPattern,
            )
        val match = DeepLinkMatcher(request, pattern).match() ?: return null
        val data = KeyDecoder(match.args).decodeSerializableValue(match.serializer)
        return Match(
            route = deepLink.callback(data),
            host = uriPattern.host,
        )
    }

    data class Match(
        val route: DeeplinkRoute,
        val host: String,
    )
}
