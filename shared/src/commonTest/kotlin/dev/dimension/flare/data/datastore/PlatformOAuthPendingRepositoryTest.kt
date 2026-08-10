package dev.dimension.flare.data.datastore

import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.deleteTestRootPath
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformOAuthPendingRepositoryTest {
    private val root = createTestRootPath()
    private val fileStorage =
        OkioFileStorage(
            fileSystem = createTestFileSystem(),
            root = root,
        )
    private val repository =
        PlatformOAuthPendingRepository(
            AppDataStore(fileStorage),
        )

    @AfterTest
    fun tearDown() {
        deleteTestRootPath(root)
    }

    @Test
    fun saveReplacesPendingForSamePlatformHostAndFlow() =
        runTest {
            repository.save(
                PlatformOAuthPending(
                    platformId = "Mastodon",
                    host = "mastodon.social",
                    createdAtEpochMillis = 100,
                    attributes = mapOf("client_id" to "old"),
                ),
            )
            repository.save(
                PlatformOAuthPending(
                    platformId = "Mastodon",
                    host = "mastodon.social",
                    createdAtEpochMillis = 200,
                    attributes = mapOf("client_id" to "new"),
                ),
            )

            val pending =
                repository.get(
                    platformId = "Mastodon",
                    host = "mastodon.social",
                )

            assertEquals("new", pending?.attributes?.get("client_id"))
            assertEquals(200, pending?.createdAtEpochMillis)
        }

    @Test
    fun latestReturnsNewestPendingForPlatform() =
        runTest {
            repository.save(
                PlatformOAuthPending(
                    platformId = "Mastodon",
                    host = "old.example",
                    createdAtEpochMillis = 100,
                ),
            )
            repository.save(
                PlatformOAuthPending(
                    platformId = "Mastodon",
                    host = "new.example",
                    createdAtEpochMillis = 200,
                ),
            )
            repository.save(
                PlatformOAuthPending(
                    platformId = "Misskey",
                    host = "misskey.example",
                    createdAtEpochMillis = 300,
                ),
            )

            val pending = repository.latest("Mastodon")

            assertEquals("new.example", pending?.host)
        }

    @Test
    fun clearRemovesOnlyMatchingPending() =
        runTest {
            repository.save(
                PlatformOAuthPending(
                    platformId = "Mastodon",
                    host = "mastodon.social",
                    createdAtEpochMillis = 100,
                ),
            )
            repository.save(
                PlatformOAuthPending(
                    platformId = "Misskey",
                    host = "misskey.io",
                    createdAtEpochMillis = 200,
                ),
            )

            repository.clear(
                platformId = "Mastodon",
                host = "mastodon.social",
            )

            assertNull(
                repository.get(
                    platformId = "Mastodon",
                    host = "mastodon.social",
                ),
            )
            assertEquals("misskey.io", repository.latest("Misskey")?.host)
        }

    @Test
    fun legacyPendingFileIsIgnoredAndV2StoreRemainsUsable() =
        runTest {
            val legacyFile = fileStorage.dataStoreFile("platform_oauth_pending.pb")
            fileStorage.write(legacyFile, byteArrayOf(0x01, 0x02, 0x03))

            assertNull(repository.latest("Mastodon"))

            repository.save(
                PlatformOAuthPending(
                    platformId = "Mastodon",
                    host = "mastodon.social",
                    createdAtEpochMillis = 100,
                ),
            )
            assertEquals("mastodon.social", repository.latest("Mastodon")?.host)
            assertEquals(true, fileStorage.exists(legacyFile))
            assertEquals(true, fileStorage.exists(fileStorage.dataStoreFile("platform_oauth_pending_v2.pb")))
        }
}
