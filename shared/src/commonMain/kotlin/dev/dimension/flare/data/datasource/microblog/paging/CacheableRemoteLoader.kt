package dev.dimension.flare.data.datasource.microblog.paging

internal interface CacheableRemoteLoader<T : Any> : RemoteLoader<T> {
    val pagingKey: String
}
