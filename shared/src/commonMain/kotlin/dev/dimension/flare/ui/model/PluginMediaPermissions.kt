package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUiPlainText
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.native.HiddenFromObjC

/** Process-owned grants, checked again after cached content and translations are restored. */
@HiddenFromObjC
public object PluginMediaPermissions {
    private val grants = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    public fun replace(values: Map<String, Boolean>) {
        grants.value = values.toMap()
    }

    public fun register(
        pluginId: String,
        allowed: Boolean,
    ) {
        grants.update { it + (pluginId to allowed) }
    }

    public fun isAllowed(pluginId: String): Boolean = grants.value[pluginId] == true
}

internal fun UiMedia.allowedForPlugin(pluginId: String?): Boolean =
    pluginId == null || (PluginMediaPermissions.isAllowed(pluginId) && url.isSafePluginUrl() && preview?.url.isSafePluginUrl())

internal fun String?.isSafePluginUrl(): Boolean =
    this == null ||
        runCatching {
            val parsed = Url(this)
            parsed.protocol == URLProtocol.HTTPS && parsed.host.isNotBlank() && parsed.user == null && parsed.password == null
        }.getOrDefault(false)

internal fun UiRichText.filterPluginMedia(pluginId: String?): UiRichText {
    if (pluginId == null) return this
    val allowed = PluginMediaPermissions.isAllowed(pluginId)
    return copy(
        imageUrls = imageUrls.filter { allowed && it.isSafePluginUrl() }.toImmutableList(),
        renderRuns =
            renderRuns
                .mapNotNull { content ->
                    when (content) {
                        is RenderContent.BlockImage -> {
                            content.takeIf { allowed && it.url.isSafePluginUrl() }?.copy(
                                href = content.href?.takeIf { it.isSafePluginUrl() },
                            )
                        }

                        is RenderContent.Text -> {
                            content.copy(
                                runs =
                                    content.runs
                                        .map { run ->
                                            when (run) {
                                                is RenderRun.Image -> {
                                                    if (allowed && run.url.isSafePluginUrl()) run else RenderRun.Text(run.alt)
                                                }

                                                is RenderRun.Text -> {
                                                    run.copy(
                                                        style =
                                                            run.style.copy(
                                                                link = run.style.link?.takeIf { it.isSafePluginUrl() },
                                                                imageLink = run.style.imageLink?.takeIf { it.allowedForPlugin(pluginId) },
                                                            ),
                                                    )
                                                }
                                            }
                                        }.toImmutableList(),
                            )
                        }
                    }
                }.toImmutableList(),
    )
}

internal fun UiProfile.filterPluginMedia(owner: String?): UiProfile {
    if (owner == null) return this
    return copy(
        avatar = avatar?.takeIf { it.allowedForPlugin(owner) },
        banner = banner?.takeIf { it.allowedForPlugin(owner) },
        nameInternal = nameInternal.filterPluginMedia(owner),
        description = description?.filterPluginMedia(owner),
        bottomContent =
            when (val bottom = bottomContent) {
                is UiProfile.BottomContent.Fields -> {
                    bottom.copy(
                        fields = bottom.fields.mapValues { it.value.filterPluginMedia(owner) }.toImmutableMap(),
                    )
                }

                is UiProfile.BottomContent.Iconify -> {
                    bottom.copy(
                        items = bottom.items.mapValues { it.value.filterPluginMedia(owner) }.toImmutableMap(),
                    )
                }

                null -> {
                    null
                }
            },
    )
}

internal fun UiTimelineV2.Post.filterPluginMedia(owner: String?): UiTimelineV2.Post {
    if (owner == null) return this
    return copy(
        images = images.filter { it.allowedForPlugin(owner) }.toImmutableList(),
        user = user?.filterPluginMedia(owner),
        content =
            content.copy(
                original = content.original.filterPluginMedia(owner),
                translation = content.translation?.filterPluginMedia(owner),
            ),
        contentWarning =
            contentWarning?.let {
                it.copy(
                    original = it.original.filterPluginMedia(owner),
                    translation = it.translation?.filterPluginMedia(owner),
                )
            },
        card = card?.copy(media = card.media?.takeIf { it.allowedForPlugin(owner) }),
        emojiReactions = emojiReactions.filter { PluginMediaPermissions.isAllowed(owner) && it.url.isSafePluginUrl() }.toImmutableList(),
    )
}

internal fun UiDMItem.filterPluginMedia(owner: String?): UiDMItem {
    if (owner == null) return this
    return copy(
        user = user.filterPluginMedia(owner),
        content =
            when (val message = content) {
                is UiDMItem.Message.Text -> {
                    message.copy(text = message.text.filterPluginMedia(owner))
                }

                is UiDMItem.Message.Media -> {
                    message.takeIf { it.media.allowedForPlugin(owner) }
                        ?: UiDMItem.Message.Text("".toUiPlainText())
                }

                is UiDMItem.Message.Status -> {
                    message.copy(status = message.status.filterPluginMedia(owner))
                }

                UiDMItem.Message.Deleted -> {
                    message
                }
            },
    )
}
