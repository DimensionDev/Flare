package dev.dimension.flare.server

import dev.dimension.flare.server.service.TLDRService
import dev.dimension.flare.server.service.TranslatorService
import dev.dimension.flare.server.service.ai.AIService

internal data class ServerContext private constructor(
    val aiService: AIService,
    val translatorService: TranslatorService,
    val tldrService: TLDRService,
) {
    constructor(aiService: AIService) : this(
        aiService = aiService,
        translatorService = TranslatorService(aiService),
        tldrService = TLDRService(aiService),
    )
}