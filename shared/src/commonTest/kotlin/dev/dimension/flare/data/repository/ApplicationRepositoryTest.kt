package dev.dimension.flare.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ApplicationRepositoryTest {

    private lateinit var appDatabase: AppDatabase
    private val testDispatcher = StandardTestDispatcher()

    val host = "test-mstdn-host.com"
    val application = UiApplication.Mastodon(
        host = host,
        application = CreateApplicationResponse(
            clientID = "test-client-id",
            clientSecret = "test-client-secret",
            redirectURI = "test-redirect-mstdn-uri",
        )
    )

    @BeforeTest
    fun setUp() {
        appDatabase = Room
            .inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .build()
    }

    @AfterTest
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun `should fun host by host when findByHost`() = runTest(testDispatcher) {

        val repo = createRepository()

        repo.addApplication(
            host = application.host,
            credentialJson = application.application.encodeJson(),
            platformType = PlatformType.Mastodon
        )

        val resultApplication = repo.findByHost(host)
        assertEquals(host, resultApplication?.host)
    }

    @Test
    fun `should have correct values for pendingOAuth for get and set and clear the same`() =
        runTest(testDispatcher) {

            val repo = createRepository()

            repo.addApplication(
                host = application.host,
                credentialJson = application.application.encodeJson(),
                platformType = PlatformType.Mastodon
            )

            repo.setPendingOAuth(
                host = host,
                pendingOAuth = true
            )

            val resultApplication = repo.getPendingOAuth()
            assertEquals(host, resultApplication?.host)

            repo.clearPendingOAuth()
            val resultApplicationAfterClear = repo.getPendingOAuth()
            assertEquals(null, resultApplicationAfterClear)

        }





    private fun createRepository(): ApplicationRepository {
        return ApplicationRepository(appDatabase)
    }
}