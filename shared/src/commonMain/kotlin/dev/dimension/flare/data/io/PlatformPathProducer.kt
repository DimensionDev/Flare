package dev.dimension.flare.data.io

import okio.Path

public expect class PlatformPathProducer {
    public fun dataStoreFile(fileName: String): Path
}
