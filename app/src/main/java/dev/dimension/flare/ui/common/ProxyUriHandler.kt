package dev.dimension.flare.ui.common

import androidx.compose.ui.platform.UriHandler

internal class ProxyUriHandler(
    private val actualUriHandler: UriHandler,
    private val navigate: (String) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        if (uri.startsWith("flare://")) {
            navigate.invoke(uri)
        } else {
            // some crashes from firebase indicate that this can throw
            runCatching {
                actualUriHandler.openUri(uri)
            }
        }
    }
}
