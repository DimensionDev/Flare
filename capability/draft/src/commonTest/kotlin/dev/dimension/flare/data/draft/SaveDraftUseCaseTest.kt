package dev.dimension.flare.data.draft

import androidx.room3.Room
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftReferenceType
import dev.dimension.flare.data.database.app.model.DraftVisibility
import dev.dimension.flare.data.database.memoryDatabaseBuilder
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.io.FakeFileStorage
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SaveDraftUseCaseTest {
    private val root = "/save-draft-use-case-test".toPath()
    private lateinit var fileStorage: FakeFileStorage

    private lateinit var db: AppDatabase
    private lateinit var repository: DraftRepository
    private lateinit var mediaStore: DraftMediaStore
    private lateinit var useCase: SaveDraftUseCase

    @BeforeTest
    fun setup() {
        fileStorage = FakeFileStorage(root)
        db =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        mediaStore = DraftMediaStore(fileStorage)
        repository = DraftRepository(db, mediaStore)
        useCase = SaveDraftUseCase(repository, mediaStore)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveDraftPersistsContentTargetsAndMedias() =
        runTest {
            val accountA = mastodonAccount("alice", "mastodon.social")
            val accountB = mastodonAccount("bob", "misskey.io")
            val replyKey = MicroBlogKey("status-1", "weibo.com")
            val firstBytes = byteArrayOf(1, 2, 3)
            val secondBytes = byteArrayOf(4, 5, 6)
            val bundle =
                ComposeDraftBundle(
                    accounts = listOf(accountA, accountB),
                    groupId = "group-1",
                    template =
                        ComposeData(
                            content = "hello draft",
                            visibility = UiTimelineV2.Post.Visibility.Followers,
                            language = listOf("zh", "en"),
                            medias =
                                listOf(
                                    media(name = "a.png", bytes = firstBytes, altText = "cover"),
                                    media(
                                        name = "b.mov",
                                        bytes = secondBytes,
                                        type = dev.dimension.flare.common.FileType.Video,
                                        altText = "clip",
                                    ),
                                ),
                            sensitive = true,
                            spoilerText = "cw",
                            poll =
                                ComposeData.Poll(
                                    options = listOf("a", "b"),
                                    expiredAfter = 300_000L,
                                    multiple = true,
                                ),
                            localOnly = true,
                            referenceStatus =
                                ComposeData.ReferenceStatus(
                                    composeStatus = ComposeStatus.VVOComment(statusKey = replyKey, rootId = "root-1"),
                                ),
                        ),
                )

            val groupId = useCase(bundle)

            assertEquals("group-1", groupId)
            val draft = repository.draft(groupId).first()

            assertNotNull(draft)
            assertEquals("hello draft", draft.content.text)
            assertEquals(DraftVisibility.Followers, draft.content.visibility)
            assertEquals(listOf("zh", "en"), draft.content.language)
            assertTrue(draft.content.sensitive)
            assertEquals("cw", draft.content.spoilerText)
            assertTrue(draft.content.localOnly)
            val poll = assertNotNull(draft.content.poll)
            assertEquals(listOf("a", "b"), poll.options)
            assertEquals(300_000L, poll.expiredAfter)
            assertEquals(true, poll.multiple)
            val reference = assertNotNull(draft.content.reference)
            assertEquals(DraftReferenceType.VVO_COMMENT, reference.type)
            assertEquals(replyKey, reference.statusKey)
            assertEquals("root-1", reference.rootId)

            assertEquals(setOf(accountA.accountKey, accountB.accountKey), draft.targets.map { it.accountKey }.toSet())
            assertEquals(2, draft.medias.size)
            assertEquals(listOf("a.png", "b.mov"), draft.medias.map { it.fileName })
            assertEquals(listOf(DraftMediaType.IMAGE, DraftMediaType.VIDEO), draft.medias.map { it.mediaType })
            assertEquals(listOf("cover", "clip"), draft.medias.map { it.altText })

            draft.medias.forEach { media ->
                assertTrue(fileStorage.exists(media.cachePath.toPath()))
            }
            assertContentEquals(firstBytes, fileStorage.read(draft.medias[0].cachePath.toPath()))
            assertContentEquals(secondBytes, fileStorage.read(draft.medias[1].cachePath.toPath()))
        }

    @Test
    fun saveDraftUsesBundleGroupId() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val bundle =
                ComposeDraftBundle(
                    accounts = listOf(account),
                    groupId = "explicit-group",
                    template =
                        ComposeData(
                            content = "override me",
                        ),
                )

            val groupId = useCase(bundle)

            assertEquals("explicit-group", groupId)
            assertEquals(
                "override me",
                repository
                    .draft("explicit-group")
                    .first()
                    ?.content
                    ?.text,
            )
        }

    @Test
    fun saveDraftGeneratesGroupIdWhenMissing() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")

            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        template =
                            ComposeData(
                                content = "generated id",
                            ),
                    ),
                )

            assertEquals(groupId, Uuid.parse(groupId).toString())
            assertEquals(
                "generated id",
                repository
                    .draft(groupId)
                    .first()
                    ?.content
                    ?.text,
            )
        }

    @Test
    fun saveDraftMapsQuoteReferenceType() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val statusKey = MicroBlogKey("quoted", "mastodon.social")

            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "group-quote",
                        template =
                            ComposeData(
                                content = "quote",
                                referenceStatus =
                                    ComposeData.ReferenceStatus(
                                        composeStatus = ComposeStatus.Quote(statusKey),
                                    ),
                            ),
                    ),
                )

            val reference =
                repository
                    .draft(groupId)
                    .first()
                    ?.content
                    ?.reference

            assertNotNull(reference)
            assertEquals(DraftReferenceType.QUOTE, reference.type)
            assertEquals(statusKey, reference.statusKey)
            assertNull(reference.rootId)
        }

    @Test
    fun saveDraftMapsReplyReferenceType() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val statusKey = MicroBlogKey("reply", "mastodon.social")

            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "group-reply",
                        template =
                            ComposeData(
                                content = "reply",
                                referenceStatus =
                                    ComposeData.ReferenceStatus(
                                        composeStatus = ComposeStatus.Reply(statusKey),
                                    ),
                            ),
                    ),
                )

            val reference =
                repository
                    .draft(groupId)
                    .first()
                    ?.content
                    ?.reference

            assertNotNull(reference)
            assertEquals(DraftReferenceType.REPLY, reference.type)
            assertEquals(statusKey, reference.statusKey)
            assertNull(reference.rootId)
        }

    @Test
    fun saveDraftPreservesDefaultsAndNullFields() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "group-defaults",
                        template =
                            ComposeData(
                                content = "",
                                medias =
                                    listOf(
                                        media(name = "note.bin", bytes = byteArrayOf(7, 8), type = FileType.Other, altText = null),
                                        media(name = "empty-alt.png", bytes = byteArrayOf(9), altText = ""),
                                    ),
                                spoilerText = null,
                                poll = null,
                                referenceStatus = null,
                            ),
                    ),
                )

            val draft = repository.draft(groupId).first()

            assertNotNull(draft)
            assertEquals("", draft.content.text)
            assertEquals(DraftVisibility.Public, draft.content.visibility)
            assertEquals(listOf("en"), draft.content.language)
            assertEquals(false, draft.content.sensitive)
            assertNull(draft.content.spoilerText)
            assertEquals(false, draft.content.localOnly)
            assertNull(draft.content.poll)
            assertNull(draft.content.reference)
            assertEquals(2, draft.medias.size)
            assertEquals(listOf(DraftMediaType.OTHER, DraftMediaType.IMAGE), draft.medias.map { it.mediaType })
            assertEquals(listOf<String?>(null, ""), draft.medias.map { it.altText })
        }

    @Test
    fun saveDraftPreservesPollBoundaryValues() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "group-poll-boundary",
                        template =
                            ComposeData(
                                content = "poll",
                                spoilerText = "",
                                poll =
                                    ComposeData.Poll(
                                        options = emptyList(),
                                        expiredAfter = Long.MAX_VALUE,
                                        multiple = false,
                                    ),
                            ),
                    ),
                )

            val draft = assertNotNull(repository.draft(groupId).first())
            val poll = assertNotNull(draft.content.poll)

            assertEquals(emptyList(), poll.options)
            assertEquals(Long.MAX_VALUE, poll.expiredAfter)
            assertEquals(false, poll.multiple)
            assertEquals("", draft.content.spoilerText)
        }

    @Test
    fun saveDraftUpdatesExistingGroupAndReusesExistingMediaFiles() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val initialGroupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "group-3",
                        template =
                            ComposeData(
                                content = "first",
                                medias = listOf(media(name = "a.png", bytes = byteArrayOf(1, 2, 3), altText = "a")),
                            ),
                    ),
                )
            val initialDraft = repository.draft(initialGroupId).first()
            assertNotNull(initialDraft)
            val initialPath = initialDraft.medias.single().cachePath
            val restoredMedia = mediaStore.restore(initialDraft.medias)

            val savedGroupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = initialGroupId,
                        template =
                            ComposeData(
                                content = "second",
                                medias = restoredMedia,
                            ),
                    ),
                )

            val updatedDraft = repository.draft(savedGroupId).first()

            assertEquals(initialGroupId, savedGroupId)
            assertNotNull(updatedDraft)
            assertEquals("second", updatedDraft.content.text)
            assertEquals(1, updatedDraft.medias.size)
            assertEquals(initialPath, updatedDraft.medias.single().cachePath)
            assertTrue(fileStorage.exists(initialPath.toPath()))
            assertEquals(
                1,
                fileStorage.list(root.resolve("draft_media").resolve(initialGroupId)).size,
            )
            assertNotEquals("", updatedDraft.medias.single().cachePath)
        }

    @Test
    fun saveDraftRemovesStaleMediaFilesWhenMediaListShrinks() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "group-cleanup",
                        template =
                            ComposeData(
                                content = "first",
                                medias =
                                    listOf(
                                        media(name = "a.png", bytes = byteArrayOf(1), altText = "a"),
                                        media(name = "b.png", bytes = byteArrayOf(2), altText = "b"),
                                    ),
                            ),
                    ),
                )
            val initialDraft = assertNotNull(repository.draft(groupId).first())
            val removedPath = initialDraft.medias.last().cachePath
            val restored = mediaStore.restore(initialDraft.medias).take(1)

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(account),
                    groupId = groupId,
                    template =
                        ComposeData(
                            content = "second",
                            medias = restored,
                        ),
                ),
            )

            val updatedDraft = assertNotNull(repository.draft(groupId).first())

            assertEquals(1, updatedDraft.medias.size)
            assertFalse(fileStorage.exists(removedPath.toPath()))
            assertEquals(1, fileStorage.list(root.resolve("draft_media").resolve(groupId)).size)
        }

    @Test
    fun saveDraftDeduplicatesRepeatedAccountsByAccountKey() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")

            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account, account, account),
                        groupId = "group-duplicate-accounts",
                        template =
                            ComposeData(
                                content = "duplicate accounts",
                            ),
                    ),
                )

            val draft = assertNotNull(repository.draft(groupId).first())

            assertEquals(1, draft.targets.size)
            assertEquals(account.accountKey, draft.targets.single().accountKey)
        }

    @Test
    fun saveDraftKeepsCreatedAtAndRefreshesUpdatedAtOnResave() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "group-timestamps",
                        template =
                            ComposeData(
                                content = "first",
                            ),
                    ),
                )
            val firstDraft = assertNotNull(repository.draft(groupId).first())

            delay(5)

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(account),
                    groupId = groupId,
                    template =
                        ComposeData(
                            content = "second",
                        ),
                ),
            )

            val updatedDraft = assertNotNull(repository.draft(groupId).first())

            assertEquals(firstDraft.createdAt, updatedDraft.createdAt)
            assertTrue(updatedDraft.updatedAt >= firstDraft.updatedAt)
            assertEquals("second", updatedDraft.content.text)
        }

    @Test
    fun saveDraftAllowsEmptyAccountList() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")

            val groupId =
                useCase(
                    ComposeDraftBundle(
                        accounts = emptyList(),
                        groupId = "group-no-accounts",
                        template =
                            ComposeData(
                                content = "no accounts",
                            ),
                    ),
                )

            val draft = repository.draft(groupId).first()

            assertNotNull(draft)
            assertTrue(draft.targets.isEmpty())
            assertEquals("no accounts", draft.content.text)
        }

    private fun mastodonAccount(
        id: String,
        host: String,
    ): UiAccount =
        UiAccount.Mastodon(
            accountKey = MicroBlogKey(id, host),
            instance = host,
        )

    private fun media(
        name: String,
        bytes: ByteArray,
        type: FileType = FileType.Image,
        altText: String?,
    ): ComposeData.Media =
        ComposeData.Media(
            file = fileItem(name = name, bytes = bytes, type = type),
            altText = altText,
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
