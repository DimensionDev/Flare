package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.timeline.ListTimelineDataSource
import dev.dimension.flare.ui.model.UiList

public interface ListDataSource : ListTimelineDataSource {
    public val listHandler: ListHandler<UiList.List>
    public val listMemberHandler: ListMemberHandler
}
