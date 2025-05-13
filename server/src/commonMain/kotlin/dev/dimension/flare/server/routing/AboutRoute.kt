package dev.dimension.flare.server.routing

import dev.dimension.flare.server.Api.About
import dev.dimension.flare.server.ServerContext
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route


internal fun Route.aboutRoute(
    context: ServerContext,
) {
    get<About> {
        call.respond(
            About.Response(
                name = context.config.property("about.name").getString(),
                version = context.config.property("about.version").getString(),
                apiLevel = "v1",
            )
        )
    }
}