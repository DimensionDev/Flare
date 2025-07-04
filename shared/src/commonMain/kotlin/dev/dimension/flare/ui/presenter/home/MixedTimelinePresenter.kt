package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MixedTimelinePresenter(
    private val subTimelinePresenter: List<TimelinePresenter>,
) : TimelinePresenter(),
    KoinComponent {
    private val database: CacheDatabase by inject()
    override val loader: Flow<BaseTimelineLoader>
        get() =
            combine(subTimelinePresenter.map { it.loader }) {
                MixedRemoteMediator(
                    database = database,
                    mediators = it.filterIsInstance<BaseTimelineRemoteMediator>(),
                )
            }
}
