package dev.dimension.flare.ui.route

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dimension.flare.ui.presenter.login.LoginCookieSnapshot
import dev.dimension.flare.ui.presenter.login.LoginEffect
import dev.dimension.flare.ui.screen.serviceselect.serviceSelectEntryBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebCookieLoginNavigationTest {
    @Test
    fun entryCreationPreservesTheBoundSuspendCallback() =
        runBlocking {
            val validator = CookieValidator()
            val request = LoginEffect.OpenWebCookieLogin("https://example.com/login")
            val route = Route.ServiceSelect.WebCookieLogin(request, validator::checkCookies)
            val provider =
                entryProvider<NavKey> {
                    serviceSelectEntryBuilder(navigate = {}, onBack = {})
                }

            val entry = provider(route)
            val snapshot = LoginCookieSnapshot(rawHeader = "session=fixture")

            assertEquals(entry.contentKey, provider(route).contentKey)
            assertSame(request, route.request)
            assertTrue(route.callback(snapshot))
            assertSame(snapshot, validator.received)
        }

    private class CookieValidator {
        var received: LoginCookieSnapshot? = null

        suspend fun checkCookies(snapshot: LoginCookieSnapshot): Boolean {
            received = snapshot
            return snapshot.rawHeader == "session=fixture"
        }
    }
}
