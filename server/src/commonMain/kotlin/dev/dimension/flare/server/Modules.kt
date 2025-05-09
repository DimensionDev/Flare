package dev.dimension.flare.server

import dev.dimension.flare.server.common.JSON
import dev.dimension.flare.server.routing.configureRouting
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.hsts.HSTS

internal fun Application.modules(
    context: Context,
) {
    install(ContentNegotiation) {
        json(JSON)
    }
    install(HSTS) {
        includeSubDomains = true
    }
    configureRouting(context)
}