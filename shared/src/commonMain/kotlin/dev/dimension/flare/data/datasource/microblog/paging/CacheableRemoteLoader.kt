package dev.dimension.flare.data.datasource.microblog.paging
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface CacheableRemoteLoader<T : Any> : RemoteLoader<T> {
    public val pagingKey: String
    public val supportPrepend: Boolean
        get() = false
}

@HiddenFromObjC
public interface ReportableRemoteLoader {
    public var reportError: ((Throwable) -> Unit)?
}
