package dev.dimension.flare.data.datastore

import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.data.io.PlatformPathProducer
import okio.FileSystem
import okio.SYSTEM

internal actual class PlatformStorage<T> actual constructor(
    fileName: String,
    serializer: OkioSerializer<T>,
    platformPathProducer: PlatformPathProducer,
) : androidx.datastore.core.Storage<T> by OkioStorage(
        FileSystem.SYSTEM,
        serializer = serializer,
        producePath = {
            platformPathProducer.dataStoreFile(fileName)
        },
    )
