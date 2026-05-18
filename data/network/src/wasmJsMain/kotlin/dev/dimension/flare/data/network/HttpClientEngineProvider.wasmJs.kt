package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

public actual fun createHttpClientEngine(): HttpClientEngine = Js.create()
