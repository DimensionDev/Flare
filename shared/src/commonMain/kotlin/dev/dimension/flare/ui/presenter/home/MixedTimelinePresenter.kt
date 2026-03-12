package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MixedTimelinePresenter(
    private val subTimelinePresenter: List<TimelinePresenter>,
) : TimelinePresenter(),
    KoinComponent {
    private val database: CacheDatabase by inject()
    override val useDbKeyInItemKey: Boolean = true
    override val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() =
            combine(subTimelinePresenter.map { it.loader }) {
                MixedRemoteMediator(
                    database = database,
                    mediators = it.filterIsInstance<CacheableRemoteLoader<UiTimelineV2>>(),
                )
            }
}
