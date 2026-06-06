package dev.dimension.flare.feature.agent.runtime

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import dev.dimension.flare.data.datastore.model.AppSettings

internal class FlareAgentRuntime(
    val promptExecutor: PromptExecutor,
    val model: LLModel,
    val aiConfig: AppSettings.AiConfig,
)
