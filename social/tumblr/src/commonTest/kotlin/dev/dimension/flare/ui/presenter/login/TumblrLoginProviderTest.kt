package dev.dimension.flare.ui.presenter.login

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TumblrLoginProviderTest {
    @Test
    fun oauthErrorCallbackCanResumeAndClearsLoading() =
        runTest {
            val handler =
                TumblrLoginProvider.createHandler(
                    LoginContext(
                        host = "tumblr.com",
                        methodType = LoginMethodType.OAuth,
                        onSuccess = {},
                    ),
                )
            val callback =
                "https://flareapp.moe/tumblr-callback.html" +
                    "?error=access_denied&error_description=User%20denied%20access&state=test-state"

            assertTrue(handler.canResume(callback))

            handler.resume(callback)

            assertFalse(handler.state.value.loading)
            assertEquals("User denied access", handler.state.value.error)
        }
}
