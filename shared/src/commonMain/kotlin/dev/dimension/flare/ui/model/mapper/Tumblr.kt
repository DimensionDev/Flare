package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.jsonObjectOrNull
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.data.network.tumblr.model.TumblrBlog
import dev.dimension.flare.data.network.tumblr.model.TumblrPost
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

internal fun TumblrBlog.toUiProfile(): UiProfile {
    val key = MicroBlogKey(name, TUMBLR_HOST)
    return UiProfile(
        key = key,
        handle = UiHandle(name, TUMBLR_HOST),
        avatar = tumblrAvatarUrl(name),
        nameInternal = (title ?: name).toUiPlainText(),
        platformType = PlatformType.Tumblr,
        clickEvent = ClickEvent.Deeplink(DeeplinkRoute.Profile.User(AccountType.Specific(key), key)),
        banner = null,
        description = description?.takeIf { it.isNotBlank() }?.toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = followers ?: 0L,
                followsCount = 0L,
                statusesCount = posts ?: 0L,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

internal fun TumblrPost.toUi(accountType: AccountType): UiTimelineV2.Post {
    val blogKey = MicroBlogKey(blogName, TUMBLR_HOST)
    val statusKey = MicroBlogKey(idString ?: id.toString(), blogName)
    val accountKey = (accountType as? AccountType.Specific)?.accountKey
    val isFromMe = accountKey?.id == blogName
    val shareUrl = postUrl ?: "https://$blogName.tumblr.com/post/${idString ?: id}"
    val images = extractImages(content)
    val richText = content.toTumblrRichText(summary)
    return UiTimelineV2.Post(
        platformType = PlatformType.Tumblr,
        images = images.toPersistentList(),
        sensitive = false,
        contentWarning = null,
        user =
            UiProfile(
                key = blogKey,
                handle = UiHandle(blogName, TUMBLR_HOST),
                avatar = tumblrAvatarUrl(blogName),
                nameInternal = blogName.toUiPlainText(),
                platformType = PlatformType.Tumblr,
                clickEvent = ClickEvent.Deeplink(DeeplinkRoute.Profile.User(accountType, blogKey)),
                banner = null,
                description = null,
                matrices = UiProfile.Matrices(0, 0, 0),
                mark = persistentListOf(),
                bottomContent = null,
            ),
        content = richText,
        actions =
            buildList {
                if (canReblog && accountKey != null) {
                    add(
                        ActionMenu.tumblrReblog(
                            statusKey = statusKey,
                            canReblog = canReblog,
                            accountKey = accountKey,
                        ),
                    )
                }
                if (canLike && accountKey != null) {
                    add(
                        ActionMenu.tumblrLike(
                            statusKey = statusKey,
                            liked = liked,
                            accountKey = accountKey,
                        ),
                    )
                }
                add(
                    ActionMenu.Group(
                        displayItem =
                            ActionMenu.Item(
                                icon = UiIcon.More,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                            ),
                        actions =
                            buildList {
                                add(
                                    ActionMenu.Item(
                                        icon = UiIcon.Share,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Status.ShareSheet(
                                                    statusKey = statusKey,
                                                    accountType = accountType,
                                                    shareUrl = shareUrl,
                                                ),
                                            ),
                                    ),
                                )
                                if (isFromMe) {
                                    add(
                                        ActionMenu.Item(
                                            icon = UiIcon.Delete,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                            color = ActionMenu.Item.Color.Red,
                                            clickEvent =
                                                ClickEvent.Deeplink(
                                                    DeeplinkRoute.Status.DeleteConfirm(
                                                        accountType = accountType,
                                                        statusKey = statusKey,
                                                    ),
                                                ),
                                        ),
                                    )
                                } else if (accountKey != null) {
                                    add(ActionMenu.Divider)
                                    addAll(
                                        userActionsMenu(
                                            accountKey = accountKey,
                                            userKey = blogKey,
                                            handle = blogKey.id,
                                        ),
                                    )
                                }
                            }.toPersistentList(),
                    ),
                )
            }.toPersistentList(),
        poll = null,
        statusKey = statusKey,
        card = postUrl?.let { UiCard(title = summary ?: blogName, url = it, media = null, description = null) },
        createdAt = Instant.fromEpochSeconds(timestamp ?: 0L).toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = UiTimelineV2.Post.Visibility.Public,
        replyToHandle = null,
        references = persistentListOf(),
        parents = persistentListOf(),
        quote = persistentListOf(),
        message = null,
        clickEvent = ClickEvent.Deeplink(DeeplinkRoute.Status.Detail(statusKey, accountType)),
        accountType = accountType,
    )
}

private fun JsonArray?.toTumblrRichText(fallback: String?): dev.dimension.flare.ui.render.UiRichText {
    val renderBlocks: List<RenderContent> =
        this
            ?.mapNotNull { block ->
                val obj = block.jsonObjectOrNull ?: return@mapNotNull null
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "text" ->
                        RenderContent.Text(
                            runs =
                                persistentListOf(
                                    RenderRun.Text(
                                        text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                    ),
                                ),
                        )

                    "image" -> {
                        val mediaUrl =
                            obj["media"]
                                ?.jsonArray
                                ?.firstOrNull()
                                ?.jsonObjectOrNull
                                ?.get("url")
                                ?.jsonPrimitive
                                ?.contentOrNull
                        mediaUrl?.let {
                            RenderContent.BlockImage(url = it, href = null)
                        }
                    }

                    else -> null
                }
            }.orEmpty()
    return if (renderBlocks.isNotEmpty()) {
        uiRichTextOf(renderBlocks)
    } else {
        (fallback ?: "").toUiPlainText()
    }
}

private fun extractImages(content: JsonArray?): List<UiMedia> =
    content
        ?.mapNotNull { block ->
            val obj = block.jsonObjectOrNull ?: return@mapNotNull null
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "image") {
                return@mapNotNull null
            }
            val media =
                obj["media"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObjectOrNull
            val url = media?.get("url")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            UiMedia.Image(url)
        }.orEmpty()

internal const val TUMBLR_HOST: String = "tumblr.com"

internal fun tumblrAvatarUrl(blogName: String): String = "https://api.tumblr.com/v2/blog/$blogName.tumblr.com/avatar/128"

internal fun ActionMenu.Companion.tumblrLike(
    statusKey: MicroBlogKey,
    liked: Boolean,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "tumblr_like_$statusKey",
        icon = if (liked) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (liked) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        color = if (liked) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(accountKey) {
                PostEvent.Tumblr.Like(
                    postKey = statusKey,
                    liked = liked,
                    accountKey = it,
                )
            },
    )

internal fun ActionMenu.Companion.tumblrReblog(
    statusKey: MicroBlogKey,
    canReblog: Boolean,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "tumblr_reblog_$statusKey",
        icon = if (canReblog) UiIcon.Retweet else UiIcon.Unretweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (canReblog) {
                    ActionMenu.Item.Text.Localized.Type.Retweet
                } else {
                    ActionMenu.Item.Text.Localized.Type.Unretweet
                },
            ),
        color = if (canReblog) null else ActionMenu.Item.Color.PrimaryColor,
        clickEvent =
            if (canReblog) {
                ClickEvent.event(accountKey) {
                    PostEvent.Tumblr.Reblog(
                        postKey = statusKey,
                        canReblog = true,
                        accountKey = it,
                    )
                }
            } else {
                ClickEvent.Noop
            },
    )
