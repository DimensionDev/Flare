package dev.dimension.flare.data.datasource.microblog.paging

internal sealed interface PagingRequest {
    data object Refresh : PagingRequest

    data class Prepend(
        val previousKey: String,
    ) : PagingRequest

    data class Append(
        val nextKey: String,
    ) : PagingRequest
}
