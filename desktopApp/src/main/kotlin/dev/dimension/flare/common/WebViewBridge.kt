package dev.dimension.flare.common

import dev.dimension.flare.common.macos.MacosBridge
import dev.dimension.flare.common.windows.WindowsBridge
import dev.dimension.flare.ui.route.Route
import org.apache.commons.lang3.SystemUtils

internal class WebViewBridge(
    private val windowsBridge: WindowsBridge,
    private val windowManager: FlareWindowManager,
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
        } else if (SystemUtils.IS_OS_LINUX) {
            windowManager.put(
                key = url,
                route =
                    Route.WebViewLogin(
                        url = url,
                        cookieCallback = {
                            if (callback(it)) {
                                windowManager.remove(url)
                            }
                        },
                    ),
            )
        } else {
            // TODO: Implement for other platforms
        }
    }
}
