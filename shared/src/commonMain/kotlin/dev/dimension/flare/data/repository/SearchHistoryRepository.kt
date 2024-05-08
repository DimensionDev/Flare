package dev.dimension.flare.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.ui.model.UiSearchHistory
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal class SearchHistoryRepository(
    private val database: AppDatabase,
) {
    val allSearchHistory =
        database
            .dbSearchHistoryQueries
            .select()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map {
                it.map {
                    UiSearchHistory(
                        keyword = it.search,
                        createdAt = Instant.fromEpochMilliseconds(it.created_at),
                    )
                }.toImmutableList().toImmutableListWrapper()
            }

    fun addSearchHistory(keyword: String) {
        database.dbSearchHistoryQueries.insert(
            search = keyword,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
    }

    fun deleteSearchHistory(keyword: String) {
        database.dbSearchHistoryQueries.delete(keyword)
    }

    fun deleteAllSearchHistory() {
        database.dbSearchHistoryQueries.deleteAll()
    }
}
