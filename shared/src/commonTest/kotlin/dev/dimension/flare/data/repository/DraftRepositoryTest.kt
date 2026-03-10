package dev.dimension.flare.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftReferenceType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DraftRepositoryTest : RobolectricTest() {
    private lateinit var db: AppDatabase
    private lateinit var repository: DraftRepository

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        repository = DraftRepository(db)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveDraftAggregatesTargetsAndMedias() =
        runTest {
            val accountA = MicroBlogKey("alice", "mastodon.social")
            val accountB = MicroBlogKey("bob", "misskey.io")

            val groupId =
                repository.saveDraft(
                    SaveDraftInput(
                        groupId = "group-aggregate",
                        content =
                            DraftContent(
                                text = "hello draft",
                                visibility = UiTimelineV2.Post.Visibility.Public,
                                language = listOf("zh", "en"),
                                sensitive = true,
                                spoilerText = "cw",
                                poll =
                                    DraftContent.DraftPoll(
                                        options = listOf("a", "b"),
                                        expiredAfter = 300000,
                                        multiple = false,
                                    ),
                                reference =
                                    DraftContent.DraftReference(
                                        type = DraftReferenceType.REPLY,
                                        statusKey = MicroBlogKey("123", "mastodon.social"),
                                    ),
                            ),
                        targets =
                            listOf(
                                SaveDraftTarget(accountKey = accountA),
                                SaveDraftTarget(accountKey = accountB, status = DraftTargetStatus.FAILED, errorMessage = "network"),
                            ),
                        medias =
                            listOf(
                                SaveDraftMedia(
                                    cachePath = "/tmp/a.png",
                                    fileName = "a.png",
                                    mediaType = DraftMediaType.IMAGE,
                                    altText = "image a",
                                ),
                                SaveDraftMedia(
                                    cachePath = "/tmp/b.mov",
                                    fileName = "b.mov",
                                    mediaType = DraftMediaType.VIDEO,
                                    altText = "video b",
                                ),
                            ),
                    ),
                )

            val draft = repository.draft(groupId).first()

            assertNotNull(draft)
            assertEquals(groupId, draft.groupId)
            assertEquals("hello draft", draft.content.text)
            assertEquals(2, draft.targets.size)
            assertEquals(setOf(accountA, accountB), draft.targets.map { it.accountKey }.toSet())
            assertEquals(2, draft.medias.size)
            assertEquals(listOf("/tmp/a.png", "/tmp/b.mov"), draft.medias.map { it.cachePath })
            assertEquals(listOf(0, 1), draft.medias.map { it.sortOrder })
        }

    @Test
    fun visibleDraftsHideSendingOnlyGroups() =
        runTest {
            repository.saveDraft(
                SaveDraftInput(
                    groupId = "group-visible",
                    content = sampleContent("visible"),
                    targets = listOf(SaveDraftTarget(accountKey = MicroBlogKey("a", "host"), status = DraftTargetStatus.DRAFT)),
                    medias = emptyList(),
                ),
            )
            val sendingGroupId =
                repository.saveDraft(
                    SaveDraftInput(
                        groupId = "group-sending",
                        content = sampleContent("sending"),
                        targets = listOf(SaveDraftTarget(accountKey = MicroBlogKey("b", "host"), status = DraftTargetStatus.SENDING)),
                        medias = emptyList(),
                    ),
                )

            val visible = repository.visibleDrafts.first()
            val sending = repository.sendingDrafts.first()

            assertEquals(1, visible.size)
            assertEquals("visible", visible.first().content.text)
            assertEquals(1, sending.size)
            assertEquals(sendingGroupId, sending.first().groupId)
        }

    @Test
    fun deleteTargetRemovesGroupWhenLastTargetDeleted() =
        runTest {
            val account = MicroBlogKey("alice", "example.com")
            val groupId =
                repository.saveDraft(
                    SaveDraftInput(
                        groupId = "group-delete",
                        content = sampleContent("to delete"),
                        targets = listOf(SaveDraftTarget(accountKey = account)),
                        medias =
                            listOf(
                                SaveDraftMedia(
                                    cachePath = "/tmp/a.png",
                                    fileName = "a.png",
                                    mediaType = DraftMediaType.IMAGE,
                                ),
                            ),
                    ),
                )

            repository.deleteTarget(groupId, account)

            assertNull(repository.draft(groupId).first())
            assertEquals(emptyList(), repository.visibleDrafts.first())
        }

    @Test
    fun markSendingAsDraftIfExpiredResetsStatus() =
        runTest {
            val account = MicroBlogKey("alice", "example.com")
            val now = 2_000L
            val groupId =
                repository.saveDraft(
                    SaveDraftInput(
                        groupId = "group-expired",
                        content = sampleContent("retry me"),
                        targets =
                            listOf(
                                SaveDraftTarget(
                                    accountKey = account,
                                    status = DraftTargetStatus.SENDING,
                                    lastAttemptAt = 1_000L,
                                    attemptCount = 2,
                                ),
                            ),
                        medias = emptyList(),
                        createdAt = now,
                    ),
                )

            repository.markSendingAsDraftIfExpired(
                expiredBefore = 1_500L,
                errorMessage = "timeout",
            )

            val draft = repository.draft(groupId).first()

            assertNotNull(draft)
            assertEquals(DraftTargetStatus.DRAFT, draft.targets.single().status)
            assertEquals("timeout", draft.targets.single().errorMessage)
            assertEquals(
                groupId,
                repository.visibleDrafts
                    .first()
                    .single()
                    .groupId,
            )
        }

    private fun sampleContent(text: String) =
        DraftContent(
            text = text,
            visibility = UiTimelineV2.Post.Visibility.Public,
            language = listOf("en"),
            sensitive = false,
        )
}
