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
import dev.dimension.flare.data.database.cache.model.DbPagingKey
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
    fun cachedHomeInitializeDoesNotReadContinuation() =
        runBlocking {
            for (remember in listOf(false, true)) {
                val path = Files.createTempFile("flare-startup-cursor", ".db")
                val driver = CountingDriver()
                val db =
                    Room
                        .databaseBuilder<CacheDatabase>(name = path.toString())
                        .setDriver(driver)
                        .setQueryCoroutineContext(Dispatchers.IO)
                        .build()
                try {
                    val initial = mediator(db, remember)
                    val dao = db.pagingTimelineDao()
                    dao.insertPagingKey(DbPagingKey(initial.pagingKey, nextKey = "x".repeat(1_000_000)))
                    db.connect { saveToDatabase(db, TimelinePagingMapper.toDb((0..39).map(::post), initial.pagingKey)) }
                    driver.reset()
                    mediator(db, remember).initialize()
                    TimelineDbPageLoader(db, initial.pagingKey, TimelineDbPageCache()).load(0, 20)
                    kotlin.test.assertEquals(
                        0L,
                        driver.largeTextReads.get(),
                        "Cached Home must not materialize a continuation before displaying posts",
                    )
                    kotlin.test.assertEquals(20L, driver.blobs.get(), "Cold starts must decode only the ordinary first page")
                    kotlin.test.assertEquals(0L, driver.anchorLookups.get(), "Cold starts must not locate a refresh anchor")
                } finally {
                    db.close()
                    listOf("", "-wal", "-shm").forEach { Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + it)) }
                }
            }
        }

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
                            mediator(db, remember).initialize()
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
    fun normalPagingReusesTheCachedPrefix() =
        runBlocking {
            val path = Files.createTempFile("flare-startup-prefix", ".db")
            val driver = CountingDriver()
            val db =
                Room
                    .databaseBuilder<CacheDatabase>(name = path.toString())
                    .addCallback(TimelineRevisionCallback)
                    .setDriver(driver)
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                db.connect { saveToDatabase(db, TimelinePagingMapper.toDb((0..199).map(::post), "startup")) }
                driver.reset()
                val source = OffsetFromStartPagingSource(TimelineDbPageLoader(db, "startup", TimelineDbPageCache()))
                try {
                    source.load(PagingSource.LoadParams.Refresh(null, 20, false))
                    source.load(PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Refresh(100), 20, false))
                    kotlin.test.assertEquals(100L, driver.blobs.get(), "Extending a normal prefix must not decode its existing posts again")
                    kotlin.test.assertEquals(0L, driver.anchorLookups.get(), "Index restoration must not locate a refresh anchor")
                } finally {
                    source.invalidate()
                }
            } finally {
                db.close()
                listOf("", "-wal", "-shm").forEach { Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + it)) }
            }
        }

    @Test
    fun refreshingAndFillingAGapDoNotMaterializeTheStoredTailCursor() =
        runBlocking {
            val path = Files.createTempFile("flare-refresh-cursor", ".db")
            val driver = CountingDriver()
            val db =
                Room
                    .databaseBuilder<CacheDatabase>(name = path.toString())
                    .setDriver(driver)
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                val dao = db.pagingTimelineDao()
                dao.insertPagingKey(DbPagingKey("startup", nextKey = "x".repeat(1_000_000)))
                db.connect { saveToDatabase(db, TimelinePagingMapper.toDb((0..39).map(::post), "startup")) }
                var response = PagingResult<UiTimelineV2>(listOf(post(-1)), nextKey = "gap")
                val remote = mediator(db, true) { response }
                driver.reset()
                remote.load(
                    androidx.paging.LoadType.REFRESH,
                    androidx.paging.PagingState(emptyList(), null, androidx.paging.PagingConfig(20), 0),
                )
                val gap =
                    TimelineDbPageLoader(db, "startup", TimelineDbPageCache())
                        .load(0, 20)
                        .map { it.baseItem }
                        .filterIsInstance<UiTimelineV2.Gap>()
                        .single()
                response = PagingResult(listOf(post(-2)))
                remote.loadGap(gap)
                kotlin.test.assertEquals(0L, driver.largeTextReads.get())
                kotlin.test.assertEquals(
                    1_000_000,
                    dao.getNextKey("startup")?.length,
                    "Refresh must preserve the old tail without decoding it",
                )
            } finally {
                db.close()
                listOf("", "-wal", "-shm").forEach { Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + it)) }
            }
        }

    private fun mediator(
        db: CacheDatabase,
        remember: Boolean,
        response: () -> PagingResult<UiTimelineV2> = { error("Network must not be needed") },
    ) = TimelineRemoteMediator(
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey = "startup"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> = response()
        },
        db,
        false,
        retainHistory = remember,
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
        val largeTextReads = AtomicLong()
        val anchorLookups = AtomicLong()

        fun reset() {
            blobs.set(0)
            largeTextReads.set(0)
            anchorLookups.set(0)
        }

        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    if (sql.startsWith("SELECT COUNT(*) FROM DbPagingTimeline")) anchorLookups.incrementAndGet()
                    val statement = connection.prepare(sql)
                    return object : SQLiteStatement by statement {
                        override fun getText(index: Int): String =
                            statement.getText(index).also {
                                if (it.length >= 1_000_000) largeTextReads.incrementAndGet()
                            }

                        override fun getBlob(index: Int): ByteArray = statement.getBlob(index).also { blobs.incrementAndGet() }
                    }
                }
            }
        }
    }
}
