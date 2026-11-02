package dev.dimension.flare.common

import dev.dimension.flare.ui.model.UiMedia
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class MediaFileNamePolicyTest {
    @Test
    fun videoDownloadUsesItsOwnUrlAndMp4FileName() {
        val media =
            UiMedia.Video(
                url = "https://video.example/playlist.m3u8",
                thumbnailUrl = "",
                description = null,
                height = 0f,
                width = 0f,
                downloadUrl = "https://pds.example/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3Aauthor&cid=video",
            )

        assertEquals(media.downloadUrl, media.urlForDownload)
        assertEquals("https://video.example/playlist.m3u8", media.url)
        assertEquals("post_alice_01.mp4", MediaFileNamePolicy.statusMediaFileName("post", "alice", media, 0))
        assertEquals("media.mp4", MediaFileNamePolicy.rawMediaFileName(media))
        assertEquals(media, Json.decodeFromString<UiMedia.Video>(Json.encodeToString(media)))
    }

    @Test
    fun oldCachedVideoDefaultsToItsOriginalUrlForDownloads() {
        val media =
            Json.decodeFromString<UiMedia.Video>(
                """{"url":"https://example.com/video.mp4","thumbnailUrl":"","description":null,"height":0,"width":0}""",
            )

        assertEquals(null, media.downloadUrl)
        assertEquals(media.url, media.urlForDownload)
        assertEquals("video.mp4", MediaFileNamePolicy.rawMediaFileName(media))
    }

    @Test
    fun alternateDownloadUrlControlsTheVideoExtension() {
        val media =
            UiMedia.Video(
                url = "https://video.example/playlist.m3u8",
                thumbnailUrl = "",
                description = null,
                height = 0f,
                width = 0f,
                downloadUrl = "https://video.example/original.mov?token=abc",
            )

        assertEquals("post_alice_01.mov", MediaFileNamePolicy.statusMediaFileName("post", "alice", media, 0))
        assertEquals("original.mov", MediaFileNamePolicy.rawMediaFileName(media))
    }

    @Test
    fun statusMediaFileNameUsesStatusContextAndUrlExtension() {
        val media = UiMedia.Image(url = "https://example.com/image.png")

        val fileName =
            MediaFileNamePolicy.statusMediaFileName(
                statusKey = "at://did:plc:4ifi6votp7ohf4qre6phovym/app.bsky.feed.post/3mh2lxk5wmc2b",
                userHandle = "alice/bob:bsky.social",
                media = media,
                mediaIndex = 0,
            )

        assertEquals(
            "at___did_plc_4ifi6votp7ohf4qre6phovym_app.bsky.feed.post_3mh2lxk5wmc2b_alice_bob_bsky.social_01.png",
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
                mediaIndex = 1,
            )

        assertEquals("post123_alice_02.jpeg", fileName)
    }

    @Test
    fun statusMediaFileNameDistinguishesImagesFromTheSameStatus() {
        val medias =
            listOf(
                UiMedia.Image(url = "https://pbs.twimg.com/media/first.jpg"),
                UiMedia.Image(url = "https://pbs.twimg.com/media/second.jpg"),
            )

        val fileNames =
            medias.mapIndexed { index, media ->
                MediaFileNamePolicy.statusMediaFileName(
                    statusKey = "tweet123",
                    userHandle = "alice",
                    media = media,
                    mediaIndex = index,
                )
            }

        assertEquals(
            listOf(
                "tweet123_alice_01.jpg",
                "tweet123_alice_02.jpg",
            ),
            fileNames,
        )
    }

    @Test
    fun statusMediaFileNamesAddsIndexSuffixes() {
        val medias =
            listOf(
                UiMedia.Image(url = "https://example.com/image.png"),
                UiMedia.Gif(
                    url = "https://example.com/animation",
                    previewUrl = "https://example.com/animation.jpg",
                    description = null,
                    height = 100f,
                    width = 100f,
                ),
                UiMedia.Video(
                    url = "https://example.com/video.mov",
                    thumbnailUrl = "https://example.com/video.jpg",
                    description = null,
                    height = 1080f,
                    width = 1920f,
                ),
            )

        val mediaByFileName =
            MediaFileNamePolicy.statusMediaFileNames(
                statusKey = "post/123",
                userHandle = "alice:bsky.social",
                medias = medias,
            )

        assertEquals(
            listOf(
                "post_123_alice_bsky.social_01.png",
                "post_123_alice_bsky.social_02.gif",
                "post_123_alice_bsky.social_03.mov",
            ),
            mediaByFileName.keys.toList(),
        )
        assertEquals(medias, mediaByFileName.values.toList())
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
    fun rawMediaFileNamesAddsIndexSuffixes() {
        val medias =
            listOf(
                UiMedia.Image(url = "https://example.com/path/original@png?size=large"),
                UiMedia.Audio(
                    url = "https://example.com/media/original",
                    description = null,
                    previewUrl = null,
                ),
            )

        val mediaByFileName = MediaFileNamePolicy.rawMediaFileNames(medias)

        assertEquals(
            listOf(
                "original_01.png",
                "original_02.mp3",
            ),
            mediaByFileName.keys.toList(),
        )
        assertEquals(medias, mediaByFileName.values.toList())
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
