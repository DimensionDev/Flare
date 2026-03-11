package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun

internal object InitialTextResolver {
    fun resolve(
        post: UiTimelineV2.Post,
        composeStatus: ComposeStatus,
        currentUserHandle: UiHandle,
        selectedAccountKey: MicroBlogKey?,
    ): InitialText? =
        when (post.platformType) {
            PlatformType.VVo -> resolveVVo(post, composeStatus)
            PlatformType.Mastodon, PlatformType.Misskey ->
                resolveMastodonMisskey(post, composeStatus, currentUserHandle, selectedAccountKey)
            else -> null
        }

    private fun resolveVVo(
        post: UiTimelineV2.Post,
        composeStatus: ComposeStatus,
    ): InitialText? {
        if (post.quote.any() && composeStatus is ComposeStatus.Quote) {
            return InitialText(
                text = "//@${post.user?.name?.raw}:${post.content.raw}",
                cursorPosition = 0,
            )
        }
        return null
    }

    private fun resolveMastodonMisskey(
        post: UiTimelineV2.Post,
        composeStatus: ComposeStatus,
        currentUserHandle: UiHandle,
        selectedAccountKey: MicroBlogKey?,
    ): InitialText? {
        if (composeStatus !is ComposeStatus.Reply) return null

        val handleToAdd = mutableSetOf<String>()
        if (post.user?.key != selectedAccountKey) {
            post.user?.handle?.let {
                handleToAdd.add(it.canonical)
            }
        }
        post.content.renderRuns
            .asSequence()
            .flatMap { content ->
                when (content) {
                    is RenderContent.BlockImage -> emptySequence()
                    is RenderContent.Text -> content.runs.asSequence()
                }
            }.mapNotNull { run ->
                when (run) {
                    is RenderRun.Image -> null
                    is RenderRun.Text -> {
                        val href = run.style.link ?: return@mapNotNull null
                        if (!href.startsWith("flare://ProfileWithNameAndHost")) {
                            return@mapNotNull null
                        }
                        MentionLink(
                            href = href,
                            text = run.text,
                        )
                    }
                }
            }.filterNot { mention ->
                val params =
                    mention.href
                        .substringAfter("flare://ProfileWithNameAndHost/")
                        .substringBefore("?accountKey=")
                        .split('/')
                val userName = params.getOrNull(0)
                val host = params.getOrNull(1)
                currentUserHandle.canonical == "@$userName@$host"
            }.filterNot { mention ->
                mention.text == post.user?.handle?.canonical
            }.forEach { mention ->
                handleToAdd.add(mention.text)
            }
        val text =
            buildString {
                handleToAdd.distinct().forEach {
                    append("$it ")
                }
            }
        return InitialText(
            text = text,
            cursorPosition = text.length,
        )
    }

    private data class MentionLink(
        val href: String,
        val text: String,
    )
}
