package dev.dimension.flare.feature.plugin.installer

import dev.dimension.flare.feature.plugin.github.GitHubPluginRepositoryV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginGitHubInstallTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var store: PluginStateStore
    private lateinit var installer: PluginInstaller

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("plugin-github-install").toOkioPath()
        store = PluginStateStore.open(fileSystem, root / "store")
        installer = PluginInstaller(fileSystem, store)
    }

    @AfterTest
    fun tearDown() {
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun sourceArchiveRootIsAdaptedBeforePackageValidation() =
        runTest {
            val archive = root / "source.zip"
            TestFppFactory.write(
                archive,
                TestFppFactory.validEntries().map { (name, bytes) -> "repo-commit/$name" to bytes } +
                    ("repo-commit/说明.md" to "Plugin development instructions".encodeToByteArray()),
            )
            val source = source()
            val preview = installer.inspect(archive, source)

            assertEquals("1.0.0", preview.version)
            assertTrue(
                store.desired.value.plugins
                    .isEmpty(),
            )
            val installed = installer.commit(preview, confirmed = true)
            assertEquals(source, installed.source)
            assertTrue(store.running.plugins.isEmpty())
            assertTrue(store.requiresRestart)

            val restarted = PluginStateStore.open(fileSystem, root / "store")
            assertEquals(
                installed,
                restarted.running.plugins
                    .getValue(installed.pluginId)
                    .installed,
            )
            assertFalse(restarted.requiresRestart)
            val bytes = fileSystem.read(restarted.paths.packagePath(installed.packageHash)) { readByteArray() }
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                val files = mutableMapOf<String, ByteArray>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    files[entry.name] = zip.readBytes()
                }
                assertEquals(setOf("manifest.json", "plugin.js", "assets/icon.png"), files.keys)
                assertContentEquals(TestFppFactory.validScript.encodeToByteArray(), files.getValue("plugin.js"))
            }
        }

    @Test
    fun gitArchiveDirectoryEntriesAndCommitCommentsAreAccepted() =
        runTest {
            val archive = root / "git-archive.zip"
            ZipOutputStream(
                Files.newOutputStream(
                    java.nio.file.Path
                        .of(archive.toString()),
                ),
            ).use { zip ->
                zip.setComment("a".repeat(40))
                val entries =
                    listOf("repo-root/" to byteArrayOf(), "repo-root/assets/" to byteArrayOf()) +
                        TestFppFactory.validEntries().map { (name, bytes) -> "repo-root/$name" to bytes }
                entries.forEach { (name, bytes) ->
                    val entry = ZipEntry(name)
                    if (name.endsWith('/')) {
                        entry.method = ZipEntry.STORED
                        entry.size = 0
                        entry.compressedSize = 0
                        entry.crc = 0
                    }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            val preview = installer.inspect(archive, source())
            assertEquals("1.0.0", preview.version)
        }

    @Test
    fun archiveRootAndCompressionDoNotChangeThePackageIdentity() =
        runTest {
            val firstPath = root / "first.zip"
            val secondPath = root / "second.zip"
            TestFppFactory.write(firstPath, TestFppFactory.validEntries().map { (name, bytes) -> "repo-first/$name" to bytes })
            TestFppFactory.write(
                secondPath,
                TestFppFactory.validEntries().reversed().map { (name, bytes) ->
                    "repo-second/$name" to bytes
                },
                stored = true,
            )
            val first = installer.inspect(firstPath, source())
            installer.commit(first, true)
            val changedSource = source(sha = "b".repeat(40))
            val second = installer.inspect(secondPath, changedSource)

            assertEquals(first.packageHash, second.packageHash)
            assertTrue(second.requiresConfirmation)
            assertTrue(second.warnings.any { it.type == PluginInstallWarningTypeV1.TagMoved })
            assertFalse(second.warnings.any { it.type == PluginInstallWarningTypeV1.SameVersionDifferentHash })
            installer.discard(second)
            assertEquals(
                source(),
                store.desired.value.plugins.values
                    .single()
                    .source,
            )
        }

    @Test
    fun mismatchedVersionsAndPluginIdentityCannotReplaceAnInstalledPlugin() =
        runTest {
            val archive = root / "source.zip"
            TestFppFactory.write(archive, TestFppFactory.validEntries().map { (name, bytes) -> "repo-root/$name" to bytes })
            val installed = installer.commit(installer.inspect(archive, source()), true)
            assertFails { installer.inspect(archive, source(tag = "v2.0.0")) }
            assertFails { installer.inspect(archive, source(), expectedPluginId = "different.plugin") }
            assertEquals(
                installed,
                store.desired.value.plugins.values
                    .single(),
            )
            assertFalse(fileSystem.exists(store.paths.incoming))
        }

    @Test
    fun repositoryArchivesRejectTraversalMultipleRootsAndNestedPlugins() =
        runTest {
            val archive = root / "source.zip"
            val entries = TestFppFactory.validEntries().map { (name, bytes) -> "repo-root/$name" to bytes }
            listOf(
                entries + ("repo-root/../ignored.txt" to byteArrayOf(1)),
                entries + ("another-root/README.md" to byteArrayOf(1)),
                TestFppFactory.validEntries().map { (name, bytes) -> "repo-root/subdirectory/$name" to bytes },
            ).forEach { invalid ->
                TestFppFactory.write(archive, invalid)
                assertFails { installer.inspect(archive, source()) }
                assertTrue(
                    store.desired.value.plugins
                        .isEmpty(),
                )
            }
        }

    @Test
    fun ignoredRepositoryFilesAreStillSubjectToUncompressedSizeLimits() =
        runTest {
            val archive = root / "source.zip"
            TestFppFactory.write(
                archive,
                TestFppFactory.validEntries().map { (name, bytes) -> "repo-root/$name" to bytes } +
                    ("repo-root/ignored.txt" to ByteArray(FppLimits.MAX_UNCOMPRESSED_BYTES.toInt())),
            )
            assertFails { installer.inspect(archive, source()) }
            assertTrue(
                store.desired.value.plugins
                    .isEmpty(),
            )
        }

    @Test
    fun sourceSwitchesRequireConfirmationAndCannotHappenThroughTheUpdateEntryPoint() =
        runTest {
            val local = root / "local.fpp"
            TestFppFactory.write(local)
            val installed = installer.commit(installer.inspect(local), true)
            val archive = root / "source.zip"
            TestFppFactory.write(archive, TestFppFactory.validEntries().map { (name, bytes) -> "repo-root/$name" to bytes })
            assertFails { installer.inspect(archive, source(), expectedPluginId = installed.pluginId) }
            val preview = installer.inspect(archive, source())
            assertTrue(preview.warnings.any { it.type == PluginInstallWarningTypeV1.SourceChanged })
            assertFails { installer.commit(preview, confirmed = false) }
            assertEquals(
                installed,
                store.desired.value.plugins.values
                    .single(),
            )
            installer.commit(preview, confirmed = true)
            assertEquals(
                source(),
                store.desired.value.plugins.values
                    .single()
                    .source,
            )
        }

    private fun source(
        tag: String = "v1.0.0",
        sha: String = "a".repeat(40),
    ): PluginInstallSourceV1.GitHub = PluginInstallSourceV1.GitHub(GitHubPluginRepositoryV1(1, "owner", "repo"), tag, sha)
}
