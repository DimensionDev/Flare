package dev.dimension.flare.data.datasource.microblog.list

import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineLoader

internal interface ListDataSource {
    fun listTimeline(listId: String): BaseTimelineLoader

    val listHandler: ListHandler
    val listMemberHandler: ListMemberHandler
}
