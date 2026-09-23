package dev.dimension.flare.data.network

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.common.UploadMedia
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.FormBuilder
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun UploadMedia.asContent(): OutgoingContent.WriteChannelContent =
    object : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ContentType.parse(mimeType)
        override val contentLength: Long = size

        override suspend fun writeTo(channel: ByteWriteChannel) {
            forEachChunk { bytes, count -> channel.writeFully(bytes, 0, count) }
        }
    }

/** The scope belongs to the upload call, so an HTTP failure also cancels its file reader. */
@HiddenFromObjC
public fun FormBuilder.appendMedia(
    key: String,
    media: UploadMedia,
    scope: CoroutineScope,
) {
    append(
        key,
        ChannelProvider(media.size) {
            scope
                .writer(PlatformDispatchers.IO) {
                    media.forEachChunk { bytes, count -> channel.writeFully(bytes, 0, count) }
                }.channel
        },
        Headers.build {
            append(HttpHeaders.ContentDisposition, "filename=\"${media.name}\"")
            append(HttpHeaders.ContentType, media.mimeType)
        },
    )
}
