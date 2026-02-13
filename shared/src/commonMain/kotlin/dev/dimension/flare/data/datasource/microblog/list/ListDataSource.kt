package dev.dimension.flare.data.datasource.microblog.list

import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineLoader
import dev.dimension.flare.model.MicroBlogKey

internal interface ListDataSource {
    fun listTimeline(listKey: MicroBlogKey): BaseTimelineLoader

    val listHandler: ListHandler
    val listMemberHandler: ListMemberHandler
}
