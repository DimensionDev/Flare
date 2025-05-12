package dev.dimension.flare.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import dev.dimension.flare.server.service.ai.AIService
import io.ktor.server.cio.CIO
import io.ktor.server.config.yaml.YamlConfig
import io.ktor.server.engine.embeddedServer

public fun main(args: Array<String>) {
    Server().main(args)
}

internal class Server : CliktCommand() {
    val configPath: String by option().required().help("Path to the configuration file")

    override fun run() {
        val config = YamlConfig(configPath) ?:
        throw IllegalStateException("Failed to load configuration")
        val aiService = AIService.create(config)
        val context = ServerContext(
            aiService = aiService,
        )
        embeddedServer(
            factory = CIO,
            port = 8080,
            host = "localhost",
            module = {
                modules(
                    context = context,
                )
            }
        ).start(wait = true)
    }
}

