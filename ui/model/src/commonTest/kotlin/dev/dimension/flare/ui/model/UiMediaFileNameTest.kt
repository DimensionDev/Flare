package dev.dimension.flare.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals

class UiMediaFileNameTest {
    @Test
    fun sanitizesStatusKeyAndHandleForFileNames() {
        val media = UiMedia.Image(url = "https://example.com/image.png")

        val fileName =
            media.getFileName(
                statusKey = "at://did:plc:4ifi6votp7ohf4qre6phovym/app.bsky.feed.post/3mh2lxk5wmc2b",
                userHandle = "alice/bob:bsky.social",
            )

        assertEquals(
            "at___did_plc_4ifi6votp7ohf4qre6phovym_app.bsky.feed.post_3mh2lxk5wmc2b_alice_bob_bsky.social.png",
            fileName,
        )
    }

    @Test
    fun handlesUrlWithQueryParams() {
        val media = UiMedia.Image(url = "https://example.com/image.jpg?name=orig&size=large")

        val fileName =
            media.getFileName(
                statusKey = "post123",
                userHandle = "alice",
            )

        assertEquals("post123_alice.jpg", fileName)
    }

    @Test
    fun handlesUrlWithoutExtensionAndAppendsFallback() {
        val media = UiMedia.Image(url = "https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:x/bafkrei")

        val fileName =
            media.getFileName(
                statusKey = "post123",
                userHandle = "alice",
            )

        assertEquals("post123_alice.jpg", fileName)
    }

    @Test
    fun handlesBlueskyFormatUrl() {
        val media = UiMedia.Image(url = "https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:x/bafkrei...@jpeg")

        val fileName =
            media.getFileName(
                statusKey = "post123",
                userHandle = "alice",
            )

        assertEquals("post123_alice.jpeg", fileName)
    }
}
