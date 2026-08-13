package dev.dimension.flare.feature.plugin.installer

import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import okio.Source
import okio.Timeout
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginInstallerTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var input: Path
    private lateinit var stateStore: PluginStateStore
    private lateinit var installer: PluginInstaller

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("plugin-installer-test").toOkioPath()
        input = root / "input.fpp"
        stateStore = PluginStateStore.open(fileSystem, root / "social-plugins-v2")
        installer = PluginInstaller(fileSystem, stateStore)
    }

    @AfterTest
    fun tearDown() {
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun installChangesDesiredOnlyUntilStoreIsReopened() =
        runBlocking {
            TestFppFactory.write(input)

            val preview = installer.inspect(input)
            assertTrue(preview.requiresConfirmation)
            assertTrue(preview.warnings.any { it.type == PluginInstallWarningTypeV1.UnverifiedLocal })
            assertFails { installer.commit(preview, confirmed = false) }

            val installed = installer.commit(preview, confirmed = true)
            assertEquals(
                installed,
                stateStore.desired.value.plugins
                    .getValue(installed.pluginId),
            )
            assertTrue(stateStore.running.plugins.isEmpty())
            assertTrue(stateStore.requiresRestart)

            val restarted = PluginStateStore.open(fileSystem, root / "social-plugins-v2")
            assertNotNull(restarted.running.plugins[installed.pluginId])
            assertFalse(restarted.requiresRestart)

            restarted.setEnabled(installed.pluginId, enabled = false)
            assertNotNull(restarted.running.plugins[installed.pluginId])
            assertTrue(restarted.requiresRestart)
            val disabledAfterRestart = PluginStateStore.open(fileSystem, root / "social-plugins-v2")
            assertTrue(disabledAfterRestart.running.plugins.isEmpty())
            assertTrue(fileSystem.exists(disabledAfterRestart.paths.packagePath(installed.packageHash)))
        }

    @Test
    fun validatesRegistrationCatalogAndIcon() =
        runBlocking {
            TestFppFactory.write(
                input,
                TestFppFactory.validEntries(script = "definePlugin({ detector: { detect() {} } });"),
            )
            assertFails { installer.inspect(input) }

            val localized =
                TestFppFactory.validManifest.replace(
                    "\"name\": \"Test plugin\"",
                    "\"name\": { \"key\": \"plugin.name\", \"fallback\": \"Test plugin\" }",
                )
            TestFppFactory.write(input, TestFppFactory.validEntries(manifest = localized))
            assertFails { installer.inspect(input) }

            TestFppFactory.write(input, TestFppFactory.validEntries(icon = byteArrayOf(1, 2, 3)))
            assertFails { installer.inspect(input) }

            TestFppFactory.write(
                input,
                TestFppFactory.validEntries(script = TestFppFactory.validScript + "\nasync function later() { return import('x'); }"),
            )
            assertFails { installer.inspect(input) }
            Unit
        }

    @Test
    fun updateWarningsIncludePermissionDowngradeAndChangedHash() =
        runBlocking {
            TestFppFactory.write(input)
            installer.inspect(input).also { installer.commit(it, confirmed = true) }

            val changedManifest =
                TestFppFactory.validManifest
                    .replace("\"version\": \"1.0.0\"", "\"version\": \"0.9.0\"")
                    .replace(
                        "\"name\": \"Test plugin\",",
                        "\"name\": \"Test plugin\",\n  \"permissions\": { \"authOrigins\": [\"https://auth.example\"] },",
                    )
            TestFppFactory.write(input, TestFppFactory.validEntries(manifest = changedManifest))
            val downgrade = installer.inspect(input)
            assertTrue(downgrade.warnings.any { it.type == PluginInstallWarningTypeV1.Downgrade })
            assertTrue(downgrade.warnings.any { it.type == PluginInstallWarningTypeV1.AddedPermission })
            installer.discard(downgrade)

            TestFppFactory.write(
                input,
                TestFppFactory.validEntries(script = TestFppFactory.validScript + "\n// changed"),
            )
            val changedHash = installer.inspect(input)
            assertTrue(changedHash.warnings.any { it.type == PluginInstallWarningTypeV1.SameVersionDifferentHash })
        }

    @Test
    fun rejectsCompressedInputOverLimitWhileStreaming() =
        runBlocking {
            val source = RepeatingSource(FppLimits.MAX_PACKAGE_BYTES + 1)

            assertFails { installer.inspect(source) }
            assertFalse(fileSystem.exists(stateStore.paths.incoming))
        }

    @Test
    fun explicitlyRebuildsCorruptIndexFromValidatedPackages() =
        runBlocking {
            TestFppFactory.write(input)
            val installed = installer.inspect(input).let { installer.commit(it, confirmed = true) }
            fileSystem.write(stateStore.paths.index) { writeUtf8("not-json") }

            val corruptStore = PluginStateStore.open(fileSystem, root / "social-plugins-v2")
            assertFalse(corruptStore.desired.value.indexHealthy)
            val result = PluginInstaller(fileSystem, corruptStore).rebuildIndex()

            assertEquals(1, result.restored)
            assertEquals(0, result.skipped)
            assertEquals(
                installed.packageHash,
                corruptStore.desired.value.plugins
                    .getValue(installed.pluginId)
                    .packageHash,
            )
            assertTrue(corruptStore.running.plugins.isEmpty())
            assertTrue(corruptStore.requiresRestart)
            assertNotNull(
                PluginStateStore
                    .open(fileSystem, root / "social-plugins-v2")
                    .running.plugins[installed.pluginId],
            )
            Unit
        }
}

private class RepeatingSource(
    private var remaining: Long,
) : Source {
    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        if (remaining == 0L) return -1L
        val count = minOf(remaining, byteCount, 8_192L).toInt()
        sink.write(ByteArray(count))
        remaining -= count
        return count.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}
