package dev.dimension.flare.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.platform.AndroidUriHandler
import androidx.compose.ui.platform.UriHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AppUriHandlerTest {
    @Test
    fun rejectsEmptyAndRelativeLinksBeforeLaunching() {
        val context = browserContext { error("Must not launch a browser") }
        var failures = 0
        val handler = AppUriHandler(context, uriHandler { error("Must not launch an activity") }, true) { failures++ }
        val invalidLinks = listOf("", " ", "#section", "/posts/123", "//example.com/post", "example.com/post", "https://", "https:///post")

        invalidLinks.forEach(handler::openUri)

        assertEquals(invalidLinks.size, failures)
    }

    @Test
    fun handlesTheComposeExceptionWhenNoActivityCanOpenTheLink() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        var failures = 0
        val handler = AppUriHandler(activity, AndroidUriHandler(activity), false) { failures++ }

        handler.openUri("uninstalled://post/123")
        handler.openUri("https://example.com/post")

        assertEquals(2, failures)
    }

    @Test
    fun handlesFailureOfBothCustomTabsAndTheFallback() {
        var browserAttempts = 0
        var fallbackAttempts = 0
        var failures = 0
        val context =
            browserContext {
                browserAttempts++
                throw ActivityNotFoundException()
            }
        val fallback =
            uriHandler {
                fallbackAttempts++
                throw IllegalArgumentException("Cannot open link", ActivityNotFoundException())
            }

        AppUriHandler(context, fallback, true) { failures++ }.openUri("https://example.com/post")

        assertEquals(1, browserAttempts)
        assertEquals(1, fallbackAttempts)
        assertEquals(1, failures)
    }

    @Test
    fun normalizesWebLinksAndOpensCustomTabsWithoutCallingTheFallback() {
        val opened = mutableListOf<String>()
        val context = browserContext { opened.add(it.dataString.orEmpty()) }
        val handler = AppUriHandler(context, uriHandler { error("Must not use fallback") }, true) { error("Must open link") }

        handler.openUri("  HTTPS://example.com/post  ")

        assertEquals(listOf("https://example.com/post"), opened)
    }

    @Test
    fun fallsBackWhenCustomTabsAreUnavailable() {
        val opened = mutableListOf<String>()
        val context = browserContext { throw ActivityNotFoundException() }
        val handler = AppUriHandler(context, uriHandler { opened.add(it) }, true) { error("Must open link") }

        handler.openUri("https://example.com/post")

        assertEquals(listOf("https://example.com/post"), opened)
    }

    @Test
    fun honorsExternalBrowserPreferenceAndPreservesOtherSchemes() {
        val opened = mutableListOf<String>()
        val context = browserContext { error("Must not launch custom tabs") }
        val fallback = uriHandler { opened.add(it) }

        AppUriHandler(context, fallback, false) { error("Must open link") }.openUri("https://example.com/post")
        AppUriHandler(context, fallback, true) { error("Must open link") }.openUri("mailto:hello@example.com")

        assertEquals(listOf("https://example.com/post", "mailto:hello@example.com"), opened)
    }

    @Test
    fun handlesActivityLaunchErrorsButDoesNotHideProgrammingErrors() {
        val context = browserContext { error("Must not launch custom tabs") }
        var failures = 0
        listOf(ActivityNotFoundException(), SecurityException()).forEach { failure ->
            AppUriHandler(context, uriHandler { throw failure }, false) { failures++ }.openUri("custom://post")
        }
        assertEquals(2, failures)

        val handler = AppUriHandler(context, uriHandler { error("Unexpected failure") }, false) { failures++ }
        assertThrows(IllegalStateException::class.java) { handler.openUri("custom://post") }
        assertEquals(2, failures)
    }

    private fun uriHandler(open: (String) -> Unit): UriHandler =
        object : UriHandler {
            override fun openUri(uri: String) = open(uri)
        }

    private fun browserContext(open: (Intent) -> Unit) =
        object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(
                intent: Intent,
                options: Bundle?,
            ) = open(intent)
        }
}
