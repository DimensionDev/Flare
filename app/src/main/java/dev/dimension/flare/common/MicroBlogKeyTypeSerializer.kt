package dev.dimension.flare.common

import com.ramcosta.composedestinations.navargs.DestinationsNavTypeSerializer
import com.ramcosta.composedestinations.navargs.NavTypeSerializer
import dev.dimension.flare.model.MicroBlogKey

@NavTypeSerializer
class MicroBlogKeyTypeSerializer : DestinationsNavTypeSerializer<MicroBlogKey> {
    override fun toRouteString(value: MicroBlogKey): String =
        value.toString()

    override fun fromRouteString(routeStr: String): MicroBlogKey =
        MicroBlogKey.valueOf(routeStr)
}
