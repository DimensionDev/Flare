package dev.dimension.flare.data.repository

import dev.dimension.flare.common.toImmutableListWrapper
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbSearchHistory
import dev.dimension.flare.ui.model.UiSearchHistory
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

internal class SearchHistoryRepository(
    private val database: AppDatabase,
    private val coroutineScope: CoroutineScope,
) {
    val allSearchHistory =
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

    fun addSearchHistory(keyword: String) =
        coroutineScope.launch {
            database.searchHistoryDao().insert(
                DbSearchHistory(
                    search = keyword,
                    created_at = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }

    fun deleteSearchHistory(keyword: String) =
        coroutineScope.launch {
            database.searchHistoryDao().delete(keyword)
        }

    fun deleteAllSearchHistory() =
        coroutineScope.launch {
            database.searchHistoryDao().deleteAll()
        }
}
