package dev.dimension.flare.server.common

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun createEngine(): HttpClientEngine {
    return Darwin.create()
}