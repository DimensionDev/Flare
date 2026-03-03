package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey

internal interface ReactionDataSource : AuthenticatedMicroblogDataSource {
    fun react(
        statusKey: MicroBlogKey,
        hasReacted: Boolean,
        reaction: String,
    )
}
