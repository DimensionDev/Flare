package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.LoadState
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbUserHistory
import dev.dimension.flare.data.database.cache.model.DbUserRelation
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.createSampleStatus
import dev.dimension.flare.ui.model.createSampleUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RelationHandlerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var loader: FakeRelationLoader
    private lateinit var handler: RelationHandler

    private val accountKey = MicroBlogKey(id = "account-1", host = "test.social")
    private val userKey = MicroBlogKey(id = "user-1", host = "test.social")

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        loader = FakeRelationLoader(supportedTypes = RelationActionType.entries.toSet())
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun relationRefreshStoresRelationInDatabase() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                    },
                )
            }

            val expected = UiRelation(following = true, muted = true)
            loader.nextRelation = expected
            handler = RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader)
            val cacheable = handler.relation(userKey)

            val valueDeferred =
                async {
                    cacheable.data
                        .filterIsInstance<CacheState.Success<UiRelation>>()
                        .first()
                        .data
                }

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.NotLoading)
            assertEquals(expected, valueDeferred.await())

            val saved = db.userDao().getUserRelation(AccountType.Specific(accountKey), userKey).first()
            assertEquals(expected, saved?.relation)
            assertEquals(1, loader.relationCallCount)
        }

    @Test
    fun followSuccessSetsFollowingTrue() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                    },
                )
            }

            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                    relation = UiRelation(following = false),
                ),
            )

            handler = RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader)
            handler.follow(userKey)
            advanceUntilIdle()

            val saved = db.userDao().getUserRelation(AccountType.Specific(accountKey), userKey).first()
            assertTrue(saved?.relation?.following == true)
            assertEquals(1, loader.followCallCount)
        }

    @Test
    fun followRequestSuccessSetsPendingRequestTrue() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                    },
                )
            }

            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                    relation = UiRelation(following = false, hasPendingFollowRequestFromYou = false),
                ),
            )

            handler = RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader)
            handler.follow(userKey, requestFollow = true)
            advanceUntilIdle()

            val saved = assertNotNull(db.userDao().getUserRelation(AccountType.Specific(accountKey), userKey).first())
            assertTrue(saved.relation.following == false)
            assertTrue(saved.relation.hasPendingFollowRequestFromYou == true)
            assertEquals(1, loader.followCallCount)
        }

    @Test
    fun followFailureRevertsFollowingFlag() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                    },
                )
            }

            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                    relation = UiRelation(following = false),
                ),
            )
            loader.failFollow = true

            handler = RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader)
            handler.follow(userKey)
            advanceUntilIdle()

            val saved = db.userDao().getUserRelation(AccountType.Specific(accountKey), userKey).first()
            assertTrue(saved?.relation?.following == false)
            assertEquals(1, loader.followCallCount)
        }

    @Test
    fun blockWithoutCachedRelationStillCallsRemote() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                    },
                )
            }

            handler = RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader)
            val job = handler.block(userKey)
            advanceUntilIdle()

            assertTrue(job.isCompleted)
            assertEquals(1, loader.blockCallCount)
            val saved = assertNotNull(db.userDao().getUserRelation(AccountType.Specific(accountKey), userKey).first())
            assertTrue(saved.relation.blocking)
        }

    @Test
    fun blockNotifiesProfileBeforeRemoteRequestAfterCacheCleanup() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                        single<PlatformFormatter> { TestFormatter() }
                    },
                )
            }

            val accountType = AccountType.Specific(accountKey)
            val (_, otherStatusId) = saveTargetAndOtherPosts(pagingKey = "home")
            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = accountType,
                    userKey = userKey,
                    relation = UiRelation(blocking = false),
                ),
            )

            val events = mutableListOf<String>()
            val profileObserver =
                launch {
                    db
                        .userDao()
                        .getUserRelation(accountType, userKey)
                        .filterNotNull()
                        .first { it.relation.blocking }
                    events += "profile_flow"
                }
            advanceUntilIdle()

            loader.onBlock = {
                events += "remote_request"
                assertEquals(listOf("profile_flow", "remote_request"), events)
                assertEquals(
                    listOf(otherStatusId),
                    db.pagingTimelineDao().getByPagingKey("home").map { it.statusId },
                )
                val persisted = assertNotNull(db.userDao().getUserRelation(accountType, userKey).first())
                assertTrue(persisted.relation.blocking)
            }

            handler = RelationHandler(accountType = accountType, dataSource = loader)
            handler.block(userKey)
            advanceUntilIdle()

            assertEquals(listOf("profile_flow", "remote_request"), events)
            profileObserver.cancel()
        }

    @Test
    fun blockDeletesTargetPastFirstCacheCleanupPage() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                        single<PlatformFormatter> { TestFormatter() }
                    },
                )
            }

            val accountType = AccountType.Specific(accountKey)
            val otherUser = createSampleUser().copy(key = MicroBlogKey(id = "other-user", host = "test.social"))
            val otherTimelines =
                (0 until 100).map { index ->
                    TimelinePagingMapper
                        .toDb(
                            data =
                                createSampleStatus(otherUser).copy(
                                    accountType = accountType,
                                    statusKey = MicroBlogKey(id = "other-status-$index", host = "test.social"),
                                ),
                            pagingKey = "home",
                            sortId = index.toLong(),
                        ).let { item ->
                            item.copy(
                                timeline = item.timeline.copy(_id = "a-${index.toString().padStart(3, '0')}"),
                            )
                        }
                }
            val targetTimeline =
                TimelinePagingMapper
                    .toDb(
                        data =
                            createSampleStatus(createSampleUser().copy(key = userKey)).copy(
                                accountType = accountType,
                                statusKey = MicroBlogKey(id = "target-status", host = "test.social"),
                            ),
                        pagingKey = "home",
                        sortId = 100L,
                    ).let { item ->
                        item.copy(timeline = item.timeline.copy(_id = "z-target"))
                    }
            saveToDatabase(db, otherTimelines + targetTimeline)

            handler = RelationHandler(accountType = accountType, dataSource = loader)
            handler.block(userKey)
            advanceUntilIdle()

            val cached = db.pagingTimelineDao().getByPagingKey("home")
            assertEquals(1, loader.blockCallCount)
            assertEquals(100, cached.size)
            assertTrue(cached.none { it.statusId == targetTimeline.timeline.statusId })
        }

    @Test
    fun blockFailureKeepsLocalDeletionAndRevertsRelation() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                        single<PlatformFormatter> { TestFormatter() }
                    },
                )
            }

            val accountType = AccountType.Specific(accountKey)
            val (_, otherStatusId) = saveTargetAndOtherPosts(pagingKey = "home")
            db.userDao().insertHistory(
                DbUserHistory(
                    accountType = accountType,
                    userKey = userKey,
                    lastVisit = 1L,
                ),
            )
            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = accountType,
                    userKey = userKey,
                    relation = UiRelation(blocking = false),
                ),
            )
            loader.failBlock = true
            var cacheDeletedBeforeRemote = false
            loader.onBlock = {
                cacheDeletedBeforeRemote =
                    db
                        .pagingTimelineDao()
                        .getByPagingKey("home")
                        .map { it.statusId } == listOf(otherStatusId) &&
                    db.userDao().getUserHistory(limit = 10).none { it.data.userKey == userKey }
            }

            handler = RelationHandler(accountType = accountType, dataSource = loader)
            handler.block(userKey)
            advanceUntilIdle()

            assertEquals(1, loader.blockCallCount)
            assertTrue(cacheDeletedBeforeRemote)
            assertEquals(listOf(otherStatusId), db.pagingTimelineDao().getByPagingKey("home").map { it.statusId })
            assertTrue(db.userDao().getUserHistory(limit = 10).none { it.data.userKey == userKey })
            val saved = assertNotNull(db.userDao().getUserRelation(accountType, userKey).first())
            assertTrue(saved.relation.blocking == false)
        }

    @Test
    fun muteFailureKeepsLocalDeletionAndRevertsRelation() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                        single<PlatformFormatter> { TestFormatter() }
                    },
                )
            }

            val accountType = AccountType.Specific(accountKey)
            val (_, otherStatusId) = saveTargetAndOtherPosts(pagingKey = "home")
            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = accountType,
                    userKey = userKey,
                    relation = UiRelation(muted = false),
                ),
            )
            loader.failMute = true
            var cacheDeletedBeforeRemote = false
            loader.onMute = {
                cacheDeletedBeforeRemote =
                    db
                        .pagingTimelineDao()
                        .getByPagingKey("home")
                        .map { it.statusId } == listOf(otherStatusId)
            }

            handler = RelationHandler(accountType = accountType, dataSource = loader)
            handler.mute(userKey)
            advanceUntilIdle()

            assertEquals(1, loader.muteCallCount)
            assertTrue(cacheDeletedBeforeRemote)
            assertEquals(listOf(otherStatusId), db.pagingTimelineDao().getByPagingKey("home").map { it.statusId })
            val saved = assertNotNull(db.userDao().getUserRelation(accountType, userKey).first())
            assertTrue(saved.relation.muted == false)
        }

    @Test
    fun unfollowSuccessClearsPendingRequest() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                    },
                )
            }

            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                    relation = UiRelation(following = false, hasPendingFollowRequestFromYou = true),
                ),
            )

            handler = RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader)
            handler.unfollow(userKey)
            advanceUntilIdle()

            val saved = assertNotNull(db.userDao().getUserRelation(AccountType.Specific(accountKey), userKey).first())
            assertTrue(saved.relation.following == false)
            assertTrue(saved.relation.hasPendingFollowRequestFromYou == false)
        }

    @Test
    fun approveAndRejectFollowRequestUpdateRelationLocally() =
        runTest {
            startKoin {
                modules(
                    module {
                        single { db }
                        single<CoroutineScope> { this@runTest }
                    },
                )
            }

            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                    relation = UiRelation(hasPendingFollowRequestToYou = true, isFans = false),
                ),
            )

            handler = RelationHandler(accountType = AccountType.Specific(accountKey), dataSource = loader)
            handler.approveFollowRequest(userKey)

            val afterApprove = assertNotNull(db.userDao().getUserRelation(AccountType.Specific(accountKey), userKey).first())
            assertTrue(afterApprove.relation.hasPendingFollowRequestToYou == false)
            assertTrue(afterApprove.relation.isFans == true)

            db.userDao().insertUserRelation(
                DbUserRelation(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                    relation = UiRelation(hasPendingFollowRequestToYou = true, isFans = true),
                ),
            )
            handler.rejectFollowRequest(userKey)

            val afterReject = assertNotNull(db.userDao().getUserRelation(AccountType.Specific(accountKey), userKey).first())
            assertTrue(afterReject.relation.hasPendingFollowRequestToYou == false)
            assertTrue(afterReject.relation.isFans == false)
        }

    private suspend fun saveTargetAndOtherPosts(pagingKey: String): Pair<String, String> {
        val accountType = AccountType.Specific(accountKey)
        val targetUser = createSampleUser().copy(key = userKey)
        val otherUser = createSampleUser().copy(key = MicroBlogKey(id = "other-user", host = "test.social"))
        val targetPost =
            createSampleStatus(targetUser).copy(
                accountType = accountType,
                statusKey = MicroBlogKey(id = "target-status", host = "test.social"),
            )
        val otherPost =
            createSampleStatus(otherUser).copy(
                accountType = accountType,
                statusKey = MicroBlogKey(id = "other-status", host = "test.social"),
            )
        val targetTimeline = TimelinePagingMapper.toDb(targetPost, pagingKey = pagingKey, sortId = 1L)
        val otherTimeline = TimelinePagingMapper.toDb(otherPost, pagingKey = pagingKey, sortId = 2L)
        saveToDatabase(db, listOf(targetTimeline, otherTimeline))
        return targetTimeline.timeline.statusId to otherTimeline.timeline.statusId
    }

    private class FakeRelationLoader(
        override val supportedTypes: Set<RelationActionType>,
    ) : RelationLoader {
        var nextRelation: UiRelation = UiRelation()
        var relationCallCount: Int = 0
        var followCallCount: Int = 0
        var blockCallCount: Int = 0
        var muteCallCount: Int = 0
        var failFollow: Boolean = false
        var failBlock: Boolean = false
        var failMute: Boolean = false
        var onBlock: (suspend () -> Unit)? = null
        var onMute: (suspend () -> Unit)? = null

        override suspend fun relation(userKey: MicroBlogKey): UiRelation {
            relationCallCount++
            return nextRelation
        }

        override suspend fun follow(userKey: MicroBlogKey) {
            followCallCount++
            if (failFollow) {
                error("follow failed")
            }
        }

        override suspend fun unfollow(userKey: MicroBlogKey) = Unit

        override suspend fun block(userKey: MicroBlogKey) {
            blockCallCount++
            onBlock?.invoke()
            if (failBlock) {
                error("block failed")
            }
        }

        override suspend fun unblock(userKey: MicroBlogKey) = Unit

        override suspend fun mute(userKey: MicroBlogKey) {
            muteCallCount++
            onMute?.invoke()
            if (failMute) {
                error("mute failed")
            }
        }

        override suspend fun unmute(userKey: MicroBlogKey) = Unit
    }
}
