package dev.dimension.flare.data.model.tab

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.home.TimelinePresenter

public class TimelineResolver internal constructor(
    private val timelinePersistenceMapper: TimelinePersistenceMapper,
    private val timelinePresenterFactory: TimelinePresenterFactory,
) {
    public fun toTabItem(source: TimelineSourceRef): SourceTimelineTabItemV2 = timelinePersistenceMapper.toTabItem(source)

    public fun createPresenter(item: TimelineTabItemV2): TimelinePresenter = timelinePresenterFactory.create(item)

    public fun resolveAccountKey(item: TimelineTabItemV2): MicroBlogKey? = timelinePersistenceMapper.resolveAccountKey(item)
}
