package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.model.MicroBlogKey

public interface MastodonReportDataSource {
    public suspend fun report(
        userKey: MicroBlogKey,
        statusKey: MicroBlogKey?,
    )
}
