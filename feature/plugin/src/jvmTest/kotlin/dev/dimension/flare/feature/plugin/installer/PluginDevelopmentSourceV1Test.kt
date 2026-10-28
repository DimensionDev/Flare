package dev.dimension.flare.feature.plugin.installer

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class PluginDevelopmentSourceV1Test {
    private lateinit var root: Path
    private val files = FileSystem.SYSTEM

    @BeforeTest
    fun setup() {
        root = Files.createTempDirectory("plugin-development-source").toOkioPath()
        TestFppFactory.validEntries().forEach { (name, bytes) ->
            val path = root / name
            files.createDirectories(path.parent!!)
            files.write(path) { write(bytes) }
        }
    }

    @AfterTest
    fun teardown() {
        files.deleteRecursively(root)
    }

    @Test
    fun hashesOnlyPluginInputsAndKeepsAnImmutableSnapshot() {
        val first = PluginDevelopmentSourceV1.read(files, root)
        files.write(root / "package.json") { writeUtf8("ignored build metadata") }
        assertEquals(first.hash, PluginDevelopmentSourceV1.read(files, root).hash)
        files.write(root / "plugin.js") { writeUtf8("changed") }
        assertNotEquals(first.hash, PluginDevelopmentSourceV1.read(files, root).hash)
        assertEquals(
            TestFppFactory.validScript,
            first.archive.files
                .getValue("plugin.js")
                .decodeToString(),
        )
        files.createDirectories(root / "locales")
        val scriptHash = PluginDevelopmentSourceV1.read(files, root).hash
        files.write(root / "locales/en.json") { writeUtf8("{}") }
        assertNotEquals(scriptHash, PluginDevelopmentSourceV1.read(files, root).hash)
    }

    @Test
    fun rejectsMissingFilesAndSymlinks() {
        files.delete(root / "plugin.js")
        assertFails { PluginDevelopmentSourceV1.read(files, root) }
        files.write(root / "elsewhere.js") { writeUtf8(TestFppFactory.validScript) }
        files.createSymlink(root / "plugin.js", root / "elsewhere.js")
        assertFails { PluginDevelopmentSourceV1.read(files, root) }
    }

    @Test
    fun rejectsOversizedInputsBeforeReadingThem() {
        java.io.RandomAccessFile((root / "manifest.json").toString(), "rw").use { it.setLength(16 * 1024 * 1024) }
        assertFails { PluginDevelopmentSourceV1.read(files, root) }
    }
}
