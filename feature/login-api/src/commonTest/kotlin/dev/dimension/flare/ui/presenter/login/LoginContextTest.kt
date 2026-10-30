package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginContextTest {
    @Test
    fun reloginAccountMatchIsAllowed() {
        val accountKey = MicroBlogKey(id = "user-a", host = "example.com")
        val context =
            loginContext(
                ReloginTarget(
                    accountKey = accountKey,
                    platformId = "Mastodon",
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
                    platformId = "Mastodon",
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

    @Test
    fun cookieSnapshotKeepsLegacyHandlersWorking() =
        runTest {
            val handler = RecordingCookieHandler()

            assertFalse(handler.checkCookies(LoginCookieSnapshot()))
            assertTrue(
                handler.checkCookies(
                    LoginCookieSnapshot(
                        values = listOf(LoginCookieValue("https://example.com", "ignored", "value")),
                        rawHeader = "session=valid; theme=dark",
                    ),
                ),
            )

            assertEquals(listOf("session=valid; theme=dark"), handler.resumed)
        }

    private fun loginContext(reloginTarget: ReloginTarget?): LoginContext =
        LoginContext(
            host = "example.com",
            methodType = LoginMethodType.OAuth,
            onSuccess = {},
            reloginTarget = reloginTarget,
        )
}

private class RecordingCookieHandler : LoginMethodHandler {
    private val mutableState = MutableStateFlow(LoginFlowState())
    val resumed = mutableListOf<String>()

    override val state: StateFlow<LoginFlowState> = mutableState
    override val effects: Flow<LoginEffect> = emptyFlow()

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) = Unit

    override suspend fun resume(value: String) {
        resumed += value
    }

    override fun canResume(value: String): Boolean = value.contains("session=valid")

    override fun clear() = Unit
}
