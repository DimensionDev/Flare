package dev.dimension.flare.server

import dev.dimension.flare.server.service.TLDRService
import dev.dimension.flare.server.service.TranslatorService
import dev.dimension.flare.server.service.ai.AIService
import io.ktor.server.config.ApplicationConfig

internal data class ServerContext private constructor(
    val aiService: AIService,
    val translatorService: TranslatorService,
    val tldrService: TLDRService,
    val config: ApplicationConfig,
) {
    constructor(aiService: AIService, config: ApplicationConfig) : this(
        aiService = aiService,
        translatorService = TranslatorService(aiService),
        tldrService = TLDRService(aiService),
        config = config,
    )
}