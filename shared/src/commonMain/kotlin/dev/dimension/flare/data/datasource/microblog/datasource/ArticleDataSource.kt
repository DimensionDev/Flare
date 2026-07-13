package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface ArticleDataSource {
    public suspend fun article(articleKey: MicroBlogKey): UiArticle

    public fun articleComments(articleKey: MicroBlogKey): RemoteLoader<UiTimelineV2>
}
