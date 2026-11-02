package dev.dimension.flare.data.network.rss

import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

internal actual fun Throwable.isRssTransportSecurityFailure(): Boolean =
    this is SSLHandshakeException || this is SSLPeerUnverifiedException ||
        (this is UnknownServiceException && message?.startsWith("CLEARTEXT communication") == true)
