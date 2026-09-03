package dev.dimension.flare.data.datasource.microblog.paging

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.TimelineRevisionCallback
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TimelineDbPageLoaderTransactionTest {
    @Test
    fun fileBackedPageLoadDoesNotUpgradeReaderToWriter() =
        runTest {
            val databasePath = Files.createTempFile("flare-page-loader", ".db")
            val path = databasePath.toAbsolutePath().toString()
            val database =
                Room
                    .databaseBuilder<CacheDatabase>(name = path)
                    .addCallback(TimelineRevisionCallback)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                val statusKey = MicroBlogKey(id = "status", host = "test.invalid")
                val content =
                    UiTimelineV2.Message(
                        statusKey = statusKey,
                        icon = UiIcon.Info,
                        type = UiTimelineV2.Message.Type.Raw("message"),
                        createdAt = Instant.fromEpochMilliseconds(1).toUi(),
                        clickEvent = ClickEvent.Noop,
                        accountType = AccountType.Guest,
                    )
                val status =
                    DbStatus(
                        statusKey = statusKey,
                        accountType = AccountType.Guest,
                        content = content,
                        renderHash = content.renderHash,
                        text = null,
                    )
                database.statusDao().insertNew(listOf(status))
                database
                    .pagingTimelineDao()
                    .insertNew(
                        listOf(
                            DbPagingTimeline(
                                pagingKey = "home",
                                statusId = status.id,
                                sortId = 0,
                            ),
                        ),
                    )

                val loaded = TimelineDbPageLoader(database, "home", TimelineDbPageCache()).load(0, 1)

                assertEquals(listOf(status.id), loaded.map { it.statusId })
            } finally {
                database.close()
                deleteDatabaseFiles(databasePath)
            }
        }

    private fun deleteDatabaseFiles(databasePath: Path) {
        Files.deleteIfExists(databasePath)
        Files.deleteIfExists(databasePath.resolveSibling("${databasePath.fileName}-wal"))
        Files.deleteIfExists(databasePath.resolveSibling("${databasePath.fileName}-shm"))
    }
}
