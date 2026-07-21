package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import dev.dimension.flare.data.network.misskey.api.model.DriveFileProperties
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.data.network.misskey.api.model.UserLite
import dev.dimension.flare.data.network.misskey.api.model.Visibility
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.postEventOrNull
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MisskeyRenderTest {
    private val accountKey = MicroBlogKey(id = "me", host = "misskey.io")

    @BeforeTest
    fun setup() {
        startKoin {
            modules(
                module {
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun mediaIsRecognized() {
        val note =
            createNote(
                id = "note-media",
                user = createUser("user-media"),
                text = "has media",
                files =
                    listOf(
                        createFile(
                            id = "file-image",
                            type = "image/png",
                            url = "https://cdn.misskey.io/image.png",
                            thumbnailUrl = "https://cdn.misskey.io/image-thumb.png",
                            width = 800.0,
                            height = 600.0,
                            isSensitive = true,
                        ),
                        createFile(
                            id = "file-video",
                            type = "video/mp4",
                            url = "https://cdn.misskey.io/video.mp4",
                            thumbnailUrl = "https://cdn.misskey.io/video-thumb.jpg",
                            width = 1920.0,
                            height = 1080.0,
                        ),
                    ),
            )

        val rendered = rootPostOf(note.render(accountKey))
        assertEquals(2, rendered.images.size)

        val image = assertIs<UiMedia.Image>(rendered.images[0])
        assertEquals("https://cdn.misskey.io/image.png", image.url)
        assertEquals("https://cdn.misskey.io/image-thumb.png", image.previewUrl)
        assertEquals(800f, image.width)
        assertEquals(600f, image.height)
        assertTrue(image.sensitive)

        val video = assertIs<UiMedia.Video>(rendered.images[1])
        assertEquals("https://cdn.misskey.io/video.mp4", video.url)
        assertEquals("https://cdn.misskey.io/video-thumb.jpg", video.thumbnailUrl)
        assertEquals(1920f, video.width)
        assertEquals(1080f, video.height)
    }

    @Test
    fun quoteIsRecognized() {
        val quoted =
            createNote(
                id = "note-quoted",
                user = createUser("user-quoted"),
                text = "quoted content",
            )
        val note =
            createNote(
                id = "note-main",
                user = createUser("user-main"),
                text = "main content",
                renote = quoted,
            )

        val rendered = timelinePostItemOf(note.render(accountKey))
        assertEquals(1, rendered.presentation.quotes.size)
        assertEquals(
            "quoted content",
            rendered.presentation.quotes
                .first()
                .content.original.innerText,
        )
    }

    @Test
    fun parentsAreRecognized() {
        val parent =
            createNote(
                id = "note-parent",
                user = createUser("user-parent"),
                text = "parent content",
            )
        val child =
            createNote(
                id = "note-child",
                user = createUser("user-child"),
                text = "child content",
                reply = parent,
                replyId = parent.id,
            )

        val rendered = rootPostOf(child.render(accountKey))
        assertEquals(
            listOf("note-parent"),
            rendered.references
                .filter { it.type == ReferenceType.Reply }
                .map { it.statusKey.id },
        )
    }

    @Test
    fun reactionChipKeepsTotalCountWhenReacting() {
        val note =
            createNote(
                id = "note-reactions",
                user = createUser("user-reactions"),
                text = "react to me",
                reactions = mapOf("👍" to 3L, "🎉" to 2L),
            )

        val rendered = rootPostOf(note.render(accountKey))
        val action =
            rendered.actions
                .filterIsInstance<ActionMenu.Item>()
                .first { it.actionFamily == PostActionFamily.React }
        val reaction = rendered.emojiReactions.first { it.name == "👍" }
        val event =
            assertIs<PostEvent.Misskey.React>(
                assertNotNull(reaction.clickEvent.postEventOrNull()).postEvent,
            )

        assertEquals(5L, action.count?.value)
        assertEquals(6L, event.nextActionMenu().count?.value)
    }

    @Test
    fun repostShowsMessageAndUsesRepostedContent() {
        val original =
            createNote(
                id = "note-original",
                user = createUser("user-original"),
                text = "original content",
            )
        val repost =
            createNote(
                id = "note-repost-wrapper",
                user = createUser("user-reposter"),
                text = null,
                renote = original,
            )

        val rendered = timelinePostItemOf(repost.render(accountKey))
        val message = assertNotNull(rendered.presentation.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
        val repostInternal = assertNotNull(rendered.presentation.repost)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertEquals("user-reposter", message.user?.key?.id)
        assertTrue(
            rendered.post.content.original.innerText
                .isBlank(),
        )
        assertEquals("note-repost-wrapper", rendered.statusKey.id)
        assertEquals("original content", repostInternal.content.original.innerText)
        assertEquals("note-original", repostInternal.statusKey.id)
    }

    @Test
    fun repostWithQuoteShowsMessagePostAndQuote() {
        val quoted =
            createNote(
                id = "note-quoted-2",
                user = createUser("user-quoted-2"),
                text = "quoted payload",
            )
        val original =
            createNote(
                id = "note-original-2",
                user = createUser("user-original-2"),
                text = "original payload",
                renote = quoted,
            )
        val repost =
            createNote(
                id = "note-repost-wrapper-2",
                user = createUser("user-reposter-2"),
                text = null,
                renote = original,
            )

        val rendered = timelinePostItemOf(repost.render(accountKey))
        val message = assertNotNull(rendered.presentation.message)
        val type = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
        val repostInternal = assertNotNull(rendered.presentation.repost)

        assertEquals(UiTimelineV2.Message.Type.Localized.MessageId.Repost, type.data)
        assertTrue(
            rendered.post.content.original.innerText
                .isBlank(),
        )
        assertEquals("note-repost-wrapper-2", rendered.statusKey.id)
        assertEquals("original payload", repostInternal.content.original.innerText)
        assertEquals(1, rendered.presentation.quotes.size)
        assertEquals(
            "quoted payload",
            rendered.presentation.quotes
                .first()
                .content.original.innerText,
        )
    }

    @Test
    fun mfmQuoteDoesNotIntroduceExtraBlankLines() {
        val note =
            createNote(
                id = "note-mfm-quote",
                user = createUser("user-mfm-quote"),
                text = "before\n\n> quoted\n\nafter",
            )

        val rendered = rootPostOf(note.render(accountKey))

        assertEquals("beforequotedafter", rendered.content.original.innerText)
    }

    @Test
    fun latestMisskeyNotificationTypesAreLocalized() {
        val user = createUser("user-notification")

        val cases =
            listOf(
                "scheduledNotePosted" to UiTimelineV2.Message.Type.Localized.MessageId.ScheduledNotePosted,
                "scheduledNotePostFailed" to UiTimelineV2.Message.Type.Localized.MessageId.ScheduledNotePostFailed,
                "roleAssigned" to UiTimelineV2.Message.Type.Localized.MessageId.RoleAssigned,
                "chatRoomInvitationReceived" to UiTimelineV2.Message.Type.Localized.MessageId.ChatRoomInvitationReceived,
                "exportCompleted" to UiTimelineV2.Message.Type.Localized.MessageId.ExportCompleted,
                "test" to UiTimelineV2.Message.Type.Localized.MessageId.Test,
                "login" to UiTimelineV2.Message.Type.Localized.MessageId.Login,
                "createToken" to UiTimelineV2.Message.Type.Localized.MessageId.CreateToken,
            )

        cases.forEach { (type, expected) ->
            val notification =
                Notification(
                    id = "notification-$type",
                    createdAt = "2024-01-01T00:00:00Z",
                    type = type,
                    user = user,
                )

            val rendered = notification.render(accountKey)
            val message =
                when (rendered) {
                    is UiTimelineV2.Message -> rendered

                    is UiTimelineV2.TimelinePostItem -> assertNotNull(rendered.presentation.message)

                    is UiTimelineV2.Post -> error("Expected post notification to carry a presentation message")

                    is UiTimelineV2.User -> assertNotNull(rendered.message)

                    is UiTimelineV2.Feed,
                    is UiTimelineV2.UserList,
                    -> error("Unexpected rendered timeline item: ${rendered::class.simpleName}")
                }
            val localized = assertIs<UiTimelineV2.Message.Type.Localized>(message.type)
            assertEquals(expected, localized.data)
        }
    }

    private fun createUser(id: String): UserLite =
        UserLite(
            id = id,
            username = id,
            name = id,
            host = null,
            avatarUrl = "https://misskey.io/$id.png",
        )

    private fun createFile(
        id: String,
        type: String,
        url: String,
        thumbnailUrl: String,
        width: Double,
        height: Double,
        isSensitive: Boolean = false,
    ): DriveFile =
        DriveFile(
            id = id,
            createdAt = "2024-01-01T00:00:00Z",
            name = id,
            type = type,
            md5 = "md5-$id",
            propertySize = 1.0,
            isSensitive = isSensitive,
            properties = DriveFileProperties(width = width, height = height),
            url = url,
            thumbnailUrl = thumbnailUrl,
            comment = null,
        )

    private fun createNote(
        id: String,
        user: UserLite,
        text: String?,
        reply: Note? = null,
        replyId: String? = null,
        renote: Note? = null,
        files: List<DriveFile>? = null,
        reactions: Map<String, Long> = emptyMap(),
    ): Note =
        Note(
            id = id,
            createdAt = "2024-01-01T00:00:00Z",
            text = text,
            userId = user.id,
            user = user,
            visibility = Visibility.Public,
            reactions = reactions,
            renoteCount = 0.0,
            repliesCount = 0.0,
            replyId = replyId,
            renoteId = renote?.id,
            reply = reply,
            renote = renote,
            files = files,
        )

    private fun rootPostOf(item: UiTimelineV2): UiTimelineV2.Post =
        when (item) {
            is UiTimelineV2.TimelinePostItem -> item.post
            is UiTimelineV2.Post -> item
            else -> error("Expected post timeline item, got ${item::class.simpleName}")
        }

    private fun timelinePostItemOf(item: UiTimelineV2): UiTimelineV2.TimelinePostItem = assertIs<UiTimelineV2.TimelinePostItem>(item)
}
