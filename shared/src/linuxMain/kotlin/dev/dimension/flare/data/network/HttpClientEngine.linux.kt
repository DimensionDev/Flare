package dev.dimension.flare.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.curl.Curl

actual val httpClientEngine: HttpClientEngine =
    Curl.create {
    }
