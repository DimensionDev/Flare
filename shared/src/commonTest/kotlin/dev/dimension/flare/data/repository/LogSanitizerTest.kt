package dev.dimension.flare.data.repository

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LogSanitizerTest {
    @Test
    fun sanitizeRedactsOAuthFormBody() {
        val log =
            "client_id=pixiv-client&client_secret=pixiv-secret&grant_type=authorization_code&" +
                "code=C0gnZ62abcdefgh&code_verifier=verifier-value"

        val sanitized = LogSanitizer.sanitize(log)

        assertContains(sanitized, "client_id=[REDACTED]")
        assertContains(sanitized, "client_secret=[REDACTED]")
        assertContains(sanitized, "grant_type=authorization_code")
        assertContains(sanitized, "code=[REDACTED]")
        assertContains(sanitized, "code_verifier=[REDACTED]")
        assertFalse(sanitized.contains("pixiv-secret"))
        assertFalse(sanitized.contains("C0gnZ62abcdefgh"))
        assertFalse(sanitized.contains("verifier-value"))
    }

    @Test
    fun sanitizeRedactsJsonSecretsAndEmails() {
        val log =
            """
            {"access_token":"access-value","refresh_token":"refresh-value","mail_address":"user@example.com","user":{"id":"123"}}
            """.trimIndent()

        val sanitized = LogSanitizer.sanitize(log)

        assertContains(sanitized, "\"access_token\":\"[REDACTED]\"")
        assertContains(sanitized, "\"refresh_token\":\"[REDACTED]\"")
        assertContains(sanitized, "\"mail_address\":\"[REDACTED]\"")
        assertContains(sanitized, "\"id\":\"123\"")
        assertFalse(sanitized.contains("access-value"))
        assertFalse(sanitized.contains("refresh-value"))
        assertFalse(sanitized.contains("user@example.com"))
    }

    @Test
    fun sanitizeRedactsSensitiveHeadersAndBearerTokens() {
        val log =
            """
            Authorization: Bearer abc.def.secret
            Cookie: auth_token=secret; ct0=csrf
            Set-Cookie: sid=secret; Path=/
            X-CSRF-Token: csrf-value
            """.trimIndent()

        val sanitized = LogSanitizer.sanitize(log)

        assertEquals(
            """
            Authorization: [REDACTED]
            Cookie: [REDACTED]
            Set-Cookie: [REDACTED]
            X-CSRF-Token: [REDACTED]
            """.trimIndent(),
            sanitized,
        )
    }

    @Test
    fun sanitizeDoesNotHideShortNumericErrorCode() {
        val log = """{"error":"invalid_grant","code":1508}"""

        assertEquals(log, LogSanitizer.sanitize(log))
    }

    @Test
    fun sanitizeRedactsSignedQueryParametersWithoutCorruptingJson() {
        val log =
            """
            {"profile_image_url":"https://example.com/avatar.jpg?Expires=1&ssig=secret-signature","screen_name":"alice"}
            """.trimIndent()

        val sanitized = LogSanitizer.sanitize(log)

        assertContains(sanitized, "ssig=[REDACTED]")
        assertContains(sanitized, "\"screen_name\":\"alice\"")
        assertFalse(sanitized.contains("secret-signature"))
    }

    @Test
    fun sanitizeIsIdempotent() {
        val log =
            """
            Authorization: Bearer abc.def.secret
            client_secret=pixiv-secret&code=C0gnZ62abcdefgh
            {"access_token":"access-value","mail_address":"user@example.com","code":1508}
            """.trimIndent()

        val sanitized = LogSanitizer.sanitize(log)

        assertEquals(sanitized, LogSanitizer.sanitize(sanitized))
    }
}
