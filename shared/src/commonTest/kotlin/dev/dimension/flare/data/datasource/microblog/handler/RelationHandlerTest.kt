package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.LoadState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUserRelation
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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
                .setDriver(BundledSQLiteDriver())
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

    private class FakeRelationLoader(
        override val supportedTypes: Set<RelationActionType>,
    ) : RelationLoader {
        var nextRelation: UiRelation = UiRelation()
        var relationCallCount: Int = 0
        var followCallCount: Int = 0
        var failFollow: Boolean = false

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

        override suspend fun block(userKey: MicroBlogKey) = Unit

        override suspend fun unblock(userKey: MicroBlogKey) = Unit

        override suspend fun mute(userKey: MicroBlogKey) = Unit

        override suspend fun unmute(userKey: MicroBlogKey) = Unit
    }
}
