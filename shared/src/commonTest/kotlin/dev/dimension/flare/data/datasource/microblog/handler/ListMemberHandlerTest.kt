package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.mapper.upsertUsers
import dev.dimension.flare.data.database.cache.model.DbListMember
import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.createPagingRemoteMediator
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.render.toUiPlainText
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
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
            fakeLoader.nextAddMemberResult = createUiProfile(userKey)

            handler.addMember(listId, userKey)

            val listKey = MicroBlogKey(listId, accountKey.host)
            val members = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(1, members.size)
            assertEquals(userKey, members.first().member.memberKey)

            val savedUser = db.userDao().findByKey(userKey).first()
            assertEquals("user-1", savedUser?.userKey?.id)
        }

    @Test
    fun addMultipleMembersToSameList() =
        runTest {
            val listId = "list-2"
            val userKey1 = MicroBlogKey(id = "user-a", host = "test.social")
            val userKey2 = MicroBlogKey(id = "user-b", host = "test.social")

            fakeLoader.nextAddMemberResult = createUiProfile(userKey1)
            handler.addMember(listId, userKey1)

            fakeLoader.nextAddMemberResult = createUiProfile(userKey2)
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
            fakeLoader.nextAddMemberResult = createUiProfile(userKey)

            handler.addMember(listId, userKey)

            val listKey = MicroBlogKey(listId, accountKey.host)
            val before = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(1, before.size)

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

            fakeLoader.nextAddMemberResult = createUiProfile(userKey1)
            handler.addMember(listId, userKey1)
            fakeLoader.nextAddMemberResult = createUiProfile(userKey2)
            handler.addMember(listId, userKey2)

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
            fakeLoader.nextAddMemberResult = createUiProfile(userKey)
            handler.addMember(listId, userKey)

            fakeLoader.shouldFail = true
            handler.removeMember(listId, userKey)

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

            val initial = db.listDao().getListMembersFlow(listKey).first()
            assertTrue(initial.isEmpty())

            fakeLoader.nextAddMemberResult = createUiProfile(userKey1, name = "Alice")
            handler.addMember(listId, userKey1)
            fakeLoader.nextAddMemberResult = createUiProfile(userKey2, name = "Bob")
            handler.addMember(listId, userKey2)

            val members = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(2, members.size)

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

            fakeLoader.nextAddMemberResult = createUiProfile(userKey1, name = "Alice")
            handler.addMember(listId, userKey1)
            fakeLoader.nextAddMemberResult = createUiProfile(userKey2, name = "Bob")
            handler.addMember(listId, userKey2)

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

            fakeLoader.setMembers(
                listId,
                listOf(
                    createUiProfile(userKey1, name = "Paged Alice"),
                    createUiProfile(userKey2, name = "Paged Bob"),
                ),
            )

            val snapshot = handler.listMembers(listId).asSnapshot()

            assertEquals(2, snapshot.size)
            val names = snapshot.map { it.name.raw }.toSet()
            assertTrue(names.contains("Paged Alice"))
            assertTrue(names.contains("Paged Bob"))

            val listKey = MicroBlogKey(listId, accountKey.host)
            val dbMembers = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(2, dbMembers.size)
        }

    @Test
    fun listMembersPagerHandlesLoadMore() =
        runTest {
            val listId = "list-load-more"
            val members =
                (1..50).map { i ->
                    createUiProfile(
                        userKey = MicroBlogKey(id = "user-$i", host = "test.social"),
                        name = "User $i",
                    )
                }
            fakeLoader.setMembers(listId, members)

            val snapshot =
                handler.listMembers(listId).asSnapshot {
                    appendScrollWhile {
                        it.name.raw != "User 50"
                    }
                }

            assertEquals(50, snapshot.size)
            val names = snapshot.map { it.name.raw }.toSet()
            assertTrue(names.contains("User 1"))
            assertTrue(names.contains("User 50"))
        }

    @Test
    fun listMembersPagerHandlesRefresh() =
        runTest {
            val listId = "list-refresh"
            val listKey = MicroBlogKey(listId, accountKey.host)
            val initialMembers =
                (1..20).map { i ->
                    createUiProfile(
                        userKey = MicroBlogKey(id = "refresh-user-$i", host = "test.social"),
                        name = "Refresh User $i",
                    )
                }
            fakeLoader.setMembers(listId, initialMembers)

            val initialSnapshot = handler.listMembers(listId).asSnapshot()
            assertEquals(20, initialSnapshot.size)

            val updatedMembers = initialMembers.take(10)
            fakeLoader.setMembers(listId, updatedMembers)

            val refreshMediator =
                createPagingRemoteMediator<Any, UiProfile>(
                    pagingKey = "${pagingKey}_members_$listId",
                    database = db,
                    onLoad = { pageSize, request ->
                        fakeLoader.loadMembers(
                            pageSize = pageSize,
                            request = request,
                            listId = listId,
                        )
                    },
                    onSave = { request, data ->
                        db.connect {
                            if (request == PagingRequest.Refresh) {
                                db.listDao().deleteMembersByListKey(listKey)
                            }
                            db.listDao().insertAllMember(
                                data.map { item ->
                                    DbListMember(
                                        listKey = listKey,
                                        memberKey = item.key,
                                    )
                                },
                            )
                            db.upsertUsers(data.map { it.toDbUser() })
                        }
                    },
                )
            refreshMediator.doLoad(
                loadType = LoadType.REFRESH,
                state =
                    PagingState(
                        pages = emptyList(),
                        anchorPosition = null,
                        config = dev.dimension.flare.data.datasource.microblog.pagingConfig,
                        leadingPlaceholderCount = 0,
                    ),
            )

            val refreshedMembers = db.listDao().getListMembersFlow(listKey).first()
            assertEquals(10, refreshedMembers.size)
            val memberKeys = refreshedMembers.map { it.member.memberKey.id }.toSet()
            assertTrue(memberKeys.contains("refresh-user-1"))
            assertFalse(memberKeys.contains("refresh-user-20"))
        }

    private fun createUiProfile(
        userKey: MicroBlogKey,
        name: String = userKey.id,
    ): UiProfile =
        UiProfile(
            key = userKey,
            handle =
                UiHandle(
                    raw = userKey.id,
                    host = userKey.host,
                ),
            avatar = "https://${userKey.host}/${userKey.id}.png",
            nameInternal = name.toUiPlainText(),
            platformType = PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices =
                UiProfile.Matrices(
                    fansCount = 0,
                    followsCount = 0,
                    statusesCount = 0,
                ),
            mark = persistentListOf(),
            bottomContent = null,
        )
}

private class FakeListMemberLoader : ListMemberLoader {
    var nextAddMemberResult: UiProfile? = null
    var shouldFail: Boolean = false

    private val members = mutableMapOf<String, MutableList<UiProfile>>()

    fun setMembers(
        listId: String,
        users: List<UiProfile>,
    ) {
        members[listId] = users.toMutableList()
    }

    override suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listId: String,
    ): PagingResult<UiProfile> {
        val allMembers = members[listId] ?: emptyList()
        val page =
            when (request) {
                is PagingRequest.Refresh -> 1
                is PagingRequest.Append ->
                    request.nextKey.toIntOrNull()
                        ?: return PagingResult(data = emptyList(), endOfPaginationReached = true)
                is PagingRequest.Prepend -> return PagingResult(data = emptyList(), endOfPaginationReached = true)
            }

        val fromIndex = (page - 1) * pageSize
        if (fromIndex >= allMembers.size) {
            return PagingResult(data = emptyList(), endOfPaginationReached = true)
        }

        val toIndex = minOf(fromIndex + pageSize, allMembers.size)
        val data = allMembers.subList(fromIndex, toIndex)
        val nextKey = if (toIndex < allMembers.size) (page + 1).toString() else null

        return PagingResult(
            data = data,
            nextKey = nextKey,
            endOfPaginationReached = nextKey == null,
        )
    }

    override suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ): UiProfile {
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
        members[listId]?.removeAll { it.key == userKey }
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
