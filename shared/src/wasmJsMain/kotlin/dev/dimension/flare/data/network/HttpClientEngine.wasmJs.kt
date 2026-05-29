package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

internal actual val httpClientEngine: HttpClientEngine = Js.create()
