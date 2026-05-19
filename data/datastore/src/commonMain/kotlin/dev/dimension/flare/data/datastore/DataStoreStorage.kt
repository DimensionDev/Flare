package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.data.io.PlatformPathProducer

public expect fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    platformPathProducer: PlatformPathProducer,
): Storage<T>
