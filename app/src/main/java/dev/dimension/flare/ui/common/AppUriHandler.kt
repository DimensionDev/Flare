package dev.dimension.flare.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

internal class AppUriHandler(
    private val context: Context,
    private val fallback: UriHandler,
    private val inAppBrowser: Boolean,
    private val onFailure: () -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        val target = uri.trim().toUri().normalizeScheme()
        val isWebLink = target.scheme == "http" || target.scheme == "https"
        if (target.scheme.isNullOrEmpty() || (isWebLink && target.host.isNullOrEmpty())) {
            onFailure()
            return
        }
        if (inAppBrowser && isWebLink && tryOpen { CustomTabsIntent.Builder().build().launchUrl(context, target) }) {
            return
        }
        if (!tryOpen { fallback.openUri(target.toString()) }) {
            onFailure()
        }
    }

    private inline fun tryOpen(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            // Compose wraps ActivityNotFoundException in IllegalArgumentException.
            false
        } catch (_: SecurityException) {
            false
        }
}
