package dev.dimension.flare.ui.presenter.compose

import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.createTestFileItem
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftReferenceType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.UnavailableMicroblogDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.FileStorage
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
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
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
    fun unspecifiedAudienceUsesEachPlatformDefaultAfterSaveAndRetry() =
        runTest {
            val first = mastodonAccount("a", "first.example")
            val second = mastodonAccount("b", "second.example")
            val accounts = listOf(first, second)
            val defaults =
                mapOf(
                    first.accountKey to UiTimelineV2.Post.Visibility.SelfOnly,
                    second.accountKey to UiTimelineV2.Post.Visibility.Followers,
                )
            val published = mutableListOf<Pair<MicroBlogKey, UiTimelineV2.Post.Visibility?>>()
            var failSecond = true
            val useCase =
                testUseCase(findAccount = { key -> accounts.find { it.accountKey == key } }) { account, data, progress ->
                    assertNull(data.visibility)
                    if (account == second && failSecond) {
                        failSecond = false
                        error("retry this account")
                    }
                    val source =
                        object :
                            ComposeDataSource,
                            MicroblogDataSource by UnavailableMicroblogDataSource {
                            override val accountKey = account.accountKey

                            override fun composeConfig(type: ComposeType) =
                                ComposeConfig(
                                    visibility =
                                        ComposeConfig.Visibility(
                                            allVisibilities =
                                                persistentListOf(
                                                    UiTimelineV2.Post.Visibility.Public,
                                                    defaults.getValue(accountKey),
                                                ),
                                            defaultVisibility = defaults.getValue(accountKey),
                                        ),
                                )

                            override suspend fun publish(
                                data: ComposeData,
                                progress: () -> Unit,
                            ) {
                                published += accountKey to data.visibility
                            }
                        }
                    source.compose(data, progress)
                }
            useCase(ComposeDraftBundle(groupId = "defaults", accounts = accounts, template = ComposeData("post"))) {}
            assertNull(assertNotNull(repository.draft("defaults").first()).content.visibility)
            useCase("defaults") {}
            assertEquals(
                listOf<UiTimelineV2.Post.Visibility?>(
                    UiTimelineV2.Post.Visibility.SelfOnly,
                    UiTimelineV2.Post.Visibility.Followers,
                ),
                published.map {
                    it.second
                },
            )
            assertNull(repository.draft("defaults").first())
        }

    @Test
    fun cancelledSendKeepsMediaAndMakesUnsentDestinationsRetryable() =
        runTest {
            val accounts = listOf(mastodonAccount("a", "example.com"), mastodonAccount("b", "example.com"))
            val entered = CompletableDeferred<Unit>()
            val useCase =
                testUseCase { _, _, _ ->
                    entered.complete(Unit)
                    awaitCancellation()
                }
            val job =
                launch {
                    useCase(
                        ComposeDraftBundle(
                            accounts = accounts,
                            groupId = "cancelled",
                            template =
                                ComposeData(
                                    content = "video",
                                    medias = listOf(media("clip.mp4", byteArrayOf(1, 2, 3), altText = null)),
                                ),
                        ),
                    ) {}
                }
            entered.await()
            job.cancelAndJoin()
            val draft = assertNotNull(repository.draft("cancelled").first())
            assertTrue(draft.targets.all { it.status == DraftTargetStatus.FAILED })
            assertContentEquals(
                byteArrayOf(1, 2, 3),
                mediaStore
                    .restore(draft.medias)
                    .single()
                    .file
                    .readBytes(),
            )
            var retries = 0
            val retry =
                testUseCase(findAccount = { key -> accounts.find { it.accountKey == key } }) { _, data, _ ->
                    assertContentEquals(
                        byteArrayOf(1, 2, 3),
                        data.medias
                            .single()
                            .file
                            .readBytes(),
                    )
                    retries++
                }
            retry("cancelled") {}
            assertEquals(2, retries)
            assertNull(repository.draft("cancelled").first())
        }

    @Test
    fun cancellationInPreparedCallbackMakesDraftRetryable() =
        runTest {
            assertSetupCancellationIsRetryable(
                onPrepared = {
                    currentCoroutineContext().cancel()
                    awaitCancellation()
                },
            )
        }

    @Test
    fun cancellationInInitialProgressMakesDraftRetryable() =
        runTest {
            assertSetupCancellationIsRetryable(
                progress = {
                    currentCoroutineContext().cancel()
                    awaitCancellation()
                },
            )
        }

    @Test
    fun cancellationAtInitialStatusUpdateMakesDraftRetryable() =
        runTest {
            assertSetupCancellationIsRetryable(
                // Return normally so cancellation is observed by the following database update.
                progress = { currentCoroutineContext().cancel() },
            )
        }

    @Test
    fun cancellationExceptionFromPreparedCallbackMakesDraftRetryable() =
        runTest {
            assertSetupCancellationIsRetryable(
                onPrepared = { throw CancellationException("Preparation cancelled") },
            )
        }

    @Test
    fun cancellationInFailureProgressMakesRemainingTargetsRetryable() =
        runTest {
            val accounts = listOf(mastodonAccount("a", "example.com"), mastodonAccount("b", "example.com"))
            val useCase =
                testUseCase { _, _, progress ->
                    progress()
                    error("Upload failed")
                }
            val job =
                launch {
                    useCase(
                        ComposeDraftBundle(
                            accounts = accounts,
                            groupId = "cancelled-failure-progress",
                            template =
                                ComposeData(
                                    content = "video",
                                    medias = listOf(media("clip.mp4", byteArrayOf(1, 2, 3), altText = null)),
                                ),
                        ),
                    ) { state ->
                        if (state is ComposeProgressState.Progress && state.current == 1) {
                            currentCoroutineContext().cancel()
                            awaitCancellation()
                        }
                    }
                }
            job.join()
            assertTrue(job.isCancelled)
            val draft = assertNotNull(repository.draft("cancelled-failure-progress").first())
            assertEquals(List(2) { DraftTargetStatus.FAILED }, draft.targets.map { it.status })
            val sent = mutableListOf<SentCompose>()
            val retry = testUseCase(sent = sent, findAccount = { key -> accounts.find { it.accountKey == key } })
            retry("cancelled-failure-progress") {}
            assertEquals(accounts.map { it.accountKey }, sent.map { it.account.accountKey })
            assertNull(repository.draft("cancelled-failure-progress").first())
        }

    @Test
    fun cancellationInMediaProgressAfterPublishDoesNotRetryPublishedTarget() =
        runTest {
            assertCancellationAfterPublishOnlyRetriesUnsentTargets(cancelAtProgress = 1)
        }

    @Test
    fun cancellationInSuccessProgressAfterPublishDoesNotRetryPublishedTarget() =
        runTest {
            assertCancellationAfterPublishOnlyRetriesUnsentTargets(cancelAtProgress = 2)
        }

    @Test
    fun cancellationAsComposeReturnsStillRetiresPublishedTarget() =
        runTest {
            val accounts = listOf(mastodonAccount("a", "example.com"), mastodonAccount("b", "example.com"))
            val published = mutableListOf<MicroBlogKey>()
            val useCase =
                testUseCase { account, _, _ ->
                    published += account.accountKey
                    currentCoroutineContext().cancel()
                }
            val job =
                launch {
                    useCase(
                        ComposeDraftBundle(
                            accounts = accounts,
                            groupId = "cancelled-compose-return",
                            template = ComposeData(content = "post"),
                        ),
                    ) {}
                }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(listOf(accounts.first().accountKey), published)
            val draft = assertNotNull(repository.draft("cancelled-compose-return").first())
            assertEquals(listOf(accounts.last().accountKey), draft.targets.map { it.accountKey })
            assertEquals(DraftTargetStatus.FAILED, draft.targets.single().status)
            val retry =
                testUseCase(findAccount = { key -> accounts.find { it.accountKey == key } }) { account, _, _ ->
                    published += account.accountKey
                }
            retry("cancelled-compose-return") {}
            assertEquals(accounts.map { it.accountKey }, published)
            assertNull(repository.draft("cancelled-compose-return").first())
        }

    @Test
    fun cancellationKeepsCompletedTargetsDeletedAndPreservesEarlierFailures() =
        runTest {
            val accounts = listOf("sent", "failed", "cancelled", "queued").map { mastodonAccount(it, "example.com") }
            val entered = CompletableDeferred<Unit>()
            val useCase =
                testUseCase { account, _, _ ->
                    when (account.accountKey.id) {
                        "failed" -> {
                            error("Server rejected the post")
                        }

                        "cancelled" -> {
                            entered.complete(Unit)
                            awaitCancellation()
                        }
                    }
                }
            val job =
                launch {
                    useCase(
                        ComposeDraftBundle(
                            accounts = accounts,
                            groupId = "cancelled-after-partial-send",
                            template = ComposeData(content = "partial send"),
                        ),
                    ) {}
                }
            entered.await()
            job.cancelAndJoin()
            val draft = assertNotNull(repository.draft("cancelled-after-partial-send").first())
            assertEquals(accounts.drop(1).map { it.accountKey }.toSet(), draft.targets.map { it.accountKey }.toSet())
            assertEquals(List(3) { DraftTargetStatus.FAILED }, draft.targets.map { it.status })
            assertEquals("Server rejected the post", draft.targets.single { it.accountKey.id == "failed" }.errorMessage)
            val sent = mutableListOf<SentCompose>()
            val retry = testUseCase(sent = sent, findAccount = { key -> accounts.find { it.accountKey == key } })
            retry("cancelled-after-partial-send") {}
            assertEquals(3, sent.size)
            assertEquals(accounts.drop(1).map { it.accountKey }.toSet(), sent.map { it.account.accountKey }.toSet())
            assertNull(repository.draft("cancelled-after-partial-send").first())
        }

    @Test
    fun cancellationDuringRetryDoesNotChangeAlreadySendingTargets() =
        runTest {
            val accounts = listOf("sending", "draft", "failed").map { mastodonAccount(it, "example.com") }
            saveDraftGroup(
                groupId = "cancelled-retry",
                content = sampleContent("retry"),
                targets =
                    listOf(
                        SaveDraftTarget(accounts[0].accountKey, DraftTargetStatus.SENDING),
                        SaveDraftTarget(accounts[1].accountKey, DraftTargetStatus.DRAFT),
                        SaveDraftTarget(accounts[2].accountKey, DraftTargetStatus.FAILED),
                    ),
            )
            val sent = mutableListOf<SentCompose>()
            val useCase = testUseCase(sent = sent, findAccount = { key -> accounts.find { it.accountKey == key } })
            val job =
                launch {
                    useCase("cancelled-retry") {
                        currentCoroutineContext().cancel()
                        awaitCancellation()
                    }
                }
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(sent.isEmpty())
            val draft = assertNotNull(repository.draft("cancelled-retry").first())
            assertEquals(
                mapOf(
                    accounts[0].accountKey to DraftTargetStatus.SENDING,
                    accounts[1].accountKey to DraftTargetStatus.FAILED,
                    accounts[2].accountKey to DraftTargetStatus.FAILED,
                ),
                draft.targets.associate { it.accountKey to it.status },
            )
            useCase("cancelled-retry") {}
            assertEquals(accounts.drop(1).map { it.accountKey }, sent.map { it.account.accountKey })
            assertEquals(
                accounts[0].accountKey,
                repository
                    .draft("cancelled-retry")
                    .first()
                    ?.targets
                    ?.single()
                    ?.accountKey,
            )
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
            var prepared = false
            val medias =
                listOf(
                    media(name = "a.png", bytes = byteArrayOf(1), altText = "a"),
                    media(name = "b.mov", bytes = byteArrayOf(2), type = FileType.Video, altText = "b"),
                )
            val useCase =
                testUseCase(sent = sent) { _, data, _ ->
                    assertTrue(prepared)
                    data.medias.zip(medias).forEach { (sentMedia, sourceMedia) ->
                        assertNotSame(sourceMedia.file, sentMedia.file)
                    }
                    assertContentEquals(byteArrayOf(1), data.medias[0].file.readBytes())
                    assertContentEquals(byteArrayOf(2), data.medias[1].file.readBytes())
                }

            useCase(
                bundle =
                    ComposeDraftBundle(
                        accounts = listOf(account),
                        groupId = "send-multi-media",
                        template =
                            ComposeData(
                                content = "multi media",
                                medias = medias,
                            ),
                    ),
                onPrepared = { prepared = true },
            ) {}
            advanceUntilIdle()

            val sentMedias = sent.single().data.medias
            assertEquals(2, sentMedias.size)
            assertEquals(
                listOf("a", "b"),
                sentMedias.map { it.altText },
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

    private suspend fun TestScope.assertCancellationAfterPublishOnlyRetriesUnsentTargets(cancelAtProgress: Int) {
        val accounts = listOf(mastodonAccount("a", "example.com"), mastodonAccount("b", "example.com"))
        val published = mutableListOf<MicroBlogKey>()
        val bytes = byteArrayOf(1, 2, 3)
        val useCase =
            testUseCase { account, _, progress ->
                progress()
                published += account.accountKey
            }
        val job =
            launch {
                useCase(
                    ComposeDraftBundle(
                        accounts = accounts,
                        groupId = "cancelled-after-publish",
                        template =
                            ComposeData(
                                content = "video",
                                medias = listOf(media("clip.mp4", bytes, type = FileType.Video, altText = null)),
                            ),
                    ),
                ) { state ->
                    if (state is ComposeProgressState.Progress && state.current == cancelAtProgress) {
                        currentCoroutineContext().cancel()
                        awaitCancellation()
                    }
                }
            }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf(accounts.first().accountKey), published)
        val draft = assertNotNull(repository.draft("cancelled-after-publish").first())
        assertEquals(listOf(accounts.last().accountKey), draft.targets.map { it.accountKey })
        assertEquals(DraftTargetStatus.FAILED, draft.targets.single().status)
        assertContentEquals(
            bytes,
            mediaStore
                .restore(draft.medias)
                .single()
                .file
                .readBytes(),
        )
        val retry =
            testUseCase(findAccount = { key -> accounts.find { it.accountKey == key } }) { account, data, _ ->
                assertContentEquals(
                    bytes,
                    data.medias
                        .single()
                        .file
                        .readBytes(),
                )
                published += account.accountKey
            }
        retry("cancelled-after-publish") {}
        assertEquals(accounts.map { it.accountKey }, published)
        assertNull(repository.draft("cancelled-after-publish").first())
    }

    private suspend fun TestScope.assertSetupCancellationIsRetryable(
        onPrepared: suspend () -> Unit = {},
        progress: suspend (ComposeProgressState) -> Unit = {},
    ) {
        val accounts = listOf(mastodonAccount("a", "example.com"), mastodonAccount("b", "example.com"))
        val sent = mutableListOf<SentCompose>()
        val useCase = testUseCase(sent = sent)
        val job =
            launch {
                useCase(
                    bundle =
                        ComposeDraftBundle(
                            accounts = accounts,
                            groupId = "cancelled-setup",
                            template =
                                ComposeData(
                                    content = "video",
                                    medias = listOf(media("clip.mp4", byteArrayOf(1, 2, 3), altText = null)),
                                ),
                        ),
                    onPrepared = onPrepared,
                    progress = progress,
                )
            }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(sent.isEmpty())
        val draft = assertNotNull(repository.draft("cancelled-setup").first())
        assertEquals(accounts.map { it.accountKey }.toSet(), draft.targets.map { it.accountKey }.toSet())
        assertEquals(List(accounts.size) { DraftTargetStatus.FAILED }, draft.targets.map { it.status })
        assertContentEquals(
            byteArrayOf(1, 2, 3),
            mediaStore
                .restore(draft.medias)
                .single()
                .file
                .readBytes(),
        )
        val retry = testUseCase(sent = sent, findAccount = { key -> accounts.find { it.accountKey == key } })
        retry("cancelled-setup") {}
        assertEquals(accounts.map { it.accountKey }, sent.map { it.account.accountKey })
        assertNull(repository.draft("cancelled-setup").first())
    }

    @Test
    fun nativePreparationFailureReportsErrorAndReleasesSendAndSaveCallbacks() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val failingStore =
                    DraftMediaStore(
                        object : FileStorage by fileStorage {
                            override suspend fun write(
                                path: Path,
                                media: UploadMedia,
                            ) {
                                error("Cannot copy imported file")
                            }
                        },
                    )
                var prepared = 0
                var failed = 0
                var notified = CompletableDeferred<Unit>()
                val notification =
                    object : InAppNotification {
                        override fun onProgress(
                            message: Message,
                            progress: Int,
                            total: Int,
                        ) = Unit

                        override fun onSuccess(message: Message) = Unit

                        override fun onError(
                            message: Message,
                            throwable: Throwable,
                        ) {
                            notified.complete(Unit)
                        }
                    }
                val useCase =
                    ComposeUseCase(
                        scope = this,
                        inAppNotification = notification,
                        appDataStore = AppDataStore(fileStorage),
                        saveDraftUseCase = SaveDraftUseCase(repository, failingStore),
                        sendDraftUseCase =
                            SendDraftUseCase(repository, failingStore, findAccount = { null }, composeDraft = {
                                _,
                                _,
                                _,
                                ->
                                error("Must not upload")
                            }),
                    )
                val accounts = listOf(mastodonAccount("alice", "example.com"))
                val data = ComposeData(content = "image", medias = listOf(media("a.gif", byteArrayOf(1), altText = null)))
                useCase(accounts, data, "failed-send", onPrepared = { prepared++ }, onPreparationFailed = { failed++ })
                notified.await()
                assertEquals(0, prepared)
                assertEquals(1, failed)
                notified = CompletableDeferred()
                useCase.saveDraft(accounts, data, "failed-save", onSaved = { prepared++ }, onFailure = { failed++ })
                notified.await()
                assertEquals(0, prepared)
                assertEquals(2, failed)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun nativeAccountLookupCancellationCompletesDispatchBeforeAndAfterStart() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                for (startLookup in listOf(false, true)) {
                    val composerScope = CoroutineScope(coroutineContext + Job())
                    val results = mutableListOf<Boolean>()
                    composerScope.launchComposeWithAccounts(
                        accounts = flow { awaitCancellation() },
                        onDispatched = { results += it },
                        dispatch = { error("Cancelled lookup must not dispatch") },
                    )
                    if (startLookup) runCurrent()
                    composerScope.cancel()
                    advanceUntilIdle()
                    assertEquals(listOf(false), results)
                }
                val closedScope = CoroutineScope(coroutineContext + Job()).apply { cancel() }
                val results = mutableListOf<Boolean>()
                closedScope.launchComposeWithAccounts(
                    accounts = flowOf(listOf(mastodonAccount("alice", "example.com"))),
                    onDispatched = { results += it },
                    dispatch = { error("A closed composer must not dispatch synchronous accounts") },
                )
                advanceUntilIdle()
                assertEquals(listOf(false), results)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun nativeAccountLookupKeepsBackgroundDispatchAliveAfterComposerCloses() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                for (accounts in listOf(emptyList(), listOf(mastodonAccount("alice", "example.com")))) {
                    val composerScope = CoroutineScope(coroutineContext + Job())
                    val results = mutableListOf<Boolean>()
                    var dispatches = 0
                    composerScope.launchComposeWithAccounts(
                        accounts = flowOf(accounts),
                        onDispatched = { results += it },
                        dispatch = { dispatches++ },
                    )
                    advanceUntilIdle()
                    composerScope.cancel()
                    advanceUntilIdle()
                    assertEquals(if (accounts.isEmpty()) 0 else 1, dispatches)
                    assertEquals(if (accounts.isEmpty()) listOf(false) else emptyList(), results)
                }
            } finally {
                Dispatchers.resetMain()
            }
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

    private suspend fun mastodonAccount(
        id: String,
        host: String,
    ): UiAccount =
        UiAccount(
            accountKey = MicroBlogKey(id, host),
            platformId = "Mastodon",
        ).also { db.accountDao().insert(DbAccount(it.accountKey, "{}", it.platformId, 0)) }

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
