package dev.dimension.flare.data.repository

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.io.PlatformPathProducer
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

public class DraftMediaStore private constructor(
    private val platformPathProducer: PlatformPathProducer,
    private val storage: DraftMediaStorage = defaultDraftMediaStorage(),
) {
    public constructor(
        platformPathProducer: PlatformPathProducer,
    ) : this(
        platformPathProducer = platformPathProducer,
        storage = defaultDraftMediaStorage(),
    )

    public constructor(
        platformPathProducer: PlatformPathProducer,
        fileSystem: FileSystem,
    ) : this(
        platformPathProducer = platformPathProducer,
        storage = FileSystemDraftMediaStorage(fileSystem),
    )

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
                val path = platformPathProducer.draftMediaFile(groupId, "${index}_$fileName")
                storage.createDirectories(checkNotNull(path.parent))
                storage.write(path, media.file.readBytes())
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

    public fun delete(medias: List<DraftMedia>) {
        medias.forEach { media ->
            val path = media.cachePath.toPath()
            if (storage.exists(path)) {
                storage.delete(path)
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
        if (!storage.exists(groupDirectory)) {
            return
        }
        storage
            .list(groupDirectory)
            .filterNot { keepPaths.contains(it) }
            .forEach { stalePath ->
                if (storage.exists(stalePath)) {
                    storage.delete(stalePath)
                }
            }
        cleanupEmptyGroupDirectory(groupId)
    }

    private fun cleanupEmptyGroupDirectory(groupId: String) {
        val groupDirectory =
            platformPathProducer
                .draftMediaFile(groupId, "__placeholder__")
                .parent ?: return
        if (!storage.exists(groupDirectory)) {
            return
        }
        if (storage.list(groupDirectory).isEmpty()) {
            storage.delete(groupDirectory)
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
