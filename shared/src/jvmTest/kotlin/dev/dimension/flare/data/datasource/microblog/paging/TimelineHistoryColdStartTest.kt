package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.TimelineRevisionCallback
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbTimelineBookmark
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimelineHistoryColdStartTest {
    private val reads = RootReads(createDatabaseDriver())
    private val database =
        Room
            .inMemoryDatabaseBuilder<CacheDatabase>()
            .setDriver(reads)
            .addCallback(TimelineRevisionCallback)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    private val store = TimelineHistoryStore(database)
    private var response: List<UiTimelineV2> = (1..240).map(::historyTestPost)
    private var requests = 0
    private val loader =
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey = "cold-start"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                requests++
                return PagingResult(response, nextKey = "older")
            }
        }

    @AfterTest
    fun close() = database.close()

    @Test
    fun coldTopAndDeepBookmarksReadOnlyTheRequestedPage() =
        runTest {
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            reads.count.set(0)
            val baseline = TimelineDbPageLoader(database, history.key, TimelineDbPageCache()).load(0, 20)
            assertEquals(20, baseline.size)
            val baselineReads = reads.count.get()
            assertEquals(20, baselineReads)
            for (index in listOf(0, 200)) {
                val anchor = database.timelineHistoryDao().page(history.key, index, 1).single()
                database.timelineHistoryDao().save(
                    DbTimelineBookmark("tab-$index", history.key, anchor.itemKey, anchor.ordinal, anchor.position, -42.5),
                )
                val reopenedStore = TimelineHistoryStore(database)
                val reopened = assertNotNull(reopenedStore.open(loader, false))
                val reader = TimelineReader("tab-$index", reopened, database, reopenedStore, backgroundScope)
                reads.count.set(0)
                reader.restore()
                assertEquals(0, reads.count.get(), "Restoring an unfiltered bookmark must not decode post bodies")
                val page = load(reader)
                assertEquals(20, page.size)
                assertTrue(page.any { it.baseItem.stableItemKey == anchor.itemKey })
                assertEquals(-42.5, reader.target.value?.offset)
                assertEquals(baselineReads, reads.count.get(), "Cold restoration must have the pre-history payload budget")
                if (index == 0) assertEquals(baseline.map { it.baseItem.stableItemKey }, page.map { it.baseItem.stableItemKey })
            }
            assertEquals(1, requests, "The cached first page must not require the network")
        }

    @Test
    fun filteredHeadAndPagingSourceReplacementReuseTheSameDecodedPage() =
        runTest {
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val reader = TimelineReader("filtered", history, database, store, backgroundScope)
            reader.setVisibility("filter") { it.statusKey.id != "hidden" }
            reads.count.set(0)
            reader.restore()
            val first = load(reader)
            assertEquals(20, reads.count.get(), "Head probing and the first page must share their decoded content")
            reads.count.set(0)
            val second = load(reader)
            assertEquals(0, reads.count.get(), "Replacing a paging source must not rehydrate unchanged posts")
            first.zip(second).forEach { (before, after) -> assertSame(before, after) }
        }

    @Test
    fun anotherReaderSeesContentChangesWithoutRehydratingUnchangedPosts() =
        runTest {
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val readers = listOf("first", "second").map { TimelineReader(it, history, database, store, backgroundScope) }
            readers.forEach {
                it.restore()
                load(it)
            }
            saveToDatabase(database, listOf(TimelinePagingMapper.toDb(historyTestPost(4).copy(sensitive = true), "other-source")))
            for (reader in readers) {
                reads.count.set(0)
                val page = load(reader)
                assertEquals(1, reads.count.get(), "The shared content revision must invalidate only the edited post")
                assertTrue(assertNotNull(page.single { it.baseItem.statusKey.id == "4" }.baseItem.asTimelinePostItem()).post.sensitive)
            }
        }

    @Test
    fun cachedQuotesObserveContentChangesFromAnotherSource() =
        runTest {
            val quote = historyTestPost(999)
            response =
                listOf(
                    UiTimelineV2.TimelinePostItem(
                        historyTestPost(
                            1,
                        ).copy(references = persistentListOf(UiTimelineV2.Post.Reference(quote.statusKey, ReferenceType.Quote))),
                        UiTimelineV2.PostPresentation(quotes = persistentListOf(quote)),
                    ),
                )
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val reader = TimelineReader("quoted", history, database, store, backgroundScope)
            reader.restore()
            load(reader)
            saveToDatabase(database, listOf(TimelinePagingMapper.toDb(quote.copy(sensitive = true), "other-source")))
            reads.count.set(0)
            val updated = assertNotNull(load(reader).single().baseItem.asTimelinePostItem())
            assertTrue(
                updated.presentation.quotes
                    .single()
                    .sensitive,
            )
            assertEquals(1, reads.count.get())
        }

    @Test
    fun pageCacheEvictsOldWindowsWhileKeepingRecentDecodedPosts() =
        runTest {
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val reader = TimelineReader("bounded", history, database, store, backgroundScope)
            reader.restore()
            val dao = database.timelineHistoryDao()
            for (offset in 0 until 240 step 20) reader.pageItems(dao.page(history.key, offset, 20))
            reads.count.set(0)
            reader.pageItems(dao.page(history.key, 0, 20))
            assertEquals(20, reads.count.get(), "Decoded history must not accumulate without a bound")
            reads.count.set(0)
            reader.pageItems(dao.page(history.key, 220, 20))
            assertEquals(0, reads.count.get(), "Recent pages should survive head checks and source replacement")
        }

    @Test
    fun aLongFilteredPrefixAndDisablingFiltersFindTheCorrectHead() =
        runTest {
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val reader = TimelineReader("hidden-prefix", history, database, store, backgroundScope)
            reader.setVisibility("hide-prefix") { it.statusKey.id.toInt() > 225 }
            reads.historyPages.set(0)
            reader.restore()
            assertTrue(reads.historyPages.get() <= 4, "Long filtered prefixes must not turn into many small database scans")
            val dao = database.timelineHistoryDao()
            assertEquals(dao.page(history.key, 225, 1).single(), reader.visibleHead())
            reader.setVisibility("unfiltered", null)
            reads.count.set(0)
            assertEquals(dao.head(history.key), reader.visibleHead())
            assertEquals(0, reads.count.get())
            assertEquals(1, requests)
        }

    private suspend fun load(reader: TimelineReader): List<TimelinePageItem> {
        val paging = TimelineHistoryPagingSource(database, reader) { error("A cached read cannot activate a gap") }
        return try {
            assertIs<PagingSource.LoadResult.Page<TimelineHistoryPagingKey, TimelinePageItem>>(
                paging.load(PagingSource.LoadParams.Refresh(null, 20, false)),
            ).data
        } finally {
            paging.invalidate()
        }
    }

    /** Count actual root rows crossing SQLite, without adding a production instrumentation hook. */
    private class RootReads(
        private val delegate: SQLiteDriver,
    ) : SQLiteDriver by delegate {
        val count = AtomicInteger()
        val historyPages = AtomicInteger()

        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    val root = sql.contains("DbStatus.content AS status_content")
                    val historyPage = sql.contains("ORDER BY ordinal, position LIMIT") && sql.contains("OFFSET")
                    if (!root && !historyPage) return statement
                    return object : SQLiteStatement by statement {
                        private var started = false

                        override fun step(): Boolean {
                            if (!started && historyPage) historyPages.incrementAndGet()
                            started = true
                            return statement.step().also { if (it && root) count.incrementAndGet() }
                        }

                        override fun reset() {
                            started = false
                            statement.reset()
                        }
                    }
                }
            }
        }
    }
}
