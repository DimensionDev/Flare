package dev.dimension.flare.data.datasource.microblog.list

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2

internal interface ListDataSource {
    fun listTimeline(listId: String): RemoteLoader<UiTimelineV2>

    val listHandler: ListHandler
    val listMemberHandler: ListMemberHandler
}
