package dev.dimension.flare.data.datasource.microblog.paging

internal enum class RefreshBehavior {
    Replace,
    MergeTop,
}

internal interface CacheableRemoteLoader<T : Any> : RemoteLoader<T> {
    val pagingKey: String
    val supportPrepend: Boolean
        get() = false
    val refreshBehavior: RefreshBehavior
        get() = RefreshBehavior.Replace
}

internal interface ReportableRemoteLoader {
    var reportError: ((Throwable) -> Unit)?
}
