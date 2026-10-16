package dev.dimension.flare.data.datasource.microblog.paging
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public enum class TimelineOrder {
    Continuous,
    Ranked,
}

@HiddenFromObjC
public interface CacheableRemoteLoader<T : Any> : RemoteLoader<T> {
    public val pagingKey: String

    // Only stable, cursor-ordered timelines can join refresh batches at an overlap.
    public val timelineOrder: TimelineOrder
        get() = TimelineOrder.Ranked
    public val supportPrepend: Boolean
        get() = false
    public val collapseReplyChains: Boolean
        get() = true
}

@HiddenFromObjC
public interface ReportableRemoteLoader {
    public var reportError: ((Throwable) -> Unit)?
}
