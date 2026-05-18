package dev.dimension.flare.common.deeplink

import dev.dimension.flare.data.repository.DebugRepository
import kotlinx.serialization.KSerializer

internal class DeepLinkMatcher<T>(
    val request: DeepLinkRequest,
    val deepLinkPattern: DeepLinkPattern<T>,
) {
    /**
     * Match a [DeepLinkRequest] to a [DeepLinkPattern].
     *
     * Returns a [DeepLinkMatchResult] if this matches the pattern, returns null otherwise
     */
    fun match(): DeepLinkMatchResult<T>? {
        if (request.pathSegments.size != deepLinkPattern.pathSegments.size) return null
        // exact match (url does not contain any arguments)
        if (request.uri == deepLinkPattern.uriPattern) {
            return DeepLinkMatchResult(deepLinkPattern.serializer, mapOf())
        }

        val args = mutableMapOf<String, Any>()
        // match the host
        if (request.uri.host != deepLinkPattern.uriPattern.host) return null

        // match the path
        request.pathSegments
            .asSequence()
            // zip to compare the two objects side by side, order matters here so we
            // need to make sure the compared segments are at the same position within the url
            .zip(deepLinkPattern.pathSegments.asSequence())
            .forEach { it ->
                // retrieve the two path segments to compare
                val requestedSegment = it.first
                val candidateSegment = it.second
                // if the potential match expects a path arg for this segment, try to parse the
                // requested segment into the expected type
                if (candidateSegment.isParamArg) {
                    var valueToParse = requestedSegment
                    if (!valueToParse.startsWith(candidateSegment.prefix)) return null
                    valueToParse = valueToParse.removePrefix(candidateSegment.prefix)

                    if (!valueToParse.endsWith(candidateSegment.suffix)) return null
                    valueToParse = valueToParse.removeSuffix(candidateSegment.suffix)

                    val parsedValue =
                        try {
                            candidateSegment.typeParser.invoke(valueToParse)
                        } catch (e: IllegalArgumentException) {
                            DebugRepository.log(
                                "${TAG_LOG_ERROR}: Failed to parse path value:[$requestedSegment]" +
                                    ". ${e.stackTraceToString()}",
                            )
                            return null
                        }
                    args[candidateSegment.stringValue] = parsedValue
                } else if (requestedSegment != candidateSegment.stringValue) {
                    // if it's path arg is not the expected type, its not a match
                    return null
                }
            }
        // match queries (if any)
        request.queries.forEach { query ->
            val name = query.key
            // if the query param is not part of the pattern, we just ignore it
            val queryStringParser = deepLinkPattern.queryValueParsers[name] ?: return@forEach
            val queryParsedValue =
                try {
                    queryStringParser.invoke(query.value.first())
                } catch (e: IllegalArgumentException) {
                    DebugRepository.log(
                        "${TAG_LOG_ERROR}: Failed to parse query name:[$name] value:[${query.value}]." +
                            " ${e.stackTraceToString()}",
                    )
                    return null
                }
            args[name] = queryParsedValue
        }
        // provide the serializer of the matching key and map of arg names to parsed arg values
        return DeepLinkMatchResult(deepLinkPattern.serializer, args)
    }
}

/**
 * Created when a requested deeplink matches with a supported deeplink
 *
 * @param [T] the backstack key associated with the deeplink that matched with the requested deeplink
 * @param serializer serializer for [T]
 * @param args The map of argument name to argument value. The value is expected to have already
 * been parsed from the raw url string back into its proper KType as declared in [T].
 * Includes arguments for all parts of the uri - path, query, etc.
 * */
internal data class DeepLinkMatchResult<T>(
    val serializer: KSerializer<T>,
    val args: Map<String, Any>,
)

private const val TAG_LOG_ERROR = "Nav3RecipesDeepLink"
