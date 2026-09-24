package dev.dimension.flare.data.datasource.bluesky

import app.bsky.feed.FeedViewPost
import app.bsky.feed.PostView
import app.bsky.feed.ReplyRefParentUnion
import dev.dimension.flare.data.network.bluesky.resolveBlueskyVideoDownloadUrls
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal suspend fun List<FeedViewPost>.renderWithDownloadUrls(accountKey: MicroBlogKey): List<UiTimelineV2> {
    val posts =
        flatMap {
            listOfNotNull(it.post, (it.reply?.parent as? ReplyRefParentUnion.PostView)?.value)
        }
    return render(accountKey, resolveBlueskyVideoDownloadUrls(posts))
}

internal suspend fun PostView.renderWithDownloadUrls(accountKey: MicroBlogKey): UiTimelineV2.Post =
    render(accountKey, resolveBlueskyVideoDownloadUrls(listOf(this)))
