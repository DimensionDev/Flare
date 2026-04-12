package dev.dimension.flare.data.datasource.microblog.paging

public sealed interface PagingRequest {
    public data object Refresh : PagingRequest

    public data class Prepend(
        val previousKey: String,
    ) : PagingRequest

    public data class Append(
        val nextKey: String,
    ) : PagingRequest
}
