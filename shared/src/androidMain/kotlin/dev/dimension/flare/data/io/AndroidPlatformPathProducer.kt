package dev.dimension.flare.data.io

import android.content.Context
import androidx.datastore.dataStoreFile
import okio.Path
import okio.Path.Companion.toOkioPath

internal class AndroidPlatformPathProducer(
    private val context: Context,
) : PlatformPathProducer {
    override fun dataStoreFile(fileName: String): Path = context.dataStoreFile(fileName).toOkioPath()

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path =
        context
            .dataStoreFile("draft_media")
            .toOkioPath()
            .resolve(groupId)
            .resolve(fileName)
}
