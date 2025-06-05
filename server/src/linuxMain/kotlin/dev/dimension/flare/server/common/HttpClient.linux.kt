package dev.dimension.flare.server.common

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.curl.Curl

internal actual fun createEngine(): HttpClientEngine {
    return Curl.create()
}