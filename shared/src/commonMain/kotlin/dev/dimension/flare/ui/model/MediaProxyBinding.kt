package dev.dimension.flare.ui.model

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlin.native.HiddenFromObjC

public fun UiMedia.withProxy(proxy: ProxyRef): UiMedia =
    when (this) {
        is UiMedia.Image -> copy(proxy = proxy)
        is UiMedia.Video -> copy(proxy = proxy)
        is UiMedia.Gif -> copy(proxy = proxy)
        is UiMedia.Audio -> copy(proxy = proxy)
        is UiMedia.Ugoira -> copy(proxy = proxy)
    }

@HiddenFromObjC
public fun UiRichText.withProxy(proxy: ProxyRef): UiRichText =
    copy(
        renderRuns = renderRuns.map { it.withProxy(proxy) }.toImmutableList(),
    )

private fun RenderContent.withProxy(proxy: ProxyRef): RenderContent =
    when (this) {
        is RenderContent.BlockImage -> {
            copy(proxy = proxy)
        }

        is RenderContent.Text -> {
            copy(
                runs =
                    runs
                        .map {
                            when (it) {
                                is RenderRun.Image -> {
                                    it.copy(proxy = proxy)
                                }

                                is RenderRun.Text -> {
                                    it.style.imageLink?.let { image ->
                                        it.copy(style = it.style.copy(imageLink = image.copy(proxy = proxy)))
                                    } ?: it
                                }
                            }
                        }.toImmutableList(),
            )
        }
    }

@HiddenFromObjC
public fun UiProfile.withProxy(proxy: ProxyRef): UiProfile = filterPluginMedia(pluginId).bindProfileProxy(proxy)

private fun UiProfile.bindProfileProxy(proxy: ProxyRef): UiProfile =
    copy(
        avatar = avatar?.copy(proxy = proxy),
        banner = banner?.copy(proxy = proxy),
        nameInternal = nameInternal.withProxy(proxy),
        description = description?.withProxy(proxy),
        bottomContent =
            when (val content = bottomContent) {
                is UiProfile.BottomContent.Fields -> {
                    content.copy(
                        fields = content.fields.mapValues { it.value.withProxy(proxy) }.toImmutableMap(),
                    )
                }

                is UiProfile.BottomContent.Iconify -> {
                    content.copy(
                        items = content.items.mapValues { it.value.withProxy(proxy) }.toImmutableMap(),
                    )
                }

                null -> {
                    null
                }
            },
    )

private fun UiTranslatableText.withProxy(proxy: ProxyRef): UiTranslatableText =
    copy(
        original = original.withProxy(proxy),
        translation = translation?.withProxy(proxy),
    )

// Apply after cache/translation hydration as well as mapping. Viewer identity never enters shared cache bytes.
@HiddenFromObjC
@Suppress("UNCHECKED_CAST")
public fun <T : UiTimelineV2> T.bindMediaProxy(): T {
    val proxy = ProxyRef.forAccountType(accountType)
    return when (this) {
        is UiTimelineV2.Post -> {
            filterPluginMedia(pluginId).let { post ->
                post.copy(
                    images = post.images.map { it.withProxy(proxy) }.toImmutableList(),
                    user = post.user?.withProxy(proxy),
                    content = post.content.withProxy(proxy),
                    contentWarning = post.contentWarning?.withProxy(proxy),
                    card = post.card?.let { it.copy(media = it.media?.withProxy(proxy)) },
                    emojiReactions = post.emojiReactions.map { it.copy(proxy = proxy) }.toImmutableList(),
                )
            }
        }

        is UiTimelineV2.Message -> {
            copy(user = user?.withProxy(proxy))
        }

        is UiTimelineV2.Feed -> {
            copy(
                descriptionHtml = descriptionHtml?.takeIf { pluginId == null || PluginMediaPermissions.isAllowed(pluginId) },
                media = media?.takeIf { it.allowedForPlugin(pluginId) }?.copy(proxy = proxy),
                source = source.copy(icon = source.icon?.takeIf { UiMedia.Image(it).allowedForPlugin(pluginId) }, proxy = proxy),
            )
        }

        is UiTimelineV2.User -> {
            copy(value = value.withProxy(proxy), message = message?.bindMediaProxy())
        }

        is UiTimelineV2.UserList -> {
            copy(
                users = users.map { it.withProxy(proxy) }.toImmutableList(),
                message = message?.bindMediaProxy(),
                post = post?.bindMediaProxy(),
            )
        }

        is UiTimelineV2.TimelinePostItem -> {
            copy(
                post = post.bindMediaProxy(),
                presentation =
                    presentation.copy(
                        message = presentation.message?.bindMediaProxy(),
                        inlineParents = presentation.inlineParents.map { it.bindMediaProxy() }.toImmutableList(),
                        quotes = presentation.quotes.map { it.bindMediaProxy() }.toImmutableList(),
                        repost = presentation.repost?.bindMediaProxy(),
                    ),
            )
        }

        is UiTimelineV2.Gap -> {
            this
        }
    } as T
}

@HiddenFromObjC
public fun UiList.withProxy(proxy: ProxyRef): UiList =
    when (this) {
        is UiList.List -> copy(proxy = proxy, creator = creator?.withProxy(proxy))
        is UiList.Feed -> copy(proxy = proxy, creator = creator?.withProxy(proxy))
        is UiList.Channel -> copy(proxy = proxy, description = description?.withProxy(proxy))
        is UiList.Antenna -> this
    }

@HiddenFromObjC
public fun UiArticle.withProxy(proxy: ProxyRef): UiArticle =
    copy(
        cover = cover?.takeIf { it.allowedForPlugin(pluginId) }?.copy(proxy = proxy),
        author =
            when (val author = author) {
                is UiArticleAuthor.Profile -> {
                    author.copy(profile = author.profile.filterPluginMedia(pluginId).withProxy(proxy))
                }

                is UiArticleAuthor.Rss -> {
                    author.copy(
                        iconUrl = author.iconUrl?.takeIf { UiMedia.Image(it).allowedForPlugin(pluginId) },
                        proxy = proxy,
                    )
                }

                null -> {
                    null
                }
            },
        content =
            content.copy(
                blocks =
                    content.blocks
                        .mapNotNull { block ->
                            when (block) {
                                is UiArticleBlock.Image -> {
                                    if (block.media.allowedForPlugin(
                                            pluginId,
                                        )
                                    ) {
                                        block.copy(media = block.media.copy(proxy = proxy))
                                    } else {
                                        null
                                    }
                                }

                                is UiArticleBlock.Video -> {
                                    if (block.media.allowedForPlugin(
                                            pluginId,
                                        )
                                    ) {
                                        block.copy(media = block.media.copy(proxy = proxy))
                                    } else {
                                        null
                                    }
                                }

                                is UiArticleBlock.Text -> {
                                    val text = block.richText.filterPluginMedia(pluginId).withProxy(proxy)
                                    block.copy(content = text.renderRuns.single() as RenderContent.Text)
                                }

                                is UiArticleBlock.Embed -> {
                                    block.takeIf { pluginId == null }?.copy(proxy = proxy)
                                }

                                is UiArticleBlock.File -> {
                                    block.takeIf { it.media.allowedForPlugin(pluginId) }?.copy(proxy = proxy)
                                }

                                is UiArticleBlock.ContentGate -> {
                                    block
                                }
                            }
                        }.toImmutableList(),
            ),
    )

@HiddenFromObjC
public fun UiDMItem.withProxy(proxy: ProxyRef): UiDMItem = filterPluginMedia(pluginId).bindDMProxy(proxy)

private fun UiDMItem.bindDMProxy(proxy: ProxyRef): UiDMItem =
    copy(
        user = user.withProxy(proxy),
        content =
            when (val message = content) {
                is UiDMItem.Message.Media -> message.copy(media = message.media.withProxy(proxy))
                is UiDMItem.Message.Text -> message.copy(text = message.text.withProxy(proxy))
                is UiDMItem.Message.Status -> message.copy(status = message.status.bindMediaProxy())
                UiDMItem.Message.Deleted -> message
            },
    )

@HiddenFromObjC
public fun UiDMRoom.withProxy(proxy: ProxyRef): UiDMRoom =
    copy(
        users = users.map { it.filterPluginMedia(pluginId).withProxy(proxy) }.toImmutableList(),
        lastMessage = lastMessage?.filterPluginMedia(pluginId)?.withProxy(proxy),
    )
