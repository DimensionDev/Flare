package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.WebOpfsStorage
import dev.dimension.flare.data.io.FileStorage

public actual fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    @Suppress("UNUSED_PARAMETER") fileStorage: FileStorage,
): Storage<T> =
    WebOpfsStorage(
        serializer = serializer,
        name = name,
    )
