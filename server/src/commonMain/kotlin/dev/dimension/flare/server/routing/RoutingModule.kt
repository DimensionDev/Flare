package dev.dimension.flare.server.routing

import dev.dimension.flare.server.Context
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing

internal fun Application.configureRouting(
    context: Context,
) {
    install(Resources)
    routing {
        translateRoute(context = context)
    }
}