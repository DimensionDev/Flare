package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

public actual fun createHttpClientEngine(): HttpClientEngine =
    Darwin.create {
    }
