package dev.dimension.flare.data.datasource.microblog.paging
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface CacheableRemoteLoader<T : Any> : RemoteLoader<T> {
    public val pagingKey: String

    // Only an ordered source may finish a retained page at its known lower neighbour.
    public val canJoinTimeline: Boolean
        get() = false
    public val refreshGapOncePerSession: Boolean
        get() = false
    public val supportPrepend: Boolean
        get() = false
    public val collapseReplyChains: Boolean
        get() = true
}

@HiddenFromObjC
public interface ReportableRemoteLoader {
    public var reportError: ((Throwable) -> Unit)?
}
