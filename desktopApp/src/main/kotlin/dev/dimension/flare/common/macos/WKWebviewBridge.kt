package dev.dimension.flare.common.macos

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native

private interface MacOSBridge : Library {
    @Suppress("FunctionName")
    fun wkb_set_log_callback(cb: LogCallback?)

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
    fun wkb_set_decision_callback(cb: DecisionCallback?)

    @Suppress("FunctionName")
    fun wkb_set_window_closed_callback(cb: WindowClosedCallback?)

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

internal object WKWebviewBridge {
    private val iphoneUA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 " +
            "Mobile/15E148 Safari/604.1"

    private val lib: MacOSBridge by lazy {
        Native.load("macosBridge", MacOSBridge::class.java).apply {
            wkb_set_log_callback(
                MacOSBridge.LogCallback { level, msg ->
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
            MacOSBridge.DecisionCallback { cookies ->
                if (decisionCallback(cookies)) 1 else 0
            }
        val closedCb =
            MacOSBridge.WindowClosedCallback { id, reason ->
                windowClosedCallback(id, reason)
            }
        lib.wkb_set_decision_callback(decisionCb)
        lib.wkb_set_window_closed_callback(closedCb)
        val windowId = lib.wkb_open_webview_poll(url, intervalMs)
    }
}
