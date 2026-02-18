package dev.dimension.flare.data.datasource.microblog.list

import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ListHandlerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var fakeLoader: FakeListLoader
    private lateinit var handler: ListHandler

    private val accountKey = MicroBlogKey(id = "testuser", host = "test.social")
    private val pagingKey = "test_lists"

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        fakeLoader = FakeListLoader()

        startKoin {
            modules(
                module {
                    single { db }
                },
            )
        }

        handler =
            ListHandler(
                pagingKey = pagingKey,
                accountKey = accountKey,
                loader = fakeLoader,
            )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun createInsertsIntoDatabase() =
        runTest {
            val metaData = ListMetaData(title = "My List", description = "Description")
            fakeLoader.nextCreateResult =
                UiList.List(
                    id = "list-1",
                    title = "My List",
                    description = "Description",
                )

            handler.create(metaData)

            // Verify that the list was inserted into the database
            val listKey = MicroBlogKey("list-1", accountKey.host)
            val dbList =
                db
                    .listDao()
                    .getList(listKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(dbList)
            assertEquals("list-1", dbList.content.data.id)
            assertEquals("My List", dbList.content.data.title)

            // Verify paging entry exists
            val pagingEntries = db.listDao().getListKeysFlow(pagingKey).first()
            assertEquals(1, pagingEntries.size)
            assertEquals(
                "list-1",
                pagingEntries
                    .first()
                    .list.content.data.id,
            )
        }

    @Test
    fun updateModifiesDatabaseContent() =
        runTest {
            // First, create a list
            val createResult =
                UiList.List(
                    id = "list-2",
                    title = "Original Title",
                    description = "Original",
                )
            fakeLoader.nextCreateResult = createResult
            handler.create(ListMetaData(title = "Original Title"))

            // Now update it
            val updatedResult =
                UiList.List(
                    id = "list-2",
                    title = "Updated Title",
                    description = "Updated Desc",
                )
            fakeLoader.nextUpdateResult = updatedResult

            handler.update("list-2", ListMetaData(title = "Updated Title", description = "Updated Desc"))

            // Verify the database content was updated
            val listKey = MicroBlogKey("list-2", accountKey.host)
            val dbList =
                db
                    .listDao()
                    .getList(listKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(dbList)
            assertEquals("Updated Title", dbList.content.data.title)
        }

    @Test
    fun deleteRemovesFromDatabase() =
        runTest {
            // Create a list first
            fakeLoader.nextCreateResult =
                UiList.List(
                    id = "list-3",
                    title = "To Delete",
                )
            handler.create(ListMetaData(title = "To Delete"))

            // Verify it exists
            val listKey = MicroBlogKey("list-3", accountKey.host)
            val before =
                db
                    .listDao()
                    .getList(listKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(before)

            // Delete it
            handler.delete("list-3")

            // Verify the list was removed
            val after =
                db
                    .listDao()
                    .getList(listKey, AccountType.Specific(accountKey))
                    .first()
            assertNull(after)

            // Verify paging entry was removed
            val pagingEntries = db.listDao().getListKeysFlow(pagingKey).first()
            assertTrue(pagingEntries.isEmpty())
        }

    @Test
    fun insertToDatabaseWritesDirectly() =
        runTest {
            val list =
                UiList.List(
                    id = "list-4",
                    title = "Direct Insert",
                    description = "Directly inserted",
                )

            handler.insertToDatabase(list)

            // Verify the list was inserted
            val listKey = MicroBlogKey("list-4", accountKey.host)
            val dbList =
                db
                    .listDao()
                    .getList(listKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(dbList)
            assertEquals("Direct Insert", dbList.content.data.title)

            // Verify paging entry exists
            val pagingEntries = db.listDao().getListKeysFlow(pagingKey).first()
            assertEquals(1, pagingEntries.size)
        }

    @Test
    fun withDatabaseUpdatesContent() =
        runTest {
            // Insert a list first
            val original =
                UiList.List(
                    id = "list-5",
                    title = "Before Update",
                )
            handler.insertToDatabase(original)

            // Use withDatabase to update content
            handler.withDatabase { update ->
                update(
                    UiList.List(
                        id = "list-5",
                        title = "After Update",
                        description = "Updated via withDatabase",
                    ),
                )
            }

            // Verify the content was updated
            val listKey = MicroBlogKey("list-5", accountKey.host)
            val dbList =
                db
                    .listDao()
                    .getList(listKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(dbList)
            assertEquals("After Update", dbList.content.data.title)
        }

    @Test
    fun createWithLoaderFailureDoesNotInsert() =
        runTest {
            fakeLoader.shouldFail = true

            handler.create(ListMetaData(title = "Should Fail"))

            // Verify nothing was inserted into the database
            val pagingEntries = db.listDao().getListKeysFlow(pagingKey).first()
            assertTrue(pagingEntries.isEmpty())
        }

    @Test
    fun deleteWithLoaderFailureDoesNotRemove() =
        runTest {
            // Create a list first
            fakeLoader.nextCreateResult =
                UiList.List(
                    id = "list-6",
                    title = "Should Survive",
                )
            handler.create(ListMetaData(title = "Should Survive"))

            // Now make loader fail and try to delete
            fakeLoader.shouldFail = true
            handler.delete("list-6")

            // Verify the list is still in the database
            val listKey = MicroBlogKey("list-6", accountKey.host)
            val dbList =
                db
                    .listDao()
                    .getList(listKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(dbList)
            assertEquals("Should Survive", dbList.content.data.title)
        }

    @Test
    fun dataLoadsThroughPagerAndSavesToDatabase() =
        runTest {
            // Pre-populate the fake loader with items
            fakeLoader.nextCreateResult =
                UiList.List(id = "paged-1", title = "First")
            fakeLoader.create(ListMetaData(title = "First"))
            fakeLoader.nextCreateResult =
                UiList.List(id = "paged-2", title = "Second")
            fakeLoader.create(ListMetaData(title = "Second"))

            // Collect the paging data — this triggers the remote mediator
            val snapshot = handler.data.asSnapshot()

            assertEquals(2, snapshot.size)
            val titles = snapshot.map { it.title }.toSet()
            assertTrue(titles.contains("First"))
            assertTrue(titles.contains("Second"))

            // Verify items were persisted to the database by the remote mediator
            val dbEntries = db.listDao().getListKeysFlow(pagingKey).first()
            assertEquals(2, dbEntries.size)
        }

    @Test
    fun cacheDataReflectsDatabaseState() =
        runTest {
            // Initially empty
            val initial = handler.cacheData.first()
            assertTrue(initial.isEmpty())

            // Insert items via insertToDatabase
            handler.insertToDatabase(
                UiList.List(id = "cache-1", title = "Cached A"),
            )
            handler.insertToDatabase(
                UiList.List(id = "cache-2", title = "Cached B"),
            )

            // cacheData should now reflect the two inserted items
            val cached = handler.cacheData.first()
            assertEquals(2, cached.size)
            val titles = cached.map { it.title }.toSet()
            assertTrue(titles.contains("Cached A"))
            assertTrue(titles.contains("Cached B"))

            // Delete one and verify cacheData updates
            fakeLoader.shouldFail = false
            handler.delete("cache-1")
            val afterDelete = handler.cacheData.first()
            assertEquals(1, afterDelete.size)
            assertEquals("Cached B", afterDelete.first().title)
        }
}

private class FakeListLoader : ListLoader {
    var nextCreateResult: UiList = UiList.List(id = "default", title = "Default")
    var nextUpdateResult: UiList = UiList.List(id = "default", title = "Default")
    var shouldFail: Boolean = false

    private val items = mutableListOf<UiList>()

    override val supportedMetaData: ImmutableList<ListMetaDataType> =
        persistentListOf(ListMetaDataType.TITLE, ListMetaDataType.DESCRIPTION)

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList> =
        PagingResult(
            data = items.toList(),
            endOfPaginationReached = true,
        )

    override suspend fun info(listId: String): UiList = items.first { it.id == listId }

    override suspend fun create(metaData: ListMetaData): UiList {
        if (shouldFail) throw RuntimeException("Fake loader failure")
        items.add(nextCreateResult)
        return nextCreateResult
    }

    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    override suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): UiList {
        if (shouldFail) throw RuntimeException("Fake loader failure")
        items.replaceAll { if (it.id == listId) nextUpdateResult else it }
        return nextUpdateResult
    }

    override suspend fun delete(listId: String) {
        if (shouldFail) throw RuntimeException("Fake loader failure")
        items.removeAll { it.id == listId }
    }
}
