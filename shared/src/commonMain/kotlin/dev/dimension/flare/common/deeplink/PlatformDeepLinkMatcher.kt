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
    ): ImmutableMap<UiAccount, DeeplinkRoute> {
        val request = DeepLinkRequest(Url(url))
        val resultBuilder = persistentMapOf<UiAccount, DeeplinkRoute>().builder()

        mapping.forEach { (account, deepLinks) ->
            val route =
                deepLinks.firstNotNullOfOrNull { deepLink ->
                    match(request, deepLink)
                }

            if (route != null) {
                resultBuilder[account] = route
            }
        }

        return resultBuilder.build()
    }

    private fun <T> match(
        request: DeepLinkRequest,
        deepLink: PlatformDeepLink<T>,
    ): DeeplinkRoute? {
        val pattern =
            DeepLinkPattern(
                serializer = deepLink.serializer,
                uriPattern = Url(deepLink.uriPattern),
            )
        val match = DeepLinkMatcher(request, pattern).match() ?: return null
        val data = KeyDecoder(match.args).decodeSerializableValue(match.serializer)
        return deepLink.callback(data)
    }
}
