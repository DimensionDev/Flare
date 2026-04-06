package dev.dimension.flare.readability

/**
 * Internal metadata holder used during parsing.
 */
internal data class ArticleMetadata(
    var title: String? = null,
    var byline: String? = null,
    var excerpt: String? = null,
    var siteName: String? = null,
    var publishedTime: String? = null,
)
