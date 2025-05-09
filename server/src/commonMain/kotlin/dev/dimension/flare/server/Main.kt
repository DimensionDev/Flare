package dev.dimension.flare.server

import io.ktor.resources.Resource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.hsts.HSTS
import io.ktor.server.resources.Resources
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

public fun main(args: Array<String>) {
    embeddedServer(
        factory = CIO,
        port = 8080,
        module = Application::modules
    ).start(wait = true)
}

internal fun Application.modules() {
    install(ContentNegotiation) {
        json()
    }
    install(HSTS) {
        includeSubDomains = true
    }
    install(Resources)
    routing {
        post<V1.Translate> { translate ->
            call.respond(translate.text)
        }
    }
}

@Serializable
@Resource("/v1")
internal class V1 {
    @Serializable
    @Resource("translate")
    internal data class Translate(val text: String, val targetLanguage: String)
}