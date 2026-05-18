package dev.dimension.flare.data.repository

import dev.dimension.flare.common.ImmutableListWrapper
import dev.dimension.flare.common.toImmutableListWrapper
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbSearchHistory
import dev.dimension.flare.ui.model.UiSearchHistory
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

public class SearchHistoryRepository(
    private val database: AppDatabase,
    private val coroutineScope: CoroutineScope,
) {
    public val allSearchHistory: Flow<ImmutableListWrapper<UiSearchHistory>> =
        database
            .searchHistoryDao()
            .select()
            .map {
                it
                    .map {
                        UiSearchHistory(
                            keyword = it.search,
                            createdAt = Instant.fromEpochMilliseconds(it.created_at),
                        )
                    }.toImmutableList()
                    .toImmutableListWrapper()
            }

    public fun addSearchHistory(keyword: String): Job =
        coroutineScope.launch {
            database.searchHistoryDao().insert(
                DbSearchHistory(
                    search = keyword,
                    created_at = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }

    public fun deleteSearchHistory(keyword: String): Job =
        coroutineScope.launch {
            database.searchHistoryDao().delete(keyword)
        }

    public fun deleteAllSearchHistory(): Job =
        coroutineScope.launch {
            database.searchHistoryDao().deleteAll()
        }
}
