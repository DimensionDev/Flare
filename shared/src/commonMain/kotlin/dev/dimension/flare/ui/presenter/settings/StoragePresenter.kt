package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StoragePresenter :
    PresenterBase<StorageState>(),
    KoinComponent {
    private val cacheDatabase by inject<CacheDatabase>()

    @Composable
    override fun body(): StorageState {
        val scope = rememberCoroutineScope()
        val statusCount by remember {
            cacheDatabase
                .statusDao()
                .count()
        }.collectAsState(0L)
        val userCount by remember {
            cacheDatabase
                .userDao()
                .count()
        }.collectAsState(0L)
        return object : StorageState {
            override val userCount: Long = userCount
            override val statusCount: Long = statusCount

            override fun clearCache() {
                scope.launch {
                    cacheDatabase.pagingTimelineDao().clear()
                    cacheDatabase.statusDao().clear()
                    cacheDatabase.userDao().clear()
                    cacheDatabase.emojiDao().clear()
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
