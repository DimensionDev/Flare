package dev.dimension.flare.feature.plugin.installer

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class FppArchiveReaderTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var packagePath: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("fpp-archive-test").toOkioPath()
        packagePath = root / "test.fpp"
    }

    @AfterTest
    fun tearDown() {
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun readsValidPackage() {
        TestFppFactory.write(packagePath)

        val archive = FppArchiveReader(fileSystem).read(packagePath)

        assertEquals(TestFppFactory.validEntries().map(Pair<String, ByteArray>::first).toSet(), archive.files.keys)
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        listOf("../escape", "/absolute").forEach { invalidPath ->
            TestFppFactory.write(packagePath, TestFppFactory.validEntries() + (invalidPath to byteArrayOf(1)))

            assertFails { FppArchiveReader(fileSystem).read(packagePath) }
        }
    }

    @Test
    fun rejectsDuplicateAndCaseConflictingPaths() {
        TestFppFactory.write(
            packagePath,
            TestFppFactory.validEntries() + ("second.js" to "duplicate".encodeToByteArray()),
        )
        TestFppFactory.replaceAscii(packagePath, "second.js", "plugin.js")
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }

        TestFppFactory.write(
            packagePath,
            TestFppFactory.validEntries() + ("Assets/Icon.png" to TestFppFactory.validPng),
        )
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }
    }

    @Test
    fun rejectsEncryptionUnsupportedCompressionZip64AndSymlink() {
        TestFppFactory.write(packagePath)
        TestFppFactory.patchLittleEndian(packagePath, LOCAL_HEADER, 6, 1, 2)
        TestFppFactory.patchLittleEndian(packagePath, CENTRAL_HEADER, 8, 1, 2)
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }

        TestFppFactory.write(packagePath)
        TestFppFactory.patchLittleEndian(packagePath, LOCAL_HEADER, 8, 99, 2)
        TestFppFactory.patchLittleEndian(packagePath, CENTRAL_HEADER, 10, 99, 2)
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }

        TestFppFactory.write(packagePath)
        TestFppFactory.patchLittleEndian(packagePath, EOCD_HEADER, 16, 0xffff_ffffL, 4)
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }

        TestFppFactory.write(packagePath)
        TestFppFactory.patchLittleEndian(packagePath, CENTRAL_HEADER, 4, 0x0314, 2)
        TestFppFactory.patchLittleEndian(packagePath, CENTRAL_HEADER, 38, 0xa000_0000L, 4)
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }

        TestFppFactory.write(packagePath)
        TestFppFactory.patchLittleEndian(packagePath, LOCAL_HEADER, 6, 0x20, 2)
        TestFppFactory.patchLittleEndian(packagePath, CENTRAL_HEADER, 8, 0x20, 2)
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }

        TestFppFactory.write(packagePath, stored = true)
        TestFppFactory.patchLittleEndian(packagePath, LOCAL_HEADER, 6, 0x02, 2)
        TestFppFactory.patchLittleEndian(packagePath, CENTRAL_HEADER, 8, 0x02, 2)
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }
    }

    @Test
    fun rejectsDeclaredBombAndTooManyEntries() {
        TestFppFactory.write(packagePath)
        TestFppFactory.patchLittleEndian(packagePath, LOCAL_HEADER, 22, 60L * 1024 * 1024, 4)
        TestFppFactory.patchLittleEndian(packagePath, CENTRAL_HEADER, 24, 60L * 1024 * 1024, 4)
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }

        TestFppFactory.write(packagePath)
        TestFppFactory.patchLittleEndian(packagePath, LOCAL_HEADER, 22, 1, 4)
        TestFppFactory.patchLittleEndian(packagePath, CENTRAL_HEADER, 24, 1, 4)
        assertFails { FppArchiveReader(fileSystem).read(packagePath) }

        val manyEntries =
            TestFppFactory.validEntries() +
                (0 until 254).map { index -> "locales/x-$index.json" to "{}".encodeToByteArray() }
        assertEquals(257, manyEntries.size)
        TestFppFactory.write(packagePath, manyEntries)
        val error = assertFails { FppArchiveReader(fileSystem).read(packagePath) }
        assertTrue(error.message.orEmpty().contains("entry count"))
    }

    private companion object {
        const val LOCAL_HEADER = 0x04034b50
        const val CENTRAL_HEADER = 0x02014b50
        const val EOCD_HEADER = 0x06054b50
    }
}
