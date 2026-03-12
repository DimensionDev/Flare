package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.LoadState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserHandlerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var loader: FakeUserLoader
    private lateinit var handler: UserHandler

    private val accountKey = MicroBlogKey(id = "account-1", host = "test.social")

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        loader = FakeUserLoader()

        startKoin {
            modules(
                module {
                    single { db }
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }

        handler = UserHandler(host = accountKey.host, loader = loader)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun userByHandleAndHostRefreshStoresAndEmitsUser() =
        runTest {
            val expected = createProfile(id = "alice", host = "test.social", handle = "alice")
            loader.nextByHandleAndHost = expected

            val cacheable =
                handler.userByHandleAndHost(
                    UiHandle(
                        raw = "alice",
                        host = "test.social",
                    ),
                )
            val valueDeferred =
                async {
                    cacheable.data
                        .filterIsInstance<CacheState.Success<UiProfile>>()
                        .first()
                        .data
                }

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.NotLoading)
            assertEquals(expected.key, valueDeferred.await().key)
            assertEquals(1, loader.byHandleCallCount)

            val saved = db.userDao().findByCanonicalHandleAndHost("@alice@test.social", "test.social").first()
            assertNotNull(saved)
            assertEquals(expected.key, saved.userKey)
        }

    @Test
    fun userByIdRefreshStoresAndEmitsUser() =
        runTest {
            val expected = createProfile(id = "bob", host = "test.social", handle = "@bob@test.social")
            loader.nextById = expected

            val cacheable = handler.userById("bob")
            val valueDeferred =
                async {
                    cacheable.data
                        .filterIsInstance<CacheState.Success<UiProfile>>()
                        .first()
                        .data
                }

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.NotLoading)
            assertEquals(expected.key, valueDeferred.await().key)
            assertEquals(1, loader.byIdCallCount)

            val saved = db.userDao().findByKey(MicroBlogKey("bob", "test.social")).first()
            assertNotNull(saved)
            assertEquals(expected.key, saved.userKey)
        }

    @Test
    fun refreshFailureKeepsExistingCachedUser() =
        runTest {
            val existing = createProfile(id = "charlie", host = "test.social", handle = "@charlie@test.social")
            db.userDao().insert(
                DbUser(
                    userKey = existing.key,
                    name = existing.name.raw,
                    canonicalHandle = existing.handle.canonical,
                    host = "test.social",
                    content = existing,
                ),
            )
            loader.failById = true

            val cacheable = handler.userById("charlie")
            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.Error)

            val latest =
                cacheable.data
                    .filterIsInstance<CacheState.Success<UiProfile>>()
                    .first()
                    .data
            assertEquals(existing.key, latest.key)
        }

    @Test
    fun uiHandleQueryMatchesAtHandleInCache() =
        runTest {
            val atHandleProfile = createProfile(id = "david", host = "test.social", handle = "@david@test.social")
            db.userDao().insert(
                DbUser(
                    userKey = atHandleProfile.key,
                    name = atHandleProfile.name.raw,
                    canonicalHandle = atHandleProfile.handle.canonical,
                    host = "test.social",
                    content = atHandleProfile,
                ),
            )

            // UserHandler now normalizes raw+host to canonical before querying cache.
            val uiHandleHit = db.userDao().findByCanonicalHandleAndHost("@david@test.social", "test.social").first()
            assertNotNull(uiHandleHit)
            assertEquals(atHandleProfile.key, uiHandleHit.userKey)

            // Stored shape in many loaders/renderers: "@david@test.social"
            val atHandleHit = db.userDao().findByCanonicalHandleAndHost("@david@test.social", "test.social").first()
            assertNotNull(atHandleHit)
            assertEquals(atHandleProfile.key, atHandleHit.userKey)
        }

    private fun createProfile(
        id: String,
        host: String,
        handle: String,
    ): UiProfile =
        UiProfile(
            key = MicroBlogKey(id = id, host = host),
            handle =
                UiHandle(
                    raw = handle.removePrefix("@").substringBefore("@"),
                    host = host,
                ),
            avatar = "https://$host/$id.png",
            nameInternal = id.toUiPlainText(),
            platformType = PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private class FakeUserLoader : UserLoader {
        var nextByHandleAndHost: UiProfile? = null
        var nextById: UiProfile? = null
        var failById: Boolean = false
        var byHandleCallCount: Int = 0
        var byIdCallCount: Int = 0

        override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
            byHandleCallCount++
            return requireNotNull(nextByHandleAndHost)
        }

        override suspend fun userById(id: String): UiProfile {
            byIdCallCount++
            if (failById) {
                error("userById failed")
            }
            return requireNotNull(nextById)
        }
    }
}
