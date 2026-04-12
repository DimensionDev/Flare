package dev.dimension.flare.data.datasource.microblog.paging

public data class PagingResult<T : Any>(
    val data: List<T> = emptyList(),
    val nextKey: String? = null,
    val previousKey: String? = null,
) {
    public constructor(
        endOfPaginationReached: Boolean,
        data: List<T> = emptyList(),
        nextKey: String? = null,
        previousKey: String? = null,
    ) : this(
        data = data.toList(),
        nextKey = if (endOfPaginationReached) null else nextKey,
        previousKey = previousKey,
    )
}
