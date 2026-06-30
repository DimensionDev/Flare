package dev.dimension.flare.common

import dev.dimension.flare.ui.model.UiMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class MediaFileNamePolicyTest {
    @Test
    fun statusMediaFileNameUsesStatusContextAndUrlExtension() {
        val media = UiMedia.Image(url = "https://example.com/image.png")

        val fileName =
            MediaFileNamePolicy.statusMediaFileName(
                statusKey = "at://did:plc:4ifi6votp7ohf4qre6phovym/app.bsky.feed.post/3mh2lxk5wmc2b",
                userHandle = "alice/bob:bsky.social",
                media = media,
            )

        assertEquals(
            "at___did_plc_4ifi6votp7ohf4qre6phovym_app.bsky.feed.post_3mh2lxk5wmc2b_alice_bob_bsky.social.png",
            fileName,
        )
    }

    @Test
    fun statusMediaFileNameHandlesBlueskyAtExtension() {
        val media = UiMedia.Image(url = "https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:x/bafkrei...@jpeg")

        val fileName =
            MediaFileNamePolicy.statusMediaFileName(
                statusKey = "post123",
                userHandle = "alice",
                media = media,
            )

        assertEquals("post123_alice.jpeg", fileName)
    }

    @Test
    fun rawMediaFileNameUsesUrlFileName() {
        val media = UiMedia.Image(url = "https://example.com/path/original@png?size=large")

        val fileName =
            MediaFileNamePolicy.rawMediaFileName(
                media = media,
            )

        assertEquals("original.png", fileName)
    }

    @Test
    fun rawMediaFileNameUsesFallbackExtension() {
        val media =
            UiMedia.Video(
                url = "https://example.com/media/original",
                thumbnailUrl = "https://example.com/media/original.jpg",
                description = null,
                height = 1080f,
                width = 1920f,
            )

        val fileName =
            MediaFileNamePolicy.rawMediaFileName(
                media = media,
            )

        assertEquals("original.mp4", fileName)
    }

    @Test
    fun rawMediaFileNameUsesAudioFallbackExtension() {
        val media =
            UiMedia.Audio(
                url = "https://example.com/media/original",
                description = null,
                previewUrl = null,
            )

        val fileName = MediaFileNamePolicy.rawMediaFileName(media)

        assertEquals("original.mp3", fileName)
    }

    @Test
    fun articleFileNameUsesNameBeforeUrl() {
        val fileName =
            MediaFileNamePolicy.articleFileName(
                name = "report",
                url = "https://example.com/download",
                extensionName = ".pdf",
            )

        assertEquals("report.pdf", fileName)
    }

    @Test
    fun articleFileNameFallsBackToUrlFileName() {
        val fileName =
            MediaFileNamePolicy.articleFileName(
                name = " ",
                url = "https://example.com/files/report.csv?token=abc",
                extensionName = "txt",
            )

        assertEquals("report.csv", fileName)
    }

    @Test
    fun safeDownloadFileNameReplacesPathAndControlCharacters() {
        val fileName =
            MediaFileNamePolicy.safeDownloadFileName(
                value = " report/2026\\draft\u0001.pdf ",
            )

        assertEquals("report_2026_draft_.pdf", fileName)
    }

    @Test
    fun safeLocalFileNameReplacesCommonFileSystemCharacters() {
        val fileName =
            MediaFileNamePolicy.safeLocalFileName(
                value = " status:host/post?2026.png ",
            )

        assertEquals("status_host_post_2026.png", fileName)
    }

    @Test
    fun sanitizeFileNameUsesStrictAsciiSet() {
        val fileName =
            MediaFileNamePolicy.sanitizeFileName(
                value = "at://did:plc:x/post 日本語.png",
            )

        assertEquals("at___did_plc_x_post____.png", fileName)
    }

    @Test
    fun sanitizeFileNameUsesFallbackForBlankValues() {
        val fileName =
            MediaFileNamePolicy.sanitizeFileName(
                value = "",
                fallback = "media",
            )

        assertEquals("media", fileName)
    }

    @Test
    fun sanitizeFileNameAllowsBlankFallback() {
        val fileName =
            MediaFileNamePolicy.sanitizeFileName(
                value = "",
                fallback = "",
            )

        assertEquals("", fileName)
    }

    @Test
    fun screenshotFileNameUsesSanitizedStatusKeyAndCurrentTimestamp() {
        val before = Clock.System.now().toEpochMilliseconds()
        val prefix = "status_host_status_123_"

        val fileName =
            MediaFileNamePolicy.screenshotFileName(
                statusKey = "host/status/123",
            )

        val after = Clock.System.now().toEpochMilliseconds()
        val timestamp =
            fileName
                .removePrefix(prefix)
                .removeSuffix(".png")
                .toLongOrNull()

        assertTrue(fileName.startsWith(prefix))
        assertTrue(fileName.endsWith(".png"))
        assertTrue(timestamp != null && timestamp in before..after)
    }
}
