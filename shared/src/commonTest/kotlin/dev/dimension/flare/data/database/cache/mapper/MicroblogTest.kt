package dev.dimension.flare.data.database.cache.mapper

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MicroblogTest : RobolectricTest() {
    private lateinit var db: CacheDatabase

    @BeforeTest
    fun setup() {
        val db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        this.db = db
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSaveToDatabaseWithTestContent() =
        runTest {
            val userKey = MicroBlogKey(id = Uuid.random().toString(), host = "test.com")
            val statusKey = MicroBlogKey(id = Uuid.random().toString(), host = "test.com")
            val accountKey = MicroBlogKey(id = Uuid.random().toString(), host = "test.com")

            val userContent = UserContent.Test("test user content")
            val statusContent = StatusContent.Test("test status content")

            val user =
                DbUser(
                    userKey = userKey,
                    platformType = PlatformType.Mastodon,
                    name = "Test User",
                    handle = "testuser",
                    host = "test.com",
                    content = userContent,
                )

            val status =
                DbStatus(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                    content = statusContent,
                    text = "test status text",
                    createdAt = Clock.System.now(),
                )

            val statusWithUser =
                DbStatusWithUser(
                    data = status,
                    user = user,
                )

            val timelineItem =
                createDbPagingTimelineWithStatus(
                    accountKey = accountKey,
                    pagingKey = "home",
                    sortId = 1L,
                    status = statusWithUser,
                    references = emptyMap(),
                )

            saveToDatabase(db, listOf(timelineItem))

            // Verify User
            val savedUser =
                db
                    .userDao()
                    .findByKey(userKey)
                    .first()
            assertNotNull(savedUser)
            assertEquals(userContent, savedUser.content)

            // Verify Status
            val savedStatus =
                db
                    .statusDao()
                    .get(statusKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(savedStatus)
            assertEquals(statusContent, savedStatus.content)
        }

    @Test
    fun testSaveToDatabaseWithReference() =
        runTest {
            val accountKey = MicroBlogKey(id = Uuid.random().toString(), host = "test.com")

            // Main Status
            val mainUserKey = MicroBlogKey(id = Uuid.random().toString(), host = "test.com")
            val mainStatusKey = MicroBlogKey(id = Uuid.random().toString(), host = "test.com")
            val mainUserContent = UserContent.Test("main user content")
            val mainStatusContent = StatusContent.Test("main status content")

            val mainUser =
                DbUser(
                    userKey = mainUserKey,
                    platformType = PlatformType.Mastodon,
                    name = "Main User",
                    handle = "mainuser",
                    host = "test.com",
                    content = mainUserContent,
                )

            val mainStatus =
                DbStatus(
                    statusKey = mainStatusKey,
                    accountType = AccountType.Specific(accountKey),
                    userKey = mainUserKey,
                    content = mainStatusContent,
                    text = "main status text",
                    createdAt = Clock.System.now(),
                )

            val mainStatusWithUser =
                DbStatusWithUser(
                    data = mainStatus,
                    user = mainUser,
                )

            // Referenced Status
            val refUserKey = MicroBlogKey(id = Uuid.random().toString(), host = "test.com")
            val refStatusKey = MicroBlogKey(id = Uuid.random().toString(), host = "test.com")
            val refUserContent = UserContent.Test("ref user content")
            val refStatusContent = StatusContent.Test("ref status content")

            val refUser =
                DbUser(
                    userKey = refUserKey,
                    platformType = PlatformType.Mastodon,
                    name = "Ref User",
                    handle = "refuser",
                    host = "test.com",
                    content = refUserContent,
                )

            val refStatus =
                DbStatus(
                    statusKey = refStatusKey,
                    accountType = AccountType.Specific(accountKey),
                    userKey = refUserKey,
                    content = refStatusContent,
                    text = "ref status text",
                    createdAt = Clock.System.now(),
                )

            val refStatusWithUser =
                DbStatusWithUser(
                    data = refStatus,
                    user = refUser,
                )

            val references = mapOf(ReferenceType.Reply to listOf(refStatusWithUser))

            val timelineItem =
                createDbPagingTimelineWithStatus(
                    accountKey = accountKey,
                    pagingKey = "home",
                    sortId = 1L,
                    status = mainStatusWithUser,
                    references = references,
                )

            saveToDatabase(db, listOf(timelineItem))

            // Verify Main Status
            val savedMainStatus =
                db
                    .statusDao()
                    .get(mainStatusKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(savedMainStatus)
            assertEquals(mainStatusContent, savedMainStatus.content)

            // Verify Referenced Status
            val savedRefStatus =
                db
                    .statusDao()
                    .get(refStatusKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(savedRefStatus)
            assertEquals(refStatusContent, savedRefStatus.content)

            // Verify Reference
            val savedReferences = db.statusReferenceDao().getByStatusKey(mainStatusKey)
            assertEquals(1, savedReferences.size)
            val savedRef = savedReferences.first()
            assertEquals(ReferenceType.Reply, savedRef.referenceType)
            assertEquals(refStatusKey, savedRef.referenceStatusKey)
        }

    @Test
    fun testReferencesClearedWhenMissingInSubsequentInsert() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "test.com")

            val mainUserKey = MicroBlogKey(id = "main_user", host = "test.com")
            val mainStatusKey = MicroBlogKey(id = "main_status", host = "test.com")
            val mainUserContent = UserContent.Test("main user content")
            val mainStatusContent = StatusContent.Test("main status content")

            val mainUser =
                DbUser(
                    userKey = mainUserKey,
                    platformType = PlatformType.Mastodon,
                    name = "Main User",
                    handle = "mainuser",
                    host = "test.com",
                    content = mainUserContent,
                )

            val mainStatus =
                DbStatus(
                    statusKey = mainStatusKey,
                    accountType = AccountType.Specific(accountKey),
                    userKey = mainUserKey,
                    content = mainStatusContent,
                    text = "main status text",
                    createdAt = Clock.System.now(),
                )

            val mainStatusWithUser =
                DbStatusWithUser(
                    data = mainStatus,
                    user = mainUser,
                )

            val refUserKey = MicroBlogKey(id = "ref_user", host = "test.com")
            val refStatusKey = MicroBlogKey(id = "ref_status", host = "test.com")
            val refUserContent = UserContent.Test("ref user content")
            val refStatusContent = StatusContent.Test("ref status content")

            val refUser =
                DbUser(
                    userKey = refUserKey,
                    platformType = PlatformType.Mastodon,
                    name = "Ref User",
                    handle = "refuser",
                    host = "test.com",
                    content = refUserContent,
                )

            val refStatus =
                DbStatus(
                    statusKey = refStatusKey,
                    accountType = AccountType.Specific(accountKey),
                    userKey = refUserKey,
                    content = refStatusContent,
                    text = "ref status text",
                    createdAt = Clock.System.now(),
                )

            val refStatusWithUser =
                DbStatusWithUser(
                    data = refStatus,
                    user = refUser,
                )

            val initialReferences = mapOf(ReferenceType.Reply to listOf(refStatusWithUser))

            val initialTimelineItem =
                createDbPagingTimelineWithStatus(
                    accountKey = accountKey,
                    pagingKey = "home",
                    sortId = 1L,
                    status = mainStatusWithUser,
                    references = initialReferences,
                )

            saveToDatabase(db, listOf(initialTimelineItem))

            val savedRefsBefore = db.statusReferenceDao().getByStatusKey(mainStatusKey)
            assertEquals(1, savedRefsBefore.size)

            val updatedTimelineItem =
                createDbPagingTimelineWithStatus(
                    accountKey = accountKey,
                    pagingKey = "home",
                    sortId = 2L,
                    status = mainStatusWithUser,
                    references = emptyMap(),
                )

            saveToDatabase(db, listOf(updatedTimelineItem))

            val savedRefsAfter = db.statusReferenceDao().getByStatusKey(mainStatusKey)
            assertEquals(1, savedRefsAfter.size)

            val savedMainStatus =
                db
                    .statusDao()
                    .get(mainStatusKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(savedMainStatus)
            assertEquals(mainStatusContent, savedMainStatus.content)

            val savedRefStatus =
                db
                    .statusDao()
                    .get(refStatusKey, AccountType.Specific(accountKey))
                    .first()
            assertNotNull(savedRefStatus)
            assertEquals(refStatusContent, savedRefStatus.content)

            val paging = db.pagingTimelineDao().getPagingSource("home")
            val pager = TestPager(config = PagingConfig(pageSize = 20), paging)
            val refreshResult = pager.refresh()
            assertIs<PagingSource.LoadResult.Page<Int, DbPagingTimelineWithStatus>>(refreshResult)
        }
}
