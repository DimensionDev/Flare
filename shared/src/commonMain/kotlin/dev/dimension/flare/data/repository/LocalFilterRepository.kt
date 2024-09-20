package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbKeywordFilter
import dev.dimension.flare.ui.model.UiKeywordFilter
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal class LocalFilterRepository(
    private val database: AppDatabase,
    private val coroutineScope: CoroutineScope,
) {
    fun getAllFlow() =
        database
            .keywordFilterDao()
            .selectAll()
            .map {
                it
                    .map {
                        UiKeywordFilter(
                            keyword = it.keyword,
                            forTimeline = it.for_timeline == 1L,
                            forNotification = it.for_notification == 1L,
                            forSearch = it.for_search == 1L,
                            expiredAt =
                                it.expired_at
                                    .takeIf { it > 0L }
                                    ?.let { Instant.fromEpochMilliseconds(it) },
                        )
                    }.toImmutableList()
                    .toImmutableListWrapper()
            }

    fun getFlow(
        forTimeline: Boolean = false,
        forNotification: Boolean = false,
        forSearch: Boolean = false,
    ) = database
        .keywordFilterDao()
        .selectNotExpiredFor(
            currentTime = Clock.System.now().toEpochMilliseconds(),
            forTimeline = if (forTimeline) 1L else 0L,
            forNotification = if (forNotification) 1L else 0L,
            forSearch = if (forSearch) 1L else 0L,
        ).map {
            it.map {
                it.keyword
            }
        }

    fun add(
        keyword: String,
        forTimeline: Boolean,
        forNotification: Boolean,
        forSearch: Boolean,
        expiredAt: Instant?,
    ) = coroutineScope.launch {
        database.keywordFilterDao().insert(
            DbKeywordFilter(
                keyword = keyword,
                for_timeline = if (forTimeline) 1L else 0L,
                for_notification = if (forNotification) 1L else 0L,
                for_search = if (forSearch) 1L else 0L,
                expired_at = expiredAt?.toEpochMilliseconds() ?: 0L,
            ),
        )
    }

    fun update(
        keyword: String,
        forTimeline: Boolean,
        forNotification: Boolean,
        forSearch: Boolean,
        expiredAt: Instant?,
    ) = coroutineScope.launch {
        database.keywordFilterDao().update(
            forTimeline = if (forTimeline) 1L else 0L,
            forNotification = if (forNotification) 1L else 0L,
            forSearch = if (forSearch) 1L else 0L,
            expiredAt = expiredAt?.toEpochMilliseconds() ?: 0L,
            keyword = keyword,
        )
    }

    fun delete(filter: String) =
        coroutineScope.launch {
            database.keywordFilterDao().deleteByKeyword(filter)
        }

    fun clear() =
        coroutineScope.launch {
            database.keywordFilterDao().deleteAll()
        }
}
