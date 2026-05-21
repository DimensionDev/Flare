package dev.dimension.flare.common.deeplink

import io.ktor.http.Url
import io.ktor.util.toMap

/**
 * Parse the requested Uri and store it in a easily readable format
 *
 * @param uri the target deeplink uri to link to
 */
internal class DeepLinkRequest(
    internal val uri: Url,
) {
    /**
     * A list of path segments
     */
    internal val pathSegments: List<String> = uri.rawSegments

    /**
     * A map of query name to query value
     */
    internal val queries: Map<String, List<String>> = uri.parameters.toMap()

    // TODO add parsing for other Uri components, i.e. fragments, mimeType, action
}
