package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

/** A response updates post contents independently of the surrounding list membership. */
@HiddenFromObjC
public data class ContextUpdate(
    val posts: List<UiTimelineV2>,
    val before: ContextPageUpdate? = null,
    val after: ContextPageUpdate? = null,
)

/** Null in [ContextUpdate] means untouched; an empty replacement means a confirmed empty side. */
@HiddenFromObjC
public data class ContextPageUpdate(
    val keys: List<MicroBlogKey>,
    val cursor: String?,
    val replace: Boolean,
)

@HiddenFromObjC
public interface PostContextLoader : CacheableRemoteLoader<UiTimelineV2> {
    public val statusKey: MicroBlogKey
    public val accountKey: MicroBlogKey

    /**
     * Refresh normally contains both sides. A loader fetching context in a subsequent request
     * leaves that side null and returns its initial cursor in the paging result instead.
     * [initial] identifies those first context requests, independently of the cursor's value.
     */
    public fun contextUpdate(
        request: PagingRequest,
        result: PagingResult<UiTimelineV2>,
        initial: Boolean,
    ): ContextUpdate = result.toContextUpdate(statusKey, request, initial)
}

@HiddenFromObjC
public fun PagingResult<UiTimelineV2>.toContextUpdate(
    statusKey: MicroBlogKey,
    request: PagingRequest,
    initial: Boolean,
): ContextUpdate {
    val anchor = data.indexOfFirst { it.statusKey == statusKey }
    return if (request == PagingRequest.Refresh) {
        require(anchor >= 0) { "Post context does not contain $statusKey" }
        ContextUpdate(
            posts = data,
            before = ContextPageUpdate(data.take(anchor).map { it.statusKey }, previousKey, replace = true),
            after = ContextPageUpdate(data.drop(anchor + 1).map { it.statusKey }, nextKey, replace = true),
        )
    } else {
        when (request) {
            is PagingRequest.Append -> {
                ContextUpdate(
                    posts = data,
                    after =
                        ContextPageUpdate(
                            keys = (if (anchor >= 0) data.drop(anchor + 1) else data).map { it.statusKey },
                            cursor = nextKey,
                            replace = initial,
                        ),
                )
            }

            is PagingRequest.Prepend -> {
                ContextUpdate(
                    posts = data,
                    before =
                        ContextPageUpdate(
                            keys = (if (anchor >= 0) data.take(anchor) else data).map { it.statusKey },
                            cursor = previousKey,
                            replace = initial,
                        ),
                )
            }

            PagingRequest.Refresh -> {
                error("Handled above")
            }
        }
    }
}
