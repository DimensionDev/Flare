package dev.dimension.flare.data.database.cache

import androidx.room3.Room
import androidx.room3.useWriterConnection
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimelineDatabaseWriteAmplificationTest : RobolectricTest() {
    @Test
    fun savingEquivalentRemapDoesNotWrite() =
        runTest {
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(PlatformDispatchers.IO)
                    .build()
            try {
                database.connect {
                    saveToDatabase(database, listOf(createMappedTimeline()))
                }
                val changesBefore = database.totalChanges()

                database.connect {
                    saveToDatabase(database, listOf(createMappedTimeline()))
                }

                assertEquals(0L, database.totalChanges() - changesBefore)
            } finally {
                database.close()
            }
        }

    @Test
    fun savingPersistedContentOutsideRenderHashUpdatesCanonicalStatus() =
        runTest {
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(PlatformDispatchers.IO)
                    .build()
            try {
                val original = createPost("content-fingerprint")
                val updated = original.copy(clickEvent = ClickEvent.Deeplink("https://updated.invalid"))
                assertEquals(original.renderHash, updated.renderHash)

                database.connect {
                    saveToDatabase(
                        database,
                        listOf(TimelinePagingMapper.toDb(original, PAGING_KEY, sortId = 0)),
                    )
                }
                val revisionBefore =
                    database
                        .pagingTimelineDao()
                        .getTimelinePageIdentities(PAGING_KEY, offset = 0, limit = 1)
                        .single()
                        .contentRevision

                database.connect {
                    saveToDatabase(
                        database,
                        listOf(TimelinePagingMapper.toDb(updated, PAGING_KEY, sortId = 0)),
                    )
                }

                val identityAfter =
                    database
                        .pagingTimelineDao()
                        .getTimelinePageIdentities(PAGING_KEY, offset = 0, limit = 1)
                        .single()
                assertTrue(identityAfter.contentRevision > revisionBefore)
                val stored =
                    database
                        .pagingTimelineDao()
                        .getTimelinePage(PAGING_KEY, offset = 0, limit = 1)
                        .single()
                        .status
                        .status
                        .data
                        .content
                assertEquals(updated, stored)
            } finally {
                database.close()
            }
        }

    @Test
    fun savingMessageClickEventUpdatesTimeline() =
        runTest {
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(PlatformDispatchers.IO)
                    .build()
            try {
                val originalMessage =
                    UiTimelineV2.Message(
                        statusKey = MicroBlogKey(id = "message", host = "benchmark.invalid"),
                        icon = UiIcon.Retweet,
                        type = UiTimelineV2.Message.Type.Raw("reposted"),
                        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000).toUi(),
                        clickEvent = ClickEvent.Noop,
                        accountType = AccountType.Guest,
                    )
                val original =
                    createTimeline().let {
                        it.copy(presentation = it.presentation.copy(message = originalMessage))
                    }
                database.connect {
                    saveToDatabase(
                        database,
                        listOf(TimelinePagingMapper.toDb(original, PAGING_KEY, sortId = 0)),
                    )
                }

                val updatedClickEvent = ClickEvent.Deeplink("https://updated.invalid")
                val updated =
                    original.copy(
                        presentation =
                            original.presentation.copy(
                                message = originalMessage.copy(clickEvent = updatedClickEvent),
                            ),
                    )
                database.connect {
                    saveToDatabase(
                        database,
                        listOf(TimelinePagingMapper.toDb(updated, PAGING_KEY, sortId = 0)),
                    )
                }

                val storedMessage =
                    database
                        .pagingTimelineDao()
                        .getTimelinePage(PAGING_KEY, offset = 0, limit = 1)
                        .single()
                        .timeline
                        .message
                assertEquals(updatedClickEvent, storedMessage?.clickEvent)
            } finally {
                database.close()
            }
        }

    @Test
    fun updatingSharedQuoteInvalidatesAndHydratesEveryTimelineOccurrence() =
        runTest {
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(PlatformDispatchers.IO)
                    .build()
            try {
                val quote = createPost("quote")
                val items =
                    listOf(
                        createTimelineWithQuote("root-a", quote),
                        createTimelineWithQuote("root-b", quote),
                    ).mapIndexed { index, item ->
                        TimelinePagingMapper.toDb(item, PAGING_KEY, sortId = index.toLong())
                    }
                database.connect { saveToDatabase(database, items) }
                val identitiesBefore =
                    database
                        .pagingTimelineDao()
                        .getTimelinePageIdentities(PAGING_KEY, offset = 0, limit = items.size)
                        .associate { it.statusId to it.contentRevision }

                val updatedQuote =
                    quote.copy(content = UiTranslatableText("updated quote body".toUiPlainText()))
                database.statusDao().update(
                    statusKey = updatedQuote.statusKey,
                    accountType = AccountType.Guest,
                    content = updatedQuote,
                    renderHash = updatedQuote.renderHash,
                    text = updatedQuote.searchText,
                )

                val identitiesAfter =
                    database
                        .pagingTimelineDao()
                        .getTimelinePageIdentities(PAGING_KEY, offset = 0, limit = items.size)
                assertTrue(
                    identitiesAfter.all { identity ->
                        identity.contentRevision > identitiesBefore.getValue(identity.statusId)
                    },
                )
                val hydrated = database.pagingTimelineDao().getTimelinePage(PAGING_KEY, offset = 0, limit = items.size)
                hydrated.forEach { item ->
                    val semanticQuote =
                        item.status.references
                            .single()
                            .status
                            ?.data
                            ?.content
                    val presentationQuote =
                        item.presentationReferences
                            .single()
                            .status
                            ?.data
                            ?.content
                    assertEquals(updatedQuote, semanticQuote)
                    assertEquals(updatedQuote, presentationQuote)
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun insertingPreviouslyMissingReferenceStatusInvalidatesTimeline() =
        runTest {
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(PlatformDispatchers.IO)
                    .build()
            try {
                val quote = createPost("late-quote")
                val mapped =
                    TimelinePagingMapper.toDb(
                        createTimelineWithQuote("late-root", quote),
                        PAGING_KEY,
                        sortId = 0,
                    )
                val missingReferenceStatus =
                    mapped.copy(
                        references = mapped.references.map { it.copy(status = null) },
                        presentationReferences =
                            mapped.presentationReferences.map { it.copy(status = null) },
                    )
                database.connect { saveToDatabase(database, listOf(missingReferenceStatus)) }
                val revisionBefore =
                    database
                        .pagingTimelineDao()
                        .getTimelinePageIdentities(PAGING_KEY, offset = 0, limit = 1)
                        .single()
                        .contentRevision

                database.connect {
                    saveToDatabase(
                        database,
                        listOf(TimelinePagingMapper.toDb(quote, "late-reference-source", sortId = 0)),
                    )
                }

                val revisionAfter =
                    database
                        .pagingTimelineDao()
                        .getTimelinePageIdentities(PAGING_KEY, offset = 0, limit = 1)
                        .single()
                        .contentRevision
                assertTrue(revisionAfter > revisionBefore)
                val hydrated = database.pagingTimelineDao().getTimelinePage(PAGING_KEY, offset = 0, limit = 1).single()
                assertEquals(
                    quote,
                    hydrated.status.references
                        .single()
                        .status
                        ?.data
                        ?.content,
                )
                assertEquals(
                    quote,
                    hydrated.presentationReferences
                        .single()
                        .status
                        ?.data
                        ?.content,
                )
            } finally {
                database.close()
            }
        }

    private suspend fun createMappedTimeline() =
        TimelinePagingMapper.toDb(
            data = createTimeline(),
            pagingKey = PAGING_KEY,
            sortId = 0,
        )

    private fun createTimeline(): UiTimelineV2.TimelinePostItem {
        val parent = createPost("parent")
        val quote = createPost("quote")
        val repost = createPost("repost")
        val root =
            createPost(
                role = "root",
                references =
                    persistentListOf(
                        UiTimelineV2.Post.Reference(parent.statusKey, ReferenceType.Reply),
                        UiTimelineV2.Post.Reference(quote.statusKey, ReferenceType.Quote),
                        UiTimelineV2.Post.Reference(repost.statusKey, ReferenceType.Retweet),
                    ),
            )
        return UiTimelineV2.TimelinePostItem(
            post = root,
            presentation =
                UiTimelineV2.PostPresentation(
                    inlineParents = persistentListOf(parent),
                    quotes = persistentListOf(quote),
                    repost = repost,
                ),
        )
    }

    private fun createTimelineWithQuote(
        rootRole: String,
        quote: UiTimelineV2.Post,
    ): UiTimelineV2.TimelinePostItem {
        val root =
            createPost(
                role = rootRole,
                references = persistentListOf(UiTimelineV2.Post.Reference(quote.statusKey, ReferenceType.Quote)),
            )
        return UiTimelineV2.TimelinePostItem(
            post = root,
            presentation = UiTimelineV2.PostPresentation(quotes = persistentListOf(quote)),
        )
    }

    private fun createPost(
        role: String,
        references: kotlinx.collections.immutable.ImmutableList<UiTimelineV2.Post.Reference> = persistentListOf(),
    ) = UiTimelineV2.Post(
        platformId = "benchmark",
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText("$role body".toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id = role, host = "benchmark.invalid"),
        card = null,
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000).toUi(),
        references = references,
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Guest,
    )

    private suspend fun CacheDatabase.totalChanges(): Long =
        useWriterConnection { connection ->
            connection.usePrepared("SELECT total_changes()") { statement ->
                check(statement.step())
                statement.getLong(0)
            }
        }

    private companion object {
        const val PAGING_KEY = "write-amplification-test"
    }
}
