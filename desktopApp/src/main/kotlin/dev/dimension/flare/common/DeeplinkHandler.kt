package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

internal object DeeplinkHandler {
    private val handlers = mutableListOf<(String) -> Boolean>()

    fun registerHandler(handler: (String) -> Boolean) {
        handlers.add(handler)
    }

    fun unregisterHandler(handler: (String) -> Boolean) {
        handlers.remove(handler)
    }

    fun handleDeeplink(deeplink: String): Boolean {
        for (handler in handlers) {
            if (handler(deeplink)) {
                return true
            }
        }
        return false
    }
}

@Composable
internal fun OnDeepLink(handler: (String) -> Boolean) {
    DisposableEffect(handler) {
        DeeplinkHandler.registerHandler(handler)
        onDispose {
            DeeplinkHandler.unregisterHandler(handler)
        }
    }
}
