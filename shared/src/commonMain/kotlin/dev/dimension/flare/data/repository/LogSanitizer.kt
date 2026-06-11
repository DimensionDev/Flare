package dev.dimension.flare.data.repository

internal object LogSanitizer {
    private const val REDACTED = "[REDACTED]"
    private const val REDACTED_EMAIL = "[REDACTED_EMAIL]"
    private const val MIN_SECRET_LENGTH = 8

    private val sensitiveKeys =
        listOf(
            "access_token",
            "accessToken",
            "refresh_token",
            "refreshToken",
            "id_token",
            "idToken",
            "client_id",
            "clientId",
            "client_secret",
            "clientSecret",
            "code",
            "code_verifier",
            "codeVerifier",
            "code_challenge",
            "codeChallenge",
            "token",
            "user_token",
            "userToken",
            "auth_token",
            "authToken",
            "api_key",
            "apiKey",
            "apikey",
            "password",
            "passwd",
            "secret",
            "secret_key",
            "secretKey",
            "private_key",
            "privateKey",
            "cookie",
            "set-cookie",
            "csrf",
            "csrf_token",
            "csrfToken",
            "x-csrf-token",
            "authenticity_token",
            "authenticityToken",
            "ct0",
            "kdt",
            "st",
            "ssig",
            "sig",
            "signature",
            "mail",
            "email",
            "mail_address",
            "mailAddress",
        )
    private val keyPattern = sensitiveKeys.joinToString("|") { Regex.escape(it) }

    private val sensitiveHeaderPattern =
        Regex("""(?im)^(\s*(?:authorization|cookie|set-cookie|x-csrf-token)\s*:\s*).*$""")
    private val bearerPattern = Regex("""(?i)\b(Bearer)\s+[A-Za-z0-9._~+/\-=]+""")
    private val quotedKeyValuePattern = Regex("""(?i)(["']($keyPattern)["']\s*:\s*["'])([^"']*)(["'])""")
    private val unquotedKeyValuePattern = Regex("""(?i)(\b($keyPattern)\b\s*:\s*)([^,\s}"']+)""")
    private val formKeyValuePattern = Regex("""(?i)(\b($keyPattern)\b\s*=\s*)([^&\s"'<>]+)""")
    private val emailPattern = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    fun sanitize(value: String): String {
        var sanitized = value
        sanitized =
            bearerPattern.replace(sanitized) { match ->
                "${match.groupValues[1]} $REDACTED"
            }
        sanitized =
            quotedKeyValuePattern.replace(sanitized) { match ->
                if (shouldRedact(match.groupValues[2], match.groupValues[3])) {
                    "${match.groupValues[1]}$REDACTED${match.groupValues[4]}"
                } else {
                    match.value
                }
            }
        sanitized =
            unquotedKeyValuePattern.replace(sanitized) { match ->
                if (shouldRedact(match.groupValues[2], match.groupValues[3])) {
                    "${match.groupValues[1]}$REDACTED"
                } else {
                    match.value
                }
            }
        sanitized =
            formKeyValuePattern.replace(sanitized) { match ->
                if (shouldRedact(match.groupValues[2], match.groupValues[3])) {
                    "${match.groupValues[1]}$REDACTED"
                } else {
                    match.value
                }
            }
        sanitized = emailPattern.replace(sanitized, REDACTED_EMAIL)
        sanitized =
            sensitiveHeaderPattern.replace(sanitized) { match ->
                "${match.groupValues[1]}$REDACTED"
            }
        return sanitized
    }

    private fun shouldRedact(
        key: String,
        value: String,
    ): Boolean {
        if (value.isBlank() || value == REDACTED || value == REDACTED_EMAIL) {
            return false
        }

        return when (key.lowercase()) {
            "code" -> value.length >= MIN_SECRET_LENGTH || value.any { !it.isDigit() }
            else -> true
        }
    }
}
