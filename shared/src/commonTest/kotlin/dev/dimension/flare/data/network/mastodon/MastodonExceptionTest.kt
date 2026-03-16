package dev.dimension.flare.data.network.mastodon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MastodonExceptionTest {
    @Test
    fun parsesValidErrorResponse() {
        val result = """{"error":"Rate limit exceeded"}""".toMastodonExceptionOrNull()

        assertEquals("Rate limit exceeded", result?.error)
    }

    @Test
    fun ignoresInvalidJsonResponse() {
        val result = "<html>502 Bad Gateway</html>".toMastodonExceptionOrNull()

        assertNull(result)
    }

    @Test
    fun ignoresResponseWithoutErrorMessage() {
        val result = """{"details":"missing error field"}""".toMastodonExceptionOrNull()

        assertNull(result)
    }
}
