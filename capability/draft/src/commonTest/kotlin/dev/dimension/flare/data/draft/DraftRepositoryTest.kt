package dev.dimension.flare.data.draft

import androidx.room3.Room
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftReferenceType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.database.app.model.DraftVisibility
import dev.dimension.flare.data.database.memoryDatabaseBuilder
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.io.FakeFileStorage
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DraftRepositoryTest {
    private lateinit var fileStorage: FakeFileStorage
    private lateinit var db: AppDatabase
    private lateinit var repository: DraftRepository
    private lateinit var mediaStore: DraftMediaStore

    @BeforeTest
    fun setup() {
        fileStorage = FakeFileStorage()
        db =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        mediaStore = DraftMediaStore(fileStorage)
        repository = DraftRepository(db, mediaStore)
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
                                visibility = DraftVisibility.Public,
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
    fun deleteGroupDeletesPersistedMediaFiles() =
        runTest {
            val persistedMedia =
                mediaStore.persist(
                    groupId = "group-media-delete",
                    medias =
                        listOf(
                            ComposeData.Media(
                                file = fileItem(name = "a.png", bytes = byteArrayOf(1, 2, 3), type = FileType.Image),
                                altText = "cover",
                            ),
                        ),
                )

            repository.saveDraft(
                SaveDraftInput(
                    groupId = "group-media-delete",
                    content = sampleContent("with media"),
                    targets = listOf(SaveDraftTarget(accountKey = MicroBlogKey("alice", "example.com"))),
                    medias = persistedMedia,
                ),
            )

            repository.deleteGroup("group-media-delete")

            assertNull(repository.draft("group-media-delete").first())
            assertFalse(fileStorage.exists(persistedMedia.single().cachePath.toPath()))
        }

    @Test
    fun markSendingAsFailedResetsStatus() =
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

            repository.markSendingAsFailed(errorMessage = "interrupted")

            val draft = repository.draft(groupId).first()

            assertNotNull(draft)
            assertEquals(DraftTargetStatus.FAILED, draft.targets.single().status)
            assertEquals("interrupted", draft.targets.single().errorMessage)
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
            visibility = DraftVisibility.Public,
            language = listOf("en"),
            sensitive = false,
        )

    private fun fileItem(
        name: String?,
        bytes: ByteArray,
        type: FileType,
    ): FileItem =
        FileItem(
            name,
            type,
            { bytes },
        )
}
