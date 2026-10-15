package dev.dimension.flare.data.network.rss

// Browsers do not expose whether a failed fetch was caused by TLS, CORS, or connectivity.
internal actual fun Throwable.isRssTransportSecurityFailure(): Boolean = false
