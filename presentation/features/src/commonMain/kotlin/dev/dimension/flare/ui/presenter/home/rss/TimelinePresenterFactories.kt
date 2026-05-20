package dev.dimension.flare.ui.presenter.home.rss

import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.rss.RssDataSource
import dev.dimension.flare.data.datasource.rss.RssTimelineSpecs
import dev.dimension.flare.ui.presenter.home.StandaloneTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.flowOf

public fun createAllRssTimeline(): TimelinePresenter =
    StandaloneTimelinePresenter { context ->
        RssTimelineSpecs.allRss.createLoader(
            context = context,
            data = RssTimelineSpecs.AllRssData,
        )
    }

public fun createRssTimeline(url: String): TimelinePresenter =
    StandaloneTimelinePresenter {
        flowOf(RssDataSource.fetchLoader(url))
    }

public fun createSubscriptionTimeline(
    type: SubscriptionType,
    url: String,
): TimelinePresenter =
    StandaloneTimelinePresenter {
        flowOf(
            RssDataSource.fetchLoader(
                DbRssSources(
                    url = url,
                    title = null,
                    icon = null,
                    lastUpdate = 0,
                    type = type,
                ),
            ),
        )
    }
