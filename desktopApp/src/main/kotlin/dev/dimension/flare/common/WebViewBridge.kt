package dev.dimension.flare.common

import dev.dimension.flare.common.macos.MacosBridge
import dev.dimension.flare.common.windows.WindowsBridge
import org.apache.commons.lang3.SystemUtils

internal class WebViewBridge(
    private val windowsBridge: WindowsBridge,
) {
    fun openAndWaitCookies(
        url: String,
        callback: (cookies: String?) -> Boolean,
    ) {
        if (SystemUtils.IS_OS_MAC_OSX) {
            MacosBridge.openAndWaitCookies(
                url = url,
                decisionCallback = {
                    callback(it)
                },
                windowClosedCallback = { _, _ ->
                },
            )
        } else if (SystemUtils.IS_OS_WINDOWS) {
            windowsBridge.openAndWaitCookies(
                url = url,
                decisionCallback = {
                    callback(it)
                },
            )
        } else {
            // TODO: Implement for other platforms
        }
    }
}
