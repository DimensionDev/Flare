package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.native.HiddenFromObjC

/** Keeps the detail source's order while exposing each reply independently. */
@HiddenFromObjC
public class ShareRepliesLoader(
    private val source: RemoteLoader<UiTimelineV2>,
    private val statusKey: MicroBlogKey,
    private val comments: Boolean = false,
) : RemoteLoader<UiTimelineV2> {
    private val ancestors = mutableSetOf(statusKey)
    private var focalKey = statusKey

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val result = source.load(pageSize, request)
        result.data.firstOrNull { it.statusKey == statusKey }?.asTimelinePostItem()?.let {
            focalKey = it.displayPost.statusKey
            ancestors += focalKey
        }
        val flattened =
            buildList {
                fun append(
                    item: UiTimelineV2,
                    parent: UiTimelineV2.Post? = null,
                ) {
                    val post = item.asTimelinePostItem() ?: return
                    post.presentation.inlineParents.forEach { append(it) }
                    val body = post.displayPost
                    add(
                        post.copy(
                            post =
                                if (parent == null) {
                                    body
                                } else {
                                    body.copy(
                                        replyToHandle = body.replyToHandle ?: parent.user?.handle?.canonical,
                                        references =
                                            (
                                                body.references +
                                                    UiTimelineV2.Post.Reference(
                                                        parent.statusKey,
                                                        ReferenceType.Reply,
                                                    )
                                            ).toImmutableList(),
                                    )
                                },
                            presentation =
                                post.presentation.copy(
                                    inlineParents = persistentListOf(),
                                    repost = null,
                                    quotes = if (comments) persistentListOf() else post.presentation.quotes,
                                ),
                        ),
                    )
                    if (comments) post.presentation.quotes.forEach { append(it, body) }
                }
                result.data.forEach { append(it) }
            }
        if (!comments) {
            val anchor = flattened.indexOfFirst { it.statusKey == focalKey }
            if (anchor >= 0) ancestors += flattened.take(anchor).map { it.statusKey }
        }
        return result.copy(
            data = flattened.filter { it.statusKey !in ancestors }.distinctBy { it.statusKey },
            previousKey = null,
        )
    }
}
