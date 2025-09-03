package dev.dimension.flare.common.macos

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.serialization.Serializable

internal object MacosBridge {
    private interface Bridge : Library {
        @Suppress("FunctionName")
        fun open_video_viewer(modelJson: String)

        @Suppress("FunctionName")
        fun open_img_viewer(url: String)

        @Suppress("FunctionName")
        fun wkb_set_log_callback(cb: Bridge.LogCallback?)

        fun interface LogCallback : Callback {
            fun invoke(
                level: Int,
                msg: String?,
            )
        }

        @Suppress("FunctionName")
        fun wkb_open_webview_poll_with_ua(
            url: String,
            intervalMs: Int,
            ua: String?,
        ): Long

        @Suppress("FunctionName")
        fun wkb_set_decision_callback(cb: Bridge.DecisionCallback?)

        @Suppress("FunctionName")
        fun wkb_set_window_closed_callback(cb: Bridge.WindowClosedCallback?)

        @Suppress("FunctionName")
        fun wkb_open_webview_poll(
            url: String,
            intervalMs: Int,
        ): Long

        @Suppress("FunctionName")
        fun wkb_close_window(id: Long)

        fun interface DecisionCallback : Callback {
            // return 1 = close window, 0 = continue
            fun invoke(cookies: String?): Int
        }

        fun interface WindowClosedCallback : Callback {
            // reason: 0=user, 1=API, 2=decision callback
            fun invoke(
                id: Long,
                reason: Int,
            )
        }
    }

    private val lib: Bridge by lazy {
        Native.load("macosBridge", Bridge::class.java).apply {
            wkb_set_log_callback(
                Bridge.LogCallback { level, msg ->
                    println("WebviewBridge log [$level]: $msg")
                },
            )
        }
    }

    fun openAndWaitCookies(
        url: String,
        intervalMs: Int = 2000,
        decisionCallback: (cookies: String?) -> Boolean,
        windowClosedCallback: (id: Long, reason: Int) -> Unit,
    ) {
        val decisionCb =
            Bridge.DecisionCallback { cookies ->
                if (decisionCallback(cookies)) 1 else 0
            }
        val closedCb =
            Bridge.WindowClosedCallback { id, reason ->
                windowClosedCallback(id, reason)
            }
        lib.wkb_set_decision_callback(decisionCb)
        lib.wkb_set_window_closed_callback(closedCb)
        val windowId = lib.wkb_open_webview_poll(url, intervalMs)
    }

    fun openImageViewer(url: String) {
        lib.open_img_viewer(url)
    }

    fun openStatusImageViewer(
        data: List<UiMedia>,
        selectedIndex: Int,
    ) {
        val medias =
            data.map {
                StatusMediaItem(
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
            OpenStatusImageModel(
                index = selectedIndex,
                medias = medias,
            )

        val selectedItem = data.getOrNull(selectedIndex)
        when (selectedItem) {
            is UiMedia.Audio -> Unit
            is UiMedia.Gif ->
                lib.open_img_viewer(selectedItem.url)
            is UiMedia.Image ->
                lib.open_img_viewer(selectedItem.url)
            is UiMedia.Video ->
                lib.open_video_viewer(selectedItem.url)
            null -> Unit
        }
    }

    @Serializable
    data class OpenStatusImageModel(
        val index: Int,
        val medias: List<StatusMediaItem>,
    )

    @Serializable
    data class StatusMediaItem(
        val url: String,
        val type: String,
        val placeholder: String?,
    )
}
