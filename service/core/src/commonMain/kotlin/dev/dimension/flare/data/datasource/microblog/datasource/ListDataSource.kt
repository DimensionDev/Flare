package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2

public interface ListDataSource {
    public fun listTimeline(listId: String): RemoteLoader<UiTimelineV2>

    public val listHandler: ListHandler
    public val listMemberHandler: ListMemberHandler
}
