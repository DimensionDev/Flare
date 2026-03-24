package dev.dimension.flare.data.network.nostr

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.nostr.DatabaseNostrCache
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NostrServiceJvmIntegrationTest : RobolectricTest() {
    private lateinit var database: CacheDatabase
    private lateinit var cache: DatabaseNostrCache

    @BeforeTest
    fun setUp() {
        database =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        cache = DatabaseNostrCache(database)
        stopKoin()
        startKoin {
            modules(
                module {
                    single { database }
                    single<NostrCache> { cache }
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        database.close()
        stopKoin()
    }

    @Test
    fun generatedAccountCanLoadProfileForKnownNpub() =
        runBlocking {
            val viewerAccount = createViewerAccount()
            val credential =
                UiAccount.Nostr.Credential(
                    pubkey = viewerAccount.pubkeyHex,
                    nsec = viewerAccount.nsec,
                    relays = viewerAccount.relays,
                )

            val profile =
                NostrService.loadProfile(
                    credential = credential,
                    accountKey = TEST_ACCOUNT_KEY,
                    targetPubkey = TARGET_PUBKEY.pubkeyHex,
                )

            assertNotEquals(TARGET_PUBKEY.pubkeyHex, viewerAccount.pubkeyHex)
            assertNotNull(viewerAccount.nsec)
            assertEquals(MicroBlogKey(TARGET_PUBKEY.pubkeyHex, NostrService.NOSTR_HOST), profile.key)
            assertEquals(PlatformType.Nostr, profile.platformType)
            assertEquals(NostrService.NOSTR_HOST, profile.handle.host)
            assertTrue(
                profile.avatar.isNotBlank() ||
                    profile.banner != null ||
                    profile.description != null ||
                    profile.name.raw != TARGET_NPUB.take(16),
                "Expected profile metadata to be populated for $TARGET_NPUB, " +
                    "actual avatar=${profile.avatar}, banner=${profile.banner}, " +
                    "description=${profile.description?.raw}, name=${profile.name.raw}, handle=${profile.handle.raw}",
            )
        }

    @Test
    fun generatedAccountCanLoadTimelineForKnownNpub() =
        runBlocking {
            val viewerAccount = createViewerAccount()
            val credential =
                UiAccount.Nostr.Credential(
                    pubkey = viewerAccount.pubkeyHex,
                    nsec = viewerAccount.nsec,
                    relays = viewerAccount.relays,
                )

            val timeline =
                loadTimelineWithRetry(
                    credential = credential,
                    targetPubkey = TARGET_PUBKEY.pubkeyHex,
                )
            assertTrue(timeline.isNotEmpty(), "Expected timeline entries for $TARGET_NPUB")
            val posts = timeline.filterIsInstance<UiTimelineV2.Post>()
            assertTrue(posts.isNotEmpty(), "Expected mapped posts for $TARGET_NPUB")
            assertTrue(posts.all { it.platformType == PlatformType.Nostr })
            assertTrue(posts.any { it.content.raw.isNotBlank() })
            assertTrue(
                posts.any { it.user?.key == MicroBlogKey(TARGET_PUBKEY.pubkeyHex, NostrService.NOSTR_HOST) },
                "Expected at least one post authored by $TARGET_NPUB",
            )
        }

    private fun createViewerAccount(): NostrService.ImportedAccount =
        NostrService.importAccount(
            publicKeyInput = "",
            secretKeyInput = generateSecretKeyHex(),
            relayInput = "",
        )

    private suspend fun loadTimelineWithRetry(
        credential: UiAccount.Nostr.Credential,
        targetPubkey: String,
    ): List<UiTimelineV2> {
        repeat(TIMELINE_RETRY_COUNT) { attempt ->
            val timeline =
                NostrService.loadUserTimeline(
                    credential = credential,
                    accountKey = TEST_ACCOUNT_KEY,
                    targetPubkey = targetPubkey,
                    pageSize = 20,
                    until = null,
                    mediaOnly = false,
                )
            if (timeline.isNotEmpty()) {
                return timeline
            }
            if (attempt < TIMELINE_RETRY_COUNT - 1) {
                delay(TIMELINE_RETRY_DELAY_MILLIS)
            }
        }
        return emptyList()
    }

    private companion object {
        const val TARGET_NPUB = "npub1plstrz6dhu8q4fq0e4rjpxe2fxe5x87y2w6xpm70gh9qh5tt66kqkgkx8j"
        const val TIMELINE_RETRY_COUNT = 3
        const val TIMELINE_RETRY_DELAY_MILLIS = 1_000L
        val TEST_ACCOUNT_KEY = MicroBlogKey("nostr-integration", NostrService.NOSTR_HOST)
        val TARGET_PUBKEY = NostrService.importAccount(TARGET_NPUB, "", "")
        val SECURE_RANDOM = SecureRandom()

        fun generateSecretKeyHex(): String =
            ByteArray(32)
                .also(SECURE_RANDOM::nextBytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
