package dev.dimension.flare.data.datasource.bluesky

import app.bsky.embed.AspectRatio
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.embed.RecordWithMediaMediaUnion
import app.bsky.feed.PostEmbedUnion
import com.atproto.repo.StrongRef
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.model.Blob
import sh.christian.ozone.api.model.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BlueskyPostEmbedTest {
    @Test
    fun imageCountSelectsLegacyImagesOrGallery() {
        val legacy = assertIs<BlueskyImageEmbed.LegacyImages>(images(4).toBlueskyImageEmbed())
        val gallery = assertIs<BlueskyImageEmbed.GalleryImages>(images(5).toBlueskyImageEmbed())

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
        val gallery = assertIs<BlueskyImageEmbed.GalleryImages>(images(10).toBlueskyImageEmbed())

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
        val images = assertIs<BlueskyImageEmbed.LegacyImages>(images(4).toBlueskyImageEmbed())

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
            images(11).toBlueskyImageEmbed()
        }
    }

    @Test
    fun galleryRequiresAspectRatio() {
        val images = images(5).toMutableList()
        images[4] = images[4].copy(aspectRatio = null)

        assertFailsWith<IllegalArgumentException> {
            images.toBlueskyImageEmbed()
        }
    }

    @Test
    fun legacyImagesDoNotRequireAspectRatio() {
        val images = images(4).map { it.copy(aspectRatio = null) }

        assertIs<BlueskyImageEmbed.LegacyImages>(images.toBlueskyImageEmbed())
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
