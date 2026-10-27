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
public fun UiProfile.withProxy(proxy: ProxyRef): UiProfile =
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
            copy(
                images = images.map { it.withProxy(proxy) }.toImmutableList(),
                user = user?.withProxy(proxy),
                content = content.withProxy(proxy),
                contentWarning = contentWarning?.withProxy(proxy),
                card = card?.copy(media = card.media?.withProxy(proxy)),
                emojiReactions = emojiReactions.map { it.copy(proxy = proxy) }.toImmutableList(),
            )
        }

        is UiTimelineV2.Message -> {
            copy(user = user?.withProxy(proxy))
        }

        is UiTimelineV2.Feed -> {
            copy(media = media?.copy(proxy = proxy), source = source.copy(proxy = proxy))
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
        cover = cover?.copy(proxy = proxy),
        author =
            when (val author = author) {
                is UiArticleAuthor.Profile -> author.copy(profile = author.profile.withProxy(proxy))
                is UiArticleAuthor.Rss -> author.copy(proxy = proxy)
                null -> null
            },
        content =
            content.copy(
                blocks =
                    content.blocks
                        .map { block ->
                            when (block) {
                                is UiArticleBlock.Image -> block.copy(media = block.media.copy(proxy = proxy))
                                is UiArticleBlock.Video -> block.copy(media = block.media.copy(proxy = proxy))
                                is UiArticleBlock.Text -> block.copy(content = block.content.withProxy(proxy) as RenderContent.Text)
                                is UiArticleBlock.Embed -> block.copy(proxy = proxy)
                                is UiArticleBlock.File -> block.copy(proxy = proxy)
                                is UiArticleBlock.ContentGate -> block
                            }
                        }.toImmutableList(),
            ),
    )

@HiddenFromObjC
public fun UiDMItem.withProxy(proxy: ProxyRef): UiDMItem =
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
        users = users.map { it.withProxy(proxy) }.toImmutableList(),
        lastMessage = lastMessage?.withProxy(proxy),
    )
