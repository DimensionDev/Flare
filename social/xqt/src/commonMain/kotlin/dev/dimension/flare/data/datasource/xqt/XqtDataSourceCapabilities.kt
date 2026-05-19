package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.data.network.xqt.model.TweetUnion
import dev.dimension.flare.ui.model.UiPodcast
import kotlinx.collections.immutable.ImmutableList

public interface XqtContentDataSource {
    public val accountKey: MicroBlogKey

    public suspend fun getTweetResultByRestId(tweetId: String): TweetUnion?

    public suspend fun podcast(id: String): Result<UiPodcast>

    public suspend fun getFleets(): Result<ImmutableList<UiPodcast>>
}
