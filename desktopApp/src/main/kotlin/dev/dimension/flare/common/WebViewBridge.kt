package dev.dimension.flare.common

import dev.dimension.flare.common.macos.WKWebviewBridge
import org.apache.commons.lang3.SystemUtils

internal object WebViewBridge {
    fun openAndWaitCookies(
        url: String,
        callback: (cookies: String?) -> Boolean,
    ) {
        if (SystemUtils.IS_OS_MAC_OSX) {
            WKWebviewBridge.openAndWaitCookies(
                url = url,
                decisionCallback = {
                    callback(it)
                },
                windowClosedCallback = { _, _ ->
                },
            )
        } else {
            // TODO: Implement for other platforms
        }
    }
}
