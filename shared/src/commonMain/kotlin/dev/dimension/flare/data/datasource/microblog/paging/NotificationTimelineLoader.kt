package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface NotificationTimelineLoader : CacheableRemoteLoader<UiTimelineV2> {
    override val collapseReplyChains: Boolean
        get() = false
}
