package dev.dimension.flare.model

import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun ComposeInitialTextContext.resolveReplyParticipantInitialText(): InitialText? {
    if (composeType != ComposeType.Reply) return null

    val author = post.user?.takeUnless { it.key == selectedAccountKey }?.handle
    val routes =
        post.content.original.renderRuns
            .asSequence()
            .flatMap { content ->
                when (content) {
                    is RenderContent.BlockImage -> emptySequence()
                    is RenderContent.Text -> content.runs.asSequence()
                }
            }.mapNotNull { run ->
                val href = (run as? RenderRun.Text)?.style?.link ?: return@mapNotNull null
                DeeplinkRoute.parse(href)
            }.toList()
    // Legacy Mastodon links retain only an ID; wait for their existing refresh to supply the acct.
    if (routes.any { it is DeeplinkRoute.Profile.User }) return null
    val mentions =
        routes
            .filterIsInstance<DeeplinkRoute.Profile.UserNameWithHost>()
            .map { UiHandle(it.userName, it.host) }
    val text =
        buildString {
            (listOfNotNull(author) + mentions)
                .asSequence()
                .filter { it.normalizedRaw.isNotBlank() && it.normalizedHost.isNotBlank() }
                .map { it.canonical }
                .filterNot {
                    it.equals(currentUserHandle.canonical, ignoreCase = true) ||
                        (author == null && it.equals(post.user?.handle?.canonical, ignoreCase = true))
                }.distinctBy { it.lowercase() }
                .forEach { append("$it ") }
        }
    return InitialText(
        text = text,
        cursorPosition = text.length,
    )
}
