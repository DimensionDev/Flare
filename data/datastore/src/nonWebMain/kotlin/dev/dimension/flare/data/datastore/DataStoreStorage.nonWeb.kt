package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import dev.dimension.flare.data.io.PlatformPathProducer
import okio.FileSystem
import okio.SYSTEM

public actual fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    platformPathProducer: PlatformPathProducer,
): Storage<T> =
    OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = serializer,
        producePath = {
            platformPathProducer.dataStoreFile(name)
        },
    )
