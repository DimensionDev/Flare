package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.io.PlatformPathProducer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.uuid.Uuid

internal class DraftMediaStore(
    private val platformPathProducer: PlatformPathProducer,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    suspend fun persist(
        groupId: String,
        medias: List<ComposeData.Media>,
    ): List<SaveDraftMedia> {
        val persisted =
            medias.mapIndexed { index, media ->
                val fileName =
                    media.file.name
                        ?.sanitizeFileName()
                        .orEmpty()
                        .ifBlank { "${Uuid.random()}.bin" }
                val path = platformPathProducer.draftMediaFile(groupId, "${index}_$fileName")
                fileSystem.createDirectories(checkNotNull(path.parent))
                fileSystem.write(path) {
                    write(media.file.readBytes())
                }
                SaveDraftMedia(
                    cachePath = path.toString(),
                    fileName = media.file.name,
                    mediaType = media.file.type.toDraftMediaType(),
                    altText = media.altText,
                    sortOrder = index,
                )
            }
        cleanupStaleFiles(
            groupId = groupId,
            keepPaths = persisted.map { it.cachePath.toPath() }.toSet(),
        )
        return persisted
    }

    suspend fun restore(medias: List<DraftMedia>): List<ComposeData.Media> =
        medias.map { media ->
            ComposeData.Media(
                file =
                    draftFileItem(
                        path = media.cachePath,
                        name = media.fileName,
                        type = media.mediaType.toFileType(),
                    ),
                altText = media.altText,
            )
        }

    fun delete(medias: List<DraftMedia>) {
        medias.forEach { media ->
            val path = media.cachePath.toPath()
            if (fileSystem.exists(path)) {
                fileSystem.delete(path)
            }
            cleanupEmptyGroupDirectory(media.groupId)
        }
    }

    private fun cleanupStaleFiles(
        groupId: String,
        keepPaths: Set<okio.Path>,
    ) {
        val groupDirectory =
            platformPathProducer
                .draftMediaFile(groupId, "__placeholder__")
                .parent ?: return
        if (!fileSystem.exists(groupDirectory)) {
            return
        }
        fileSystem
            .list(groupDirectory)
            .filterNot { keepPaths.contains(it) }
            .forEach { stalePath ->
                if (fileSystem.exists(stalePath)) {
                    fileSystem.delete(stalePath)
                }
            }
        cleanupEmptyGroupDirectory(groupId)
    }

    private fun cleanupEmptyGroupDirectory(groupId: String) {
        val groupDirectory =
            platformPathProducer
                .draftMediaFile(groupId, "__placeholder__")
                .parent ?: return
        if (!fileSystem.exists(groupDirectory)) {
            return
        }
        if (fileSystem.list(groupDirectory).isEmpty()) {
            fileSystem.delete(groupDirectory)
        }
    }
}

internal expect fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
): FileItem

private fun FileType.toDraftMediaType(): DraftMediaType =
    when (this) {
        FileType.Image -> DraftMediaType.IMAGE
        FileType.Video -> DraftMediaType.VIDEO
        FileType.Other -> DraftMediaType.OTHER
    }

private fun DraftMediaType.toFileType(): FileType =
    when (this) {
        DraftMediaType.IMAGE -> FileType.Image
        DraftMediaType.VIDEO -> FileType.Video
        DraftMediaType.OTHER -> FileType.Other
    }

private fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
