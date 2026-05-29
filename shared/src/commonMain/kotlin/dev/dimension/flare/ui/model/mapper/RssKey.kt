package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.model.MicroBlogKey
import kotlin.io.encoding.Base64

public fun MicroBlogKey.Companion.fromRss(url: String): MicroBlogKey =
    MicroBlogKey(
        id = Base64.encode(url.encodeToByteArray()),
        host = "RSS",
    )
