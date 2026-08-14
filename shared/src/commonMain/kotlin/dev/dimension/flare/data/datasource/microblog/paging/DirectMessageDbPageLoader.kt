package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class DirectMessageTimelineDbPageLoader(
    private val database: CacheDatabase,
    private val accountType: DbAccountType,
) : OffsetFromStartPageLoader<DbDirectMessageTimeline> {
    override suspend fun load(
        offset: Int,
        limit: Int,
    ): List<DbDirectMessageTimeline> =
        database.messageDao().getRoomTimelinePage(
            accountType = accountType,
            offset = offset,
            limit = limit,
        )

    override fun observeInvalidations(invalidate: () -> Unit): PageInvalidationSubscription =
        observeTableInvalidations(
            database = database,
            tableNames = arrayOf("DbDirectMessageTimeline"),
            invalidate = invalidate,
        )
}

internal class DirectMessageItemDbPageLoader(
    private val database: CacheDatabase,
    private val roomKey: MicroBlogKey,
) : OffsetFromStartPageLoader<DbMessageItem> {
    override suspend fun load(
        offset: Int,
        limit: Int,
    ): List<DbMessageItem> =
        database.messageDao().getRoomMessagesPage(
            roomKey = roomKey,
            offset = offset,
            limit = limit,
        )

    override fun observeInvalidations(invalidate: () -> Unit): PageInvalidationSubscription =
        observeTableInvalidations(
            database = database,
            tableNames = arrayOf("DbMessageItem"),
            invalidate = invalidate,
        )
}

private fun observeTableInvalidations(
    database: CacheDatabase,
    tableNames: Array<String>,
    invalidate: () -> Unit,
): PageInvalidationSubscription {
    val ready = CompletableDeferred<Unit>()
    val job =
        CoroutineScope(PlatformDispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
            database
                .invalidationTracker
                .createFlow(
                    *tableNames,
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
