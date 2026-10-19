package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.TimelineRevisionCallback
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbTimelinePosition
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class)
class TimelineStartupTest {
    @Test
    fun cachedHomeDoesNotWaitForAnUnrelatedWriter() =
        runBlocking {
            val outcomes = mutableListOf<Boolean>()
            for (remember in listOf(false, true)) {
                val path = Files.createTempFile("flare-startup", ".db")
                val db =
                    Room
                        .databaseBuilder<CacheDatabase>(name = path.toString())
                        .addCallback(TimelineRevisionCallback)
                        .setDriver(BundledSQLiteDriver())
                        .setQueryCoroutineContext(Dispatchers.IO)
                        .build()
                val release = CompletableDeferred<Unit>()
                try {
                    val initial = mediator(db, remember)
                    initial.prepareHomeTimeline()
                    db.connect { saveToDatabase(db, TimelinePagingMapper.toDb((0..59).map(::post), initial.pagingKey)) }
                    // Open the reader before taking the writer, so schema initialization is not part of this contention test.
                    TimelineDbPageLoader(db, initial.pagingKey, TimelineDbPageCache()).load(0, 20)
                    val entered = CompletableDeferred<Unit>()
                    val writer =
                        launch(Dispatchers.IO) {
                            db.connect {
                                entered.complete(Unit)
                                release.await()
                            }
                        }
                    entered.await()
                    TimelineDbPageLoader(db, initial.pagingKey, TimelineDbPageCache()).load(0, 20)
                    val home =
                        async(Dispatchers.IO) {
                            mediator(db, remember).prepareHomeTimeline()
                            TimelineDbPageLoader(db, initial.pagingKey, TimelineDbPageCache()).load(0, 20)
                        }
                    val ready = withTimeoutOrNull(1_000) { home.await() } != null
                    outcomes += ready
                    release.complete(Unit)
                    home.await()
                    writer.join()
                } finally {
                    release.complete(Unit)
                    db.close()
                    listOf("", "-wal", "-shm").forEach { Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + it)) }
                }
            }
            assertTrue(outcomes.all { it }, "Both settings must display an existing cache without waiting for unrelated writes")
        }

    @Test
    fun restoringTheHeadReusesContentAcrossVisibilityChecks() = checkRestoredWindow(deep = false)

    @Test
    fun checkingTheHeadDoesNotEvictTheRestoredDeepWindow() = checkRestoredWindow(deep = true)

    private fun checkRestoredWindow(deep: Boolean) =
        runBlocking {
            val path = Files.createTempFile("flare-startup-projection", ".db")
            val driver = CountingDriver()
            val db =
                Room
                    .databaseBuilder<CacheDatabase>(name = path.toString())
                    .addCallback(TimelineRevisionCallback)
                    .setDriver(driver)
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                val initial = mediator(db, true)
                initial.prepareHomeTimeline()
                db.connect { saveToDatabase(db, TimelinePagingMapper.toDb((0..999).map(::post), initial.pagingKey)) }
                if (deep) {
                    val item = TimelineDbPageLoader(db, initial.pagingKey, TimelineDbPageCache()).load(500, 1).single()
                    db.pagingTimelineDao().insertPosition(
                        DbTimelinePosition("startup", initial.pagingKey, item.baseItem.stableItemKey, item.sortId, 12.0, 1),
                    )
                }
                driver.reset()
                val pageCache = TimelineDbPageCache()
                val reader = TimelineViewport(db, initial.pagingKey, "startup", this, this, pageCache = pageCache)
                reader.restore()
                val source =
                    OffsetFromStartPagingSource(
                        TimelineDbPageLoader(db, initial.pagingKey, pageCache, windowed = true),
                        anchor = { reader.anchor() },
                    )
                source.load(PagingSource.LoadParams.Refresh(null, 40, false))
                if (deep) driver.reset()
                reader.refreshHead()
                source.load(PagingSource.LoadParams.Refresh(null, 40, false))
                kotlin.test.assertEquals(40L, driver.blobs.get(), "Head checks must not decode the visible window again")
                db.pagingTimelineDao().delete(initial.pagingKey)
                reader.refreshHead()
                assertTrue(
                    pageCache.hasCurrentWindowChanged(db.pagingTimelineDao(), initial.pagingKey),
                    "Visibility probes must leave changes for the paging source's invalidation observer",
                )
            } finally {
                db.close()
                listOf("", "-wal", "-shm").forEach { Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + it)) }
            }
        }

    private fun mediator(
        db: CacheDatabase,
        remember: Boolean,
    ) = TimelineRemoteMediator(
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey = "startup"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> = error("Network must not be needed")
        },
        db,
        false,
        rememberPosition = remember,
    )

    private fun post(id: Int) =
        UiTimelineV2.Post(
            platformId = "xQt",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText(("Post $id " + "cached content ".repeat(200)).toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey(id.toString(), "example.invalid"),
            card = null,
            createdAt = Instant.fromEpochMilliseconds(id.toLong()).toUi(),
            references = persistentListOf(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Guest,
        )

    private class CountingDriver(
        private val delegate: SQLiteDriver = BundledSQLiteDriver(),
    ) : SQLiteDriver by delegate {
        val blobs = AtomicLong()

        fun reset() {
            blobs.set(0)
        }

        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    return object : SQLiteStatement by statement {
                        override fun getBlob(index: Int): ByteArray = statement.getBlob(index).also { blobs.incrementAndGet() }
                    }
                }
            }
        }
    }
}
