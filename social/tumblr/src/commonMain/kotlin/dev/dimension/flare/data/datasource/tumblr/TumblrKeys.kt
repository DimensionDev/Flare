package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.platform.TUMBLR_HOST
import dev.dimension.flare.model.MicroBlogKey

private const val POST_KEY_SEPARATOR = ":"

public fun tumblrUserKey(blogName: String): MicroBlogKey =
    MicroBlogKey(
        id = blogName.normalizedTumblrBlogName(),
        host = TUMBLR_HOST,
    )

public fun tumblrPostKey(
    blogName: String,
    postId: String,
): MicroBlogKey =
    MicroBlogKey(
        id = "${blogName.normalizedTumblrBlogName()}$POST_KEY_SEPARATOR$postId",
        host = TUMBLR_HOST,
    )

internal data class TumblrPostKeyParts(
    val blogName: String,
    val postId: String,
)

internal fun MicroBlogKey.toTumblrPostKeyParts(): TumblrPostKeyParts {
    val blogName = id.substringBefore(POST_KEY_SEPARATOR).normalizedTumblrBlogName()
    val postId = id.substringAfter(POST_KEY_SEPARATOR, missingDelimiterValue = id)
    return TumblrPostKeyParts(blogName = blogName, postId = postId)
}

internal fun MicroBlogKey.toTumblrBlogIdentifier(): String = id.normalizedTumblrBlogName()

internal fun String.normalizedTumblrBlogName(): String =
    trim()
        .removePrefix("@")
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .removeSuffix("/")
        .substringBefore(".tumblr.com")
        .substringBefore("/")
        .lowercase()

internal fun tumblrBlogUrl(blogName: String): String = "https://${blogName.normalizedTumblrBlogName()}.tumblr.com/"

internal fun tumblrAvatarUrl(blogName: String): String = "https://api.tumblr.com/v2/blog/${blogName.normalizedTumblrBlogName()}/avatar/512"
