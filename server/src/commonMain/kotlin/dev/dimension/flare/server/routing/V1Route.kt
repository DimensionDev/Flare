package dev.dimension.flare.server.routing

import dev.dimension.flare.server.Api.V1
import dev.dimension.flare.server.ServerContext
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

internal fun Route.v1Route(
    context: ServerContext,
) {
    post<V1.Tldr, V1.Tldr.Request> { _, request ->
        val result = context.tldrService.summarize(request.text, request.targetLanguage)
        call.respond(V1.Tldr.Response(result))
    }
    post<V1.Translate, V1.Translate.Request> { _, request ->
        val result = context.translatorService.translate(request.text, request.targetLanguage)
        call.respond(V1.Translate.Response(result))
    }
}