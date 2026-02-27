package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2

internal interface ListDataSource {
    fun listTimeline(listId: String): RemoteLoader<UiTimelineV2>

    val listHandler: ListHandler
    val listMemberHandler: ListMemberHandler
}
