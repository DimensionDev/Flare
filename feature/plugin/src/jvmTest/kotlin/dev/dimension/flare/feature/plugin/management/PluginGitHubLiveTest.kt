package dev.dimension.flare.feature.plugin.management

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.adapter.PluginDataSourceV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
import dev.dimension.flare.ui.model.contentPostOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assume.assumeTrue
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Opt-in, real HTTP, persisted data, and a fresh JVM for each phase; never runs on ordinary CI. */
class PluginGitHubLiveTest {
    @Test
    fun verifyRealRepositoryLifecycle() =
        runBlocking<Unit> {
            val phase = System.getProperty("flare.plugin.live.phase")
            assumeTrue("Set -Pflare.plugin.live.phase to run live GitHub verification", phase != null)
            withTimeout(120_000) {
                val root = requireNotNull(System.getProperty("flare.plugin.live.root")).toPath()
                val repository = requireNotNull(System.getProperty("flare.plugin.live.repository"))
                val fileSystem = FileSystem.SYSTEM
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
                GlobalContext.startKoin(KoinApplication.init().apply { koin.declare(ProxyRepository(LiveProxyStore())) })
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
                val presenter = PluginManagementPresenterV1(subsystem, scope)
                try {
                    when (phase) {
                        "install" -> {
                            assertTrue(
                                subsystem.stateStore.desired.value.plugins
                                    .isEmpty(),
                                "Use an empty, isolated live-test root",
                            )
                            presenter.loadGitHubRepository(repository)
                            assertTrue(assertNotNull(presenter.state.value.githubCatalog).versions.any { it.tag == "v1.0.0" })
                            presenter.inspectGitHub("v1.0.0")
                            assertEquals("1.0.0", assertNotNull(presenter.state.value.pendingInstall).version)
                            assertTrue(
                                subsystem.stateStore.desired.value.plugins
                                    .isEmpty(),
                            )
                            presenter.confirmInstall()
                            assertEquals(
                                "1.0.0",
                                subsystem.stateStore.desired.value.plugins.values
                                    .single()
                                    .version,
                            )
                            assertTrue(
                                subsystem.stateStore.running.plugins
                                    .isEmpty(),
                            )
                            assertTrue(presenter.state.value.requiresRestart)
                        }

                        "restart-v1" -> {
                            assertRunningVersion(subsystem, "1.0.0")
                            assertFalse(presenter.state.value.requiresRestart)
                        }

                        "update" -> {
                            assertRunningVersion(subsystem, "1.0.0")
                            presenter.checkUpdates()
                            assertEquals(
                                "1.1.0",
                                assertNotNull(
                                    presenter.state.value.plugins
                                        .single()
                                        .availableUpdate,
                                ).version,
                            )
                            assertNull(presenter.state.value.pendingInstall)
                            assertEquals(
                                "1.0.0",
                                subsystem.stateStore.desired.value.plugins.values
                                    .single()
                                    .version,
                            )
                            presenter.inspectGitHubUpdate(PLUGIN_ID)
                            assertEquals("1.1.0", assertNotNull(presenter.state.value.pendingInstall).version)
                            assertEquals(
                                "1.0.0",
                                subsystem.stateStore.desired.value.plugins.values
                                    .single()
                                    .version,
                            )
                            presenter.cancelInstall()
                            assertEquals(
                                "1.0.0",
                                subsystem.stateStore.desired.value.plugins.values
                                    .single()
                                    .version,
                            )
                            presenter.inspectGitHubUpdate(PLUGIN_ID)
                            presenter.confirmInstall()
                            assertEquals(
                                "1.1.0",
                                subsystem.stateStore.desired.value.plugins.values
                                    .single()
                                    .version,
                            )
                            assertTrue(presenter.state.value.requiresRestart)
                            assertRunningVersion(subsystem, "1.0.0")
                        }

                        "restart-v2" -> {
                            assertRunningVersion(subsystem, "1.1.0")
                            assertFalse(presenter.state.value.requiresRestart)
                            presenter.checkUpdates()
                            assertFalse(presenter.state.value.busy)
                            assertNull(presenter.state.value.error)
                            assertNull(
                                presenter.state.value.plugins
                                    .single()
                                    .availableUpdate,
                            )
                        }

                        else -> {
                            error("Unknown live verification phase: $phase")
                        }
                    }
                    val installed =
                        subsystem.stateStore.desired.value.plugins.values
                            .single()
                    val source = installed.source as PluginInstallSourceV1.GitHub
                    val running =
                        subsystem.stateStore.running.plugins[PLUGIN_ID]
                            ?.installed
                            ?.version ?: "none"
                    val evidence =
                        listOf(
                            "phase=$phase",
                            "process=${ProcessHandle.current().pid()}",
                            "desired=${installed.version}",
                            "running=$running",
                            "restart=${subsystem.stateStore.requiresRestart}",
                            "tag=${source.tag}",
                            "commit=${source.commitSha}",
                            "repository=${source.repository.url}",
                        ).joinToString(" ")
                    println("LIVE_GITHUB_VERIFIED $evidence")
                    fileSystem.write(root / "$phase.txt") { writeUtf8(evidence + "\n") }
                } finally {
                    presenter.close()
                    scope.cancel()
                    subsystem.runtimePool.close()
                    subsystem.github.close()
                    stopKoin()
                }
            }
        }

    private suspend fun assertRunningVersion(
        subsystem: PluginSubsystemV1,
        expected: String,
    ) {
        val plugin =
            subsystem.stateStore.running.plugins
                .getValue(PLUGIN_ID)
        assertEquals(expected, plugin.installed.version)
        val timeline =
            PluginDataSourceV1
                .guest(
                    plugin = plugin,
                    runtimePool = subsystem.runtimePool,
                    origin = "https://example.com",
                    locale = "en",
                    timelineSpecs = emptyMap(),
                    dynamicTimelineSpec = null,
                ).timeline("home")
                .load(20, PagingRequest.Refresh)
        assertEquals(
            "GitHub E2E active version $expected",
            timeline.data
                .single()
                .contentPostOrNull()
                ?.content
                ?.original
                ?.raw,
        )
    }
}

private class LiveProxyStore : DataStore<ProxySettings> {
    override val data = MutableStateFlow(ProxySettings())

    override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
        transform(data.value).also { data.value = it }
}

private const val PLUGIN_ID = "dev.dimension.flare.github.e2e"
