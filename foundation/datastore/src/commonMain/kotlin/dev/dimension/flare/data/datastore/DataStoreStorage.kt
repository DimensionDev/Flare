package dev.dimension.flare.data.datastore

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import dev.dimension.flare.data.io.FileStorage

public expect fun <T> createDataStoreStorage(
    name: String,
    serializer: OkioSerializer<T>,
    fileStorage: FileStorage,
): Storage<T>
