package dev.dimension.flare.data.draft

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.sanitizeFileName
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.io.FileStorage
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

public class DraftMediaStore(
    private val fileStorage: FileStorage,
) {
    public suspend fun persist(
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
                val path = fileStorage.draftMediaFile(groupId, "${index}_$fileName")
                fileStorage.createDirectories(checkNotNull(path.parent))
                fileStorage.write(path, media.file.readBytes())
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

    public suspend fun restore(medias: List<DraftMedia>): List<ComposeData.Media> =
        medias.map { media ->
            val path = media.cachePath.toPath()
            ComposeData.Media(
                file =
                    FileItem(
                        media.fileName ?: path.name,
                        media.mediaType.toFileType(),
                        {
                            fileStorage.read(path)
                        },
                    ),
                altText = media.altText,
            )
        }

    public fun delete(medias: List<DraftMedia>) {
        medias.forEach { media ->
            val path = media.cachePath.toPath()
            if (fileStorage.exists(path)) {
                fileStorage.delete(path)
            }
            cleanupEmptyGroupDirectory(media.groupId)
        }
    }

    private fun cleanupStaleFiles(
        groupId: String,
        keepPaths: Set<okio.Path>,
    ) {
        val groupDirectory =
            fileStorage
                .draftMediaFile(groupId, "__placeholder__")
                .parent ?: return
        if (!fileStorage.exists(groupDirectory)) {
            return
        }
        fileStorage
            .list(groupDirectory)
            .filterNot { keepPaths.contains(it) }
            .forEach { stalePath ->
                if (fileStorage.exists(stalePath)) {
                    fileStorage.delete(stalePath)
                }
            }
        cleanupEmptyGroupDirectory(groupId)
    }

    private fun cleanupEmptyGroupDirectory(groupId: String) {
        val groupDirectory =
            fileStorage
                .draftMediaFile(groupId, "__placeholder__")
                .parent ?: return
        if (!fileStorage.exists(groupDirectory)) {
            return
        }
        if (fileStorage.list(groupDirectory).isEmpty()) {
            fileStorage.delete(groupDirectory)
        }
    }
}

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
