package dev.dimension.flare.data.datasource.microblog.paging

public interface CacheableRemoteLoader<T : Any> : RemoteLoader<T> {
    public val pagingKey: String
    public val supportPrepend: Boolean
        get() = false
}

public interface ReportableRemoteLoader {
    public var reportError: ((Throwable) -> Unit)?
}
