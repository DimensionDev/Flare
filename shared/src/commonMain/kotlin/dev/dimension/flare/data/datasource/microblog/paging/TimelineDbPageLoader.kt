package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class TimelineDbPageLoader(
    private val database: CacheDatabase,
    private val pagingKey: String,
) : OffsetFromStartPageLoader<DbStatusWithReference> {
    override suspend fun load(
        offset: Int,
        limit: Int,
    ): List<DbStatusWithReference> =
        database.pagingTimelineDao().getTimelinePage(
            pagingKey = pagingKey,
            offset = offset,
            limit = limit,
        )

    override fun observeInvalidations(invalidate: () -> Unit): PageInvalidationSubscription {
        val ready = CompletableDeferred<Unit>()
        val job =
            CoroutineScope(PlatformDispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
                database
                    .invalidationTracker
                    .createFlow(
                        "DbPagingTimeline",
                        "DbStatus",
                        "status_reference",
                        "DbTranslation",
                        emitInitialState = true,
                    ).collect {
                        if (ready.complete(Unit)) {
                            return@collect
                        }
                        invalidate()
                    }
            }
        return PageInvalidationSubscription(
            job = job,
            ready = ready,
        )
    }
}
