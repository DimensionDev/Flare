package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.timeline.ListTimelineDataSource
import dev.dimension.flare.ui.model.UiList

internal interface ListDataSource : ListTimelineDataSource {
    val listHandler: ListHandler<UiList.List>
    val listMemberHandler: ListMemberHandler
}
