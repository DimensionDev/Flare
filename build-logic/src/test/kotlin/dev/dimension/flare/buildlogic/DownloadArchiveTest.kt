package dev.dimension.flare.buildlogic

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadsBinaryArchiveAndReusesItOffline() {
        val source = temporaryFolder.newFile("source.zip")
        val bytes = byteArrayOf(0, 1, 127, -1)
        source.writeBytes(bytes)
        val cache = temporaryFolder.newFolder("cache")
        val url = source.toURI().toString()
        val downloaded = downloadArchive(url, cache, offline = false)
        assertContentEquals(bytes, downloaded.readBytes())
        source.delete()
        assertEquals(downloaded, downloadArchive(url, cache, offline = true))
    }

    @Test
    fun separatesArchivesWithTheSameFilenameByUrl() {
        val first = File(temporaryFolder.newFolder("first"), "archive.zip").apply { writeText("first") }
        val second = File(temporaryFolder.newFolder("second"), "archive.zip").apply { writeText("second") }
        val cache = temporaryFolder.newFolder("cache")
        assertEquals("first", downloadArchive(first.toURI().toString(), cache, offline = false).readText())
        assertEquals("second", downloadArchive(second.toURI().toString(), cache, offline = false).readText())
    }

    @Test
    fun failedDownloadLeavesNoArchiveAndCanBeRetried() {
        val source = File(temporaryFolder.root, "missing.zip")
        val cache = temporaryFolder.newFolder("cache")
        val url = source.toURI().toString()
        assertFailsWith<IOException> { downloadArchive(url, cache, offline = false) }
        assertTrue(cache.walkTopDown().none { it.isFile })
        source.writeText("retry")
        assertEquals("retry", downloadArchive(url, cache, offline = false).readText())
    }

    @Test
    fun offlineGenerationRejectsUncachedArchive() {
        val source = temporaryFolder.newFile("source.zip")
        val cache = temporaryFolder.newFolder("cache")
        assertFailsWith<IllegalStateException> { downloadArchive(source.toURI().toString(), cache, offline = true) }
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }
}
