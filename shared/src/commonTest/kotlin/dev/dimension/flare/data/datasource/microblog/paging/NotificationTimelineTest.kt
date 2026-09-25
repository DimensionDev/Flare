package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.TimelineRevisionCallback
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class)
class NotificationTimelineTest : RobolectricTest() {
    private lateinit var database: CacheDatabase
    private val options = TranslationDisplayOptions(false, false, "")
    private val pagingKey = "notifications"

    @BeforeTest
    fun setup() {
        database =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(PlatformDispatchers.IO)
                .addCallback(TimelineRevisionCallback)
                .build()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    @Test
    fun repliesKeepApiOrderAndOnlyExplicitParents() =
        runTest {
            val root = post("root")
            val reply = post("reply", root)
            val leaf = post("leaf", reply)
            val embedded = post("api-parent")
            val items =
                listOf(
                    notification("leaf", leaf),
                    notification("like-root", root, embedded),
                    notification("reply", reply),
                    notification("root", root),
                )
            val mediator = mediator { PagingResult(items) }

            load(mediator, LoadType.REFRESH)

            val rendered = read()
            assertEquals(items.map { it.presentation.notificationKey }, rendered.map { it.presentation.notificationKey })
            assertEquals(
                listOf(emptyList(), listOf(embedded.statusKey), emptyList(), emptyList()),
                rendered.map { it.presentation.inlineParents.map { parent -> parent.statusKey } },
            )
            assertEquals(root.statusKey, rendered[1].post.statusKey)
            assertEquals(root.statusKey, rendered[3].post.statusKey)
        }

    @Test
    fun samePostNotificationsHaveIndependentIdentityAndShareUpdatedContent() =
        runTest {
            val target = post("target")
            val first = notification("like", target, post("api-parent"))
            val second = notification("repost", target)
            saveToDatabase(database, TimelinePagingMapper.toDb(listOf(first, second), pagingKey, listOf(0L, 0L)))

            val dao = database.pagingTimelineDao()
            val identities = dao.getTimelinePageIdentities(pagingKey, 0, 20)
            val stored = dao.getTimelinePage(pagingKey, 0, 20)
            assertEquals(2, stored.size)
            assertEquals(1, stored.map { it.statusData.id }.distinct().size)
            assertNotEquals(identities[0], identities[1])
            val rendered =
                TimelinePagingMapper
                    .toPageItems(stored, identities, pagingKey, mutableMapOf())
                    .map { assertIs<UiTimelineV2.TimelinePostItem>(it.toUi(options)) }
            assertEquals(2, rendered.map { it.itemKey }.distinct().size)
            assertEquals(setOf("like", "repost"), rendered.map { it.presentation.notificationKey?.id }.toSet())
            assertEquals(
                1,
                rendered
                    .single { it.presentation.notificationKey?.id == "like" }
                    .presentation.inlineParents.size,
            )
            assertTrue(
                rendered
                    .single { it.presentation.notificationKey?.id == "repost" }
                    .presentation.inlineParents
                    .isEmpty(),
            )

            val updated = target.copy(content = UiTranslatableText("updated content".toUiPlainText()))
            saveToDatabase(database, listOf(TimelinePagingMapper.toDb(updated, "home")))

            assertTrue(
                dao.getTimelinePageIdentities(pagingKey, 0, 20).zip(identities).all { (after, before) ->
                    after.contentRevision > before.contentRevision
                },
            )
            assertTrue(read().all { it.post.content.original.innerText == "updated content" })
        }

    @Test
    fun refreshRemovesOldChainsAndObsoleteNotificationsForTheSamePost() =
        runTest {
            val target = post("target")
            val cached = listOf(notification("kept", target, post("old-parent")), notification("removed", target))
            saveToDatabase(database, TimelinePagingMapper.toDb(cached, pagingKey))

            load(mediator { PagingResult(listOf(notification("kept", target))) }, LoadType.REFRESH)

            val kept = read().single()
            assertEquals("kept", kept.presentation.notificationKey?.id)
            assertTrue(kept.presentation.inlineParents.isEmpty())
            assertTrue(
                database
                    .pagingTimelineDao()
                    .getPagePresentationReferences(
                        pagingKey,
                        database.pagingTimelineDao().getByPagingKey(pagingKey).map { it._id },
                    ).isEmpty(),
            )
        }

    @Test
    fun overlappingAppendUpdatesTheExistingNotificationWithoutMovingIt() =
        runTest {
            val target = post("target")
            val first = notification("first", target)
            val updated =
                first.copy(
                    presentation =
                        first.presentation.copy(
                            message = first.presentation.message!!.copy(type = UiTimelineV2.Message.Type.Raw("updated notification")),
                        ),
                )
            val mediator =
                mediator { request ->
                    if (request == PagingRequest.Refresh) {
                        PagingResult(listOf(first, notification("second", target)), nextKey = "next")
                    } else {
                        PagingResult(listOf(updated, notification("third", target)))
                    }
                }

            load(mediator, LoadType.REFRESH)
            load(mediator, LoadType.APPEND)

            val rendered = read()
            assertEquals(listOf("first", "second", "third"), rendered.map { it.presentation.notificationKey?.id })
            assertEquals(
                UiTimelineV2.Message.Type.Raw("updated notification"),
                rendered
                    .first()
                    .presentation.message
                    ?.type,
            )
        }

    private fun mediator(response: (PagingRequest) -> PagingResult<UiTimelineV2>) =
        TimelineRemoteMediator(
            loader =
                object : NotificationTimelineLoader {
                    override val pagingKey: String = this@NotificationTimelineTest.pagingKey

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ): PagingResult<UiTimelineV2> = response(request)
                },
            database = database,
            allowLongText = false,
        )

    private suspend fun load(
        mediator: TimelineRemoteMediator,
        type: LoadType,
    ) {
        assertIs<RemoteMediator.MediatorResult.Success>(
            mediator.load(
                type,
                PagingState(
                    pages = emptyList(),
                    anchorPosition = null,
                    config = PagingConfig(20),
                    leadingPlaceholderCount = 0,
                ),
            ),
        )
    }

    private suspend fun read() =
        database.pagingTimelineDao().getTimelinePage(pagingKey, 0, 20).map {
            assertIs<UiTimelineV2.TimelinePostItem>(TimelinePagingMapper.toUi(it, pagingKey, options))
        }

    private fun notification(
        id: String,
        post: UiTimelineV2.Post,
        vararg parents: UiTimelineV2.Post,
    ): UiTimelineV2.TimelinePostItem {
        val key = MicroBlogKey(id, "example.com")
        return UiTimelineV2.TimelinePostItem(
            post,
            UiTimelineV2.PostPresentation(
                message =
                    UiTimelineV2.Message(
                        statusKey = key,
                        icon = UiIcon.Like,
                        type = UiTimelineV2.Message.Type.Raw(id),
                        createdAt = Instant.fromEpochSeconds(2).toUi(),
                        clickEvent = ClickEvent.Noop,
                        accountType = AccountType.Guest,
                    ),
                notificationKey = key,
                inlineParents = parents.map { UiTimelineV2.TimelinePostItem(it) }.toImmutableList(),
            ),
        )
    }

    private fun post(
        id: String,
        parent: UiTimelineV2.Post? = null,
    ) = UiTimelineV2.Post(
        platformId = "test",
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText(id.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id, "example.com"),
        card = null,
        createdAt = Instant.fromEpochSeconds(1).toUi(),
        references = listOfNotNull(parent?.let { UiTimelineV2.Post.Reference(it.statusKey, ReferenceType.Reply) }).toImmutableList(),
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Guest,
    )
}
