package dev.dimension.flare.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.ui.model.UiKeywordFilter
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal class LocalFilterRepository(
    private val database: AppDatabase,
) {
    fun getAllFlow() =
        database.dbKeywordFilterQueries.selectAll().asFlow().mapToList(Dispatchers.IO)
            .map {
                it.map {
                    UiKeywordFilter(
                        keyword = it.keyword,
                        forTimeline = it.for_timeline == 1L,
                        forNotification = it.for_notification == 1L,
                        forSearch = it.for_search == 1L,
                        expiredAt =
                            it.expired_at.takeIf { it > 0L }
                                ?.let { Instant.fromEpochMilliseconds(it) },
                    )
                }.toImmutableList().toImmutableListWrapper()
            }

    fun getFlow(
        forTimeline: Boolean = false,
        forNotification: Boolean = false,
        forSearch: Boolean = false,
    ) = database.dbKeywordFilterQueries.selectNotExpiredFor(
        currentTime = Clock.System.now().toEpochMilliseconds(),
        forTimeline = if (forTimeline) 1L else 0L,
        forNotification = if (forNotification) 1L else 0L,
        forSearch = if (forSearch) 1L else 0L,
    ).asFlow().mapToList(Dispatchers.IO).map {
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
    ) {
        database.dbKeywordFilterQueries.insert(
            keyword = keyword,
            forTimeline = if (forTimeline) 1L else 0L,
            forNotification = if (forNotification) 1L else 0L,
            forSearch = if (forSearch) 1L else 0L,
            expiredAt = expiredAt?.toEpochMilliseconds() ?: 0L,
        )
    }

    fun update(
        keyword: String,
        forTimeline: Boolean,
        forNotification: Boolean,
        forSearch: Boolean,
        expiredAt: Instant?,
    ) {
        database.dbKeywordFilterQueries.update(
            forTimeline = if (forTimeline) 1L else 0L,
            forNotification = if (forNotification) 1L else 0L,
            forSearch = if (forSearch) 1L else 0L,
            expiredAt = expiredAt?.toEpochMilliseconds() ?: 0L,
            keyword = keyword,
        )
    }

    fun delete(filter: String) {
        database.dbKeywordFilterQueries.deleteByKeyword(filter)
    }

    fun clear() {
        database.dbKeywordFilterQueries.deleteAll()
    }
}
