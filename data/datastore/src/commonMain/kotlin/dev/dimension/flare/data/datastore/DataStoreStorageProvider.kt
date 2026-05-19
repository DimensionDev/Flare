package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.data.io.PlatformPathProducer

public class DataStoreStorageProvider(
    private val platformPathProducer: PlatformPathProducer,
) {
    public fun <T> storage(
        name: String,
        serializer: OkioSerializer<T>,
    ): Storage<T> =
        createDataStoreStorage(
            name = name,
            serializer = serializer,
            platformPathProducer = platformPathProducer,
        )
}

internal expect fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    platformPathProducer: PlatformPathProducer,
): Storage<T>
