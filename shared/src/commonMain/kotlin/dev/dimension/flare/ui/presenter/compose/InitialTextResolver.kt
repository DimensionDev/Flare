package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiTimelineV2

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
        post.content.data
            .getElementsByAttributeValueStarting(
                "href",
                "flare://ProfileWithNameAndHost",
            ).filter {
                val href = it.attr("href")
                val params =
                    href
                        .substringAfter("flare://ProfileWithNameAndHost/")
                        .substringBefore("?accountKey=")
                        .split('/')
                val userName = params.getOrNull(0)
                val host = params.getOrNull(1)
                currentUserHandle.canonical != "@$userName@$host"
            }.filter {
                it.text() != post.user?.handle?.canonical
            }.forEach {
                handleToAdd.add(it.text())
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
}
