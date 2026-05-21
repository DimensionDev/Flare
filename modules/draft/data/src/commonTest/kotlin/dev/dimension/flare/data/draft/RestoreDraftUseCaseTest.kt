package dev.dimension.flare.data.draft

import androidx.room3.Room
import dev.dimension.flare.data.account.AccountLookup
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DraftContent
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.database.app.model.DraftVisibility
import dev.dimension.flare.data.database.memoryDatabaseBuilder
import dev.dimension.flare.data.io.FakeFileStorage
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDraftMediaType
import dev.dimension.flare.ui.model.UiDraftStatus
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RestoreDraftUseCaseTest {
    private val root = "/restore-draft-use-case-test".toPath()
    private lateinit var db: AppDatabase
    private lateinit var repository: DraftRepository
    private lateinit var useCase: RestoreDraftUseCase
    private lateinit var accountLookup: FakeAccountLookup

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        repository = DraftRepository(db, DraftMediaStore(FakeFileStorage(root)))
        accountLookup = FakeAccountLookup()
        useCase = RestoreDraftUseCase(repository, accountLookup)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun restoreDraftMapsDraftDataAndResolvedAccounts() =
        runTest {
            val account = mastodonAccount("alice", "mastodon.social")
            accountLookup.accounts = mapOf(account.accountKey to account)
            repository.saveDraft(
                SaveDraftInput(
                    groupId = "restore-1",
                    content =
                        DraftContent(
                            text = "restored text",
                            visibility = DraftVisibility.Followers,
                            language = listOf("en"),
                            sensitive = true,
                            spoilerText = "cw",
                        ),
                    targets =
                        listOf(
                            SaveDraftTarget(
                                accountKey = account.accountKey,
                                status = DraftTargetStatus.FAILED,
                            ),
                        ),
                    medias =
                        listOf(
                            SaveDraftMedia(
                                cachePath = "/drafts/restore-1/a.png",
                                fileName = "a.png",
                                mediaType = DraftMediaType.IMAGE,
                                altText = "cover",
                            ),
                        ),
                ),
            )

            val draft = assertNotNull(useCase("restore-1"))

            assertEquals("restore-1", draft.groupId)
            assertEquals(UiDraftStatus.FAILED, draft.status)
            assertEquals(listOf(account), draft.accounts.map { it.account })
            assertEquals("restored text", draft.data.content)
            assertEquals(UiTimelineV2.Post.Visibility.Followers, draft.data.visibility)
            assertEquals(listOf("en"), draft.data.language)
            assertEquals(true, draft.data.sensitive)
            assertEquals("cw", draft.data.spoilerText)
            assertEquals(1, draft.medias.size)
            assertEquals("a.png", draft.medias.single().fileName)
            assertEquals(UiDraftMediaType.IMAGE, draft.medias.single().type)
            assertEquals("cover", draft.medias.single().altText)
        }

    @Test
    fun restoreDraftReturnsNullWhenMissing() =
        runTest {
            assertNull(useCase("missing"))
        }

    private class FakeAccountLookup : AccountLookup {
        var accounts: Map<MicroBlogKey, UiAccount> = emptyMap()

        override suspend fun find(accountKey: MicroBlogKey): UiAccount? = accounts[accountKey]
    }

    private fun mastodonAccount(
        id: String,
        host: String,
    ): UiAccount =
        UiAccount.Mastodon(
            accountKey = MicroBlogKey(id, host),
            instance = host,
        )
}
