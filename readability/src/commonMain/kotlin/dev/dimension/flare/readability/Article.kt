package dev.dimension.flare.readability

/**
 * Result of parsing an article with [Readability].
 */
public data class Article(
    public val title: String,
    public val byline: String?,
    public val dir: String?,
    public val lang: String?,
    public val content: String,
    public val textContent: String,
    public val length: Int,
    public val excerpt: String?,
    public val siteName: String?,
    public val publishedTime: String?,
)
