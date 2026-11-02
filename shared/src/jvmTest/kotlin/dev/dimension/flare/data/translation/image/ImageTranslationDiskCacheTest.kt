package dev.dimension.flare.data.translation.image

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageTranslationDiskCacheTest {
    private val fs = FileSystem.SYSTEM
    private val info = TranslationImageInfo(3, 1, "image/png")

    @Test
    fun ttlSlidesOnReadAndOldTicketsCannotRepopulateClearedCache() =
        runTest {
            val root = Files.createTempDirectory("image-translation").toString().toPath()
            try {
                var now = 0L
                val cache = ImageTranslationDiskCache(fs, root / "cache", ttlMillis = 100, now = { now })
                val image = root / "source"
                fs.write(image) { writeUtf8("complete-image") }
                val ticket = cache.ticket("owner")
                cache.put(ticket, "one", image, info)
                now = 90
                assertNotNull(cache.read("one", root / "read"))
                now = 180
                assertNotNull(cache.read("one", root / "read"))
                now = 281
                assertNull(cache.read("one", root / "read"))
                cache.clear()
                cache.put(ticket, "late", image, info)
                assertNull(cache.read("late", root / "read"))
            } finally {
                fs.deleteRecursively(root)
            }
        }

    @Test
    fun capacityIsGlobalLruAndOwnerRemovalRejectsLateWrites() =
        runTest {
            val root = Files.createTempDirectory("image-translation").toString().toPath()
            try {
                var now = 0L
                val cache = ImageTranslationDiskCache(fs, root / "cache", maxBytes = 1500, now = { now })
                val image = root / "source"
                fs.write(image) { write(ByteArray(350)) }
                val ownerA = cache.ticket("a")
                val ownerB = cache.ticket("b")
                cache.put(ownerA, "a1", image, info)
                now++
                cache.put(ownerB, "b1", image, info)
                now++
                assertNotNull(cache.read("a1", root / "read"))
                now++
                cache.put(ownerB, "b2", image, info)
                assertTrue(cache.size() <= 1500)
                assertNull(cache.read("b1", root / "read"))
                assertNotNull(cache.read("a1", root / "read"))
                cache.clear("a")
                cache.put(ownerA, "late", image, info)
                assertNull(cache.read("a1", root / "read"))
                assertNull(cache.read("late", root / "read"))
                assertNotNull(cache.read("b2", root / "read"))
            } finally {
                fs.deleteRecursively(root)
            }
        }

    @Test
    fun unreadableCacheMetadataWritesDoNotDiscardAValidHit() =
        runTest {
            val root = Files.createTempDirectory("image-translation").toString().toPath()
            var rejectWrites = false
            var metadataWritesRejected = 0
            val readOnlyCache =
                object : ForwardingFileSystem(fs) {
                    override fun sink(
                        file: Path,
                        mustCreate: Boolean,
                    ): Sink {
                        if (rejectWrites && file.name == "manifest.json") {
                            metadataWritesRejected++
                            throw IOException("read-only cache")
                        }
                        return super.sink(file, mustCreate)
                    }
                }
            try {
                val cache = ImageTranslationDiskCache(readOnlyCache, root / "cache")
                val image = root / "source"
                fs.write(image) { writeUtf8("complete") }
                cache.put(cache.ticket("owner"), "entry", image, info)
                rejectWrites = true
                assertEquals(info, cache.read("entry", root / "read"))
                assertEquals("complete", fs.read(root / "read") { readUtf8() })
                assertEquals(1, metadataWritesRejected)
            } finally {
                fs.deleteRecursively(root)
            }
        }

    @Test
    fun damagedFileIsNeverReturnedAsAHit() =
        runTest {
            val root = Files.createTempDirectory("image-translation").toString().toPath()
            try {
                val cache = ImageTranslationDiskCache(fs, root / "cache")
                val image = root / "source"
                fs.write(image) { writeUtf8("complete") }
                cache.put(cache.ticket("a"), "entry", image, info)
                fs.write(root / "cache" / "entry" / "image.png") { writeUtf8("broken") }
                assertNull(cache.read("entry", root / "read"))
                assertEquals(0L, cache.size())
                cache.put(cache.ticket("a"), "entry", image, info)
                val manifest = root / "cache" / "entry" / "manifest.json"
                val escaped = fs.read(manifest) { readUtf8() }.replace("image.png", "../../source")
                fs.write(manifest) { writeUtf8(escaped) }
                assertNull(cache.read("entry", root / "read"))
                assertEquals(0L, cache.size())
            } finally {
                fs.deleteRecursively(root)
            }
        }
}
