package dev.dimension.flare.feature.agent.runtime

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

internal class FlareAgentRuntime(
    val promptExecutor: PromptExecutor,
    val model: LLModel,
    val params: LLMParams,
)
