package dev.dimension.flare.data.io

import okio.Path
import okio.Path.Companion.toPath

public class WebPlatformPathProducer : PlatformPathProducer {
    override fun dataStoreFile(fileName: String): Path = "/flare/$fileName".toPath()

    override fun draftMediaFile(
        groupId: String,
        fileName: String,
    ): Path = "/flare/draft_media/$groupId/$fileName".toPath()
}
