package dev.dimension.flare.data.model.tab

import dev.dimension.flare.model.AccountType

public object XQTTimelineTabItemV2Helpers {
    public fun xqtDeviceFollow(accountType: AccountType): SourceTimelineTabItemV2? {
        val accountKey = (accountType as? AccountType.Specific)?.accountKey ?: return null
        return SourceTimelineTabItemV2.xqtDeviceFollow(accountKey)
    }
}
