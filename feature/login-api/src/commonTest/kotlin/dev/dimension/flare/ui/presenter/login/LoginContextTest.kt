package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginContextTest {
    @Test
    fun reloginAccountMatchIsAllowed() {
        val accountKey = MicroBlogKey(id = "user-a", host = "example.com")
        val context =
            loginContext(
                ReloginTarget(
                    accountKey = accountKey,
                    platformType = PlatformType.Mastodon,
                ),
            )

        context.requireReloginAccount(accountKey)
    }

    @Test
    fun reloginAccountMismatchFails() {
        val expected = MicroBlogKey(id = "user-a", host = "example.com")
        val actual = MicroBlogKey(id = "user-b", host = "example.com")
        val context =
            loginContext(
                ReloginTarget(
                    accountKey = expected,
                    platformType = PlatformType.Mastodon,
                ),
            )

        val error =
            assertFailsWith<ReloginAccountMismatchException> {
                context.requireReloginAccount(actual)
            }

        assertEquals(expected, error.expected)
        assertEquals(actual, error.actual)
    }

    @Test
    fun normalLoginDoesNotRestrictAccount() {
        val context = loginContext(reloginTarget = null)

        context.requireReloginAccount(MicroBlogKey(id = "any-user", host = "example.com"))
    }

    private fun loginContext(reloginTarget: ReloginTarget?): LoginContext =
        LoginContext(
            host = "example.com",
            methodType = LoginMethodType.OAuth,
            onSuccess = {},
            reloginTarget = reloginTarget,
        )
}
