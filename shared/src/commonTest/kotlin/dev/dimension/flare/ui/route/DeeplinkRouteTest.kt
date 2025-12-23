package dev.dimension.flare.ui.route

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeeplinkRouteTest {
    private fun DeeplinkRoute.toUri(): String = with(DeeplinkRoute.Companion) { this@toUri.toUri() }

    @Test
    fun testLogin() {
        val route = DeeplinkRoute.Login
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        if (route != parsed) {
            println("FAILURE DEBUG: Expected=$route, Actual=$parsed, URI=$uri")
        }
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusDetail() {
        val route =
            DeeplinkRoute.Status.Detail(
                statusKey = MicroBlogKey("status123", "example.com"),
                accountType = AccountType.Active,
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusVVOComment() {
        val route =
            DeeplinkRoute.Status.VVOComment(
                commentKey = MicroBlogKey("comment123", "example.com"),
                accountType = AccountType.Guest,
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusVVOStatus() {
        val route =
            DeeplinkRoute.Status.VVOStatus(
                statusKey = MicroBlogKey("status456", "example.com"),
                accountType = AccountType.Active,
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusAddReaction() {
        val route =
            DeeplinkRoute.Status.AddReaction(
                statusKey = MicroBlogKey("status789", "example.com"),
                accountType = AccountType.Active,
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusAltText() {
        val route = DeeplinkRoute.Status.AltText(text = "Some alt text")
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusBlueskyReport() {
        val route =
            DeeplinkRoute.Status.BlueskyReport(
                statusKey = MicroBlogKey("statusBS", "bsky.app"),
                accountType = AccountType.Active,
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusDeleteConfirm() {
        val route =
            DeeplinkRoute.Status.DeleteConfirm(
                statusKey = MicroBlogKey("delStatus", "example.com"),
                accountType = AccountType.Active,
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusMastodonReport() {
        val route =
            DeeplinkRoute.Status.MastodonReport(
                userKey = MicroBlogKey("user1", "mstdn.io"),
                statusKey = MicroBlogKey("status1", "mstdn.io"),
                accountType = AccountType.Active,
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testStatusMisskeyReport() {
        val route =
            DeeplinkRoute.Status.MisskeyReport(
                userKey = MicroBlogKey("userMK", "misskey.io"),
                statusKey = null,
                accountType = AccountType.Guest,
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testRssDetail() {
        val route = DeeplinkRoute.Rss.Detail(url = "https://example.com/rss")
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testSearch() {
        val route =
            DeeplinkRoute.Search(
                accountType = AccountType.Active,
                query = "kotlin",
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testComposeNew() {
        val route = DeeplinkRoute.Compose.New(accountType = AccountType.Active)
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testComposeReply() {
        val route =
            DeeplinkRoute.Compose.Reply(
                accountKey = MicroBlogKey("acc1", "example.com"),
                statusKey = MicroBlogKey("statusR", "example.com"),
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testComposeQuote() {
        val route =
            DeeplinkRoute.Compose.Quote(
                accountKey = MicroBlogKey("acc1", "example.com"),
                statusKey = MicroBlogKey("statusQ", "example.com"),
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testComposeVVOReplyComment() {
        val route =
            DeeplinkRoute.Compose.VVOReplyComment(
                accountKey = MicroBlogKey("accVVO", "vvo.com"),
                replyTo = MicroBlogKey("replyTo", "vvo.com"),
                rootId = "root123",
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testMediaImage() {
        val route =
            DeeplinkRoute.Media.Image(
                uri = "https://image.com/1.jpg",
                previewUrl = "https://image.com/1_preview.jpg",
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testMediaStatusMedia() {
        val route =
            DeeplinkRoute.Media.StatusMedia(
                statusKey = MicroBlogKey("stMedia", "example.com"),
                accountType = AccountType.Active,
                index = 2,
                preview = "preview_data",
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testMediaPodcast() {
        val route =
            DeeplinkRoute.Media.Podcast(
                accountType = AccountType.Active,
                id = "podcast123",
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testProfileUser() {
        val route =
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Active,
                userKey = MicroBlogKey("userP", "example.com"),
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testProfileUserNameWithHost() {
        val route =
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Active,
                userName = "user",
                host = "example.com",
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testDeepLinkAccountPicker() {
        val route =
            DeeplinkRoute.DeepLinkAccountPicker(
                originalUrl = "https://original.com",
                data =
                    persistentMapOf(
                        MicroBlogKey("acc1", "host1") to DeeplinkRoute.Login,
                    ),
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testOpenLinkDirectly() {
        val route = DeeplinkRoute.OpenLinkDirectly(url = "https://direct.com")
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testEditUserList() {
        val route =
            DeeplinkRoute.EditUserList(
                accountKey = MicroBlogKey("acc1", "host1"),
                userKey = MicroBlogKey("user1", "host1"),
            )
        val uri = route.toUri()
        val parsed = DeeplinkRoute.parse(uri)
        assertEquals(route, parsed)
    }

    @Test
    fun testCallbackConstants() {
        assertEquals("flare://Callback/SignIn/Mastodon", DeeplinkRoute.Companion.Callback.MASTODON)
        assertEquals("flare://Callback/SignIn/Misskey", DeeplinkRoute.Companion.Callback.MISSKEY)
        assertEquals("flare://Callback/SignIn/Bluesky", DeeplinkRoute.Companion.Callback.BLUESKY)
    }

    @Test
    fun testInvalidUri() {
        assertNull(DeeplinkRoute.parse("invalid://uri"))
        assertNull(DeeplinkRoute.parse("flare://invalid_hex"))
    }

    @Test
    fun testLoginJson() {
        val route = DeeplinkRoute.Login
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(DeeplinkRoute.serializer(), route)
        val parsed =
            kotlinx.serialization.json.Json
                .decodeFromString(DeeplinkRoute.serializer(), json)
        assertEquals(route, parsed)
    }
}
