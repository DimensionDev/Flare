package dev.dimension.flare.feature.plugin.authoring

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.development.PluginDevelopmentSessionV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginClient
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.installer.pluginIconFixtures
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
import dev.dimension.flare.feature.plugin.management.PluginManagementPresenterV1
import dev.dimension.flare.feature.plugin.runtime.PluginSessionRetiredException
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.asType
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import okio.ByteString.Companion.decodeBase64
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginAuthoringServiceV1Test {
    private lateinit var root: Path
    private lateinit var scope: CoroutineScope
    private lateinit var subsystem: PluginSubsystemV1
    private lateinit var service: PluginAuthoringServiceV1
    private lateinit var registry: PlatformRegistry

    @Volatile private var onRead: ((Path) -> Unit)? = null
    private var failingIndexWrites = 0
    private var failCleanup = false
    private var onIndexFailure: (() -> Unit)? = null
    private val fileSystem =
        object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun source(file: Path): okio.Source {
                onRead?.invoke(file)
                return super.source(file)
            }

            override fun delete(
                path: Path,
                mustExist: Boolean,
            ) {
                if (failCleanup && path.name.endsWith(".fpp")) error("synthetic cleanup failure")
                super.delete(path, mustExist)
            }

            override fun atomicMove(
                source: Path,
                target: Path,
            ) {
                if (target.name == "index.json" && failingIndexWrites > 0) {
                    failingIndexWrites--
                    onIndexFailure?.invoke()
                    error("synthetic index failure")
                }
                super.atomicMove(source, target)
            }
        }
    private val accounts = AuthoringAccounts()
    private val pending = AuthoringPending()
    private val appFiles get() =
        object : AppFileStore {
            override val fileSystem = this@PluginAuthoringServiceV1Test.fileSystem

            override fun directory(name: String): Path = root / name
        }

    @BeforeTest
    fun setup() {
        root = Files.createTempDirectory("plugin-authoring").toOkioPath()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        subsystem = PluginSubsystemV1(appFiles, pending)
        registry = PlatformRegistry(PlatformRuntimeData(listOf(AuthoringGuest), emptyList(), listOf(subsystem.platformSource(scope))))
        service = PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { scope })
    }

    @AfterTest
    fun teardown() =
        runBlocking<Unit> {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            subsystem.runtimePool.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }

    @Test
    fun staleDeletionApprovalCannotRemoveAnUpdatedPlugin() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            val applied = await(plugin.id, PluginAuthoringPhase.Applied)
            assertFailsWith<IllegalStateException> { service.delete(plugin.id, expectedRevision = applied.revision - 1) }
            assertEquals(applied, state(plugin.id))
            assertTrue(plugin.id in subsystem.running.value.plugins)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun cleanupFailureCannotReinstallADeletedPluginFromQueuedSource() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val scheduler = TestCoroutineScheduler()
            val queuedScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
            try {
                val queued = PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { queuedScope })
                queued.write(plugin.id, queued.read(plugin.id).plugin.revision, sources(plugin, "queued"))
                failCleanup = true
                assertFailsWith<IllegalStateException> { queued.delete(plugin.id) }
                scheduler.runCurrent()
                assertTrue(queued.plugins.value.isEmpty())
                assertFalse(plugin.id in subsystem.running.value.plugins)
                assertFalse(plugin.id in subsystem.stateStore.desired.value.plugins)
                assertFalse(fileSystem.exists(root / "authored-plugins" / "${plugin.id}.json"))
                failCleanup = false
                subsystem.installer.cleanup(subsystem.running.value.plugins.values)
            } finally {
                failCleanup = false
                queuedScope.coroutineContext[Job]!!.cancelAndJoin()
            }
        }

    @Test
    fun iconsAreRetainedAcrossEditsAndRestartAndFailedDownloadsKeepRunningVersion() =
        runBlocking<Unit> {
            val downloads = mutableListOf<String>()
            val icon = pluginIconFixtures.getValue("gif")
            service =
                PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { scope }) { url ->
                    downloads += url
                    check(!url.endsWith("missing")) { "Icon returned HTTP 404" }
                    icon
                }
            val plugin = service.create(UUID.randomUUID().toString())
            val url = "https://example.com/icon"
            service.write(plugin.id, 0, sources(plugin), url)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val running =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            assertTrue(running.iconPath.endsWith(".gif"))
            assertContentEquals(icon, fileSystem.read(running.iconPath.toPath()) { readByteArray() })
            assertEquals(url, service.read(plugin.id).iconUrl)
            assertEquals(setOf("manifest.json", "plugin.js"), service.read(plugin.id).files.keys)
            service.write(plugin.id, 1, sources(plugin, "Changed source"))
            await(plugin.id, PluginAuthoringPhase.Applied)
            assertEquals(listOf(url), downloads)
            assertEquals(
                running.iconPath,
                subsystem.running.value.plugins
                    .getValue(plugin.id)
                    .iconPath,
            )

            service = PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { scope }) { error("Must use saved icon") }
            service.write(plugin.id, 2, sources(plugin, "After restart"))
            await(plugin.id, PluginAuthoringPhase.Applied)
            assertEquals(
                running.iconPath,
                subsystem.running.value.plugins
                    .getValue(plugin.id)
                    .iconPath,
            )
            val working =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            service = PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { scope }) { error("Icon returned HTTP 404") }
            service.write(plugin.id, 3, sources(plugin, "Failed icon"), "https://example.com/missing")
            assertTrue(await(plugin.id, PluginAuthoringPhase.Failed).error.orEmpty().contains("HTTP 404"))
            assertEquals(
                working,
                subsystem.running.value.plugins
                    .getValue(plugin.id),
            )
            assertEquals(4, service.read(plugin.id).plugin.revision)
            service.write(plugin.id, 4, sources(plugin, "Source edit after failed icon"))
            await(plugin.id, PluginAuthoringPhase.Applied)
            assertEquals(url, service.read(plugin.id).iconUrl)
            assertEquals(
                running.iconPath,
                subsystem.running.value.plugins
                    .getValue(plugin.id)
                    .iconPath,
            )
        }

    @Test
    fun olderIconDownloadCannotReplaceNewerSourceAndIcon() =
        runBlocking<Unit> {
            val started = kotlinx.coroutines.CompletableDeferred<Job>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            service =
                PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { scope }) { url ->
                    if (url.endsWith("old")) {
                        started.complete(kotlinx.coroutines.currentCoroutineContext()[Job]!!)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
                        pluginIconFixtures.getValue("gif")
                    } else {
                        pluginIconFixtures.getValue("webp")
                    }
                }
            val plugin = service.create(UUID.randomUUID().toString())
            service.write(plugin.id, 0, sources(plugin), "https://example.com/old")
            started.await()
            service.write(plugin.id, 1, sources(plugin, "Latest"), "https://example.com/new")
            await(plugin.id, PluginAuthoringPhase.Applied)
            release.complete(Unit)
            started.await().join()
            val current =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            assertTrue(current.iconPath.endsWith(".webp"))
            assertContentEquals(pluginIconFixtures.getValue("webp"), fileSystem.read(current.iconPath.toPath()) { readByteArray() })
            assertEquals("https://example.com/new", service.read(plugin.id).iconUrl)
        }

    @Test
    fun localSourcesApplyAutomaticallyIncludingPermissionAndCredentialChanges() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            service.write(plugin.id, 0, sources(plugin))
            await(plugin.id, PluginAuthoringPhase.Applied)
            val updated = sources(plugin, "Updated", media = true).toMutableMap()
            updated["manifest.json"] =
                updated.getValue("manifest.json").replace("\"detector\":", "\"credentialSchemaVersion\": 2, \"detector\":")
            service.write(plugin.id, 1, updated)
            val applied = await(plugin.id, PluginAuthoringPhase.Applied)
            val installed =
                subsystem.stateStore.desired.value.plugins
                    .getValue(plugin.id)
            assertEquals(2, applied.revision)
            assertTrue(applied.running)
            assertTrue(installed.manifest.permissions.mediaNetwork)
            assertEquals(2, installed.manifest.platform.credentialSchemaVersion)
        }

    @Test
    fun sameIdFileImportPreservesAuthoredSourceAndRunningVersion() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val source = service.read(plugin.id)
            val installed =
                subsystem.stateStore.desired.value.plugins
                    .getValue(plugin.id)
            val running =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val archive = root / "same-id.fpp"
            TestFppFactory.write(
                archive,
                TestFppFactory.validEntries(manifest = sources(plugin, "Imported").getValue("manifest.json").replace("1.0.0", "2.0.0")),
            )
            val presenter = PluginManagementPresenterV1(subsystem, scope, registry = { registry }, accounts = { accounts })
            try {
                val review = presenter.inspect(archive.toString())
                assertFailsWith<IllegalStateException> { presenter.confirmInstall() }
                assertEquals(installed, subsystem.stateStore.desired.value.plugins[plugin.id])
                assertEquals(running, subsystem.running.value.plugins[plugin.id])
                assertEquals(source, service.read(plugin.id))
                assertEquals(review, presenter.state.value.pendingInstall)
                assertNotNull(presenter.state.value.error)
                assertFalse(presenter.state.value.busy)
                presenter.cancelInstall()
                service.write(plugin.id, source.plugin.revision, sources(plugin, "Updated source"))
                await(plugin.id, PluginAuthoringPhase.Applied)
                assertEquals(source.plugin.revision + 1, service.read(plugin.id).plugin.revision)
            } finally {
                presenter.close()
            }
        }

    @Test
    fun creationCannotClaimAnImportedPluginIdentity() =
        runBlocking<Unit> {
            val key = UUID.randomUUID().toString()
            val plugin = service.create(key)
            service.delete(plugin.id)
            val archive = root / "imported-identity.fpp"
            TestFppFactory.write(archive, TestFppFactory.validEntries(manifest = sources(plugin).getValue("manifest.json")))
            val presenter = PluginManagementPresenterV1(subsystem, scope, registry = { registry }, accounts = { accounts })
            try {
                presenter.inspect(archive.toString())
                presenter.confirmInstall()
                val installed =
                    subsystem.stateStore.desired.value.plugins
                        .getValue(plugin.id)
                assertFailsWith<IllegalStateException> { service.create(key) }
                assertTrue(service.plugins.value.isEmpty())
                assertFalse(fileSystem.exists(root / "authored-plugins" / "${plugin.id}.json"))
                assertEquals(installed, subsystem.stateStore.desired.value.plugins[plugin.id])
                assertEquals(
                    plugin.id,
                    presenter.state.value.items
                        .single()
                        .pluginId,
                )

                val restarted = PluginSubsystemV1(appFiles, pending)
                try {
                    restarted.stateStore.uninstall(plugin.id)
                    val authoring = PluginAuthoringServiceV1(restarted, { registry }, { accounts }, { scope })
                    assertFailsWith<IllegalStateException> { authoring.create(key) }
                    assertTrue(authoring.plugins.value.isEmpty())
                    assertTrue(plugin.id in restarted.running.value.plugins)
                } finally {
                    restarted.runtimePool.close()
                }
            } finally {
                presenter.close()
            }
        }

    @Test
    fun directoryValidationDoesNotBlockAnotherPluginApplying() =
        runBlocking<Unit> {
            val first = service.create(UUID.randomUUID().toString())
            generate(first)
            await(first.id, PluginAuthoringPhase.Applied)
            val development = startDevelopment(first)
            val other = service.create(UUID.randomUUID().toString())
            val revision = service.read(other.id).plugin.revision
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            val script = root / "directory-plugin" / "plugin.js"
            onRead = { path ->
                if (path == script) {
                    entered.complete(Unit)
                    check(release.await(10, TimeUnit.SECONDS)) { "Directory read was not released" }
                }
            }
            try {
                withTimeout(5_000) { entered.await() }
                service.write(other.id, revision, sources(other))
                await(other.id, PluginAuthoringPhase.Applied)
                assertTrue(development.state.value.active)
                assertTrue(other.id in subsystem.running.value.plugins)
            } finally {
                onRead = null
                release.countDown()
                development.stop()
            }
        }

    @Test
    fun localImportPreparationDoesNotBlockApplicationAndSurvivesCleanup() =
        runBlocking<Unit> {
            val archive = root / "import.fpp"
            TestFppFactory.write(archive)
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            val source =
                object : okio.ForwardingSource(fileSystem.source(archive)) {
                    override fun read(
                        sink: okio.Buffer,
                        byteCount: Long,
                    ): Long {
                        entered.complete(Unit)
                        check(release.await(10, TimeUnit.SECONDS)) { "Import read was not released" }
                        return super.read(sink, byteCount)
                    }
                }
            val presenter = PluginManagementPresenterV1(subsystem, scope, registry = { registry }, accounts = { accounts })
            val importing = async { presenter.inspect(source) }
            try {
                withTimeout(5_000) { entered.await() }
                val plugin = service.create(UUID.randomUUID().toString())
                generate(plugin)
                await(plugin.id, PluginAuthoringPhase.Applied)
                subsystem.installer.cleanup(subsystem.running.value.plugins.values)
                assertTrue(fileSystem.exists(subsystem.stateStore.paths.incoming))
                release.countDown()
                importing.await()
                assertNotNull(presenter.state.value.pendingInstall)
                presenter.confirmInstall()
                assertTrue(plugin.id in subsystem.running.value.plugins)
                assertEquals(2, subsystem.stateStore.desired.value.plugins.size)
            } finally {
                release.countDown()
                importing.cancelAndJoin()
                source.close()
                presenter.close()
            }
        }

    @Test
    fun githubDownloadDoesNotBlockAnotherPluginApplying() =
        runBlocking<Unit> {
            val entered = CompletableDeferred<Unit>()
            val client =
                GitHubPluginClient(
                    MockEngine { request ->
                        when {
                            request.url.encodedPath.endsWith("/tags") -> {
                                respond("""[{"name":"v1.0.0","commit":{"sha":"${"a".repeat(40)}"}}]""")
                            }

                            "/zipball/" in request.url.encodedPath -> {
                                entered.complete(Unit)
                                awaitCancellation()
                            }

                            else -> {
                                respond("""{"id":42,"name":"repo","owner":{"login":"owner"},"private":false}""")
                            }
                        }
                    },
                )
            val presenter = PluginManagementPresenterV1(subsystem, scope, github = client, registry = { registry }, accounts = { accounts })
            val download = launch { presenter.inspectGitHub("https://github.com/owner/repo") }
            try {
                withTimeout(5_000) { entered.await() }
                val plugin = service.create(UUID.randomUUID().toString())
                generate(plugin)
                await(plugin.id, PluginAuthoringPhase.Applied)
                assertTrue(assertNotNull(presenter.state.value.gitHubInstall).busy)
                assertFalse(presenter.state.value.busy)
                assertTrue(plugin.id in subsystem.running.value.plugins)
            } finally {
                download.cancelAndJoin()
                presenter.close()
                client.close()
            }
            assertFalse(assertNotNull(presenter.state.value.gitHubInstall).busy)
            assertFalse(presenter.state.value.busy)
            assertTrue(
                subsystem.stateStore.desired.value.plugins
                    .isNotEmpty(),
            )
        }

    @Test
    fun multiplePluginsApplyIndependentlyAndSurviveRestartAndCleanup() =
        runBlocking<Unit> {
            val a = service.create(UUID.randomUUID().toString())
            val b = service.create(UUID.randomUUID().toString())
            coroutineScope {
                val first = async { generate(a, "A") }
                val second = async { generate(b, "B") }
                first.await()
                second.await()
            }
            await(a.id, PluginAuthoringPhase.Applied)
            await(b.id, PluginAuthoringPhase.Applied)
            assertEquals(setOf(a.id, b.id), subsystem.running.value.plugins.keys)
            subsystem.installer.cleanup(subsystem.running.value.plugins.values)
            val restarted = PluginSubsystemV1(appFiles, pending)
            assertEquals(setOf(a.id, b.id), restarted.running.value.plugins.keys)
            assertEquals(
                subsystem.running.value.plugins[a.id]
                    ?.generation,
                restarted.running.value.plugins[a.id]
                    ?.generation,
            )
            restarted.runtimePool.close()
        }

    @Test
    fun invalidEditKeepsRunningAndPersistedVersion() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val before =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val revision = service.read(plugin.id).plugin.revision
            service.write(plugin.id, revision, sources(plugin) + ("plugin.js" to "invalid {{{"))
            await(plugin.id, PluginAuthoringPhase.Failed)
            assertEquals(before, subsystem.running.value.plugins[plugin.id])
            assertEquals(before.installed, subsystem.stateStore.desired.value.plugins[plugin.id])
            assertEquals(PluginAuthoringPhase.Failed, state(plugin.id).phase)
            val reloaded = PluginAuthoringServiceV1(subsystem, { error("unused") }, { accounts }, { scope })
            val restored = reloaded.plugins.value.single { it.id == plugin.id }
            assertTrue(restored.running)
            assertEquals(PluginAuthoringPhase.Idle, restored.phase)
        }

    @Test
    fun reloadWaitsForWritesAndOnlyAppliesLatestRevision() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val previous =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coroutineScope {
                val writer =
                    async {
                        previous.session.invoke("posts.publish") {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                entered.await()
                generate(plugin, "older")
                await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
                generate(plugin, "latest")
                await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
                assertEquals(previous, subsystem.running.value.plugins[plugin.id])
                release.complete(Unit)
                writer.await()
            }
            await(plugin.id, PluginAuthoringPhase.Applied)
            assertEquals("latest", state(plugin.id).name)
            assertFalse(previous.session.isActive)
        }

    @Test
    fun reloadWaitsForOAuthWithoutConsumingItsPendingRecord() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            pending.busy = plugin.id
            generate(plugin, "after login")
            await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
            assertEquals(plugin.id, pending.busy)
            pending.busy = null
            await(plugin.id, PluginAuthoringPhase.Applied)
        }

    @Test
    fun deletedPluginRejectsLateWritesAndDoesNotCancelOtherPlugins() =
        runBlocking<Unit> {
            val a = service.create(UUID.randomUUID().toString())
            val b = service.create(UUID.randomUUID().toString())
            val revision = service.read(a.id).plugin.revision
            service.delete(a.id)
            assertFailsWith<IllegalArgumentException> { service.write(a.id, revision, sources(a)) }
            generate(b)
            await(b.id, PluginAuthoringPhase.Applied)
            assertEquals(listOf(b.id), service.plugins.value.map { it.id })
        }

    @Test
    fun generatedInstallDoesNotDiscardPendingFileImport() =
        runBlocking<Unit> {
            val path = root / "import.fpp"
            TestFppFactory.write(path)
            val preview = subsystem.installer.inspect(path)
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            subsystem.installer.commit(preview, true, activate = subsystem.stateStore::install)
            assertEquals(2, subsystem.stateStore.desired.value.plugins.size)
        }

    @Test
    fun rejectsEscapingPathsAndChangedIdentity() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            val revision = service.read(plugin.id).plugin.revision
            assertFailsWith<IllegalArgumentException> { service.write(plugin.id, revision, sources(plugin) + ("../token" to "no")) }
            service.write(plugin.id, revision, sources(plugin) + ("manifest.json" to TestFppFactory.validManifest))
            await(plugin.id, PluginAuthoringPhase.Failed)
            assertTrue(
                subsystem.running.value.plugins
                    .isEmpty(),
            )
        }

    @Test
    fun bundledSdkMatchesCurrentContractAndRejectsUnknownPaths() =
        runBlocking<Unit> {
            assertTrue(service.documentation("flare-plugin.d.ts").contains("definePlugin"))
            val guide = service.documentation("README.md")
            assertTrue(guide.contains("write_plugin_files"))
            assertTrue(service.documentation("").contains("assets/github-template/manifest.json"))
            listOf(
                "SKILL.md",
                "references/publishing.md",
                "references/testing.md",
                "examples/pixelfed/plugin.js",
                "pack.mjs",
            ).forEach { path ->
                assertFailsWith<IllegalArgumentException> { service.documentation(path) }
            }
            assertFailsWith<IllegalArgumentException> { service.documentation("../../credentials") }
            assertFailsWith<IllegalArgumentException> { service.fetchDocumentation("https://localhost/private") }
            assertFailsWith<IllegalArgumentException> { service.fetchDocumentation("https://127.0.0.1/private") }
        }

    @Test
    fun cancellingInitialApplicationPreventsItFromApplying() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            pending.busy = plugin.id
            generate(plugin, media = true)
            await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
            service.cancelUpdate(plugin.id)
            pending.busy = null
            assertFalse(plugin.id in subsystem.running.value.plugins)
            assertEquals(PluginAuthoringPhase.Idle, state(plugin.id).phase)
        }

    @Test
    fun busyDeleteKeepsRuntimeAndInstallation() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val running =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val release = running.session.holdInteraction()
            assertFailsWith<IllegalStateException> { service.delete(plugin.id) }
            assertTrue(plugin.id in subsystem.stateStore.desired.value.plugins)
            assertEquals(running, subsystem.running.value.plugins[plugin.id])
            generate(plugin, "after browser login")
            await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
            release()
            await(plugin.id, PluginAuthoringPhase.Applied)
        }

    @Test
    fun damagedSourceDoesNotPreventOtherPluginsFromLoading() =
        runBlocking<Unit> {
            val damaged = service.create(UUID.randomUUID().toString())
            val healthy = service.create(UUID.randomUUID().toString())
            FileSystem.SYSTEM.write(root / "authored-plugins" / "${damaged.id}.json") { writeUtf8("broken {") }
            val reloaded = PluginAuthoringServiceV1(subsystem, { error("not needed") }, { accounts }, { scope })
            assertEquals(
                PluginAuthoringPhase.Failed,
                reloaded.plugins.value
                    .single { it.id == damaged.id }
                    .phase,
            )
            assertEquals(
                PluginAuthoringPhase.Idle,
                reloaded.plugins.value
                    .single { it.id == healthy.id }
                    .phase,
            )
            assertEquals("broken {", FileSystem.SYSTEM.read(root / "authored-plugins" / "${damaged.id}.json") { readUtf8() })
        }

    @Test
    fun severalPluginsCanBeUpdatedWithoutAnApplicationQueue() =
        runBlocking<Unit> {
            val plugins =
                List(4) {
                    service.create(
                        UUID.randomUUID().toString(),
                    )
                }
            coroutineScope {
                plugins.map { async { service.write(it.id, 0, sources(it)) } }.forEach { it.await() }
            }
            plugins.forEach { await(it.id, PluginAuthoringPhase.Applied) }
            assertEquals(4, service.plugins.value.size)
        }

    @Test
    fun failedIndexCommitRestoresWorkingVersionAndRestartIdentity() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val before =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            failingIndexWrites = 1
            generate(plugin, "failed replacement")
            await(plugin.id, PluginAuthoringPhase.Failed)
            val restored =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            assertEquals(before.installed.packageHash, restored.installed.packageHash)
            assertTrue(restored.session.isActive)
            assertFalse(before.session.isActive)
            val restarted = PluginSubsystemV1(appFiles, pending)
            assertEquals(
                restored.generation,
                restarted.running.value.plugins
                    .getValue(plugin.id)
                    .generation,
            )
            restarted.runtimePool.close()
        }

    @Test
    fun acceptedUpdateContinuesAfterItsCallerIsCancelled() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            pending.busy = plugin.id
            val submitted = CompletableDeferred<Unit>()
            val caller =
                launch {
                    service.write(plugin.id, 1, sources(plugin, "Survives caller"))
                    submitted.complete(Unit)
                    awaitCancellation()
                }
            submitted.await()
            caller.cancelAndJoin()
            await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
            pending.busy = null
            await(plugin.id, PluginAuthoringPhase.Applied)
            assertEquals("Survives caller", state(plugin.id).name)
        }

    @Test
    fun concurrentUpdatesRejectStaleRevisionsAndKeepTheWinningSources() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            val firstRead = service.read(plugin.id)
            val secondRead = service.read(plugin.id)
            val updates = listOf(sources(plugin, "First"), sources(plugin, "Second"))
            val results =
                coroutineScope {
                    updates
                        .mapIndexed { index, files ->
                            async {
                                runCatching { service.write(plugin.id, listOf(firstRead, secondRead)[index].plugin.revision, files) }
                            }
                        }.map { it.await() }
                }
            assertEquals(1, results.count { it.isSuccess })
            assertTrue(
                results
                    .single { it.isFailure }
                    .exceptionOrNull()!!
                    .message!!
                    .contains("current revision is 1"),
            )
            val current = service.read(plugin.id)
            assertEquals(1, current.plugin.revision)
            assertEquals(updates[results.indexOfFirst { it.isSuccess }], current.files)
            await(plugin.id, PluginAuthoringPhase.Applied)
            service.write(plugin.id, current.plugin.revision, sources(plugin, "Reconciled"))
            assertEquals(2, service.read(plugin.id).plugin.revision)
            await(plugin.id, PluginAuthoringPhase.Applied)
        }

    @Test
    fun createRetriesAreIdempotentAcrossCallersAndRestart() =
        runBlocking<Unit> {
            val key =
                UUID.randomUUID().toString()
            val created =
                coroutineScope {
                    List(4) { async { service.create(key) } }.map { it.await() }
                }
            assertEquals(1, created.map { it.id }.distinct().size)
            assertEquals(1, service.plugins.value.size)
            val restarted = PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { scope })
            assertEquals(created.first().id, restarted.create(key).id)
            assertEquals(1, restarted.plugins.value.size)
        }

    @Test
    fun directoryDevelopmentBlocksDeleteUntilStopped() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val installed =
                subsystem.stateStore.desired.value.plugins
                    .getValue(plugin.id)
            val development = startDevelopment(plugin)
            val override =
                subsystem.running.value.plugins
                    .getValue(plugin.id)

            assertFailsWith<IllegalStateException> { service.delete(plugin.id) }
            assertEquals(installed, subsystem.stateStore.desired.value.plugins[plugin.id])
            assertEquals(override, subsystem.running.value.plugins[plugin.id])

            development.stop()
            assertEquals(
                installed.packageHash,
                subsystem.running.value.plugins
                    .getValue(plugin.id)
                    .installed.packageHash,
            )
            service.delete(plugin.id)
            assertFalse(plugin.id in subsystem.running.value.plugins)
            assertFalse(plugin.id in subsystem.stateStore.desired.value.plugins)
            assertTrue(service.plugins.value.isEmpty())
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun stoppingRevalidationKeepsRestoredSessionAndCancelsPendingApplication() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            val applied = await(plugin.id, PluginAuthoringPhase.Applied)
            val scheduler = TestCoroutineScheduler()
            val queuedScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
            try {
                val queued = PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { queuedScope })
                queued.write(plugin.id, applied.revision, sources(plugin, "queued edit"))
                assertEquals(
                    PluginAuthoringPhase.Validating,
                    queued.plugins.value
                        .single()
                        .phase,
                )
                val restored =
                    subsystem.running.value.plugins
                        .getValue(plugin.id)
                val invalidations = accounts.invalidations
                queued.cancelUpdate(plugin.id)
                scheduler.runCurrent()
                val stopped = queued.plugins.value.single()
                assertEquals(PluginAuthoringPhase.Idle, stopped.phase)
                assertEquals(applied.revision + 1, stopped.revision)
                assertEquals(invalidations, accounts.invalidations)
                assertEquals(
                    restored,
                    subsystem.running.value.plugins
                        .getValue(plugin.id),
                )
                assertTrue(stopped.running)
            } finally {
                queuedScope.coroutineContext[Job]!!.cancelAndJoin()
            }
        }

    @Test
    fun cancelledUpdateCanBeResubmittedWithoutApproval() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            pending.busy = plugin.id
            service.write(plugin.id, 0, sources(plugin, media = true))
            val waiting = await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
            service.cancelUpdate(plugin.id)
            pending.busy = null
            service.write(plugin.id, waiting.revision, sources(plugin, media = true))
            val applied = await(plugin.id, PluginAuthoringPhase.Applied)
            assertEquals(waiting.revision + 1, applied.revision)
            assertTrue(applied.running)
        }

    @Test
    fun directoryOverrideUpdatesAuthoredRunningStateWithoutChangingItsRecord() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            val development = startDevelopment(plugin)
            withTimeoutOrNull(5_000) { service.plugins.first { it.single().running } }
                ?: error("Directory override was not reflected in authored plugins")
            assertEquals(plugin.revision, state(plugin.id).revision)
            assertEquals(plugin.name, state(plugin.id).name)
            development.stop()
            withTimeoutOrNull(5_000) { service.plugins.first { !it.single().running } }
                ?: error("Stopped override was not reflected in authored plugins")
            assertEquals(plugin, state(plugin.id))
        }

    @Test
    fun invalidEditKeepsWorkingVersionAcrossRestart() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val before =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val revision = service.read(plugin.id).plugin.revision
            service.write(plugin.id, revision, sources(plugin) + ("plugin.js" to "invalid {{{"))
            await(plugin.id, PluginAuthoringPhase.Failed)
            await(plugin.id, PluginAuthoringPhase.Failed)
            assertTrue(state(plugin.id).running)
            assertEquals(
                before.installed.packageHash,
                subsystem.running.value.plugins
                    .getValue(plugin.id)
                    .installed.packageHash,
            )
            assertTrue(plugin.id in subsystem.stateStore.desired.value.plugins)
            val restarted = PluginSubsystemV1(appFiles, pending)
            assertEquals(
                before.installed.packageHash,
                restarted.running.value.plugins
                    .getValue(plugin.id)
                    .installed.packageHash,
            )
            restarted.runtimePool.close()
        }

    @Test
    fun editingAppliesSavedPermissionsAutomatically() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            generate(plugin, "needs media", media = true)
            await(plugin.id, PluginAuthoringPhase.Applied)
            assertTrue(
                dev.dimension.flare.ui.model.PluginMediaPermissions
                    .isAllowed(plugin.id),
            )
        }

    @Test
    fun failedCommitDoesNotAdmitWritesToTheReplacementBeforeRollback() =
        runBlocking<Unit> {
            val plugin = service.create(UUID.randomUUID().toString())
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val before =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val observed = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var writeStarted = false
            onIndexFailure = {
                val visible =
                    subsystem.running.value.plugins
                        .getValue(plugin.id)
                scope.launch {
                    try {
                        visible.session.invoke("capabilities.compose.publish") {
                            writeStarted = true
                            observed.complete(Unit)
                            release.await()
                        }
                    } catch (_: PluginSessionRetiredException) {
                        observed.complete(Unit)
                    }
                }
                runBlocking { observed.await() }
            }
            failingIndexWrites = 1
            try {
                generate(plugin, "failed replacement")
                val failed =
                    withTimeoutOrNull(5_000) {
                        service.plugins
                            .first { items ->
                                items.any {
                                    it.id == plugin.id &&
                                        it.phase in setOf(PluginAuthoringPhase.Failed, PluginAuthoringPhase.WaitingForOperations)
                                }
                            }.single { it.id == plugin.id }
                    }
                assertEquals(PluginAuthoringPhase.Failed, failed?.phase)
                assertFalse(writeStarted)
                val restored =
                    subsystem.running.value.plugins
                        .getValue(plugin.id)
                assertEquals(before.installed.packageHash, restored.installed.packageHash)
                assertTrue(restored.session.isActive)
            } finally {
                release.complete(Unit)
            }
        }

    private suspend fun startDevelopment(plugin: AuthoredPlugin): PluginDevelopmentSessionV1 {
        val directory = root / "directory-plugin"
        fileSystem.createDirectories(directory / "assets")
        sources(plugin, "directory version").forEach { (name, text) ->
            fileSystem.write(directory / name) { writeUtf8(text) }
        }
        fileSystem.write(directory / "assets/icon.png") { write(requireNotNull(DEFAULT_PLUGIN_ICON.decodeBase64())) }
        return PluginDevelopmentSessionV1(subsystem, registry, accounts, scope).also {
            it.open(directory.toString())
            it.reload()
            it.reload(confirmed = true)
            assertTrue(it.state.value.active)
        }
    }

    private suspend fun generate(
        plugin: AuthoredPlugin,
        name: String = "Example",
        media: Boolean = false,
    ) {
        val revision = service.read(plugin.id).plugin.revision
        service.write(plugin.id, revision, sources(plugin, name, media))
    }

    private fun sources(
        plugin: AuthoredPlugin,
        name: String = "Example",
        media: Boolean = false,
    ) = mapOf(
        "manifest.json" to
            TestFppFactory.validManifest
                .replace("dev.dimension.flare.test.plugin", plugin.id)
                .replace("TestPlugin", plugin.platformId)
                .replace("Test plugin", name)
                .let {
                    if (media) {
                        it.replace(
                            "\"schemaVersion\": 1,",
                            "\"schemaVersion\": 1, \"permissions\": {\"mediaNetwork\": true},",
                        )
                    } else {
                        it
                    }
                },
        "plugin.js" to TestFppFactory.validScript,
    )

    private fun state(id: String) = service.plugins.value.single { it.id == id }

    private suspend fun await(
        id: String,
        phase: PluginAuthoringPhase,
    ): AuthoredPlugin =
        requireNotNull(
            withTimeoutOrNull(5_000) {
                service.plugins
                    .first { values ->
                        values.any { it.id == id && (it.phase == phase || it.phase == PluginAuthoringPhase.Failed) }
                    }.single {
                        it.id ==
                            id
                    }.also {
                        assertEquals(phase, it.phase, it.error)
                    }
            },
        ) { "Expected $phase; actual ${state(id)}" }
}

private class AuthoringPending : PluginOAuthPendingStoreV1 {
    @Volatile var busy: String? = null

    override suspend fun hasPending(pluginId: String) = busy == pluginId

    override suspend fun removePlugin(pluginId: String) {
        if (busy == pluginId) busy = null
    }

    override suspend fun save(pending: PluginOAuthPendingV1) = Unit

    override suspend fun load(flowId: String): PluginOAuthPendingV1? = null

    override suspend fun consume(pending: PluginOAuthPendingV1) = true
}

private object AuthoringGuest : PlatformSpec {
    override val platformId = "BuiltIn"
    override val metadata = PlatformMetadata(platformId, UiIcon.World.asType())
    override val isDefaultGuest = true
    override val timelineSpecs = persistentListOf<TimelineSpec<out TimelineSpec.Data>>()

    override fun deepLinks(accountKey: MicroBlogKey) = persistentListOf<PlatformDeepLink<*>>()

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = error("unused")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = error("unused")
}

private class AuthoringAccounts : AccountService {
    override suspend fun deleteAccounts(
        platformId: String,
        ownsCredential: (String) -> Boolean,
        beforeDelete: suspend () -> Unit,
    ) = beforeDelete()

    @Volatile var invalidations = 0

    override suspend fun invalidatePlatform(platformId: String) {
        invalidations++
    }

    override fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource> = error("unused")

    override fun allAccountServicesFlow(): Flow<List<AccountMicroblogDataSource>> = error("unused")

    override suspend fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ) = Unit

    override fun <T : Any> credentialFlow(
        accountKey: MicroBlogKey,
        serializer: KSerializer<T>,
    ): Flow<T> = error("unused")

    override fun <T : Any> updateCredential(
        accountKey: MicroBlogKey,
        credential: T,
        serializer: KSerializer<T>,
    ): Job = error("unused")
}
