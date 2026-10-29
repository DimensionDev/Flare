package dev.dimension.flare.feature.plugin.management

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginClient
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningTypeV1
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
import dev.dimension.flare.ui.model.PluginMediaPermissions
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.withProxy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginGitHubManagementTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var client: GitHubPluginClient
    private var repositoryId = 42L
    private var tags = listOf("v1.0.0" to FIRST_SHA)
    private val archives = mutableMapOf<String, ByteArray>()
    private var downloads = 0
    private var blocked = false
    private val downloadStarted = CompletableDeferred<Unit>()
    private var catalogStarted: CompletableDeferred<Unit>? = null
    private var catalogRelease: CompletableDeferred<Unit>? = null
    private var catalogStatus: HttpStatusCode? = null

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("plugin-github-management").toOkioPath()
        archives[FIRST_SHA] = archive("1.0.0")
        client =
            GitHubPluginClient(
                MockEngine { request ->
                    when {
                        request.url.encodedPath.endsWith("/tags") -> {
                            respond(tags.joinToString(",", "[", "]") { (tag, sha) -> """{"name":"$tag","commit":{"sha":"$sha"}}""" })
                        }

                        "/zipball/" in request.url.encodedPath -> {
                            downloads++
                            if (blocked) {
                                downloadStarted.complete(Unit)
                                awaitCancellation()
                            }
                            val bytes = archives[request.url.encodedPath.substringAfterLast('/')]
                            if (bytes == null) respond("", HttpStatusCode.NotFound) else respond(bytes)
                        }

                        else -> {
                            catalogStarted?.complete(Unit)
                            catalogRelease?.await()
                            catalogStatus?.let { return@MockEngine respond("{}", it) }
                            respond("""{"id":$repositoryId,"name":"repo","owner":{"login":"owner"},"private":false}""")
                        }
                    }
                },
            )
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun githubCommitRechecksOwnershipAfterInspection() =
        runBlocking {
            withPresenter { subsystem, presenter ->
                val key = UUID.randomUUID().toString()
                val plugin = subsystem.authoring.create(key)
                subsystem.authoring.delete(plugin.id)
                archives[FIRST_SHA] = archive("1.0.0", id = plugin.id)
                presenter.loadGitHubRepository(REPOSITORY_URL)
                presenter.inspectGitHub("v1.0.0")
                val review = assertNotNull(presenter.state.value.pendingInstall)
                val recreated = subsystem.authoring.create(key)

                assertFailsWith<IllegalStateException> { presenter.confirmInstall() }
                assertTrue(
                    subsystem.stateStore.desired.value.plugins
                        .isEmpty(),
                )
                assertEquals(recreated, subsystem.authoring.read(plugin.id).plugin)
                assertEquals(review, presenter.state.value.pendingInstall)
                assertNotNull(presenter.state.value.error)
                assertFalse(presenter.state.value.busy)
                presenter.cancelInstall()
                assertNull(presenter.state.value.pendingInstall)

                archives[FIRST_SHA] = archive("1.0.0")
                install(presenter)
                assertEquals(
                    PLUGIN_ID,
                    presenter.state.value.items
                        .filter { it.authoring == null }
                        .single()
                        .pluginId,
                )
                assertNull(presenter.state.value.error)
            }
        }

    @Test
    fun cachedMediaGrantIsRevokedOnlyAfterTheConfirmedUpdateRestarts() =
        runBlocking {
            archives[FIRST_SHA] = archive("1.0.0", mediaNetwork = true)
            val cache = createSampleUser().copy(pluginId = PLUGIN_ID).encodeJson()
            withPresenter { _, presenter -> install(presenter) }
            archives[SECOND_SHA] = archive("1.1.0")
            tags = listOf("v1.0.0" to FIRST_SHA, "v1.1.0" to SECOND_SHA)
            withPresenter { _, presenter ->
                assertTrue(PluginMediaPermissions.isAllowed(PLUGIN_ID))
                assertNotNull(cache.decodeJson<UiProfile>().withProxy(ProxyRef.Global).avatar)
                presenter.checkUpdates()
                presenter.inspectGitHubUpdate(PLUGIN_ID)
                presenter.confirmInstall()
                assertTrue(PluginMediaPermissions.isAllowed(PLUGIN_ID))
                assertNotNull(cache.decodeJson<UiProfile>().withProxy(ProxyRef.Global).avatar)
            }
            withPresenter { _, _ ->
                assertFalse(PluginMediaPermissions.isAllowed(PLUGIN_ID))
                assertNull(cache.decodeJson<UiProfile>().withProxy(ProxyRef.Global).avatar)
            }
        }

    @Test
    fun disabledAndUninstalledPluginsLoseCachedMediaAfterRestart() =
        runBlocking {
            archives[FIRST_SHA] = archive("1.0.0", mediaNetwork = true)
            withPresenter { _, presenter -> install(presenter) }
            withPresenter { _, presenter ->
                assertTrue(PluginMediaPermissions.isAllowed(PLUGIN_ID))
                presenter.setEnabled(PLUGIN_ID, false)
                assertTrue(PluginMediaPermissions.isAllowed(PLUGIN_ID))
            }
            withPresenter { _, presenter ->
                assertFalse(PluginMediaPermissions.isAllowed(PLUGIN_ID))
                presenter.setEnabled(PLUGIN_ID, true)
            }
            withPresenter { _, presenter ->
                assertTrue(PluginMediaPermissions.isAllowed(PLUGIN_ID))
                presenter.uninstall(PLUGIN_ID)
                assertTrue(PluginMediaPermissions.isAllowed(PLUGIN_ID))
            }
            withPresenter { _, _ -> assertFalse(PluginMediaPermissions.isAllowed(PLUGIN_ID)) }
        }

    @Test
    fun githubInstallCanBeCancelledAndOnlyRunsAfterConfirmationAndRestart() =
        runBlocking {
            withPresenter { subsystem, presenter ->
                presenter.loadGitHubRepository("$REPOSITORY_URL.git")
                assertEquals(
                    42L,
                    presenter.state.value.githubCatalog
                        ?.repository
                        ?.id,
                )
                presenter.inspectGitHub("v1.0.0")
                assertEquals(
                    REPOSITORY_URL,
                    presenter.state.value.pendingInstall
                        ?.sourceUrl,
                )
                assertTrue(
                    subsystem.stateStore.desired.value.plugins
                        .isEmpty(),
                )
                presenter.cancelInstall()
                assertNull(presenter.state.value.pendingInstall)
                assertTrue(
                    subsystem.stateStore.desired.value.plugins
                        .isEmpty(),
                )

                presenter.inspectGitHub("v1.0.0")
                presenter.confirmInstall()
                assertTrue(
                    subsystem.stateStore.running.plugins
                        .isEmpty(),
                )
                assertTrue(presenter.state.value.requiresRestart)
                val source =
                    assertIs<PluginInstallSourceV1.GitHub>(
                        subsystem.stateStore.desired.value.plugins.values
                            .single()
                            .source,
                    )
                assertEquals("v1.0.0", source.tag)
                assertEquals(FIRST_SHA, source.commitSha)
            }
            withPresenter { subsystem, presenter ->
                assertEquals(
                    "1.0.0",
                    subsystem.stateStore.running.plugins.values
                        .single()
                        .installed.version,
                )
                assertFalse(presenter.state.value.requiresRestart)
            }
        }

    @Test
    fun updateChecksDoNotDownloadAndConfirmedUpdatesKeepTheRunningVersionUntilRestart() =
        runBlocking {
            withPresenter { _, presenter -> install(presenter) }
            archives[SECOND_SHA] = archive("1.1.0", addedPermission = true)
            tags = listOf("v1.0.0" to FIRST_SHA, "v1.1.0" to SECOND_SHA)
            withPresenter { subsystem, presenter ->
                val before = downloads
                presenter.checkUpdates(forceRefresh = false)
                assertNull(
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate,
                )
                presenter.checkUpdates()
                assertEquals(before, downloads)
                assertEquals(
                    "1.1.0",
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate
                        ?.version,
                )
                assertEquals(
                    "1.0.0",
                    subsystem.stateStore.desired.value.plugins.values
                        .single()
                        .version,
                )

                presenter.inspectGitHubUpdate(PLUGIN_ID)
                val review = assertNotNull(presenter.state.value.pendingInstall)
                assertTrue(review.warnings.any { it.type == PluginInstallWarningTypeV1.AddedPermission })
                assertEquals(
                    "1.0.0",
                    subsystem.stateStore.desired.value.plugins.values
                        .single()
                        .version,
                )
                presenter.cancelInstall()
                presenter.inspectGitHubUpdate(PLUGIN_ID)
                presenter.confirmInstall()

                assertEquals(
                    "1.1.0",
                    subsystem.stateStore.desired.value.plugins.values
                        .single()
                        .version,
                )
                assertEquals(
                    "1.0.0",
                    subsystem.stateStore.running.plugins.values
                        .single()
                        .installed.version,
                )
                assertNull(
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate,
                )
                assertTrue(presenter.state.value.requiresRestart)
            }
            withPresenter { subsystem, _ ->
                assertEquals(
                    "1.1.0",
                    subsystem.stateStore.running.plugins.values
                        .single()
                        .installed.version,
                )
            }
        }

    @Test
    fun rewrittenTagsAreOfferedAsExplicitReinstalls() =
        runBlocking {
            withPresenter { _, presenter -> install(presenter) }
            archives[SECOND_SHA] = archive("1.0.0")
            tags = listOf("v1.0.0" to SECOND_SHA)
            withPresenter { subsystem, presenter ->
                presenter.checkUpdates()
                assertEquals(
                    "v1.0.0",
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate
                        ?.tag,
                )
                presenter.inspectGitHubUpdate(PLUGIN_ID)
                assertTrue(
                    assertNotNull(presenter.state.value.pendingInstall).warnings.any { it.type == PluginInstallWarningTypeV1.TagMoved },
                )
                presenter.cancelInstall()
                assertEquals(
                    FIRST_SHA,
                    (
                        subsystem.stateStore.desired.value.plugins.values
                            .single()
                            .source as PluginInstallSourceV1.GitHub
                    ).commitSha,
                )
            }
        }

    @Test
    fun failedUpdatesCannotLeaveAConfirmablePreviewOrReplaceTheWorkingPackage() =
        runBlocking {
            withPresenter { _, presenter -> install(presenter) }
            tags = listOf("v1.1.0" to SECOND_SHA)
            archives[SECOND_SHA] = archive("1.1.0", id = "different.plugin")
            withPresenter { subsystem, presenter ->
                val installed =
                    subsystem.stateStore.desired.value.plugins.values
                        .single()
                presenter.checkUpdates()
                assertFails { presenter.inspectGitHubUpdate(PLUGIN_ID) }
                assertNull(presenter.state.value.pendingInstall)
                assertNotNull(presenter.state.value.error)
                assertFails { presenter.confirmInstall() }
                assertEquals(
                    installed,
                    subsystem.stateStore.desired.value.plugins.values
                        .single(),
                )
                assertEquals(
                    installed,
                    subsystem.stateStore.running.plugins.values
                        .single()
                        .installed,
                )
            }
        }

    @Test
    fun repositoryReplacementAndDeletedVersionsSilentlyClearUpdateCandidates() =
        runBlocking {
            withPresenter { _, presenter -> install(presenter) }
            tags = listOf("v1.1.0" to SECOND_SHA)
            withPresenter { _, presenter ->
                presenter.checkUpdates()
                assertNotNull(
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate,
                )
                repositoryId = 999
                presenter.checkUpdates()
                assertNull(presenter.state.value.error)
                assertFalse(presenter.state.value.busy)
                assertNull(
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate,
                )
            }
            repositoryId = 42
            tags = emptyList()
            withPresenter { _, presenter ->
                presenter.checkUpdates()
                assertNull(presenter.state.value.error)
                assertNull(
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate,
                )
            }
            tags = listOf("v0.9.0" to FIRST_SHA)
            withPresenter { _, presenter ->
                presenter.checkUpdates()
                assertNull(presenter.state.value.error)
                assertNull(
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate,
                )
            }
        }

    @Test
    fun updateChecksOnlyPublishAvailableUpdatesWithoutLoadingOrFailureMessages() =
        runBlocking {
            withPresenter { _, presenter ->
                install(presenter)
                tags = listOf("v1.1.0" to SECOND_SHA)
                catalogStarted = CompletableDeferred()
                catalogRelease = CompletableDeferred()
                val check = launch { presenter.checkUpdates() }
                try {
                    withTimeout(10_000) { catalogStarted!!.await() }
                    assertFalse(presenter.state.value.busy)
                    assertNull(presenter.state.value.error)
                    catalogRelease!!.complete(Unit)
                    check.join()
                    assertEquals(
                        "v1.1.0",
                        presenter.state.value.plugins
                            .single()
                            .availableUpdate
                            ?.tag,
                    )
                    assertFalse(presenter.state.value.busy)
                    assertNull(presenter.state.value.error)
                } finally {
                    check.cancelAndJoin()
                }

                catalogStatus = HttpStatusCode.TooManyRequests
                presenter.checkUpdates()
                assertFalse(presenter.state.value.busy)
                assertNull(presenter.state.value.error)
                assertNull(
                    presenter.state.value.plugins
                        .single()
                        .availableUpdate,
                )

                presenter.reportOperationFailure()
                val error = presenter.state.value.error
                presenter.checkUpdates()
                assertEquals(error, presenter.state.value.error)
            }
        }

    @Test
    fun slowUpdateChecksDoNotBlockFileInstallsOrRestoreStaleCandidates() =
        runBlocking {
            withPresenter { _, presenter ->
                install(presenter)
                val replacement = root / "replacement.fpp"
                TestFppFactory.write(
                    replacement,
                    TestFppFactory.validEntries(
                        manifest = TestFppFactory.validManifest.replace("\"version\": \"1.0.0\"", "\"version\": \"1.1.0\""),
                    ),
                )
                tags = listOf("v1.1.0" to SECOND_SHA)
                catalogStarted = CompletableDeferred()
                catalogRelease = CompletableDeferred()
                val check = launch { presenter.checkUpdates() }
                try {
                    withTimeout(10_000) { catalogStarted!!.await() }
                    withTimeout(10_000) {
                        presenter.inspect(replacement.toString())
                        presenter.confirmInstall()
                    }
                    catalogRelease!!.complete(Unit)
                    check.join()
                    val installed =
                        presenter.state.value.plugins
                            .single()
                    assertEquals("1.1.0", installed.version)
                    assertNull(installed.sourceUrl)
                    assertNull(installed.availableUpdate)
                    assertNull(presenter.state.value.error)
                } finally {
                    check.cancelAndJoin()
                }
            }
        }

    @Test
    fun cancellingAnUpdateCheckPreservesStateAndAllowsTheNextAction() =
        runBlocking {
            withPresenter { _, presenter ->
                install(presenter)
                presenter.reportOperationFailure()
                val previous = presenter.state.value
                catalogStarted = CompletableDeferred()
                catalogRelease = CompletableDeferred()
                val check = launch { presenter.checkUpdates() }
                try {
                    withTimeout(10_000) { catalogStarted!!.await() }
                    assertEquals(previous, presenter.state.value)
                } finally {
                    check.cancelAndJoin()
                }
                assertTrue(check.isCancelled)
                assertEquals(previous, presenter.state.value)

                catalogRelease = null
                presenter.loadGitHubRepository(REPOSITORY_URL)
                assertNotNull(presenter.state.value.githubCatalog)
                assertNull(presenter.state.value.error)
            }
        }

    @Test
    fun cancelledDownloadClearsTheOldPreviewAndKeepsTheInstalledPackage() =
        runBlocking {
            withPresenter { _, presenter -> install(presenter) }
            withPresenter { subsystem, presenter ->
                presenter.loadGitHubRepository(REPOSITORY_URL)
                presenter.inspectGitHub("v1.0.0")
                assertNotNull(presenter.state.value.pendingInstall)
                blocked = true
                val job = launch { presenter.inspectGitHub("v1.0.0") }
                downloadStarted.await()
                job.cancelAndJoin()
                assertNull(presenter.state.value.pendingInstall)
                assertFalse(presenter.state.value.busy)
                assertFalse(fileSystem.exists(subsystem.stateStore.paths.incoming))
                assertFails { presenter.confirmInstall() }
                assertEquals(
                    "1.0.0",
                    subsystem.stateStore.desired.value.plugins.values
                        .single()
                        .version,
                )
            }
        }

    private suspend fun install(presenter: PluginManagementPresenterV1) {
        presenter.loadGitHubRepository(REPOSITORY_URL)
        presenter.inspectGitHub("v1.0.0")
        presenter.confirmInstall()
    }

    private fun archive(
        version: String,
        id: String = PLUGIN_ID,
        addedPermission: Boolean = false,
        mediaNetwork: Boolean = false,
    ): ByteArray {
        var manifest = TestFppFactory.validManifest.replace("\"version\": \"1.0.0\"", "\"version\": \"$version\"").replace(PLUGIN_ID, id)
        if (mediaNetwork) {
            manifest = manifest.replace("\"defaultLocale\":", "\"permissions\": {\"mediaNetwork\":true}, \"defaultLocale\":")
        }
        if (addedPermission) {
            manifest =
                manifest.replace("\"defaultLocale\":", "\"permissions\": {\"authOrigins\":[\"https://auth.example\"]}, \"defaultLocale\":")
        }
        val path = root / "source.zip"
        TestFppFactory.write(path, TestFppFactory.validEntries(manifest = manifest).map { (name, bytes) -> "repo-commit/$name" to bytes })
        return fileSystem.read(path) { readByteArray() }
    }

    private suspend fun withPresenter(block: suspend (PluginSubsystemV1, PluginManagementPresenterV1) -> Unit) {
        val subsystem =
            PluginSubsystemV1(
                appFiles =
                    object : AppFileStore {
                        override val fileSystem = FileSystem.SYSTEM

                        override fun directory(name: String): Path = root / name
                    },
                pendingStore =
                    object : PluginOAuthPendingStoreV1 {
                        override suspend fun save(pending: PluginOAuthPendingV1) = Unit

                        override suspend fun load(flowId: String): PluginOAuthPendingV1? = null

                        override suspend fun consume(pending: PluginOAuthPendingV1): Boolean = false
                    },
            )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val presenter = PluginManagementPresenterV1(subsystem, scope, github = client)
        try {
            block(subsystem, presenter)
        } finally {
            presenter.close()
            scope.cancel()
            subsystem.runtimePool.close()
        }
    }
}

private const val REPOSITORY_URL = "https://github.com/owner/repo"
private const val PLUGIN_ID = "dev.dimension.flare.test.plugin"
private const val FIRST_SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
private const val SECOND_SHA = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
