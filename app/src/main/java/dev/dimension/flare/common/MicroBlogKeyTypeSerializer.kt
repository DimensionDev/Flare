package dev.dimension.flare.common

import com.ramcosta.composedestinations.navargs.DestinationsNavTypeSerializer
import com.ramcosta.composedestinations.navargs.NavTypeSerializer
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@NavTypeSerializer
class MicroBlogKeyTypeSerializer : DestinationsNavTypeSerializer<MicroBlogKey> {
    override fun toRouteString(value: MicroBlogKey): String = value.toString()

    override fun fromRouteString(routeStr: String): MicroBlogKey = MicroBlogKey.valueOf(routeStr)
}

@NavTypeSerializer
class AccountTypeTypeSerializer : DestinationsNavTypeSerializer<AccountType> {
    override fun toRouteString(value: AccountType): String = value.encodeJson()

    override fun fromRouteString(routeStr: String): AccountType = routeStr.decodeJson()
}

@NavTypeSerializer
class TimelineTabItemTypeSerializer : DestinationsNavTypeSerializer<TimelineTabItem> {
    override fun toRouteString(value: TimelineTabItem): String = value.encodeJson()

    override fun fromRouteString(routeStr: String): TimelineTabItem = routeStr.decodeJson()
}
