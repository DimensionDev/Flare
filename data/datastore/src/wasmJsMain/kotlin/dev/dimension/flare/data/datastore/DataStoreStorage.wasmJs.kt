package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.WebOpfsStorage
import dev.dimension.flare.data.io.PlatformPathProducer

public actual fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    @Suppress("UNUSED_PARAMETER") platformPathProducer: PlatformPathProducer,
): Storage<T> =
    WebOpfsStorage(
        serializer = serializer,
        name = name,
    )
