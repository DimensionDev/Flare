package dev.dimension.flare.ui.presenter.compose

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.FileType
import dev.dimension.flare.createTestFileItem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftReferenceType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.repository.ComposeDraftBundle
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.SaveDraftInput
import dev.dimension.flare.data.repository.SaveDraftMedia
import dev.dimension.flare.data.repository.SaveDraftTarget
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendDraftUseCaseTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val fileSystem = FileSystem.SYSTEM
    private val pathProducer =
        object : PlatformPathProducer {
            override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

            override fun draftMediaFile(
                groupId: String,
                fileName: String,
            ): Path = root.resolve("draft_media").resolve(groupId).resolve(fileName)
        }

    private lateinit var db: AppDatabase
    private lateinit var repository: DraftRepository
    private lateinit var mediaStore: DraftMediaStore

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        mediaStore = DraftMediaStore(pathProducer, fileSystem)
        repository = DraftRepository(db, mediaStore)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        deleteTestRootPath(root)
    }

    @Test
    fun sendBundleSuccessDeletesDraftAfterAllTargetsSucceed() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val sent = mutableListOf<SentCompose>()
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase =
                testUseCase(sent = sent) { _, _, progress ->
                    progress()
                }
            val bundle =
                ComposeDraftBundle(
                    accounts = listOf(account),
                    groupId = "send-success",
                    template =
                        ComposeData(
                            content = "hello",
                            medias = listOf(media(name = "a.png", bytes = byteArrayOf(1, 2, 3), altText = "cover")),
                        ),
                )

            useCase(bundle) { progresses += it }
            advanceUntilIdle()

            assertEquals(1, sent.size)
            assertEquals(account.accountKey, sent.single().account.accountKey)
            assertEquals("hello", sent.single().data.content)
            assertNull(repository.draft("send-success").first())
            assertEquals(ComposeProgressState.Progress(0, 2), progresses.first())
            assertEquals(ComposeProgressState.Progress(1, 2), progresses[1])
            assertEquals(ComposeProgressState.Progress(2, 2), progresses[2])
            assertIs<ComposeProgressState.Success>(progresses.last())
        }

    @Test
    fun sendBundleAllTargetsSuccessDeletesDraft() =
        runTest {
            val accountA = mastodonAccount("alice", "mastodon.social")
            val accountB = mastodonAccount("bob", "mastodon.social")
            val sent = mutableListOf<SentCompose>()
            val useCase = testUseCase(sent = sent) { _, _, progress -> progress() }

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(accountA, accountB),
                    groupId = "send-all-success",
                    template =
                        ComposeData(
                            content = "multi success",
                        ),
                ),
            ) {}
            advanceUntilIdle()

            assertEquals(listOf(accountA.accountKey, accountB.accountKey), sent.map { it.account.accountKey })
            assertNull(repository.draft("send-all-success").first())
        }

    @Test
    fun sendBundlePartialSuccessKeepsFailedTargetAndStillEmitsSuccess() =
        runTest {
            val accountA = mastodonAccount("alice", "mastodon.social")
            val accountB = mastodonAccount("bob", "mastodon.social")
            val progresses = mutableListOf<ComposeProgressState>()
            val sent = mutableListOf<SentCompose>()
            val useCase =
                testUseCase(sent = sent) { account, _, progress ->
                    progress()
                    if (account.accountKey == accountB.accountKey) {
                        error("account-b failed")
                    }
                }

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(accountA, accountB),
                    groupId = "send-partial-failure",
                    template =
                        ComposeData(
                            content = "partial failure",
                        ),
                ),
            ) { progresses += it }
            advanceUntilIdle()

            val draft = assertNotNull(repository.draft("send-partial-failure").first())
            assertEquals(1, draft.targets.size)
            assertEquals(accountB.accountKey, draft.targets.single().accountKey)
            assertEquals(DraftTargetStatus.FAILED, draft.targets.single().status)
            assertEquals("account-b failed", draft.targets.single().errorMessage)
            assertEquals(listOf(accountA.accountKey, accountB.accountKey), sent.map { it.account.accountKey })
            val error = assertIs<ComposeProgressState.Error>(progresses.last())
            assertIs<ComposeDraftFailedException>(error.throwable)
        }

    @Test
    fun sendBundleAllTargetsFailKeepsDraftWithAllFailedTargets() =
        runTest {
            val accountA = mastodonAccount("alice", "mastodon.social")
            val accountB = mastodonAccount("bob", "mastodon.social")
            val useCase =
                testUseCase { _, _, _ ->
                    error("all failed")
                }

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(accountA, accountB),
                    groupId = "send-all-failed",
                    template =
                        ComposeData(
                            content = "all fail",
                        ),
                ),
            ) {}
            advanceUntilIdle()

            val draft = assertNotNull(repository.draft("send-all-failed").first())
            assertEquals(2, draft.targets.size)
            assertTrue(draft.targets.all { it.status == DraftTargetStatus.FAILED })
            assertEquals(setOf("all failed"), draft.targets.mapNotNull { it.errorMessage }.toSet())
        }

    @Test
    fun sendBundleFailureMarksTargetFailedAndKeepsDraft() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase = testUseCase { _, _, _ -> error("boom") }

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(account),
                    groupId = "send-failure",
                    template =
                        ComposeData(
                            content = "failure",
                        ),
                ),
            ) { progresses += it }
            advanceUntilIdle()

            val draft = assertNotNull(repository.draft("send-failure").first())
            val target = draft.targets.single()

            assertEquals(DraftTargetStatus.FAILED, target.status)
            assertEquals("boom", target.errorMessage)
            assertEquals(ComposeProgressState.Progress(0, 1), progresses.first())
            val error = assertIs<ComposeProgressState.Error>(progresses.last())
            assertIs<ComposeDraftFailedException>(error.throwable)
        }

    @Test
    fun sendBundleWithoutMediaPersistsAndSendsNormally() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val sent = mutableListOf<SentCompose>()
            val useCase = testUseCase(sent = sent) { _, _, _ -> }

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(account),
                    groupId = "send-no-media",
                    template =
                        ComposeData(
                            content = "no media",
                            medias = emptyList(),
                        ),
                ),
            ) {}
            advanceUntilIdle()

            assertEquals(1, sent.size)
            assertTrue(
                sent
                    .single()
                    .data.medias
                    .isEmpty(),
            )
            assertNull(repository.draft("send-no-media").first())
        }

    @Test
    fun sendBundleWithMultipleMediaPersistsAllAttachments() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val sent = mutableListOf<SentCompose>()
            val useCase = testUseCase(sent = sent) { _, _, _ -> }

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(account),
                    groupId = "send-multi-media",
                    template =
                        ComposeData(
                            content = "multi media",
                            medias =
                                listOf(
                                    media(name = "a.png", bytes = byteArrayOf(1), altText = "a"),
                                    media(name = "b.mov", bytes = byteArrayOf(2), type = FileType.Video, altText = "b"),
                                ),
                        ),
                ),
            ) {}
            advanceUntilIdle()

            assertEquals(
                2,
                sent
                    .single()
                    .data.medias.size,
            )
            assertEquals(
                listOf("a", "b"),
                sent
                    .single()
                    .data.medias
                    .map { it.altText },
            )
            assertNull(repository.draft("send-multi-media").first())
        }

    @Test
    fun sendBundleWithEmptyAccountsOnlyEmitsProgressAndSuccess() =
        runTest {
            val sent = mutableListOf<SentCompose>()
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase = testUseCase(sent = sent) { _, _, _ -> }

            useCase(
                ComposeDraftBundle(
                    accounts = emptyList(),
                    groupId = "send-empty-accounts",
                    template =
                        ComposeData(
                            content = "empty accounts",
                        ),
                ),
            ) { progresses += it }
            advanceUntilIdle()

            assertTrue(sent.isEmpty())
            assertEquals(
                listOf(
                    ComposeProgressState.Progress(0, 0),
                    ComposeProgressState.Success,
                ),
                progresses,
            )
            val draft = assertNotNull(repository.draft("send-empty-accounts").first())
            assertTrue(draft.targets.isEmpty())
        }

    @Test
    fun resendMissingDraftReturnsWithoutProgress() =
        runTest {
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase = testUseCase()

            useCase("missing-group") { progresses += it }
            advanceUntilIdle()

            assertTrue(progresses.isEmpty())
        }

    @Test
    fun resendSkipsTargetsWithoutResolvedAccountAndKeepsTheirStatus() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "resend-missing-account",
                content = sampleContent("missing account"),
                targets =
                    listOf(
                        SaveDraftTarget(accountKey = account.accountKey, status = DraftTargetStatus.FAILED),
                    ),
            )
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase = testUseCase(findAccount = { null })

            useCase("resend-missing-account") { progresses += it }
            advanceUntilIdle()

            val draft = assertNotNull(repository.draft("resend-missing-account").first())
            assertEquals(DraftTargetStatus.FAILED, draft.targets.single().status)
            assertEquals(
                listOf(
                    ComposeProgressState.Progress(0, 0),
                    ComposeProgressState.Success,
                ),
                progresses,
            )
        }

    @Test
    fun resendAllSendingTargetsOnlyEmitsProgressAndSuccess() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "resend-all-sending",
                content = sampleContent("sending"),
                targets =
                    listOf(
                        SaveDraftTarget(accountKey = account.accountKey, status = DraftTargetStatus.SENDING),
                    ),
            )
            val sent = mutableListOf<SentCompose>()
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase = testUseCase(sent = sent, findAccount = { account })

            useCase("resend-all-sending") { progresses += it }
            advanceUntilIdle()

            assertTrue(sent.isEmpty())
            assertEquals(
                listOf(
                    ComposeProgressState.Progress(0, 0),
                    ComposeProgressState.Success,
                ),
                progresses,
            )
            assertEquals(
                DraftTargetStatus.SENDING,
                repository
                    .draft("resend-all-sending")
                    .first()
                    ?.targets
                    ?.single()
                    ?.status,
            )
        }

    @Test
    fun resendDraftGroupRestoresDataAndSkipsSendingTargets() =
        runTest {
            val failedAccount = mastodonAccount("alice", "mastodon.social")
            val sendingAccount = mastodonAccount("bob", "mastodon.social")
            val originalBytes = byteArrayOf(9, 8, 7)
            val persistedMedias =
                mediaStore.persist(
                    "resend-group",
                    listOf(
                        media(name = "cached.png", bytes = originalBytes, altText = "cached"),
                    ),
                )
            repository.saveDraft(
                SaveDraftInput(
                    groupId = "resend-group",
                    content =
                        DraftContent(
                            text = "restore me",
                            visibility = UiTimelineV2.Post.Visibility.Followers,
                            language = listOf("zh"),
                            sensitive = true,
                            spoilerText = "cw",
                            localOnly = true,
                            poll =
                                DraftContent.DraftPoll(
                                    options = listOf("yes"),
                                    expiredAfter = 999L,
                                    multiple = false,
                                ),
                            reference =
                                DraftContent.DraftReference(
                                    type = DraftReferenceType.QUOTE,
                                    statusKey = MicroBlogKey("quoted", "mastodon.social"),
                                ),
                        ),
                    targets =
                        listOf(
                            SaveDraftTarget(accountKey = failedAccount.accountKey, status = DraftTargetStatus.FAILED),
                            SaveDraftTarget(accountKey = sendingAccount.accountKey, status = DraftTargetStatus.SENDING),
                        ),
                    medias =
                        persistedMedias.map {
                            SaveDraftMedia(
                                cachePath = it.cachePath,
                                fileName = it.fileName,
                                mediaType = it.mediaType,
                                altText = it.altText,
                            )
                        },
                ),
            )

            val sent = mutableListOf<SentCompose>()
            val useCase =
                SendDraftUseCase(
                    draftRepository = repository,
                    draftMediaStore = mediaStore,
                    findAccount = {
                        when (it) {
                            failedAccount.accountKey -> failedAccount
                            sendingAccount.accountKey -> sendingAccount
                            else -> null
                        }
                    },
                    composeDraft = { account, data, _ -> sent += SentCompose(account = account, data = data) },
                )

            useCase("resend-group") {}
            advanceUntilIdle()

            assertEquals(1, sent.size)
            val resent = sent.single()
            assertEquals(failedAccount.accountKey, resent.account.accountKey)
            assertEquals("restore me", resent.data.content)
            assertEquals(UiTimelineV2.Post.Visibility.Followers, resent.data.visibility)
            assertEquals(listOf("zh"), resent.data.language)
            assertEquals(true, resent.data.sensitive)
            assertEquals("cw", resent.data.spoilerText)
            assertEquals(true, resent.data.localOnly)
            assertEquals(listOf("yes"), resent.data.poll?.options)
            assertEquals(999L, resent.data.poll?.expiredAfter)
            assertEquals(false, resent.data.poll?.multiple)
            assertEquals(
                DraftReferenceType.QUOTE,
                resent.data.referenceStatus?.composeStatus?.let {
                    when (it) {
                        is ComposeStatus.Quote -> DraftReferenceType.QUOTE
                        is ComposeStatus.Reply -> DraftReferenceType.REPLY
                        is ComposeStatus.VVOComment -> DraftReferenceType.VVO_COMMENT
                    }
                },
            )
            assertEquals(
                "cached",
                resent.data.medias
                    .single()
                    .altText,
            )
            assertContentEquals(
                originalBytes,
                resent.data.medias
                    .single()
                    .file
                    .readBytes(),
            )

            val remainingDraft = assertNotNull(repository.draft("resend-group").first())
            assertEquals(1, remainingDraft.targets.size)
            assertEquals(sendingAccount.accountKey, remainingDraft.targets.single().accountKey)
            assertEquals(DraftTargetStatus.SENDING, remainingDraft.targets.single().status)
        }

    @Test
    fun resendDraftRestoresReplyReference() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "resend-reply",
                content =
                    sampleContent("reply").copy(
                        reference =
                            DraftContent.DraftReference(
                                type = DraftReferenceType.REPLY,
                                statusKey = MicroBlogKey("reply", "mastodon.social"),
                            ),
                    ),
                targets = listOf(SaveDraftTarget(accountKey = account.accountKey, status = DraftTargetStatus.FAILED)),
            )
            val sent = mutableListOf<SentCompose>()
            val useCase = testUseCase(sent = sent, findAccount = { account })

            useCase("resend-reply") {}
            advanceUntilIdle()

            assertIs<ComposeStatus.Reply>(
                sent
                    .single()
                    .data.referenceStatus
                    ?.composeStatus,
            )
            assertEquals(
                MicroBlogKey("reply", "mastodon.social"),
                sent
                    .single()
                    .data.referenceStatus
                    ?.composeStatus
                    ?.statusKey,
            )
        }

    @Test
    fun resendDraftRestoresVvoCommentReference() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "resend-vvo",
                content =
                    sampleContent("vvo").copy(
                        reference =
                            DraftContent.DraftReference(
                                type = DraftReferenceType.VVO_COMMENT,
                                statusKey = MicroBlogKey("reply", "weibo.com"),
                                rootId = "root-id",
                            ),
                    ),
                targets = listOf(SaveDraftTarget(accountKey = account.accountKey, status = DraftTargetStatus.FAILED)),
            )
            val sent = mutableListOf<SentCompose>()
            val useCase = testUseCase(sent = sent, findAccount = { account })

            useCase("resend-vvo") {}
            advanceUntilIdle()

            val composeStatus =
                assertIs<ComposeStatus.VVOComment>(
                    sent
                        .single()
                        .data.referenceStatus
                        ?.composeStatus,
                )
            assertEquals("root-id", composeStatus.rootId)
        }

    @Test
    fun resendDraftWithoutPollAndReferenceRestoresNulls() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "resend-nullables",
                content = sampleContent("nullables"),
                targets = listOf(SaveDraftTarget(accountKey = account.accountKey, status = DraftTargetStatus.FAILED)),
            )
            val sent = mutableListOf<SentCompose>()
            val useCase = testUseCase(sent = sent, findAccount = { account })

            useCase("resend-nullables") {}
            advanceUntilIdle()

            assertNull(sent.single().data.poll)
            assertNull(sent.single().data.referenceStatus)
        }

    @Test
    fun resendVvoCommentWithoutRootIdThrows() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "resend-vvo-invalid",
                content =
                    sampleContent("invalid").copy(
                        reference =
                            DraftContent.DraftReference(
                                type = DraftReferenceType.VVO_COMMENT,
                                statusKey = MicroBlogKey("reply", "weibo.com"),
                                rootId = null,
                            ),
                    ),
                targets = listOf(SaveDraftTarget(accountKey = account.accountKey, status = DraftTargetStatus.FAILED)),
            )
            val useCase =
                testUseCase(findAccount = { account }) { _, data, _ ->
                    data.medias
                        .single()
                        .file
                        .readBytes()
                }

            assertFailsWith<IllegalArgumentException> {
                useCase("resend-vvo-invalid") {}
            }
        }

    @Test
    fun resendMissingCachedMediaMarksTargetFailed() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "resend-restore-fail",
                content = sampleContent("restore fail"),
                targets = listOf(SaveDraftTarget(accountKey = account.accountKey, status = DraftTargetStatus.FAILED)),
                medias =
                    listOf(
                        SaveDraftMedia(
                            cachePath =
                                root
                                    .resolve("draft_media")
                                    .resolve("resend-restore-fail")
                                    .resolve("missing.png")
                                    .toString(),
                            fileName = "missing.png",
                            mediaType = DraftMediaType.IMAGE,
                        ),
                    ),
            )
            val useCase =
                testUseCase(findAccount = { account }) { _, data, _ ->
                    data.medias
                        .single()
                        .file
                        .readBytes()
                }

            useCase("resend-restore-fail") {}
            advanceUntilIdle()

            val draft = assertNotNull(repository.draft("resend-restore-fail").first())
            assertEquals(DraftTargetStatus.FAILED, draft.targets.single().status)
            assertTrue(
                draft.targets
                    .single()
                    .errorMessage
                    ?.isNotBlank() == true,
            )
        }

    @Test
    fun sendBundlePersistFailurePropagatesException() =
        runTest {
            val blockedParent = root.resolve("blocked-send")
            fileSystem.write(blockedParent) {
                writeUtf8("not a directory")
            }
            val blockedStore =
                DraftMediaStore(
                    platformPathProducer =
                        object : PlatformPathProducer {
                            override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

                            override fun draftMediaFile(
                                groupId: String,
                                fileName: String,
                            ): Path = blockedParent.resolve(groupId).resolve(fileName)
                        },
                    fileSystem = fileSystem,
                )
            val account = mastodonAccount("alice", "mastodon.social")
            val useCase =
                SendDraftUseCase(
                    draftRepository = repository,
                    draftMediaStore = blockedStore,
                    findAccount = { null },
                    composeDraft = { _, _, _ -> },
                )

            assertFailsWith<Throwable> {
                useCase(
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "send-persist-fail",
                        template =
                            ComposeData(
                                content = "persist fail",
                                medias = listOf(media(name = "a.png", bytes = byteArrayOf(1), altText = null)),
                            ),
                    ),
                ) {}
            }
        }

    @Test
    fun sendBundleProgressSequenceIncludesErrorsAndFinalSuccess() =
        runTest {
            val accountA = mastodonAccount("alice", "mastodon.social")
            val accountB = mastodonAccount("bob", "mastodon.social")
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase =
                testUseCase { account, _, progress ->
                    progress()
                    if (account.accountKey == accountB.accountKey) {
                        error("second failed")
                    }
                    progress()
                }

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(accountA, accountB),
                    groupId = "send-progress-order",
                    template =
                        ComposeData(
                            content = "progress order",
                        ),
                ),
            ) { progresses += it }
            advanceUntilIdle()

            assertEquals(ComposeProgressState.Progress(0, 2), progresses.first())
            assertTrue(progresses.filterIsInstance<ComposeProgressState.Progress>().contains(ComposeProgressState.Progress(1, 2)))
            val error = assertIs<ComposeProgressState.Error>(progresses.last())
            assertIs<ComposeDraftFailedException>(error.throwable)
        }

    private fun testUseCase(
        sent: MutableList<SentCompose> = mutableListOf(),
        findAccount: suspend (MicroBlogKey) -> UiAccount? = { null },
        composeDraft: suspend (UiAccount, ComposeData, () -> Unit) -> Unit = { _, _, _ -> },
    ): SendDraftUseCase =
        SendDraftUseCase(
            draftRepository = repository,
            draftMediaStore = mediaStore,
            findAccount = findAccount,
            composeDraft = { account, data, progress ->
                sent += SentCompose(account = account, data = data)
                composeDraft(account, data, progress)
            },
        )

    private suspend fun saveDraftGroup(
        groupId: String,
        content: DraftContent,
        targets: List<SaveDraftTarget>,
        medias: List<SaveDraftMedia> = emptyList(),
    ) {
        repository.saveDraft(
            SaveDraftInput(
                groupId = groupId,
                content = content,
                targets = targets,
                medias = medias,
            ),
        )
    }

    private fun sampleContent(text: String) =
        DraftContent(
            text = text,
            visibility = UiTimelineV2.Post.Visibility.Public,
            language = listOf("en"),
            sensitive = false,
            spoilerText = null,
            localOnly = false,
            poll = null,
            reference = null,
        )

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
            file = createTestFileItem(root = root, name = name, bytes = bytes, type = type),
            altText = altText,
        )

    private data class SentCompose(
        val account: UiAccount,
        val data: ComposeData,
    )
}
