package dev.dimension.flare.data.io

import okio.Path

internal interface PlatformPathProducer {
    fun dataStoreFile(fileName: String): Path

    fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path
}
