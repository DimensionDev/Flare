package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine

internal actual val httpClientEngine: HttpClientEngine = createOkHttpEngine()
