package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.WebLocalStorage
import dev.dimension.flare.data.io.FileStorage

internal actual fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    @Suppress("UNUSED_PARAMETER") fileStorage: FileStorage,
): Storage<T> =
    WebLocalStorage(
        serializer = serializer,
        name = name,
    )
