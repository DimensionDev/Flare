package dev.dimension.flare.common.windows

import dev.dimension.flare.common.IPCEvent
import dev.dimension.flare.common.PlatformIPC
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.builtins.serializer

internal class WindowsBridge(
    private val platformIPC: PlatformIPC,
) {
    fun openImageViewer(url: String) {
        platformIPC.sendData(
            "open-image-viewer",
            url,
            serializer = String.serializer(),
        )
    }

    fun openStatusImageViewer(
        data: ImmutableList<UiMedia>,
        selectedIndex: Int,
    ) {
        val medias =
            data.map {
                IPCEvent.OpenStatusImageData.StatusMediaItem(
                    url = it.url,
                    type =
                        when (it) {
                            is UiMedia.Audio -> "audio"
                            is UiMedia.Gif -> "gif"
                            is UiMedia.Image -> "image"
                            is UiMedia.Video -> "video"
                        },
                    placeholder =
                        when (it) {
                            is UiMedia.Image -> it.previewUrl
                            is UiMedia.Video -> it.thumbnailUrl
                            is UiMedia.Gif -> it.previewUrl
                            is UiMedia.Audio -> null
                        },
                )
            }
        val model =
            IPCEvent.OpenStatusImageData(
                index = selectedIndex,
                medias = medias,
            )
        platformIPC.sendData(
            "open-status-image-viewer",
            model,
            serializer = IPCEvent.OpenStatusImageData.serializer(),
        )
    }
}
