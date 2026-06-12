package dev.dimension.flare.data.model.tab

import dev.dimension.flare.model.AccountType

public object XQTUiTimelineTabItemHelpers {
    public fun xqtDeviceFollow(accountType: AccountType): UiSourceTimelineTabItem? {
        val accountKey = (accountType as? AccountType.Specific)?.accountKey ?: return null
        return UiSourceTimelineTabItem.xqtDeviceFollow(accountKey)
    }
}
