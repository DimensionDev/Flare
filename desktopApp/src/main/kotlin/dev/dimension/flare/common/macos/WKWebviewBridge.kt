package dev.dimension.flare.common.macos

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native

private interface WebviewBridge : Library {
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

    @Suppress("FunctionName")
    fun wkb_clear_persistent_storage()

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
    private val lib: WebviewBridge by lazy {
        Native.load("WebviewBridge", WebviewBridge::class.java)
    }

    fun openAndWaitCookies(
        url: String,
        intervalMs: Int = 2000,
        decisionCallback: (cookies: String?) -> Boolean,
        windowClosedCallback: (id: Long, reason: Int) -> Unit,
    ) {
        val decisionCb =
            WebviewBridge.DecisionCallback { cookies ->
                if (decisionCallback(cookies)) 1 else 0
            }
        val closedCb =
            WebviewBridge.WindowClosedCallback { id, reason ->
                windowClosedCallback(id, reason)
            }
        lib.wkb_set_decision_callback(decisionCb)
        lib.wkb_set_window_closed_callback(closedCb)
        val windowId = lib.wkb_open_webview_poll(url, intervalMs)
    }
}
