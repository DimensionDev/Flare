package dev.dimension.flare.ui.presenter.compose

import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.FileType
import dev.dimension.flare.createTestFileItem
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftReferenceType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.repository.ComposeDraftBundle
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.SaveDraftInput
import dev.dimension.flare.data.repository.SaveDraftMedia
import dev.dimension.flare.data.repository.SaveDraftTarget
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
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
    private val fileSystem = createTestFileSystem()
    private val fileStorage = OkioFileStorage(fileSystem, root)

    private lateinit var db: AppDatabase
    private lateinit var repository: DraftRepository
    private lateinit var mediaStore: DraftMediaStore

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        mediaStore = DraftMediaStore(fileStorage)
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
    fun resendMissingTargetAccountKeepsDraftFailedAndReportsError() =
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
            assertEquals(ComposeProgressState.Progress(0, 0), progresses.first())
            assertIs<ComposeProgressState.Error>(progresses.last())
        }

    @Test
    fun resendAllSendingTargetsReturnsWithoutProgress() =
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
            assertTrue(progresses.isEmpty())
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
                                sourceAccountKey = account.accountKey,
                                sourcePlatform = PlatformType.Mastodon,
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
            val blockedParent = root.resolve("draft_media")
            fileSystem.write(blockedParent) {
                writeUtf8("not a directory")
            }
            val blockedStore = DraftMediaStore(fileStorage)
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

    @Test
    fun shareUrlIsAppendedOnlyWhenTargetLimitAllowsIt() {
        val url = "https://example.com/post/1"

        assertEquals(url, appendShareUrlIfAllowed("", url, 100))
        assertEquals("hello\n\n$url", appendShareUrlIfAllowed("hello  \n", url, 100))
        assertEquals("hello $url", appendShareUrlIfAllowed("hello $url", url, 100))
        assertEquals("hello", appendShareUrlIfAllowed("hello", url, 10))
        assertEquals("hello", appendShareUrlIfAllowed("hello", null, 100))
    }

    @Test
    fun crossPlatformTargetBecomesNewPostWithShareImage() {
        val sourceAccount = mastodonAccount("alice", "mastodon.social")
        val targetAccount = blueskyAccount("bob", "bsky.social")
        val userMedia = media(name = "user.png", bytes = byteArrayOf(1), altText = "user")
        val shareMedia = media(name = "share.png", bytes = byteArrayOf(2), altText = null)
        val url = "https://mastodon.social/@alice/1"
        val data =
            ComposeData(
                content = "hello",
                medias = listOf(userMedia),
                referenceStatus =
                    ComposeData.ReferenceStatus(
                        composeStatus = ComposeStatus.Quote(MicroBlogKey("1", "mastodon.social")),
                        sourceAccountKey = sourceAccount.accountKey,
                        sourcePlatform = PlatformType.Mastodon,
                        shareUrl = url,
                        shareMedia = shareMedia,
                    ),
            )

        val targetData = data.forTarget(targetAccount, composeConfig(maxLength = 100, maxMediaCount = 2))
        val nativeData = data.forTarget(sourceAccount, composeConfig(maxLength = 100, maxMediaCount = 2))

        assertEquals("hello\n\n$url", targetData.content)
        assertEquals(listOf(userMedia, shareMedia), targetData.medias)
        assertNull(targetData.referenceStatus)
        assertEquals(data, nativeData)
    }

    @Test
    fun targetPreparationFailureDoesNotBlockOtherTargets() =
        runTest {
            val validAccount = mastodonAccount("alice", "mastodon.social")
            val invalidAccount = blueskyAccount("bob", "bsky.social")
            val sent = mutableListOf<SentCompose>()
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase =
                SendDraftUseCase(
                    draftRepository = repository,
                    draftMediaStore = mediaStore,
                    findAccount = { null },
                    prepareData = { account, data ->
                        if (account.accountKey == invalidAccount.accountKey) {
                            error("invalid target payload")
                        }
                        data
                    },
                    composeDraft = { account, data, _ -> sent += SentCompose(account, data) },
                )

            useCase(
                ComposeDraftBundle(
                    accounts = listOf(validAccount, invalidAccount),
                    groupId = "prepare-partial-failure",
                    template = ComposeData(content = "hello"),
                ),
            ) { progresses += it }
            advanceUntilIdle()

            assertEquals(listOf(validAccount.accountKey), sent.map { it.account.accountKey })
            val draft = assertNotNull(repository.draft("prepare-partial-failure").first())
            assertEquals(invalidAccount.accountKey, draft.targets.single().accountKey)
            assertEquals(DraftTargetStatus.FAILED, draft.targets.single().status)
            assertEquals("invalid target payload", draft.targets.single().errorMessage)
            assertIs<ComposeProgressState.Error>(progresses.last())
        }

    @Test
    fun resendSkipsPreparingTargets() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "resend-preparing",
                content = sampleContent("preparing"),
                targets = listOf(SaveDraftTarget(accountKey = account.accountKey, status = DraftTargetStatus.PREPARING)),
            )
            val sent = mutableListOf<SentCompose>()
            val progresses = mutableListOf<ComposeProgressState>()
            val useCase = testUseCase(sent = sent, findAccount = { account })

            useCase("resend-preparing") { progresses += it }
            advanceUntilIdle()

            assertTrue(sent.isEmpty())
            assertEquals(
                DraftTargetStatus.PREPARING,
                repository
                    .draft("resend-preparing")
                    .first()
                    ?.targets
                    ?.single()
                    ?.status,
            )
            assertTrue(progresses.isEmpty())
        }

    @Test
    fun failedCrossPlatformDraftReusesPersistedShareImageOnRetry() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            val sourceAccountKey = MicroBlogKey("source", "bsky.social")
            val userMedia = media(name = "user.png", bytes = byteArrayOf(1), altText = "user")
            val shareMedia = media(name = "share.png", bytes = byteArrayOf(2), altText = null)
            val data =
                ComposeData(
                    content = "retry",
                    medias = listOf(userMedia),
                    referenceStatus =
                        ComposeData.ReferenceStatus(
                            composeStatus = ComposeStatus.Quote(MicroBlogKey("post", "bsky.social")),
                            sourceAccountKey = sourceAccountKey,
                            sourcePlatform = PlatformType.Bluesky,
                            shareUrl = "https://bsky.app/profile/source/post/post",
                            shareMedia = shareMedia,
                        ),
                )
            val failingUseCase = testUseCase { _, _, _ -> error("upload failed") }

            failingUseCase(
                ComposeDraftBundle(
                    accounts = listOf(account),
                    groupId = "share-image-retry",
                    template = data,
                ),
            ) {}

            val failedDraft = assertNotNull(repository.draft("share-image-retry").first())
            assertEquals(1, failedDraft.content.reference?.shareImageMediaIndex)
            assertEquals(sourceAccountKey, failedDraft.content.reference?.sourceAccountKey)
            assertEquals(PlatformType.Bluesky, failedDraft.content.reference?.sourcePlatform)
            assertEquals(2, failedDraft.medias.size)

            val resent = mutableListOf<SentCompose>()
            var retriedShareBytes: ByteArray? = null
            val retryUseCase =
                testUseCase(sent = resent, findAccount = { account }) { _, retryData, _ ->
                    retriedShareBytes =
                        retryData.referenceStatus
                            ?.shareMedia
                            ?.file
                            ?.readBytes()
                }
            retryUseCase("share-image-retry") {}

            val retriedData = resent.single().data
            assertEquals(listOf("user"), retriedData.medias.map { it.altText })
            assertContentEquals(byteArrayOf(2), retriedShareBytes)
            assertNull(repository.draft("share-image-retry").first())
        }

    @Test
    fun missingShareImageIsRerenderedPersistedAndReusedAfterUploadFailure() =
        runTest {
            val sourceAccountKey = MicroBlogKey("source", "bsky.social")
            val targetAccount = mastodonAccount("target", "mastodon.social")
            val statusKey = MicroBlogKey("post", "bsky.social")
            saveDraftGroup(
                groupId = "rerender-share-image",
                content =
                    sampleContent("rerender").copy(
                        reference =
                            DraftContent.DraftReference(
                                type = DraftReferenceType.QUOTE,
                                statusKey = statusKey,
                                sourceAccountKey = sourceAccountKey,
                                sourcePlatform = PlatformType.Bluesky,
                                shareUrl = "https://bsky.app/profile/source/post/post",
                            ),
                    ),
                targets =
                    listOf(
                        SaveDraftTarget(
                            accountKey = targetAccount.accountKey,
                            status = DraftTargetStatus.DRAFT,
                        ),
                    ),
            )
            val shareMedia = media(name = "rendered.png", bytes = byteArrayOf(4, 5, 6), altText = null)
            var renderAttempts = 0

            val failedRenderUseCase =
                testUseCase(
                    findAccount = { key -> targetAccount.takeIf { it.accountKey == key } },
                    resolveReferencePost = { _, _ ->
                        ResolvedReferencePost(
                            sourcePlatform = PlatformType.Bluesky,
                            shareUrl = "https://bsky.app/profile/source/post/post",
                            renderShareMedia = {
                                renderAttempts++
                                error("render failed")
                            },
                        )
                    },
                )
            failedRenderUseCase(
                groupId = "rerender-share-image",
                referenceShareImageRenderer = unusedReferenceShareImageRenderer,
            ) {}

            val renderFailedDraft = assertNotNull(repository.draft("rerender-share-image").first())
            assertEquals(DraftTargetStatus.FAILED, renderFailedDraft.targets.single().status)
            assertNull(renderFailedDraft.content.reference?.shareImageMediaIndex)
            assertEquals(1, renderAttempts)

            val uploadFailureUseCase =
                testUseCase(
                    findAccount = { key -> targetAccount.takeIf { it.accountKey == key } },
                    prepareData = { account, data ->
                        data.forTarget(account, composeConfig(maxLength = 300, maxMediaCount = 4))
                    },
                    resolveReferencePost = { _, _ ->
                        ResolvedReferencePost(
                            sourcePlatform = PlatformType.Bluesky,
                            shareUrl = "https://bsky.app/profile/source/post/post",
                            renderShareMedia = {
                                renderAttempts++
                                shareMedia
                            },
                        )
                    },
                ) { _, _, _ ->
                    error("upload failed")
                }
            uploadFailureUseCase(
                groupId = "rerender-share-image",
                referenceShareImageRenderer = unusedReferenceShareImageRenderer,
            ) {}

            val uploadFailedDraft = assertNotNull(repository.draft("rerender-share-image").first())
            assertEquals(DraftTargetStatus.FAILED, uploadFailedDraft.targets.single().status)
            assertEquals(0, uploadFailedDraft.content.reference?.shareImageMediaIndex)
            assertEquals(1, uploadFailedDraft.medias.size)
            assertContentEquals(
                byteArrayOf(4, 5, 6),
                mediaStore
                    .restore(uploadFailedDraft.medias)
                    .single()
                    .file
                    .readBytes(),
            )
            assertEquals(2, renderAttempts)

            val resent = mutableListOf<SentCompose>()
            var resentShareBytes: ByteArray? = null
            val retryUseCase =
                testUseCase(
                    sent = resent,
                    findAccount = { key -> targetAccount.takeIf { it.accountKey == key } },
                    prepareData = { account, data ->
                        data.forTarget(account, composeConfig(maxLength = 300, maxMediaCount = 4))
                    },
                ) { _, retryData, _ ->
                    resentShareBytes =
                        retryData.medias
                            .single()
                            .file
                            .readBytes()
                }
            retryUseCase(groupId = "rerender-share-image") {}

            assertEquals(2, renderAttempts)
            assertNull(resent.single().data.referenceStatus)
            assertContentEquals(byteArrayOf(4, 5, 6), resentShareBytes)
            assertNull(repository.draft("rerender-share-image").first())
        }

    @Test
    fun federatedReferenceUsesShareImageAcrossHostsAndStaysNativeOnSameHost() =
        runTest {
            listOf(PlatformType.Mastodon, PlatformType.Misskey).forEach { platform ->
                val sourceHost = "source.example"
                val sourceAccountKey = MicroBlogKey("logged-out-source", sourceHost)
                val differentHostTarget =
                    UiAccount(
                        accountKey = MicroBlogKey("target", "target.example"),
                        platformType = platform,
                    )
                val shareMedia = media(name = "${platform.name}-share.png", bytes = byteArrayOf(7), altText = null)
                val crossGroupId = "${platform.name}-different-host"
                saveDraftGroup(
                    groupId = crossGroupId,
                    content =
                        sampleContent("cross host").copy(
                            reference =
                                DraftContent.DraftReference(
                                    type = DraftReferenceType.QUOTE,
                                    statusKey = MicroBlogKey("post", sourceHost),
                                    sourceAccountKey = sourceAccountKey,
                                    sourcePlatform = platform,
                                ),
                        ),
                    targets = listOf(SaveDraftTarget(accountKey = differentHostTarget.accountKey)),
                )
                var renderCount = 0
                val crossSent = mutableListOf<SentCompose>()
                val crossUseCase =
                    testUseCase(
                        sent = crossSent,
                        findAccount = { key -> differentHostTarget.takeIf { it.accountKey == key } },
                        prepareData = { account, data ->
                            data.forTarget(account, composeConfig(maxLength = 300, maxMediaCount = 4))
                        },
                        resolveReferencePost = { _, _ ->
                            ResolvedReferencePost(
                                sourcePlatform = platform,
                                shareUrl = null,
                                renderShareMedia = {
                                    renderCount++
                                    shareMedia
                                },
                            )
                        },
                    )
                crossUseCase(
                    groupId = crossGroupId,
                    referenceShareImageRenderer = unusedReferenceShareImageRenderer,
                ) {}

                assertEquals(1, renderCount)
                assertNull(crossSent.single().data.referenceStatus)
                assertEquals(
                    1,
                    crossSent
                        .single()
                        .data.medias.size,
                )

                val sameHostTarget =
                    UiAccount(
                        accountKey = MicroBlogKey("target", sourceHost),
                        platformType = platform,
                    )
                val nativeGroupId = "${platform.name}-same-host"
                saveDraftGroup(
                    groupId = nativeGroupId,
                    content =
                        sampleContent("same host").copy(
                            reference =
                                DraftContent.DraftReference(
                                    type = DraftReferenceType.REPLY,
                                    statusKey = MicroBlogKey("post", sourceHost),
                                    sourceAccountKey = sourceAccountKey,
                                    sourcePlatform = null,
                                ),
                        ),
                    targets = listOf(SaveDraftTarget(accountKey = sameHostTarget.accountKey)),
                )
                val nativeSent = mutableListOf<SentCompose>()
                val nativeUseCase =
                    testUseCase(
                        sent = nativeSent,
                        findAccount = { key -> sameHostTarget.takeIf { it.accountKey == key } },
                        prepareData = { account, data ->
                            data.forTarget(account, composeConfig(maxLength = 300, maxMediaCount = 4))
                        },
                    )
                nativeUseCase(groupId = nativeGroupId) {}

                assertIs<ComposeStatus.Reply>(
                    nativeSent
                        .single()
                        .data.referenceStatus
                        ?.composeStatus,
                )
                assertTrue(
                    nativeSent
                        .single()
                        .data.medias
                        .isEmpty(),
                )
            }
        }

    @Test
    fun rendererFailureKeepsCrossTargetFailedAndSendsNativeTargetOnce() =
        runTest {
            val sourceAccountKey = MicroBlogKey("source", "mastodon.social")
            val nativeAccount = mastodonAccount("native", "mastodon.social")
            val crossAccount = blueskyAccount("cross", "bsky.social")
            saveDraftGroup(
                groupId = "partial-render-failure",
                content =
                    sampleContent("partial").copy(
                        reference =
                            DraftContent.DraftReference(
                                type = DraftReferenceType.QUOTE,
                                statusKey = MicroBlogKey("post", "mastodon.social"),
                                sourceAccountKey = sourceAccountKey,
                                sourcePlatform = PlatformType.Mastodon,
                            ),
                    ),
                targets =
                    listOf(
                        SaveDraftTarget(accountKey = nativeAccount.accountKey),
                        SaveDraftTarget(accountKey = crossAccount.accountKey),
                    ),
            )
            val sent = mutableListOf<SentCompose>()
            val useCase =
                testUseCase(
                    sent = sent,
                    findAccount = { key ->
                        when (key) {
                            nativeAccount.accountKey -> nativeAccount
                            crossAccount.accountKey -> crossAccount
                            else -> null
                        }
                    },
                    prepareData = { account, data ->
                        data.forTarget(account, composeConfig(maxLength = 300, maxMediaCount = 4))
                    },
                    resolveReferencePost = { _, _ ->
                        ResolvedReferencePost(
                            sourcePlatform = PlatformType.Mastodon,
                            shareUrl = null,
                            renderShareMedia = { error("render failed") },
                        )
                    },
                )
            useCase(
                groupId = "partial-render-failure",
                referenceShareImageRenderer = unusedReferenceShareImageRenderer,
            ) {}

            assertEquals(listOf(nativeAccount.accountKey), sent.map { it.account.accountKey })
            val remainingDraft = assertNotNull(repository.draft("partial-render-failure").first())
            assertEquals(crossAccount.accountKey, remainingDraft.targets.single().accountKey)
            assertEquals(DraftTargetStatus.FAILED, remainingDraft.targets.single().status)
            assertEquals("render failed", remainingDraft.targets.single().errorMessage)
        }

    @Test
    fun concurrentDraftDispatchClaimsTargetsOnlyOnce() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            saveDraftGroup(
                groupId = "concurrent-claim",
                content = sampleContent("claim once"),
                targets = listOf(SaveDraftTarget(accountKey = account.accountKey)),
            )
            val bothResolvingAccount = CompletableDeferred<Unit>()
            var findAccountCalls = 0
            var composeCalls = 0
            val firstProgress = mutableListOf<ComposeProgressState>()
            val secondProgress = mutableListOf<ComposeProgressState>()
            val useCase =
                testUseCase(
                    findAccount = {
                        findAccountCalls++
                        if (findAccountCalls == 2) {
                            bothResolvingAccount.complete(Unit)
                        }
                        bothResolvingAccount.await()
                        account
                    },
                ) { _, _, _ ->
                    composeCalls++
                }

            listOf(
                launch { useCase(groupId = "concurrent-claim") { firstProgress += it } },
                launch { useCase(groupId = "concurrent-claim") { secondProgress += it } },
            ).joinAll()

            assertEquals(2, findAccountCalls)
            assertEquals(1, composeCalls)
            assertEquals(listOf(0, 3), listOf(firstProgress.size, secondProgress.size).sorted())
            assertNull(repository.draft("concurrent-claim").first())
        }

    private fun testUseCase(
        sent: MutableList<SentCompose> = mutableListOf(),
        findAccount: suspend (MicroBlogKey) -> UiAccount? = { null },
        prepareData: suspend (UiAccount, ComposeData) -> ComposeData = { _, data -> data },
        resolveReferencePost: suspend (MicroBlogKey, MicroBlogKey) -> ResolvedReferencePost = { _, _ ->
            error("Referenced post resolver should not be called.")
        },
        composeDraft: suspend (UiAccount, ComposeData, () -> Unit) -> Unit = { _, _, _ -> },
    ): SendDraftUseCase =
        SendDraftUseCase(
            draftRepository = repository,
            draftMediaStore = mediaStore,
            findAccount = findAccount,
            prepareData = prepareData,
            composeDraft = { account, data, progress ->
                sent += SentCompose(account = account, data = data)
                composeDraft(account, data, progress)
            },
            resolveReferencePost = resolveReferencePost,
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
        UiAccount(
            accountKey = MicroBlogKey(id, host),
            platformType = PlatformType.Mastodon,
        )

    private fun blueskyAccount(
        id: String,
        host: String,
    ): UiAccount =
        UiAccount(
            accountKey = MicroBlogKey(id, host),
            platformType = PlatformType.Bluesky,
        )

    private fun composeConfig(
        maxLength: Int,
        maxMediaCount: Int,
    ): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(maxLength),
            media =
                ComposeConfig.Media(
                    maxCount = maxMediaCount,
                    canSensitive = true,
                    altTextMaxLength = 1_000,
                    allowMediaOnly = true,
                ),
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

    private val unusedReferenceShareImageRenderer =
        object : ReferenceShareImageRenderer {
            override fun render(
                post: UiTimelineV2,
                completion: (ComposeData.Media?, String?) -> Unit,
            ) {
                error("Test resolver renders without calling the platform renderer.")
            }
        }

    private data class SentCompose(
        val account: UiAccount,
        val data: ComposeData,
    )
}
