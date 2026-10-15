package dev.dimension.flare.data.network.rss

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorDomain

internal actual fun Throwable.isRssTransportSecurityFailure(): Boolean =
    this is DarwinHttpRequestException && origin.domain == NSURLErrorDomain &&
        (origin.code in -1206L..-1200L || origin.code == -1022L)
