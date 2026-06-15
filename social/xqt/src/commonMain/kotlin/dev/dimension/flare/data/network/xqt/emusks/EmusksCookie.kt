package dev.dimension.flare.data.network.xqt.emusks

internal data class EmusksCookie(
    val values: Map<String, String>,
) {
    val authToken: String?
        get() = values[AUTH_TOKEN]

    val csrfToken: String?
        get() = values[CSRF_TOKEN]

    val guestToken: String?
        get() = values[GUEST_TOKEN]

    val hasAuthenticatedSession: Boolean
        get() = authToken != null && csrfToken != null

    fun toHeader(): String = values.entries.joinToString("; ") { (name, value) -> "$name=$value" }

    fun plus(other: EmusksCookie): EmusksCookie =
        EmusksCookie(
            values = values + other.values,
        )

    companion object {
        private const val AUTH_TOKEN = "auth_token"
        private const val CSRF_TOKEN = "ct0"
        private const val GUEST_TOKEN = "gt"

        fun parse(cookieHeader: String): EmusksCookie =
            EmusksCookie(
                values =
                    cookieHeader
                        .split(';')
                        .mapNotNull { part ->
                            val index = part.indexOf('=')
                            if (index <= 0) {
                                return@mapNotNull null
                            }
                            val name = part.substring(0, index).trim()
                            val value = part.substring(index + 1).trim()
                            if (name.isEmpty()) {
                                null
                            } else {
                                name to value
                            }
                        }.toMap(),
            )

        fun fromAuthToken(authToken: String): EmusksCookie =
            EmusksCookie(
                values = mapOf(AUTH_TOKEN to authToken),
            )

        fun fromSetCookieHeaders(setCookieHeaders: List<String>): EmusksCookie =
            EmusksCookie(
                values =
                    setCookieHeaders
                        .mapNotNull { header ->
                            val cookiePair = header.substringBefore(';')
                            val index = cookiePair.indexOf('=')
                            if (index <= 0) {
                                return@mapNotNull null
                            }
                            val name = cookiePair.substring(0, index).trim()
                            val value = cookiePair.substring(index + 1).trim()
                            if (name.isEmpty()) {
                                null
                            } else {
                                name to value
                            }
                        }.toMap(),
            )
    }
}
