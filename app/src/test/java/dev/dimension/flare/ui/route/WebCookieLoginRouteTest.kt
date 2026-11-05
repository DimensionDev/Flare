package dev.dimension.flare.ui.route

import androidx.navigation3.runtime.NavEntry
import dev.dimension.flare.ui.presenter.login.LoginCookieSnapshot
import dev.dimension.flare.ui.presenter.login.LoginEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WebCookieLoginRouteTest {
    @Test
    fun creatingAnEntryDoesNotInspectTheCookieCallback() {
        val callback =
            object : suspend (LoginCookieSnapshot) -> Boolean {
                override suspend fun invoke(snapshot: LoginCookieSnapshot): Boolean = false

                override fun toString(): String = error("Navigation must not stringify the cookie callback")
            }
        val route =
            Route.ServiceSelect.WebCookieLogin(
                request = LoginEffect.OpenWebCookieLogin("https://example.com/login"),
                callback = callback,
            )

        val entry = NavEntry(route) {}

        assertEquals(entry.contentKey, NavEntry(route) {}.contentKey)
    }

    @Test
    fun separateLoginSessionsAtTheSameUrlHaveDifferentContentKeys() {
        val request = LoginEffect.OpenWebCookieLogin("https://example.com/login")
        val callback: suspend (LoginCookieSnapshot) -> Boolean = { false }
        val first = Route.ServiceSelect.WebCookieLogin(request, callback)
        val second = Route.ServiceSelect.WebCookieLogin(request, callback)

        assertNotEquals(NavEntry(first) {}.contentKey, NavEntry(second) {}.contentKey)
    }
}
