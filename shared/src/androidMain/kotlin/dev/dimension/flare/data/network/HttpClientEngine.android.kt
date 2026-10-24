package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine

public actual val httpClientEngine: HttpClientEngine = createOkHttpEngine()
