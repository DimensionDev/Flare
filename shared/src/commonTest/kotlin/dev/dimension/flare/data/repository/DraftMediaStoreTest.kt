package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileType
import dev.dimension.flare.createTestFileItem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.deleteTestRootPath
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DraftMediaStoreTest {
    private val root = createTestRootPath()
    private val fileSystem = FileSystem.SYSTEM
    private val pathProducer =
        object : PlatformPathProducer {
            override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

            override fun draftMediaFile(
                groupId: String,
                fileName: String,
            ): Path = root.resolve("draft_media").resolve(groupId).resolve(fileName)
        }

    @AfterTest
    fun tearDown() {
        deleteTestRootPath(root)
    }

    @Test
    fun persistRestoreDeleteFlow() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val medias =
                listOf(
                    media(name = "a.png", bytes = byteArrayOf(1, 2, 3), type = FileType.Image, altText = "a"),
                    media(name = "b.mov", bytes = byteArrayOf(4, 5, 6), type = FileType.Video, altText = "b"),
                )

            val persisted = store.persist("group-1", medias)

            assertEquals(2, persisted.size)
            persisted.forEach {
                assertTrue(fileSystem.exists(it.cachePath.toPath()))
            }

            val restored = store.restore(persisted.mapIndexed { index, media -> media.toDraftMedia("group-1", index) })

            assertEquals(2, restored.size)
            assertEquals(listOf("a", "b"), restored.map { it.altText })
            assertContentEquals(byteArrayOf(1, 2, 3), restored[0].file.readBytes())
            assertContentEquals(byteArrayOf(4, 5, 6), restored[1].file.readBytes())

            store.delete(persisted.mapIndexed { index, media -> media.toDraftMedia("group-1", index) })

            persisted.forEach {
                assertFalse(fileSystem.exists(it.cachePath.toPath()))
            }
        }

    @Test
    fun persistRestorePersistDoesNotCreateNewFiles() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val firstPersist =
                store.persist(
                    "group-2",
                    listOf(
                        media(name = "a.png", bytes = byteArrayOf(1), type = FileType.Image, altText = "a"),
                        media(name = "b.mov", bytes = byteArrayOf(2), type = FileType.Video, altText = "b"),
                    ),
                )
            val restored = store.restore(firstPersist.mapIndexed { index, media -> media.toDraftMedia("group-2", index) })

            val secondPersist = store.persist("group-2", restored)

            assertEquals(firstPersist.map { it.cachePath }, secondPersist.map { it.cachePath })
            assertEquals(
                2,
                fileSystem
                    .list(root.resolve("draft_media").resolve("group-2"))
                    .size,
            )
            secondPersist.forEach {
                assertTrue(fileSystem.exists(it.cachePath.toPath()))
            }
        }

    @Test
    fun persistRestorePersistWithSameFileNameOverwritesContent() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val firstPersist =
                store.persist(
                    "group-same-name",
                    listOf(
                        media(name = "a.png", bytes = byteArrayOf(1, 2, 3), type = FileType.Image, altText = "first"),
                    ),
                )
            val restored = store.restore(firstPersist.mapIndexed { index, media -> media.toDraftMedia("group-same-name", index) })
            val updatedMedia =
                listOf(
                    restored.single().copy(
                        file = createTestFileItem(root = root, name = "a.png", bytes = byteArrayOf(9, 8, 7), type = FileType.Image),
                    ),
                )

            val secondPersist = store.persist("group-same-name", updatedMedia)

            assertEquals(firstPersist.single().cachePath, secondPersist.single().cachePath)
            assertContentEquals(
                byteArrayOf(9, 8, 7),
                fileSystem.read(secondPersist.single().cachePath.toPath()) { readByteArray() },
            )
        }

    @Test
    fun persistRestoreModifyPersistDeletesRemovedAndAddsNewFiles() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val firstPersist =
                store.persist(
                    "group-3",
                    listOf(
                        media(name = "a.png", bytes = byteArrayOf(1, 1), type = FileType.Image, altText = "a"),
                        media(name = "b.mov", bytes = byteArrayOf(2, 2), type = FileType.Video, altText = "b"),
                    ),
                )
            val restored = store.restore(firstPersist.mapIndexed { index, media -> media.toDraftMedia("group-3", index) })
            val originalPaths = firstPersist.map { it.cachePath }.toSet()

            val modified =
                listOf(
                    restored.first(),
                    media(name = "c.png", bytes = byteArrayOf(3, 3), type = FileType.Image, altText = "c"),
                )

            val secondPersist = store.persist("group-3", modified)
            val newPaths = secondPersist.map { it.cachePath }.toSet()
            val removedPath = firstPersist[1].cachePath

            assertTrue(firstPersist[0].cachePath in newPaths)
            assertTrue(secondPersist.any { it.fileName == "c.png" })
            assertFalse(fileSystem.exists(removedPath.toPath()))
            assertEquals(2, newPaths.size)
            assertTrue(newPaths.any { it !in originalPaths })
            secondPersist.forEach {
                assertTrue(fileSystem.exists(it.cachePath.toPath()))
            }
        }

    @Test
    fun persistHandlesNullAndBlankFileNames() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val persisted =
                store.persist(
                    "group-4",
                    listOf(
                        media(name = null, bytes = byteArrayOf(7), type = FileType.Image, altText = null),
                        media(name = "", bytes = byteArrayOf(8), type = FileType.Video, altText = "blank"),
                    ),
                )

            assertEquals(2, persisted.size)
            assertNull(persisted[0].fileName)
            assertEquals("", persisted[1].fileName)
            persisted.forEach {
                assertTrue(fileSystem.exists(it.cachePath.toPath()))
                assertTrue(it.cachePath.substringAfterLast('/').matches(Regex("\\d+_.+\\.bin")))
            }
        }

    @Test
    fun persistSanitizesIllegalCharactersInFileName() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val persisted =
                store.persist(
                    "group-5",
                    listOf(
                        media(name = "a:/b*?c<>|.png", bytes = byteArrayOf(9), type = FileType.Image, altText = null),
                    ),
                )

            assertEquals(1, persisted.size)
            assertTrue(fileSystem.exists(persisted.single().cachePath.toPath()))
            assertEquals("0_a__b__c___.png", persisted.single().cachePath.substringAfterLast('/'))
        }

    @Test
    fun persistSanitizesWhitespaceAndUnicodeFileName() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)

            val persisted =
                store.persist(
                    "group-5b",
                    listOf(
                        media(name = "  测试 file?.png  ", bytes = byteArrayOf(3), type = FileType.Image, altText = null),
                    ),
                )

            assertEquals("0______file_.png__", persisted.single().cachePath.substringAfterLast('/'))
            assertTrue(fileSystem.exists(persisted.single().cachePath.toPath()))
        }

    @Test
    fun persistEmptyListClearsDraftGroupFiles() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val firstPersist =
                store.persist(
                    "group-6",
                    listOf(
                        media(name = "a.png", bytes = byteArrayOf(1), type = FileType.Image, altText = null),
                        media(name = "b.png", bytes = byteArrayOf(2), type = FileType.Image, altText = null),
                    ),
                )

            val secondPersist = store.persist("group-6", emptyList())

            assertTrue(secondPersist.isEmpty())
            firstPersist.forEach {
                assertFalse(fileSystem.exists(it.cachePath.toPath()))
            }
            val groupDir = root.resolve("draft_media").resolve("group-6")
            assertTrue(!fileSystem.exists(groupDir) || fileSystem.list(groupDir).isEmpty())
        }

    @Test
    fun deleteIgnoresMissingFilesAndRepeatedDeletes() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val missing =
                DraftMedia(
                    mediaId = "missing",
                    groupId = "group-7",
                    cachePath =
                        root
                            .resolve("draft_media")
                            .resolve("group-7")
                            .resolve("missing.png")
                            .toString(),
                    fileName = "missing.png",
                    mediaType = DraftMediaType.IMAGE,
                    altText = null,
                    sortOrder = 0,
                    createdAt = 0L,
                )
            val persisted =
                store
                    .persist(
                        "group-7",
                        listOf(
                            media(name = "exists.png", bytes = byteArrayOf(1, 2), type = FileType.Image, altText = null),
                        ),
                    ).single()
                    .toDraftMedia("group-7", 0)

            store.delete(listOf(missing, persisted))
            store.delete(listOf(missing, persisted))

            assertFalse(fileSystem.exists(persisted.cachePath.toPath()))
            assertFalse(fileSystem.exists(missing.cachePath.toPath()))
        }

    @Test
    fun restoreFailsWhenCachedFileIsMissing() =
        runTest {
            val store = DraftMediaStore(pathProducer, fileSystem)
            val missingMedia =
                DraftMedia(
                    mediaId = "missing-restore",
                    groupId = "group-restore-fail",
                    cachePath =
                        root
                            .resolve("draft_media")
                            .resolve("group-restore-fail")
                            .resolve("missing.png")
                            .toString(),
                    fileName = "missing.png",
                    mediaType = DraftMediaType.IMAGE,
                    altText = null,
                    sortOrder = 0,
                    createdAt = 0L,
                )

            assertFailsWith<Throwable> {
                store
                    .restore(listOf(missingMedia))
                    .single()
                    .file
                    .readBytes()
            }
        }

    @Test
    fun persistFailsWhenDraftDirectoryCannotBeCreated() =
        runTest {
            val blockedParent = root.resolve("blocked")
            fileSystem.write(blockedParent) {
                writeUtf8("not a directory")
            }
            val blockedStore =
                DraftMediaStore(
                    platformPathProducer =
                        object : PlatformPathProducer {
                            override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

                            override fun draftMediaFile(
                                groupId: String,
                                fileName: String,
                            ): Path = blockedParent.resolve(groupId).resolve(fileName)
                        },
                    fileSystem = fileSystem,
                )

            assertFailsWith<Throwable> {
                blockedStore.persist(
                    "group-write-fail",
                    listOf(
                        media(name = "a.png", bytes = byteArrayOf(1), type = FileType.Image, altText = null),
                    ),
                )
            }
        }

    private fun media(
        name: String?,
        bytes: ByteArray,
        type: FileType,
        altText: String?,
    ): ComposeData.Media =
        ComposeData.Media(
            file = createTestFileItem(root = root, name = name, bytes = bytes, type = type),
            altText = altText,
        )

    private fun SaveDraftMedia.toDraftMedia(
        groupId: String,
        index: Int,
    ) = DraftMedia(
        mediaId = "media-$index",
        groupId = groupId,
        cachePath = cachePath,
        fileName = fileName,
        mediaType = mediaType,
        altText = altText,
        sortOrder = sortOrder ?: index,
        createdAt = createdAt ?: 0L,
    )
}
