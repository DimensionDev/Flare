package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

internal interface ReactionDataSource : AuthenticatedMicroblogDataSource {
    fun react(
        statusKey: MicroBlogKey,
        hasReacted: Boolean,
        reaction: String,
    )

    fun emoji(): Cacheable<ImmutableMap<String, ImmutableList<UiEmoji>>>
}
