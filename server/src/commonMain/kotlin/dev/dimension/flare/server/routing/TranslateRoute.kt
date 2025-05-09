package dev.dimension.flare.server.routing

import dev.dimension.flare.server.Context
import dev.dimension.flare.server.V1
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route


internal fun Route.translateRoute(
    context: Context,
) {
    post<V1.Translate> { translate ->
        val result = context.translatorService.translate(translate.text, translate.targetLanguage)
        call.respond(V1.Translate.Response(result.translation))
    }
}