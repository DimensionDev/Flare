package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.compose.koinInject

class StoragePresenter : PresenterBase<StorageState>() {
    @Composable
    override fun body(): StorageState {
        val cacheDatabase = koinInject<CacheDatabase>()
        val statusCount by remember {
            cacheDatabase.dbStatusQueries
                .count()
                .asFlow()
                .mapToOneNotNull(Dispatchers.IO)
        }.collectAsState(0L)
        val userCount by remember {
            cacheDatabase.dbUserQueries
                .count()
                .asFlow()
                .mapToOneNotNull(Dispatchers.IO)
        }.collectAsState(0L)
        return object : StorageState {
            override val userCount: Long = userCount
            override val statusCount: Long = statusCount

            override fun clearCache() {
                cacheDatabase.transaction {
                    cacheDatabase.dbPagingTimelineQueries.clear()
                    cacheDatabase.dbStatusQueries.clear()
                    cacheDatabase.dbUserQueries.clear()
                    cacheDatabase.dbEmojiQueries.clear()
                }
            }
        }
    }
}

interface StorageState {
    val userCount: Long
    val statusCount: Long

    fun clearCache()
}
