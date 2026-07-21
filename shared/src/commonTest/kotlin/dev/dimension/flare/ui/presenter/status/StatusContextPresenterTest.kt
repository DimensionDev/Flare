package dev.dimension.flare.ui.presenter.status

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class StatusContextPresenterTest {
    private val accountKey = MicroBlogKey("me", "mastodon.example")
    private val accountType = AccountType.Specific(accountKey)
    private val detailKey = MicroBlogKey("detail", "mastodon.example")

    @Test
    fun filterDetailParents_keepsInlineParentsOnDetailPost() {
        val parent =
            createPost(
                id = "parent",
                createdAt = "2024-01-01T00:00:00Z",
            )
        val detail =
            createItem(
                post =
                    createPost(
                        id = detailKey.id,
                        createdAt = "2024-01-01T00:01:00Z",
                    ),
                parents = listOf(parent),
            )

        val filtered =
            detail.filterDetailParents(
                statusKey = detailKey,
                currentCreatedAt = null,
            )

        val post = filtered as UiTimelineV2.TimelinePostItem
        assertEquals(listOf(parent.statusKey), post.presentation.inlineParents.map { it.statusKey })
    }

    @Test
    fun filterDetailParents_keepsInlineParentsOnDescendants() {
        val olderParent =
            createPost(
                id = "older-parent",
                createdAt = "2024-01-01T00:00:00Z",
            )
        val detail =
            createPost(
                id = detailKey.id,
                createdAt = "2024-01-01T00:01:00Z",
            )
        val newerParent =
            createPost(
                id = "newer-parent",
                createdAt = "2024-01-01T00:02:00Z",
            )
        val descendant =
            createItem(
                post =
                    createPost(
                        id = "descendant",
                        createdAt = "2024-01-01T00:03:00Z",
                    ),
                parents = listOf(olderParent, detail, newerParent),
            )

        val filtered =
            descendant.filterDetailParents(
                statusKey = detailKey,
                currentCreatedAt = detail.createdAt,
            )

        val post = filtered as UiTimelineV2.TimelinePostItem
        assertEquals(
            listOf(olderParent.statusKey, detail.statusKey, newerParent.statusKey),
            post.presentation.inlineParents.map { it.statusKey },
        )
    }

    private fun createPost(
        id: String,
        createdAt: String,
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformType = PlatformType.Mastodon,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText(id.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey(id, "mastodon.example"),
            card = null,
            createdAt = Instant.parse(createdAt).toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references = persistentListOf(),
            clickEvent = ClickEvent.Noop,
            accountType = accountType,
        )

    private fun createItem(
        post: UiTimelineV2.Post,
        parents: List<UiTimelineV2.Post>,
    ): UiTimelineV2.TimelinePostItem =
        UiTimelineV2.TimelinePostItem(
            post = post,
            presentation =
                UiTimelineV2.PostPresentation(
                    inlineParents = parents.toPersistentList(),
                ),
        )
}
