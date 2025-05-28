package dev.dimension.flare.server.common

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

internal actual fun createEngine(): HttpClientEngine {
    return CIO.create()
}