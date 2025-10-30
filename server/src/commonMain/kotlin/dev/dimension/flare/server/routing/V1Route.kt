package dev.dimension.flare.server.routing

import dev.dimension.flare.server.Api.V1
import dev.dimension.flare.server.ServerContext
import dev.dimension.flare.server.common.Log
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

internal fun Route.v1Route(
    context: ServerContext,
) {
    post<V1.Tldr, V1.Tldr.Request> { _, request ->
        val result = context.tldrService.summarize(request.text, request.targetLanguage)
        call.respond(
            runCatching {
                V1.Tldr.Response(result)
            }.getOrElse {
                Log.e("V1Route", "TLDR generation failed", it)
                V1.Tldr.Response(
                    "AI summary generation is currently unavailable."
                )
            }
        )
    }
    post<V1.Translate, V1.Translate.Request> { _, request ->
        val result = context.translatorService.translate(request.text, request.targetLanguage)
        call.respond(
            runCatching {
                V1.Translate.Response(result)
            }.getOrElse {
                Log.e("V1Route", "Translation failed", it)
                V1.Translate.Response(
                    "AI translation service is currently unavailable."
                )
            }
        )
    }
}