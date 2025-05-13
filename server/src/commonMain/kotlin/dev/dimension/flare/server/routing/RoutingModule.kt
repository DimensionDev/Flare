package dev.dimension.flare.server.routing

import dev.dimension.flare.server.ServerContext
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing

internal fun Application.configureRouting(
    context: ServerContext,
) {
    install(Resources)
    routing {
        v1Route(context = context)
    }
}