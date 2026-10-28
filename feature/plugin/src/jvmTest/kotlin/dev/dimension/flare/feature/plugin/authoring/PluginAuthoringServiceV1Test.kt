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
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import okio.ByteString.Companion.decodeBase64
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
    private var failingIndexWrites = 0
    private var onIndexFailure: (() -> Unit)? = null
    private val fileSystem =
        object : ForwardingFileSystem(FileSystem.SYSTEM) {
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
    fun multiplePluginsApplyIndependentlyAndSurviveRestartAndCleanup() =
        runBlocking<Unit> {
            val a = service.create()
            val b = service.create()
            coroutineScope {
                val first = async { generate(a, "A") }
                val second = async { generate(b, "B") }
                first.await()
                second.await()
            }
            await(a.id, PluginAuthoringPhase.Applied)
            await(b.id, PluginAuthoringPhase.Applied)
            assertEquals(setOf(a.id, b.id), subsystem.running.value.plugins.keys)
            assertFalse(subsystem.stateStore.requiresRestart)
            subsystem.installer.cleanup()
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
            val plugin = service.create()
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val before =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val run = service.begin(plugin.id)
            assertTrue(service.write(plugin.id, run, sources(plugin) + ("plugin.js" to "invalid {{{")).startsWith("Validation failed"))
            service.finish(plugin.id, run, true)
            assertEquals(before, subsystem.running.value.plugins[plugin.id])
            assertEquals(before.installed, subsystem.stateStore.desired.value.plugins[plugin.id])
            assertEquals(PluginAuthoringPhase.Failed, state(plugin.id).phase)
            val reloaded = PluginAuthoringServiceV1(subsystem, { error("unused") }, { accounts }, { scope })
            val restored = reloaded.plugins.value.single { it.id == plugin.id }
            assertTrue(restored.running)
            assertEquals(PluginAuthoringPhase.Idle, restored.phase)
        }

    @Test
    fun approvalsArePerPluginAndCannotAuthorizeNewerCode() =
        runBlocking<Unit> {
            val a = service.create()
            val b = service.create()
            generate(a, media = true)
            generate(b, media = true)
            val first = await(a.id, PluginAuthoringPhase.AwaitingApproval)
            val second = await(b.id, PluginAuthoringPhase.AwaitingApproval)
            generate(a, "changed", media = true)
            val newer = await(a.id, PluginAuthoringPhase.AwaitingApproval)
            assertTrue(newer.revision > first.revision)
            assertFailsWith<IllegalStateException> { service.approve(a.id, first.revision) }
            service.approve(b.id, second.revision)
            await(b.id, PluginAuthoringPhase.Applied)
            assertFalse(a.id in subsystem.running.value.plugins)
            service.approve(a.id, newer.revision)
            await(a.id, PluginAuthoringPhase.Applied)
        }

    @Test
    fun reloadWaitsForWritesAndOnlyAppliesLatestRevision() =
        runBlocking<Unit> {
            val plugin = service.create()
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
            val plugin = service.create()
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
    fun disabledPluginIsNotReenabledByGeneration() =
        runBlocking<Unit> {
            val plugin = service.create()
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            service.setEnabled(plugin.id, false)
            generate(plugin, "disabled edit")
            assertFalse(state(plugin.id).enabled)
            assertFalse(plugin.id in subsystem.running.value.plugins)
            assertFalse(
                subsystem.stateStore.desired.value.plugins
                    .getValue(plugin.id)
                    .enabled,
            )
        }

    @Test
    fun deletedPluginRejectsLateWritesAndDoesNotCancelOtherPlugins() =
        runBlocking<Unit> {
            val a = service.create()
            val b = service.create()
            val run = coroutineScope { async { service.begin(a.id) }.await() }
            service.delete(a.id)
            assertFailsWith<IllegalArgumentException> { service.write(a.id, run, sources(a)) }
            service.finish(a.id, run, true)
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
            val plugin = service.create()
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            subsystem.installer.commit(preview, true)
            assertEquals(2, subsystem.stateStore.desired.value.plugins.size)
        }

    @Test
    fun rejectsEscapingPathsAndChangedIdentity() =
        runBlocking<Unit> {
            val plugin = service.create()
            val run = service.begin(plugin.id)
            assertFailsWith<IllegalArgumentException> { service.write(plugin.id, run, sources(plugin) + ("../token" to "no")) }
            val result = service.write(plugin.id, run, sources(plugin) + ("manifest.json" to TestFppFactory.validManifest))
            assertTrue(result.startsWith("Validation failed"))
            service.finish(plugin.id, run, true)
            assertTrue(
                subsystem.running.value.plugins
                    .isEmpty(),
            )
        }

    @Test
    fun bundledSdkMatchesCurrentContractAndRejectsUnknownPaths() =
        runBlocking<Unit> {
            assertTrue(service.documentation("flare-plugin.d.ts").contains("definePlugin"))
            assertFailsWith<IllegalArgumentException> { service.documentation("../../credentials") }
            assertFailsWith<IllegalArgumentException> { service.fetchDocumentation("https://localhost/private") }
            assertFailsWith<IllegalArgumentException> { service.fetchDocumentation("https://127.0.0.1/private") }
        }

    @Test
    fun stoppingAnApprovalPreventsItFromApplying() =
        runBlocking<Unit> {
            val plugin = service.create()
            generate(plugin, media = true)
            val revision = await(plugin.id, PluginAuthoringPhase.AwaitingApproval).revision
            service.stop(plugin.id)
            assertFailsWith<IllegalStateException> { service.approve(plugin.id, revision) }
            assertFalse(plugin.id in subsystem.running.value.plugins)
            assertEquals(PluginAuthoringPhase.Idle, state(plugin.id).phase)
        }

    @Test
    fun busyDisableKeepsRuntimeAndPersistedEnabledState() =
        runBlocking<Unit> {
            val plugin = service.create()
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val running =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val release = running.session.holdInteraction()
            assertFailsWith<IllegalStateException> { service.setEnabled(plugin.id, false) }
            assertTrue(state(plugin.id).enabled)
            assertTrue(
                subsystem.stateStore.desired.value.plugins
                    .getValue(plugin.id)
                    .enabled,
            )
            assertEquals(running, subsystem.running.value.plugins[plugin.id])
            generate(plugin, "after browser login")
            await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
            release()
            await(plugin.id, PluginAuthoringPhase.Applied)
        }

    @Test
    fun reenablingDuringGenerationDoesNotReplaceItsRun() =
        runBlocking<Unit> {
            val plugin = service.create()
            service.setEnabled(plugin.id, false)
            val run = service.begin(plugin.id)
            assertFailsWith<IllegalStateException> { service.begin(plugin.id) }
            service.setEnabled(plugin.id, true)
            assertTrue(service.write(plugin.id, run, sources(plugin)).startsWith("Validation passed"))
            service.finish(plugin.id, run, true)
            service.approve(plugin.id, await(plugin.id, PluginAuthoringPhase.AwaitingApproval).revision)
            await(plugin.id, PluginAuthoringPhase.Applied)
        }

    @Test
    fun damagedSourceDoesNotPreventOtherPluginsFromLoading() =
        runBlocking<Unit> {
            val damaged = service.create()
            val healthy = service.create()
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
    fun severalPluginsCanGenerateAtOnceWithoutAnApplicationQueue() =
        runBlocking<Unit> {
            val plugins = List(4) { service.create() }
            val runs = plugins.associate { it.id to service.begin(it.id) }
            assertTrue(service.plugins.value.all { it.phase == PluginAuthoringPhase.Generating })
            plugins.forEach { service.finish(it.id, runs.getValue(it.id), false) }
        }

    @Test
    fun failedIndexCommitRestoresWorkingVersionAndRestartIdentity() =
        runBlocking<Unit> {
            val plugin = service.create()
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
            assertFalse(subsystem.stateStore.requiresRestart)
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
    fun stopCancelsGeneratingTaskAndPreventsLateApplication() =
        runBlocking<Unit> {
            val plugin = service.create()
            val ready = CompletableDeferred<String>()
            val run =
                scope.async {
                    val token = service.begin(plugin.id)
                    service.write(plugin.id, token, sources(plugin))
                    ready.complete(token)
                    kotlinx.coroutines.awaitCancellation()
                }
            val token = ready.await()
            service.stop(plugin.id)
            run.join()
            assertTrue(run.isCancelled)
            service.finish(plugin.id, token, true)
            assertFalse(plugin.id in subsystem.running.value.plugins)
            assertEquals(PluginAuthoringPhase.Idle, state(plugin.id).phase)
        }

    @Test
    fun directoryDevelopmentBlocksDisableAndDeleteUntilStopped() =
        runBlocking<Unit> {
            val plugin = service.create()
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val installed =
                subsystem.stateStore.desired.value.plugins
                    .getValue(plugin.id)
            val development = startDevelopment(plugin)
            val override =
                subsystem.running.value.plugins
                    .getValue(plugin.id)

            assertFailsWith<IllegalStateException> { service.setEnabled(plugin.id, false) }
            assertFailsWith<IllegalStateException> { service.delete(plugin.id) }
            assertEquals(installed, subsystem.stateStore.desired.value.plugins[plugin.id])
            assertEquals(override, subsystem.running.value.plugins[plugin.id])
            assertTrue(state(plugin.id).enabled)

            development.stop()
            assertEquals(
                installed.packageHash,
                subsystem.running.value.plugins
                    .getValue(plugin.id)
                    .installed.packageHash,
            )
            service.setEnabled(plugin.id, false)
            service.delete(plugin.id)
            assertFalse(plugin.id in subsystem.running.value.plugins)
            assertFalse(plugin.id in subsystem.stateStore.desired.value.plugins)
            assertTrue(service.plugins.value.isEmpty())
        }

    @Test
    fun reenablingUnchangedSourcePreservesRevisionAndRestoresOnlyOneSession() =
        runBlocking<Unit> {
            val plugin = service.create()
            generate(plugin)
            val applied = await(plugin.id, PluginAuthoringPhase.Applied)
            service.setEnabled(plugin.id, false)
            val invalidations = accounts.invalidations
            service.setEnabled(plugin.id, true)
            val restored =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val validated = await(plugin.id, PluginAuthoringPhase.Applied)
            assertEquals(applied.revision, validated.revision)
            assertEquals(invalidations + 1, accounts.invalidations)
            assertEquals(
                restored,
                subsystem.running.value.plugins
                    .getValue(plugin.id),
            )
            assertTrue(validated.enabled && validated.running)
            val reloaded = PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { scope })
            assertEquals(
                validated.revision,
                reloaded.plugins.value
                    .single()
                    .revision,
            )
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun stoppingRevalidationKeepsRestoredSessionAndCancelsPendingApplication() =
        runBlocking<Unit> {
            val plugin = service.create()
            generate(plugin)
            val applied = await(plugin.id, PluginAuthoringPhase.Applied)
            val scheduler = TestCoroutineScheduler()
            val queuedScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
            try {
                val queued = PluginAuthoringServiceV1(subsystem, { registry }, { accounts }, { queuedScope })
                queued.setEnabled(plugin.id, false)
                queued.setEnabled(plugin.id, true)
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
                queued.stop(plugin.id)
                scheduler.runCurrent()
                val stopped = queued.plugins.value.single()
                assertEquals(PluginAuthoringPhase.Idle, stopped.phase)
                assertEquals(applied.revision, stopped.revision)
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
    fun stoppedApprovalDoesNotAuthorizeReenabledSourceAtTheSameRevision() =
        runBlocking<Unit> {
            val plugin = service.create()
            val run = service.begin(plugin.id)
            service.write(plugin.id, run, sources(plugin, media = true))
            service.finish(plugin.id, run, true)
            val review = await(plugin.id, PluginAuthoringPhase.AwaitingApproval)
            pending.busy = plugin.id
            service.approve(plugin.id, review.revision)
            await(plugin.id, PluginAuthoringPhase.WaitingForOperations)
            service.stop(plugin.id)
            pending.busy = null
            service.setEnabled(plugin.id, false)
            service.setEnabled(plugin.id, true)
            val reopened = await(plugin.id, PluginAuthoringPhase.AwaitingApproval)
            assertEquals(review.revision, reopened.revision)
            assertFalse(reopened.running)
            assertEquals(review.permissions, reopened.permissions)
            pending.busy = null
            service.approve(plugin.id, reopened.revision)
            await(plugin.id, PluginAuthoringPhase.Applied)
        }

    @Test
    fun directoryOverrideUpdatesAuthoredRunningStateWithoutChangingItsRecord() =
        runBlocking<Unit> {
            val plugin = service.create()
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
    fun reenablingAfterInvalidEditRestoresWorkingVersionAndRestartState() =
        runBlocking<Unit> {
            val plugin = service.create()
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val before =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            val run = service.begin(plugin.id)
            assertTrue(service.write(plugin.id, run, sources(plugin) + ("plugin.js" to "invalid {{{")).startsWith("Validation failed"))
            service.finish(plugin.id, run, true)
            service.setEnabled(plugin.id, false)
            service.setEnabled(plugin.id, true)
            await(plugin.id, PluginAuthoringPhase.Failed)
            assertTrue(state(plugin.id).enabled)
            assertTrue(state(plugin.id).running)
            assertEquals(
                before.installed.packageHash,
                subsystem.running.value.plugins
                    .getValue(plugin.id)
                    .installed.packageHash,
            )
            assertTrue(
                subsystem.stateStore.desired.value.plugins
                    .getValue(plugin.id)
                    .enabled,
            )
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
    fun reenablingKeepsWorkingVersionWhileAdditionalPermissionAwaitsApproval() =
        runBlocking<Unit> {
            val plugin = service.create()
            generate(plugin)
            await(plugin.id, PluginAuthoringPhase.Applied)
            val before =
                subsystem.running.value.plugins
                    .getValue(plugin.id)
            service.setEnabled(plugin.id, false)
            generate(plugin, "needs media", media = true)
            await(plugin.id, PluginAuthoringPhase.Idle)
            service.setEnabled(plugin.id, true)
            val review = await(plugin.id, PluginAuthoringPhase.AwaitingApproval)
            assertTrue(review.running)
            assertEquals(
                before.installed.packageHash,
                subsystem.running.value.plugins
                    .getValue(plugin.id)
                    .installed.packageHash,
            )
            assertFalse(
                dev.dimension.flare.ui.model.PluginMediaPermissions
                    .isAllowed(plugin.id),
            )
            service.approve(plugin.id, review.revision)
            await(plugin.id, PluginAuthoringPhase.Applied)
            assertTrue(
                dev.dimension.flare.ui.model.PluginMediaPermissions
                    .isAllowed(plugin.id),
            )
        }

    @Test
    fun failedCommitDoesNotAdmitWritesToTheReplacementBeforeRollback() =
        runBlocking<Unit> {
            val plugin = service.create()
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
                assertFalse(subsystem.stateStore.requiresRestart)
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
        fileSystem.write(directory / "assets/icon.png") { write(requireNotNull(BundledPluginSdk.icon.decodeBase64())) }
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
        val firstInstall = plugin.id !in subsystem.stateStore.desired.value.plugins
        val run = service.begin(plugin.id)
        assertTrue(service.write(plugin.id, run, sources(plugin, name, media)).startsWith("Validation passed"))
        service.finish(plugin.id, run, true)
        if (firstInstall && !media) {
            val approval = await(plugin.id, PluginAuthoringPhase.AwaitingApproval)
            service.approve(plugin.id, approval.revision)
        }
    }

    private fun sources(
        plugin: AuthoredPlugin,
        name: String = "Example",
        media: Boolean = false,
    ) = mapOf(
        "manifest.json" to
            TestFppFactory.validManifest
                .replace("dev.dimension.flare.test.plugin", plugin.id)
                .replace("TestPlugin", PluginAuthoringServiceV1.platformId(plugin.id))
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
    override val metadata = PlatformMetadata(platformId, UiIcon.World)
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
