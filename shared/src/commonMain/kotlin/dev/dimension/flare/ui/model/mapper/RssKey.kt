package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.model.MicroBlogKey
import kotlin.io.encoding.Base64
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun MicroBlogKey.Companion.fromRss(url: String): MicroBlogKey =
    MicroBlogKey(
        // Keep IDs compatible with articles saved before original HTTP URLs were preserved.
        id = Base64.encode(url.replace("http://", "https://").encodeToByteArray()),
        host = "RSS",
    )
