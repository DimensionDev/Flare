package dev.dimension.flare.data.datasource.bluesky

import app.bsky.embed.AspectRatio
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.embed.RecordWithMediaMediaUnion
import app.bsky.embed.Video
import app.bsky.embed.VideoPresentation
import app.bsky.feed.PostEmbedUnion
import com.atproto.repo.StrongRef
import dev.dimension.flare.common.UploadMedia
import kotlinx.coroutines.test.runTest
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.model.Blob
import sh.christian.ozone.api.model.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class BlueskyPostEmbedTest {
    @Test
    fun gifAndQuotedGifUseProcessedVideoWithGifPresentation() =
        runTest {
            val upload = UploadMedia.fromBytes("animation.gif", "GIF89a".encodeToByteArray())
            val processed = Blob.StandardBlob(ref = BlobRef(Cid("processed-cid")), mimeType = "video/mp4", size = 123)
            val media = upload.toBlueskyMediaEmbed(processed, "An animation")
            val embed = assertIs<PostEmbedUnion.Video>(buildBlueskyPostEmbed(null, media, null)).value

            assertEquals(processed, embed.video)
            assertEquals("An animation", embed.alt)
            assertEquals(VideoPresentation.Gif, embed.presentation)

            val quote = Record(StrongRef(uri = AtUri("at://did:plc:quoted/app.bsky.feed.post/1"), cid = Cid("quoted-cid")))
            val quoted = assertIs<PostEmbedUnion.RecordWithMedia>(buildBlueskyPostEmbed(quote, media, null))
            assertEquals(embed, assertIs<RecordWithMediaMediaUnion.Video>(quoted.value.media).value)
            assertEquals(quote, quoted.value.record)
        }

    @Test
    fun quickTimeUsesProcessedVideoWithDefaultPresentation() =
        runTest {
            val upload = UploadMedia.fromBytes("clip.mov", byteArrayOf(0, 0, 0, 20) + "ftypqt  ".encodeToByteArray())
            val processed = Blob.StandardBlob(ref = BlobRef(Cid("processed-cid")), mimeType = "video/mp4", size = 123)
            val media = upload.toBlueskyMediaEmbed(processed, "A movie")

            assertEquals(processed, media.value.video)
            assertEquals("A movie", media.value.alt)
            assertNull(media.value.presentation)
        }

    @Test
    fun videoAndQuotedVideoUseVideoEmbeds() {
        val video =
            Video(
                video = Blob.StandardBlob(ref = BlobRef(Cid("video-cid")), mimeType = "video/mp4", size = 123),
                alt = "A short clip",
            )
        val media = BlueskyMediaEmbed.VideoMedia(video)
        assertEquals(video, assertIs<PostEmbedUnion.Video>(buildBlueskyPostEmbed(null, media, null)).value)
        val quote = Record(StrongRef(uri = AtUri("at://did:plc:quoted/app.bsky.feed.post/1"), cid = Cid("quoted-cid")))
        val embed = assertIs<PostEmbedUnion.RecordWithMedia>(buildBlueskyPostEmbed(quote, media, null))
        assertEquals(video, assertIs<RecordWithMediaMediaUnion.Video>(embed.value.media).value)
        assertEquals(quote, embed.value.record)
    }

    @Test
    fun imageCountSelectsLegacyImagesOrGallery() {
        val legacy = assertIs<BlueskyMediaEmbed.LegacyImages>(images(4).toBlueskyMediaEmbed())
        val gallery = assertIs<BlueskyMediaEmbed.GalleryImages>(images(5).toBlueskyMediaEmbed())

        assertEquals(4, legacy.value.images.size)
        assertEquals(5, gallery.value.items.size)
        assertIs<PostEmbedUnion.Images>(
            buildBlueskyPostEmbed(quote = null, media = legacy, external = null),
        )
        assertIs<PostEmbedUnion.Gallery>(
            buildBlueskyPostEmbed(quote = null, media = gallery, external = null),
        )
    }

    @Test
    fun quoteAndGalleryUseRecordWithMedia() {
        val quote =
            Record(
                StrongRef(
                    uri = AtUri("at://did:plc:quoted/app.bsky.feed.post/1"),
                    cid = Cid("quoted-cid"),
                ),
            )
        val gallery = assertIs<BlueskyMediaEmbed.GalleryImages>(images(10).toBlueskyMediaEmbed())

        val embed =
            assertIs<PostEmbedUnion.RecordWithMedia>(
                buildBlueskyPostEmbed(quote = quote, media = gallery, external = null),
            )

        val media = assertIs<RecordWithMediaMediaUnion.Gallery>(embed.value.media)
        assertEquals(10, media.value.items.size)
        assertEquals(quote, embed.value.record)
    }

    @Test
    fun quoteAndLegacyImagesUseRecordWithMedia() {
        val quote =
            Record(
                StrongRef(
                    uri = AtUri("at://did:plc:quoted/app.bsky.feed.post/1"),
                    cid = Cid("quoted-cid"),
                ),
            )
        val images = assertIs<BlueskyMediaEmbed.LegacyImages>(images(4).toBlueskyMediaEmbed())

        val embed =
            assertIs<PostEmbedUnion.RecordWithMedia>(
                buildBlueskyPostEmbed(quote = quote, media = images, external = null),
            )

        val media = assertIs<RecordWithMediaMediaUnion.Images>(embed.value.media)
        assertEquals(4, media.value.images.size)
        assertEquals(quote, embed.value.record)
    }

    @Test
    fun authoringMoreThanTenImagesFails() {
        assertFailsWith<IllegalArgumentException> {
            images(11).toBlueskyMediaEmbed()
        }
    }

    @Test
    fun galleryRequiresAspectRatio() {
        val images = images(5).toMutableList()
        images[4] = images[4].copy(aspectRatio = null)

        assertFailsWith<IllegalArgumentException> {
            images.toBlueskyMediaEmbed()
        }
    }

    @Test
    fun legacyImagesDoNotRequireAspectRatio() {
        val images = images(4).map { it.copy(aspectRatio = null) }

        assertIs<BlueskyMediaEmbed.LegacyImages>(images.toBlueskyMediaEmbed())
    }

    @Test
    fun jpegDimensionsBecomeAspectRatio() {
        val jpeg =
            intArrayOf(
                0xff,
                0xd8,
                0xff,
                0xc0,
                0x00,
                0x11,
                0x08,
                0x01,
                0xe0,
                0x02,
                0x80,
                0x03,
                0x01,
                0x11,
                0x00,
                0x02,
                0x11,
                0x00,
                0x03,
                0x11,
                0x00,
                0xff,
                0xd9,
            ).map { it.toByte() }.toByteArray()

        assertEquals(AspectRatio(width = 640, height = 480), jpeg.requireJpegAspectRatio())
    }

    private fun images(count: Int): List<ImagesImage> =
        List(count) { index ->
            ImagesImage(
                image =
                    Blob.StandardBlob(
                        ref = BlobRef(Cid("cid-$index")),
                        mimeType = "image/jpeg",
                        size = 100,
                    ),
                alt = "image $index",
                aspectRatio = AspectRatio(width = (index + 1).toLong(), height = (index + 2).toLong()),
            )
        }
}
