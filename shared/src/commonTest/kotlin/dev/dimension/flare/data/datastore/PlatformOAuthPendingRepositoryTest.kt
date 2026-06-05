package dev.dimension.flare.data.datastore

import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.model.PlatformType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformOAuthPendingRepositoryTest {
    private val root = createTestRootPath()
    private val repository =
        PlatformOAuthPendingRepository(
            AppDataStore(
                OkioFileStorage(
                    fileSystem = createTestFileSystem(),
                    root = root,
                ),
            ),
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
                    platformType = PlatformType.Mastodon,
                    host = "mastodon.social",
                    createdAtEpochMillis = 100,
                    attributes = mapOf("client_id" to "old"),
                ),
            )
            repository.save(
                PlatformOAuthPending(
                    platformType = PlatformType.Mastodon,
                    host = "mastodon.social",
                    createdAtEpochMillis = 200,
                    attributes = mapOf("client_id" to "new"),
                ),
            )

            val pending =
                repository.get(
                    platformType = PlatformType.Mastodon,
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
                    platformType = PlatformType.Mastodon,
                    host = "old.example",
                    createdAtEpochMillis = 100,
                ),
            )
            repository.save(
                PlatformOAuthPending(
                    platformType = PlatformType.Mastodon,
                    host = "new.example",
                    createdAtEpochMillis = 200,
                ),
            )
            repository.save(
                PlatformOAuthPending(
                    platformType = PlatformType.Misskey,
                    host = "misskey.example",
                    createdAtEpochMillis = 300,
                ),
            )

            val pending = repository.latest(PlatformType.Mastodon)

            assertEquals("new.example", pending?.host)
        }

    @Test
    fun clearRemovesOnlyMatchingPending() =
        runTest {
            repository.save(
                PlatformOAuthPending(
                    platformType = PlatformType.Mastodon,
                    host = "mastodon.social",
                    createdAtEpochMillis = 100,
                ),
            )
            repository.save(
                PlatformOAuthPending(
                    platformType = PlatformType.Misskey,
                    host = "misskey.io",
                    createdAtEpochMillis = 200,
                ),
            )

            repository.clear(
                platformType = PlatformType.Mastodon,
                host = "mastodon.social",
            )

            assertNull(
                repository.get(
                    platformType = PlatformType.Mastodon,
                    host = "mastodon.social",
                ),
            )
            assertEquals("misskey.io", repository.latest(PlatformType.Misskey)?.host)
        }
}
