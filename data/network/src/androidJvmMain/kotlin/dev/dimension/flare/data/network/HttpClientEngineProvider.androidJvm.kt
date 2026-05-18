package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

public actual fun createHttpClientEngine(): HttpClientEngine = OkHttp.create {}
