package dev.dimension.flare.feature.plugin.lifecycle

import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import okio.Source
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginStateStoreTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var namespace: Path
    private lateinit var input: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("plugin-state-test").toOkioPath()
        namespace = root / "social-plugins-v2"
        input = root / "input.fpp"
    }

    @AfterTest
    fun tearDown() {
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun atomicIndexFailureKeepsPreviousDesiredState() =
        runBlocking {
            val installed = installValid(fileSystem)
            val failingFileSystem =
                object : ForwardingFileSystem(fileSystem) {
                    override fun atomicMove(
                        source: Path,
                        target: Path,
                    ) {
                        if (target.name == "index.json") throw IOException("synthetic atomic failure")
                        super.atomicMove(source, target)
                    }
                }
            val store = PluginStateStore.open(failingFileSystem, namespace)

            assertFails { store.setEnabled(installed.pluginId, enabled = false) }
            assertTrue(
                store.desired.value.plugins
                    .getValue(installed.pluginId)
                    .enabled,
            )
            assertTrue(
                PluginStateStore
                    .open(fileSystem, namespace)
                    .desired.value.plugins
                    .getValue(installed.pluginId)
                    .enabled,
            )
        }

    @Test
    fun corruptIndexDoesNotDeletePackagesAndDisablesCleanup() =
        runBlocking {
            val paths = PluginStoragePaths(namespace)
            val orphan = paths.packagePath("a".repeat(64))
            fileSystem.createDirectories(paths.packages)
            fileSystem.write(orphan) { writeUtf8("keep") }
            fileSystem.createDirectories(namespace)
            fileSystem.write(paths.index) { writeUtf8("{broken") }

            val store = PluginStateStore.open(fileSystem, namespace)

            assertFalse(store.desired.value.indexHealthy)
            assertFalse(store.running.indexHealthy)
            assertTrue(store.running.issues.any { it.code == "index.corrupt" })
            assertFails { store.cleanup() }
            assertTrue(fileSystem.exists(orphan))
        }

    @Test
    fun oversizedIndexIsRejectedBeforeDecoding() {
        val paths = PluginStoragePaths(namespace)
        fileSystem.createDirectories(namespace)
        fileSystem.write(paths.index) { write(ByteArray(1024 * 1024 + 1)) }

        val store = PluginStateStore.open(fileSystem, namespace)

        assertFalse(store.desired.value.indexHealthy)
        assertFalse(store.requiresRestart)
    }

    @Test
    fun startupReadsIndexButDoesNotOpenPackage() =
        runBlocking {
            installValid(fileSystem)
            val opened = mutableListOf<Path>()
            val tracing =
                object : ForwardingFileSystem(fileSystem) {
                    override fun source(file: Path): Source {
                        opened += file
                        return super.source(file)
                    }
                }

            val restarted = PluginStateStore.open(tracing, namespace)

            assertEquals(listOf(namespace / "index.json"), opened)
            assertEquals(1, restarted.running.plugins.size)
        }

    @Test
    fun missingPackageKeepsDesiredRecordAndReportsIssue() =
        runBlocking {
            val installed = installValid(fileSystem)
            val paths = PluginStoragePaths(namespace)
            fileSystem.delete(paths.packagePath(installed.packageHash))

            val restarted = PluginStateStore.open(fileSystem, namespace)

            assertNotNull(restarted.desired.value.plugins[installed.pluginId])
            assertTrue(restarted.running.plugins.isEmpty())
            assertTrue(restarted.running.issues.any { it.pluginId == installed.pluginId })
        }

    @Test
    fun cleanupProtectsDesiredAndRunningReferences() =
        runBlocking {
            val installed = installValid(fileSystem)
            val store = PluginStateStore.open(fileSystem, namespace)
            val paths = PluginStoragePaths(namespace)
            val orphanHash = "b".repeat(64)
            val orphanPackage = paths.packagePath(orphanHash)
            val orphanIcon = paths.iconPath(orphanHash)
            fileSystem.write(orphanPackage) { writeUtf8("orphan") }
            fileSystem.write(orphanIcon) { writeUtf8("orphan") }
            fileSystem.write(paths.staging / "old.tmp") { writeUtf8("orphan") }

            val result = store.cleanup()

            assertEquals(1, result.packages)
            assertEquals(1, result.icons)
            assertEquals(1, result.stagingFiles)
            assertTrue(fileSystem.exists(paths.packagePath(installed.packageHash)))
            assertTrue(fileSystem.exists(paths.iconPath(installed.packageHash)))
            assertFalse(fileSystem.exists(orphanPackage))

            store.uninstall(installed.pluginId)
            assertTrue(fileSystem.exists(paths.packagePath(installed.packageHash)))
        }

    private suspend fun installValid(targetFileSystem: FileSystem): InstalledPluginV1 {
        TestFppFactory.write(input)
        val store = PluginStateStore.open(targetFileSystem, namespace)
        val installer = PluginInstaller(targetFileSystem, store)
        val preview = installer.inspect(input)
        return installer.commit(preview, confirmed = true)
    }
}
