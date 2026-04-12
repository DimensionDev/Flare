package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

internal actual val httpClientEngine: HttpClientEngine
    get() = CIO.create { }
