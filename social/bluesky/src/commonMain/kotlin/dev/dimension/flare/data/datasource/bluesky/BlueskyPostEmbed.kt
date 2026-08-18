package dev.dimension.flare.data.datasource.bluesky

import app.bsky.embed.AspectRatio
import app.bsky.embed.Gallery
import app.bsky.embed.GalleryImage
import app.bsky.embed.GalleryItemUnion
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.embed.RecordWithMedia
import app.bsky.embed.RecordWithMediaMediaUnion
import app.bsky.feed.PostEmbedUnion
import kotlinx.collections.immutable.toImmutableList

internal const val BLUESKY_LEGACY_IMAGE_LIMIT = 4
internal const val BLUESKY_GALLERY_AUTHOR_LIMIT = 10
internal const val BLUESKY_ALT_TEXT_LIMIT = 2000

internal sealed interface BlueskyImageEmbed {
    data class LegacyImages(
        val value: Images,
    ) : BlueskyImageEmbed

    data class GalleryImages(
        val value: Gallery,
    ) : BlueskyImageEmbed
}

internal fun List<ImagesImage>.toBlueskyImageEmbed(): BlueskyImageEmbed? {
    require(size <= BLUESKY_GALLERY_AUTHOR_LIMIT) {
        "Bluesky supports at most $BLUESKY_GALLERY_AUTHOR_LIMIT images when authoring a post"
    }
    return when {
        isEmpty() -> {
            null
        }

        size <= BLUESKY_LEGACY_IMAGE_LIMIT -> {
            BlueskyImageEmbed.LegacyImages(
                Images(images = toImmutableList()),
            )
        }

        else -> {
            BlueskyImageEmbed.GalleryImages(
                Gallery(
                    items =
                        map { image ->
                            GalleryItemUnion.Image(
                                GalleryImage(
                                    image = image.image,
                                    alt = image.alt,
                                    aspectRatio =
                                        requireNotNull(image.aspectRatio) {
                                            "Bluesky gallery images require an aspect ratio"
                                        },
                                ),
                            )
                        }.toImmutableList(),
                ),
            )
        }
    }
}

internal fun buildBlueskyPostEmbed(
    quote: Record?,
    media: BlueskyImageEmbed?,
    external: PostEmbedUnion.External?,
): PostEmbedUnion? =
    when {
        quote != null && media != null -> {
            PostEmbedUnion.RecordWithMedia(
                RecordWithMedia(
                    record = quote,
                    media = media.toRecordWithMediaUnion(),
                ),
            )
        }

        quote != null -> {
            PostEmbedUnion.Record(quote)
        }

        media != null -> {
            media.toPostEmbedUnion()
        }

        else -> {
            external
        }
    }

private fun BlueskyImageEmbed.toPostEmbedUnion(): PostEmbedUnion =
    when (this) {
        is BlueskyImageEmbed.LegacyImages -> PostEmbedUnion.Images(value)
        is BlueskyImageEmbed.GalleryImages -> PostEmbedUnion.Gallery(value)
    }

private fun BlueskyImageEmbed.toRecordWithMediaUnion(): RecordWithMediaMediaUnion =
    when (this) {
        is BlueskyImageEmbed.LegacyImages -> RecordWithMediaMediaUnion.Images(value)
        is BlueskyImageEmbed.GalleryImages -> RecordWithMediaMediaUnion.Gallery(value)
    }

internal fun ByteArray.requireJpegAspectRatio(): AspectRatio {
    require(size >= 4 && unsignedByte(0) == JPEG_MARKER_PREFIX && unsignedByte(1) == JPEG_START_OF_IMAGE) {
        "Compressed Bluesky image is not a JPEG"
    }

    var offset = 2
    while (offset < size) {
        require(unsignedByte(offset) == JPEG_MARKER_PREFIX) {
            "Invalid JPEG marker at byte $offset"
        }
        while (offset < size && unsignedByte(offset) == JPEG_MARKER_PREFIX) {
            offset++
        }
        require(offset < size) { "Incomplete JPEG marker" }

        val marker = unsignedByte(offset++)
        if (marker == JPEG_START_OF_SCAN || marker == JPEG_END_OF_IMAGE) {
            break
        }
        if (marker == JPEG_TEMP_MARKER || marker in JPEG_RESTART_MARKER_RANGE) {
            continue
        }

        require(offset + 1 < size) { "Incomplete JPEG segment length" }
        val segmentLength = (unsignedByte(offset) shl 8) or unsignedByte(offset + 1)
        require(segmentLength >= 2 && offset + segmentLength <= size) {
            "Invalid JPEG segment length at byte $offset"
        }

        if (marker.isStartOfFrameMarker()) {
            require(segmentLength >= 7) { "Invalid JPEG start-of-frame segment" }
            val height = (unsignedByte(offset + 3) shl 8) or unsignedByte(offset + 4)
            val width = (unsignedByte(offset + 5) shl 8) or unsignedByte(offset + 6)
            require(width > 0 && height > 0) { "JPEG dimensions must be positive" }
            return AspectRatio(width = width.toLong(), height = height.toLong())
        }

        offset += segmentLength
    }

    throw IllegalArgumentException("JPEG dimensions were not found")
}

private fun ByteArray.unsignedByte(index: Int): Int = this[index].toInt() and 0xff

private fun Int.isStartOfFrameMarker(): Boolean =
    this == 0xc0 ||
        this == 0xc1 ||
        this == 0xc2 ||
        this == 0xc3 ||
        this == 0xc5 ||
        this == 0xc6 ||
        this == 0xc7 ||
        this == 0xc9 ||
        this == 0xca ||
        this == 0xcb ||
        this == 0xcd ||
        this == 0xce ||
        this == 0xcf

private const val JPEG_MARKER_PREFIX = 0xff
private const val JPEG_START_OF_IMAGE = 0xd8
private const val JPEG_END_OF_IMAGE = 0xd9
private const val JPEG_START_OF_SCAN = 0xda
private const val JPEG_TEMP_MARKER = 0x01
private val JPEG_RESTART_MARKER_RANGE = 0xd0..0xd7
