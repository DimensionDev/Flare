package dev.dimension.flare.data.io

import okio.Path

public interface PlatformPathProducer {
    public fun dataStoreFile(fileName: String): Path

    public fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path
}
