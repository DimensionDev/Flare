package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.LoadState
import androidx.paging.testing.asSnapshot
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
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
import kotlin.test.assertIs
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DirectMessageHandlerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase

    private val accountKey = MicroBlogKey(id = "me", host = "test.social")
    private val otherUser = UiProfile.placeholder(MicroBlogKey("other", accountKey.host))
    private val roomKey = MicroBlogKey("room-1", accountKey.host)

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun directMessageListLoadsRefreshAndAppendPagesIntoCache() =
        runTest {
            val loader =
                FakeDirectMessageLoader().apply {
                    roomPages[null] =
                        PagingResult(
                            data = listOf(room("room-1", "first page")),
                            nextKey = "cursor-1",
                        )
                    roomPages["cursor-1"] =
                        PagingResult(
                            data = listOf(room("room-2", "second page")),
                            nextKey = null,
                        )
                }
            val handler = createHandler(loader, this)

            val snapshot =
                handler
                    .directMessageList(this)
                    .asSnapshot {
                        appendScrollWhile { item -> item.key == roomKey }
                    }

            assertEquals(listOf<String?>(null, "cursor-1"), loader.roomListRequests)
            assertEquals(listOf("room-1", "room-2"), snapshot.map { it.key.id })

            val cached =
                db
                    .messageDao()
                    .getRoomTimeline(
                        dev.dimension.flare.model.AccountType
                            .Specific(accountKey),
                    ).first()
            assertEquals(2, cached.size)
            assertEquals(setOf("room-1", "room-2"), cached.map { it.roomKey.id }.toSet())
        }

    @Test
    fun directMessageConversationLoadsRefreshAndAppendPagesIntoCache() =
        runTest {
            val loader =
                FakeDirectMessageLoader().apply {
                    conversationPages[roomKey to null] =
                        PagingResult(
                            data = listOf(message("message-1", "hello", 1_000)),
                            nextKey = "msg-cursor-1",
                        )
                    conversationPages[roomKey to "msg-cursor-1"] =
                        PagingResult(
                            data = listOf(message("message-2", "world", 2_000)),
                            nextKey = null,
                        )
                }
            val handler = createHandler(loader, this)

            val snapshot =
                handler
                    .directMessageConversation(roomKey, this)
                    .asSnapshot {
                        appendScrollWhile { item -> item.key.id == "message-1" }
                    }

            assertEquals(
                listOf(roomKey to null, roomKey to "msg-cursor-1"),
                loader.conversationRequests,
            )
            assertEquals(listOf("message-1", "message-2"), snapshot.map { it.key.id })

            assertEquals(
                "message-1",
                db
                    .messageDao()
                    .getMessage(MicroBlogKey("message-1", accountKey.host))
                    ?.messageKey
                    ?.id,
            )
            assertEquals(
                "message-2",
                db
                    .messageDao()
                    .getMessage(MicroBlogKey("message-2", accountKey.host))
                    ?.messageKey
                    ?.id,
            )
        }

    @Test
    fun getConversationInfoRefreshCachesRoomAndFetchNewUpdatesLatestMessage() =
        runTest {
            val loader =
                FakeDirectMessageLoader().apply {
                    conversationInfo = room("room-1", "cached info", unreadCount = 3, timestamp = 1_000)
                    newMessages =
                        listOf(
                            message("message-2", "newest", 2_000),
                        )
                }
            val handler = createHandler(loader, this)
            val cache = handler.getDirectMessageConversationInfo(roomKey)

            cache.refresh()
            assertIs<LoadState.NotLoading>(cache.refreshState.first { it !is LoadState.Loading })
            val infoState = cache.data.first { it is CacheState.Success }
            val info = (infoState as CacheState.Success).data
            assertEquals("room-1", info.key.id)
            assertEquals("cached info", info.lastMessageText)

            handler.fetchNewDirectMessageForConversation(roomKey)

            val roomInfo =
                db
                    .messageDao()
                    .getRoomInfo(
                        roomKey,
                        dev.dimension.flare.model.AccountType
                            .Specific(accountKey),
                    ).first()
            assertEquals(0L, roomInfo?.unreadCount)
            assertEquals("newest", roomInfo?.content?.lastMessageText)

            val latestMessage = db.messageDao().getLatestMessage(roomKey)
            assertEquals("message-2", latestMessage?.messageKey?.id)
            assertEquals(listOf(roomKey), loader.fetchNewRequests)
        }

    private fun createHandler(
        loader: FakeDirectMessageLoader,
        scope: CoroutineScope,
    ): DirectMessageHandler {
        stopKoin()
        startKoin {
            modules(
                module {
                    single { db }
                    single<CoroutineScope> { scope }
                },
            )
        }
        return DirectMessageHandler(
            accountKey = accountKey,
            loader = loader,
        )
    }

    private fun room(
        id: String,
        lastText: String,
        unreadCount: Long = 0,
        timestamp: Long = 1_000,
    ) = UiDMRoom(
        key = MicroBlogKey(id, accountKey.host),
        users = persistentListOf(otherUser),
        lastMessage = message("last-$id", lastText, timestamp),
        unreadCount = unreadCount,
    )

    private fun message(
        id: String,
        text: String,
        timestamp: Long,
    ) = UiDMItem(
        key = MicroBlogKey(id, accountKey.host),
        user = otherUser,
        content = UiDMItem.Message.Text(text.toUiPlainText()),
        timestamp = Instant.fromEpochMilliseconds(timestamp).toUi(),
        isFromMe = false,
        sendState = null,
        showSender = false,
    )
}

private class FakeDirectMessageLoader : DirectMessageLoader {
    val roomPages = mutableMapOf<String?, PagingResult<UiDMRoom>>()
    val conversationPages = mutableMapOf<Pair<MicroBlogKey, String?>, PagingResult<UiDMItem>>()

    val roomListRequests = mutableListOf<String?>()
    val conversationRequests = mutableListOf<Pair<MicroBlogKey, String?>>()
    val fetchNewRequests = mutableListOf<MicroBlogKey>()

    var conversationInfo: UiDMRoom? = null
    var newMessages: List<UiDMItem> = emptyList()

    override suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    ) = Unit

    override suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) = Unit

    override suspend fun leaveConversation(roomKey: MicroBlogKey) = Unit

    override suspend fun createRoom(userKey: MicroBlogKey): MicroBlogKey = userKey

    override suspend fun canSendMessage(userKey: MicroBlogKey): Boolean = true

    override suspend fun fetchBadgeCount(): Int = 0

    override suspend fun loadRoomList(
        pageSize: Int,
        cursor: String?,
    ): PagingResult<UiDMRoom> {
        roomListRequests += cursor
        return roomPages.getValue(cursor)
    }

    override suspend fun loadConversation(
        roomKey: MicroBlogKey,
        pageSize: Int,
        cursor: String?,
    ): PagingResult<UiDMItem> {
        conversationRequests += roomKey to cursor
        return conversationPages.getValue(roomKey to cursor)
    }

    override suspend fun loadConversationInfo(roomKey: MicroBlogKey): UiDMRoom = conversationInfo ?: error("conversationInfo not set")

    override suspend fun fetchNewMessages(roomKey: MicroBlogKey): List<UiDMItem> {
        fetchNewRequests += roomKey
        return newMessages
    }
}
