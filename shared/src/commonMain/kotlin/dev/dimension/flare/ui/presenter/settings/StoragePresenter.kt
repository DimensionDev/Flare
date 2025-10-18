package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class StoragePresenter :
    PresenterBase<StorageState>(),
    KoinComponent {
    private val cacheDatabase by inject<CacheDatabase>()
    private val tablesToDelete =
        listOf(
            "DbEmoji",
            "status_reference",
            "DbStatus",
            "DbUser",
            "DbPagingTimeline",
            "DbMessageRoom",
            "DbMessageItem",
            "DbDirectMessageTimeline",
            "DbMessageRoomReference",
            "DbUserHistory",
            "DbEmojiHistory",
            "DbPagingKey",
        )

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
                    cacheDatabase.useWriterConnection {
                        it.immediateTransaction {
                            tablesToDelete.forEach { tableName ->
                                it.execSQL("DELETE FROM $tableName")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Immutable
public interface StorageState {
    public val userCount: Long
    public val statusCount: Long

    public fun clearCache()
}
