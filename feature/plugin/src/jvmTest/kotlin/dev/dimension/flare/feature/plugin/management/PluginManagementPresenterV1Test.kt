package dev.dimension.flare.feature.plugin.management

import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PluginManagementPresenterV1Test {
    private lateinit var root: Path
    private lateinit var packagePath: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("plugin-management-test").toOkioPath()
        packagePath = root / "plugin.fpp"
        TestFppFactory.write(packagePath)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun installCancelAndUninstallExposeAccurateDesiredAndRunningState() =
        runTest {
            val firstSubsystem = subsystem()
            var firstPresenter: PluginManagementPresenterV1? = null
            try {
                val presenter = PluginManagementPresenterV1(firstSubsystem, backgroundScope)
                firstPresenter = presenter
                presenter.inspect(packagePath.toString())
                assertNotNull(presenter.state.value.pendingInstall)
                assertFalse(presenter.state.value.busy)

                presenter.cancelInstall()
                assertNull(presenter.state.value.pendingInstall)

                presenter.inspect(packagePath.toString())
                presenter.confirmInstall()

                val desired =
                    presenter.state.value.plugins
                        .single()
                assertTrue(FileSystem.SYSTEM.exists(desired.iconPath.toPath()))
                assertFalse(desired.running)
                assertTrue(desired.pendingRestart)
                assertTrue(presenter.state.value.requiresRestart)
            } finally {
                firstPresenter?.close()
                firstSubsystem.runtimePool.close()
            }

            val restartedSubsystem = subsystem()
            var restartedPresenter: PluginManagementPresenterV1? = null
            try {
                val presenter = PluginManagementPresenterV1(restartedSubsystem, backgroundScope)
                restartedPresenter = presenter
                val running =
                    presenter.state.value.plugins
                        .single()
                assertTrue(running.running)
                assertFalse(running.pendingRestart)

                presenter.uninstall(running.pluginId)
                assertTrue(
                    presenter.state.value.plugins
                        .isEmpty(),
                )
                assertTrue(presenter.state.value.requiresRestart)
            } finally {
                restartedPresenter?.close()
                restartedSubsystem.runtimePool.close()
            }
        }

    @Test
    fun failedInspectionCannotLeaveAStalePreviewConfirmable() =
        runTest {
            val subsystem = subsystem()
            var presenter: PluginManagementPresenterV1? = null
            try {
                val activePresenter = PluginManagementPresenterV1(subsystem, backgroundScope)
                presenter = activePresenter
                activePresenter.inspect(packagePath.toString())
                assertNotNull(activePresenter.state.value.pendingInstall)

                assertFails { activePresenter.inspect((root / "missing.fpp").toString()) }
                assertNull(activePresenter.state.value.pendingInstall)
                assertNotNull(activePresenter.state.value.error)
                assertFails { activePresenter.confirmInstall() }
                assertTrue(
                    subsystem.stateStore.desired.value.plugins
                        .isEmpty(),
                )
            } finally {
                presenter?.close()
                subsystem.runtimePool.close()
            }
        }

    @Test
    fun closeStopsThePresenterFromObservingGlobalPluginState() =
        runTest {
            val subsystem = subsystem()
            val presenter = PluginManagementPresenterV1(subsystem, backgroundScope)
            try {
                presenter.inspect(packagePath.toString())
                presenter.confirmInstall()
                val installed =
                    presenter.state.value.plugins
                        .single()
                assertTrue(installed.enabled)

                presenter.close()
                subsystem.stateStore.setEnabled(installed.pluginId, false)
                runCurrent()

                assertTrue(
                    presenter.state.value.plugins
                        .single()
                        .enabled,
                )
            } finally {
                presenter.close()
                subsystem.runtimePool.close()
            }
        }

    private fun subsystem(): PluginSubsystemV1 =
        PluginSubsystemV1(
            appFiles =
                object : AppFileStore {
                    override val fileSystem: FileSystem = FileSystem.SYSTEM

                    override fun directory(name: String): Path = root / name
                },
            pendingStore = MemoryPendingStore(),
        )
}

private class MemoryPendingStore : PluginOAuthPendingStoreV1 {
    override suspend fun save(pending: PluginOAuthPendingV1) = Unit

    override suspend fun load(flowId: String): PluginOAuthPendingV1? = null

    override suspend fun consume(pending: PluginOAuthPendingV1): Boolean = false
}
