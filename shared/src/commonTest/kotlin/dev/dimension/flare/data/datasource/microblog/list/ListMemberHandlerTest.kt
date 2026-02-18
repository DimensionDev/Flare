package dev.dimension.flare.data.datasource.microblog.list

import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiList
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
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ListMemberHandlerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var fakeLoader: FakeListMemberLoader
    private lateinit var handler: ListMemberHandler

    private val accountKey = MicroBlogKey(id = "testuser", host = "test.social")
    private val pagingKey = "test_members"

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        fakeLoader = FakeListMemberLoader()

        startKoin {
            modules(
                module {
                    single { db }
                    single<PlatformFormatter> {
                        object : PlatformFormatter {
                            override fun formatNumber(number: Long) = number.toString()

                            override fun formatRelativeInstant(instant: Instant) = instant.toString()

                            override fun formatFullInstant(instant: Instant) = instant.toString()

                            override fun formatAbsoluteInstant(instant: Instant) = instant.toString()
                        }
                    }
                },
            )
        }

        handler =
            ListMemberHandler(
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
    fun addMemberInsertsMemberAndUserIntoDatabase() =
        runTest {
            val userKey = MicroBlogKey(id = "user-1", host = "test.social")
            val listId = "list-1"
            fakeLoader.nextAddMemberResult = createDbUser(userKey)

            handler.addMember(listId, userKey)

            // Verify DbListMember was inserted
            val listKey = MicroBlogKey(listId, accountKey.host)
            val members = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(1, members.size)
            assertEquals(userKey, members.first().member.memberKey)

            // Verify DbUser was inserted
            val savedUser = db.userDao().findByKey(userKey).first()
            assertEquals("user-1", savedUser?.userKey?.id)
        }

    @Test
    fun addMultipleMembersToSameList() =
        runTest {
            val listId = "list-2"
            val userKey1 = MicroBlogKey(id = "user-a", host = "test.social")
            val userKey2 = MicroBlogKey(id = "user-b", host = "test.social")

            fakeLoader.nextAddMemberResult = createDbUser(userKey1)
            handler.addMember(listId, userKey1)

            fakeLoader.nextAddMemberResult = createDbUser(userKey2)
            handler.addMember(listId, userKey2)

            val listKey = MicroBlogKey(listId, accountKey.host)
            val members = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(2, members.size)
            val memberKeys = members.map { it.member.memberKey }.toSet()
            assertTrue(memberKeys.contains(userKey1))
            assertTrue(memberKeys.contains(userKey2))
        }

    @Test
    fun removeMemberDeletesFromDatabase() =
        runTest {
            val userKey = MicroBlogKey(id = "user-3", host = "test.social")
            val listId = "list-3"
            fakeLoader.nextAddMemberResult = createDbUser(userKey)

            // Add first
            handler.addMember(listId, userKey)

            val listKey = MicroBlogKey(listId, accountKey.host)
            val before = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(1, before.size)

            // Remove
            handler.removeMember(listId, userKey)

            val after = db.listDao().getListMembersFlow(listKey).first()
            assertTrue(after.isEmpty())
        }

    @Test
    fun removeMemberOnlyRemovesTargetMember() =
        runTest {
            val listId = "list-4"
            val userKey1 = MicroBlogKey(id = "user-keep", host = "test.social")
            val userKey2 = MicroBlogKey(id = "user-remove", host = "test.social")

            fakeLoader.nextAddMemberResult = createDbUser(userKey1)
            handler.addMember(listId, userKey1)
            fakeLoader.nextAddMemberResult = createDbUser(userKey2)
            handler.addMember(listId, userKey2)

            // Remove only userKey2
            handler.removeMember(listId, userKey2)

            val listKey = MicroBlogKey(listId, accountKey.host)
            val remaining = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(1, remaining.size)
            assertEquals(userKey1, remaining.first().member.memberKey)
        }

    @Test
    fun addMemberWithLoaderFailureDoesNotInsert() =
        runTest {
            val userKey = MicroBlogKey(id = "user-fail", host = "test.social")
            fakeLoader.shouldFail = true

            handler.addMember("list-5", userKey)

            val listKey = MicroBlogKey("list-5", accountKey.host)
            val members = db.listDao().getListMembersFlow(listKey).first()
            assertTrue(members.isEmpty())
        }

    @Test
    fun removeMemberWithLoaderFailureDoesNotDelete() =
        runTest {
            val userKey = MicroBlogKey(id = "user-survive", host = "test.social")
            val listId = "list-6"
            fakeLoader.nextAddMemberResult = createDbUser(userKey)
            handler.addMember(listId, userKey)

            // Now make loader fail and try to remove
            fakeLoader.shouldFail = true
            handler.removeMember(listId, userKey)

            // Member should still be there
            val listKey = MicroBlogKey(listId, accountKey.host)
            val members = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(1, members.size)
            assertEquals(userKey, members.first().member.memberKey)
        }

    @Test
    fun listMembersFlowReflectsDatabaseState() =
        runTest {
            val listId = "list-7"
            val listKey = MicroBlogKey(listId, accountKey.host)
            val userKey1 = MicroBlogKey(id = "member-1", host = "test.social")
            val userKey2 = MicroBlogKey(id = "member-2", host = "test.social")

            // Initially empty
            val initial = db.listDao().getListMembersFlow(listKey).first()
            assertTrue(initial.isEmpty())

            // Add members
            fakeLoader.nextAddMemberResult = createDbUser(userKey1, name = "Alice")
            handler.addMember(listId, userKey1)
            fakeLoader.nextAddMemberResult = createDbUser(userKey2, name = "Bob")
            handler.addMember(listId, userKey2)

            // Flow should reflect 2 members
            val members = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(2, members.size)
            val memberKeys = members.map { it.member.memberKey }.toSet()
            assertTrue(memberKeys.contains(userKey1))
            assertTrue(memberKeys.contains(userKey2))

            // Remove one, flow should reflect 1 member
            fakeLoader.shouldFail = false
            handler.removeMember(listId, userKey1)
            val afterRemove = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(1, afterRemove.size)
            assertEquals(userKey2, afterRemove.first().member.memberKey)
        }

    @Test
    fun listMembersListFlowRendersUserProfiles() =
        runTest {
            val listId = "list-render"
            val userKey1 = MicroBlogKey(id = "render-1", host = "test.social")
            val userKey2 = MicroBlogKey(id = "render-2", host = "test.social")

            fakeLoader.nextAddMemberResult = createDbUser(userKey1, name = "Alice")
            handler.addMember(listId, userKey1)
            fakeLoader.nextAddMemberResult = createDbUser(userKey2, name = "Bob")
            handler.addMember(listId, userKey2)

            // listMembersListFlow calls render() — verify it returns UiProfile items
            val rendered = handler.listMembersListFlow(listId).first()
            assertEquals(2, rendered.size)
            val names = rendered.map { it.name.raw }.toSet()
            assertTrue(names.contains("Alice"))
            assertTrue(names.contains("Bob"))
        }

    @Test
    fun listMembersPagerLoadsAndSavesToDatabase() =
        runTest {
            val listId = "list-pager"
            val userKey1 = MicroBlogKey(id = "paged-1", host = "test.social")
            val userKey2 = MicroBlogKey(id = "paged-2", host = "test.social")

            // Pre-populate the fake loader with members
            fakeLoader.nextAddMemberResult = createDbUser(userKey1, name = "Paged Alice")
            fakeLoader.addMember(listId, userKey1)
            fakeLoader.nextAddMemberResult = createDbUser(userKey2, name = "Paged Bob")
            fakeLoader.addMember(listId, userKey2)

            // Collect the paging data — this triggers the remote mediator
            val snapshot = handler.listMembers(listId).asSnapshot()

            assertEquals(2, snapshot.size)
            val names = snapshot.map { it.name.raw }.toSet()
            assertTrue(names.contains("Paged Alice"))
            assertTrue(names.contains("Paged Bob"))

            // Verify members were persisted to the database
            val listKey = MicroBlogKey(listId, accountKey.host)
            val dbMembers = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(2, dbMembers.size)
        }

    private fun createDbUser(
        userKey: MicroBlogKey,
        name: String = userKey.id,
    ): DbUser =
        DbUser(
            userKey = userKey,
            platformType = PlatformType.Mastodon,
            name = name,
            handle = userKey.id,
            host = userKey.host,
            content =
                UserContent.Mastodon(
                    Account(
                        id = userKey.id,
                        username = userKey.id,
                        acct = "${userKey.id}@${userKey.host}",
                        displayName = name,
                        url = "https://${userKey.host}/@${userKey.id}",
                    ),
                ),
        )
}

private class FakeListMemberLoader : ListMemberLoader {
    var nextAddMemberResult: DbUser? = null
    var shouldFail: Boolean = false

    private val members = mutableMapOf<String, MutableList<DbUser>>()

    override suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listId: String,
    ): PagingResult<DbUser> =
        PagingResult(
            data = members[listId]?.toList() ?: emptyList(),
            endOfPaginationReached = true,
        )

    override suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ): DbUser {
        if (shouldFail) throw RuntimeException("Fake loader failure")
        val user = nextAddMemberResult ?: throw IllegalStateException("nextAddMemberResult not set")
        members.getOrPut(listId) { mutableListOf() }.add(user)
        return user
    }

    override suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        if (shouldFail) throw RuntimeException("Fake loader failure")
        members[listId]?.removeAll { it.userKey == userKey }
    }

    override suspend fun loadUserLists(
        pageSize: Int,
        request: PagingRequest,
        userKey: MicroBlogKey,
    ): PagingResult<UiList> =
        PagingResult(
            data = emptyList(),
            endOfPaginationReached = true,
        )
}
