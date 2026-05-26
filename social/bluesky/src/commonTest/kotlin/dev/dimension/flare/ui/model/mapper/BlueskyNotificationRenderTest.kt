package dev.dimension.flare.ui.model.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.feed.Like
import app.bsky.feed.PostView
import app.bsky.feed.Repost
import app.bsky.notification.ListNotificationsNotification
import app.bsky.notification.ListNotificationsNotificationReason
import com.atproto.repo.StrongRef
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class BlueskyNotificationRenderTest {
    private val accountKey = MicroBlogKey(id = "me", host = "bsky.social")

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
    fun repostAndLike_renderMessageAndPost() {
        val repostedUri = AtUri("at://did:plc:post/app.bsky.feed.post/100")
        val referencedPost =
            createPostView(
                uri = repostedUri.atUri,
                author = createProfileBasic("post-author", "post-author.bsky.social"),
                text = "reposted content",
            )
        val references = persistentMapOf(repostedUri to referencedPost)

        val repostNotification =
            createNotification(
                reason = ListNotificationsNotificationReason.Repost,
                uri = "at://did:plc:noti/app.bsky.notification/1",
                author = createProfile("reposter", "reposter.bsky.social"),
                record =
                    bskyJson.encodeAsJsonContent(
                        Repost(
                            subject = StrongRef(uri = repostedUri, cid = Cid("cid-post")),
                            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                        ),
                    ),
            )
        val likeNotification =
            createNotification(
                reason = ListNotificationsNotificationReason.Like,
                uri = "at://did:plc:noti/app.bsky.notification/2",
                author = createProfile("liker", "liker.bsky.social"),
                record =
                    bskyJson.encodeAsJsonContent(
                        Like(
                            subject = StrongRef(uri = repostedUri, cid = Cid("cid-post")),
                            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                        ),
                    ),
            )

        val repostResult = listOf(repostNotification).render(accountKey, references).single()
        val repostUserList = assertIs<UiTimelineV2.UserList>(repostResult)
        assertNotNull(repostUserList.message)
        assertNotNull(repostUserList.post)
        assertEquals("reposted content", repostUserList.post.content.innerText)

        val likeResult = listOf(likeNotification).render(accountKey, references).single()
        val likeUserList = assertIs<UiTimelineV2.UserList>(likeResult)
        assertNotNull(likeUserList.message)
        assertNotNull(likeUserList.post)
        assertEquals("reposted content", likeUserList.post.content.innerText)
    }

    @Test
    fun follow_rendersMessageAndUsers() {
        val n1 =
            createNotification(
                reason = ListNotificationsNotificationReason.Follow,
                uri = "at://did:plc:noti/app.bsky.notification/f1",
                author = createProfile("follower1", "follower1.bsky.social"),
            )
        val n2 =
            createNotification(
                reason = ListNotificationsNotificationReason.Follow,
                uri = "at://did:plc:noti/app.bsky.notification/f2",
                author = createProfile("follower2", "follower2.bsky.social"),
            )

        val result = listOf(n1, n2).render(accountKey, references = persistentMapOf())
        val userList = assertIs<UiTimelineV2.UserList>(result.single())
        assertNotNull(userList.message)
        assertEquals(2, userList.users.size)
        assertEquals(setOf("did:plc:follower1", "did:plc:follower2"), userList.users.map { it.key.id }.toSet())
    }

    @Test
    fun postReasons_renderMessageAndPost() {
        val reasons =
            listOf(
                ListNotificationsNotificationReason.LikeViaRepost,
                ListNotificationsNotificationReason.RepostViaRepost,
                ListNotificationsNotificationReason.SubscribedPost,
                ListNotificationsNotificationReason.ContactMatch,
                ListNotificationsNotificationReason.Mention,
                ListNotificationsNotificationReason.Reply,
                ListNotificationsNotificationReason.Quote,
            )

        reasons.forEachIndexed { index, reason ->
            val uri = AtUri("at://did:plc:noti/app.bsky.notification/p$index")
            val post =
                createPostView(
                    uri = uri.atUri,
                    author = createProfileBasic("post$index", "post$index.bsky.social"),
                    text = "post-$index",
                )
            val notification =
                createNotification(
                    reason = reason,
                    uri = uri.atUri,
                    author = createProfile("author$index", "author$index.bsky.social"),
                )

            val result =
                listOf(notification).render(
                    accountKey = accountKey,
                    references = persistentMapOf(uri to post),
                )
            val renderedPost = assertIs<UiTimelineV2.Post>(result.single())
            assertEquals("post-$index", renderedPost.content.innerText)
            assertNotNull(renderedPost.message)
            assertEquals(
                "did:plc:author$index",
                renderedPost.message.user
                    ?.key
                    ?.id,
            )
        }
    }

    @Test
    fun otherReasons_renderUserAndMessage() {
        val reasons =
            listOf(
                ListNotificationsNotificationReason.StarterpackJoined,
                ListNotificationsNotificationReason.Verified,
                ListNotificationsNotificationReason.Unverified,
                ListNotificationsNotificationReason.Unknown("something-else"),
            )

        reasons.forEachIndexed { index, reason ->
            val notification =
                createNotification(
                    reason = reason,
                    uri = "at://did:plc:noti/app.bsky.notification/u$index",
                    author = createProfile("user$index", "user$index.bsky.social"),
                )
            val result = listOf(notification).render(accountKey, references = persistentMapOf())
            val user = assertIs<UiTimelineV2.User>(result.single())
            assertNotNull(user.message)
            assertEquals("did:plc:user$index", user.value.key.id)
            assertEquals(
                "did:plc:user$index",
                user.message.user
                    ?.key
                    ?.id,
            )
            assertTrue(user.statusKey.id.contains("/u$index"))
        }
    }

    private fun createNotification(
        reason: ListNotificationsNotificationReason,
        uri: String,
        author: ProfileView,
        record: JsonContent = jsonRecord("notification"),
    ): ListNotificationsNotification =
        ListNotificationsNotification(
            uri = AtUri(uri),
            cid = Cid("cid-${uri.substringAfterLast('/')}"),
            author = author,
            reason = reason,
            record = record,
            isRead = false,
            indexedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    private fun createProfile(
        did: String,
        handle: String,
    ): ProfileView =
        ProfileView(
            did = Did("did:plc:$did"),
            handle = Handle(handle),
            displayName = did,
            avatar = Uri("https://cdn.example.com/$did.png"),
        )

    private fun createProfileBasic(
        did: String,
        handle: String,
    ): ProfileViewBasic =
        ProfileViewBasic(
            did = Did("did:plc:$did"),
            handle = Handle(handle),
            displayName = did,
            avatar = Uri("https://cdn.example.com/$did.png"),
        )

    private fun createPostView(
        uri: String,
        author: ProfileViewBasic,
        text: String,
    ): PostView =
        PostView(
            uri = AtUri(uri),
            cid = Cid("cid-${uri.substringAfterLast('/')}"),
            author = author,
            record = jsonRecord(text),
            indexedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    private fun jsonRecord(text: String): JsonContent =
        bskyJson.encodeAsJsonContent(
            buildJsonObject {
                put("text", JsonPrimitive(text))
            },
        )
}
