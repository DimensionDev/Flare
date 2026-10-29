package dev.dimension.flare.feature.agent.common

import ai.koog.http.client.KoogHttpClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentFailureTest {
    @Test
    fun requestTimeoutCanRetryButAnUnwrappedCancellationIsNotAnError() =
        runTest {
            val timeout = assertFailsWith<TimeoutCancellationException> { withTimeout(1) { delay(100) } }
            val failure = AgentRetryableException(timeout, AgentRetryMode.Resume).toAgentFailure()
            assertEquals(AgentFailureKind.Network, failure.kind)
            assertTrue(failure.canRetry)
            assertFailsWith<CancellationException> { timeout.toAgentFailure() }
        }

    @Test
    fun darwinTransportErrorsUseTheSharedNetworkDescription() {
        assertEquals(AgentFailureKind.Network, DarwinHttpRequestException().toAgentFailure().kind)
    }

    private class DarwinHttpRequestException : Exception("NSURLErrorDomain Code=-1009")

    @Test
    fun classifiesKnownProviderFailuresWithoutAssumingEvery429IsQuota() {
        for ((status, kind) in listOf(
            401 to AgentFailureKind.Authentication,
            403 to AgentFailureKind.AccessDenied,
            402 to AgentFailureKind.Quota,
            408 to AgentFailureKind.Network,
            429 to AgentFailureKind.RateLimited,
            503 to AgentFailureKind.Service,
            422 to AgentFailureKind.Unknown,
        )) {
            assertEquals(kind, providerError(status, "Provider explanation").toAgentFailure().kind)
        }
        assertEquals(AgentFailureKind.Quota, providerError(429, "insufficient_quota").toAgentFailure().kind)
    }

    @Test
    fun unknownErrorsShowTheActualUpstreamMessage() {
        for ((body, expected) in listOf(
            """{"error":{"message":"Model does not support this parameter","code":"unsupported"}}""" to
                "Model does not support this parameter",
            """{"detail":"Custom proxy rejected model"}""" to "Custom proxy rejected model",
            "Custom upstream failure" to "Custom upstream failure",
            "<b>Custom proxy failure</b>" to "<b>Custom proxy failure</b>",
        )) {
            val failure = providerError(400, body).toAgentFailure()
            assertEquals(AgentFailureKind.Unknown, failure.kind)
            assertEquals(expected, failure.message)
        }
        assertEquals("Local failure", IllegalStateException("Local failure").toAgentFailure().message)
    }

    @Test
    fun redactsCredentialsBeforePersistingOrDisplayingAndBoundsTheMessage() {
        val failure =
            providerError(
                400,
                """{"error":{"message":"Bad key sk-private-key, Authorization: Bearer secret-token api_key=private password=private"}}""",
            ).toAgentFailure()
        assertFalse(failure.message.contains("private"))
        assertFalse(failure.encode().contains("secret-token"))
        assertTrue(failure.message.contains("[redacted]"))
        assertEquals(2000, providerError(400, "x".repeat(3000)).toAgentFailure().message.length)
    }

    @Test
    fun onlyKnownSafeBoundariesCanRetryAndCancellationIsNotAnError() {
        val cause = providerError(500, "Service unavailable")
        assertFalse(cause.toAgentFailure().canRetry)
        assertFalse(IllegalStateException("Tool may have changed state").toAgentFailure().canRetry)
        val failure = AgentRetryableException(cause, AgentRetryMode.Resume).toAgentFailure("Original question")
        assertTrue(failure.canRetry)
        assertEquals(failure, AgentFailure.decode(failure.encode()))
        assertEquals("Original question", AgentFailure.decode(failure.encode()).userInput)
        assertFailsWith<CancellationException> { CancellationException().toAgentFailure() }
        assertFailsWith<CancellationException> { IllegalStateException("wrapped", CancellationException()).toAgentFailure() }
    }

    @Test
    fun legacyErrorsRemainVisibleWithoutGuessingRetrySafety() {
        val legacy = AgentFailure.decode("The upstream model was removed")
        assertEquals("The upstream model was removed", legacy.message)
        assertFalse(legacy.canRetry)
        assertFalse(AgentFailure.decode("flare-agent-failure:invalid").canRetry)
    }

    private fun providerError(
        status: Int,
        body: String,
    ) = KoogHttpClientException(statusCode = status, errorBody = body)
}
