package dev.dimension.flare.data.io

import android.content.Context
import androidx.datastore.dataStoreFile
import okio.Path
import okio.Path.Companion.toOkioPath

public actual class PlatformPathProducer(
    private val context: Context,
) {
    public actual fun dataStoreFile(fileName: String): Path = context.dataStoreFile(fileName).toOkioPath()
}
