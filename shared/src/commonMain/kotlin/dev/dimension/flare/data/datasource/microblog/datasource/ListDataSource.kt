package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface ListDataSource {
    public fun listTimeline(listId: String): RemoteLoader<UiTimelineV2>

    public val listHandler: ListHandler<UiList.List>
    public val listMemberHandler: ListMemberHandler
}
