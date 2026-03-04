package dev.dimension.flare.data.datasource.microblog

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class PostEventHandlerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var fakeRemoteHandler: FakeRemoteHandler
    private lateinit var handler: PostEventHandler

    private val accountKey = MicroBlogKey(id = "user-1", host = "test.social")
    private val postKey = MicroBlogKey(id = "post-1", host = "test.social")

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        fakeRemoteHandler = FakeRemoteHandler()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun updateActionMenuEventAppliesOptimisticUpdate() =
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

            val original = createPost(actions = persistentListOf(createMenuItem(updateKey = "like", count = 1)))
            insertPost(original)

            handler = PostEventHandler(accountKey = accountKey, handler = fakeRemoteHandler)
            handler.handleEvent(TestUpdateMenuEvent(postKey = postKey, updateKey = "like", nextCount = 2))
            advanceUntilIdle()

            val updated = readPost()
            assertNotNull(updated)
            val updatedLike = updated.actions.filterIsInstance<ActionMenu.Item>().first { it.updateKey == "like" }
            assertEquals(2, updatedLike.count?.value)
            assertEquals(1, fakeRemoteHandler.callCount)
        }

    @Test
    fun updateActionMenuEventFailureRevertsOriginalData() =
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

            val original = createPost(actions = persistentListOf(createMenuItem(updateKey = "like", count = 1)))
            insertPost(original)

            fakeRemoteHandler.shouldFail = true
            handler = PostEventHandler(accountKey = accountKey, handler = fakeRemoteHandler)
            handler.handleEvent(TestUpdateMenuEvent(postKey = postKey, updateKey = "like", nextCount = 2))
            advanceUntilIdle()

            val reverted = readPost()
            assertNotNull(reverted)
            val like = reverted.actions.filterIsInstance<ActionMenu.Item>().first { it.updateKey == "like" }
            assertEquals(1, like.count?.value)
            assertEquals(1, fakeRemoteHandler.callCount)
        }

    @Test
    fun pollEventUpdatesOwnVotesAndOptionCounts() =
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

            val poll =
                UiPoll(
                    id = "poll-1",
                    options =
                        persistentListOf(
                            UiPoll.Option(title = "A", votesCount = 1, percentage = 0.5f),
                            UiPoll.Option(title = "B", votesCount = 2, percentage = 0.5f),
                        ),
                    multiple = false,
                    ownVotes = persistentListOf(),
                    voteEvent = null,
                    expiresAt = null,
                )
            insertPost(createPost(poll = poll))

            handler = PostEventHandler(accountKey = accountKey, handler = fakeRemoteHandler)
            handler.handleEvent(
                PostEvent.Mastodon.Vote(
                    id = "poll-1",
                    accountKey = accountKey,
                    postKey = postKey,
                    options = persistentListOf(1),
                ),
            )
            advanceUntilIdle()

            val updated = readPost()
            assertNotNull(updated)
            val updatedPoll = updated.poll
            assertNotNull(updatedPoll)
            assertEquals(listOf(1), updatedPoll.ownVotes)
            assertEquals(1, updatedPoll.options[0].votesCount)
            assertEquals(3, updatedPoll.options[1].votesCount)
        }

    @Test
    fun deleteFromCacheRemovesStatusAndPagingEntry() =
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

            insertPost(createPost())
            db.pagingTimelineDao().insertAll(listOf(DbPagingTimeline(pagingKey = "home", statusKey = postKey, sortId = 1L)))

            handler = PostEventHandler(accountKey = accountKey, handler = fakeRemoteHandler)
            handler.deleteFromCache(postKey)

            val saved = db.statusDao().get(postKey, AccountType.Specific(accountKey)).first()
            assertNull(saved)
            val exists = db.pagingTimelineDao().existsPaging(accountKey, "home")
            assertTrue(!exists)
        }

    private suspend fun insertPost(post: UiTimelineV2.Post) {
        db.statusDao().insert(
            DbStatus(
                statusKey = postKey,
                accountType = AccountType.Specific(accountKey),
                content = post,
                text = post.content.raw,
            ),
        )
    }

    private suspend fun readPost(): UiTimelineV2.Post? =
        db
            .statusDao()
            .get(postKey, AccountType.Specific(accountKey))
            .first()
            ?.content as? UiTimelineV2.Post

    private fun createPost(
        actions: SerializableImmutableList<ActionMenu> = persistentListOf(),
        poll: UiPoll? = null,
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            message = null,
            platformType = PlatformType.Mastodon,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = createProfile(),
            quote = persistentListOf(),
            content = Element("span").apply { appendText("post content") }.toUi(),
            actions = actions,
            poll = poll,
            statusKey = postKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            parents = persistentListOf(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )

    private fun createProfile(): UiProfile =
        UiProfile(
            key = MicroBlogKey("author", "test.social"),
            handle =
                UiHandle(
                    raw = "author",
                    host = "test.social",
                ),
            avatar = "https://test.social/author.png",
            nameInternal = Element("span").apply { appendText("Author") }.toUi(),
            platformType = PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private fun createMenuItem(
        updateKey: String,
        count: Long,
    ) = ActionMenu.Item(
        updateKey = updateKey,
        text = ActionMenu.Item.Text.Raw("like"),
        count = UiNumber(count),
    )

    private class FakeRemoteHandler : PostEventHandler.Handler {
        var shouldFail: Boolean = false
        var callCount: Int = 0

        override suspend fun handle(
            event: PostEvent,
            updater: DatabaseUpdater,
        ) {
            callCount++
            if (shouldFail) {
                error("remote failed")
            }
        }
    }

    private data class TestUpdateMenuEvent(
        override val postKey: MicroBlogKey,
        val updateKey: String,
        val nextCount: Long,
    ) : UpdatePostActionMenuEvent {
        override fun nextActionMenu(): ActionMenu.Item =
            ActionMenu.Item(
                updateKey = updateKey,
                text = ActionMenu.Item.Text.Raw("updated"),
                count = UiNumber(nextCount),
            )
    }
}
