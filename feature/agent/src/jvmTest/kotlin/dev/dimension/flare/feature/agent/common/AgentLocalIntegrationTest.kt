package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResultsWithoutTools
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.subscription.SubscriptionDataSource
import dev.dimension.flare.data.datasource.subscription.SubscriptionSourceDetection
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.feature.agent.runtime.AiConfigKoogBridge
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

internal class AgentLocalIntegrationTest {
    @Test
    fun localAiRequestsSubscriptionConfirmationForMastodonSocial() =
        runTest(timeout = kotlin.time.Duration.parse("90s")) {
            val config =
                LocalAiConfig.load() ?: run {
                    println("Skipping local AI integration test: local.properties does not contain ai.url, ai.key, and ai.model.")
                    return@runTest
                }
            val runtime =
                AiConfigKoogBridge().createRuntime(
                    aiConfig = config.toAppAiConfig(),
                    httpClientFactory =
                        KtorKoogHttpClient.Factory(
                            baseClient = ktorClient(config = {}),
                        ),
                )
            assertNotNull(runtime, "Local AI config should create a runtime.")

            val subscriptionDataSource = LocalSubscriptionDataSource()
            val inputRequestStore = AgentToolInputRequestStore()
            val toolSet =
                subscriptionToolSet(
                    subscriptionDataSource = subscriptionDataSource,
                    inputRequestStore = inputRequestStore,
                )
            val request =
                FlareAgentRequest(
                    prompt = subscriptionPrompt("帮我添加订阅https://mastodon.social/"),
                    systemPrompt = LOCAL_AGENT_SYSTEM_PROMPT,
                    agentId = "flare-local-agent-test",
                    strategyName = "local_subscription_confirmation",
                    analyzeNodeName = "analyze_subscription_request",
                    executeToolsNodeName = "execute_subscription_tools",
                    sendToolResultsNodeName = "send_subscription_tool_results",
                    toolContext = AgentToolContext.Empty,
                    temperature = 0.0,
                    maxIterations = 8,
                    finishAfterToolResults = false,
                    toolSet = toolSet,
                )
            val agent =
                AIAgent
                    .builder()
                    .promptExecutor(runtime.promptExecutor)
                    .llmModel(runtime.model)
                    .toolRegistry(request.toolRegistry)
                    .id(request.agentId)
                    .systemPrompt(request.systemPromptWithToolGuidance)
                    .temperature(request.temperature)
                    .maxIterations(request.maxIterations)
                    .graphStrategy(localSubscriptionStrategy(request))
                    .build()

            val rawText =
                try {
                    agent.run(request.prompt, "local-subscription-confirmation-test")
                } finally {
                    agent.close()
                }
            val visibleResult = resolveAgentVisibleResult(rawText, inputRequestStore.snapshot())

            println("Local agent visible text:")
            println(visibleResult.text)
            println("Local agent input request:")
            println(visibleResult.inputRequest)
            println("Local subscription detect tool called: ${subscriptionDataSource.detectCalled}")
            println("Local subscription save tool called: ${subscriptionDataSource.saved}")

            val inputRequest = assertNotNull(visibleResult.inputRequest, "Agent should create a confirmation input request.")
            assertTrue(visibleResult.text.isNotBlank(), "Confirmation responses must keep visible text.")
            assertTrue(
                inputRequest.localizedPrompt.args.any { it.contains("mastodon.social") },
                "Confirmation prompt should mention mastodon.social.",
            )
            assertEquals(1, inputRequest.options.size, "Confirmation request should only include one button.")
            assertTrue(inputRequest.options.any { it.id == "confirm" }, "Confirmation request should include a confirm button.")
            assertTrue(subscriptionDataSource.detectCalled, "The agent should call detect_subscription_source.")
            assertEquals(false, subscriptionDataSource.saved, "The source must not be saved before explicit confirmation.")
        }

    private fun subscriptionToolSet(
        subscriptionDataSource: SubscriptionDataSource,
        inputRequestStore: AgentToolInputRequestStore,
    ): AgentToolSet {
        val session =
            AgentToolSession(
                status = null,
                searchTargets = emptyList(),
                composeTargets = emptyList(),
                postActionTargets = emptyList(),
                relationTargets = emptyList(),
                subscriptionDataSource = subscriptionDataSource,
                userTargets = emptyList(),
                attachmentStore = AgentToolAttachmentStore(),
                inputRequestStore = inputRequestStore,
            )
        return AgentToolSet(
            toolRegistry =
                ToolRegistry {
                    tool(DetectSubscriptionSourceTool(session))
                    tool(SaveSubscriptionSourceTool(session))
                },
            systemPromptGuidance = LOCAL_SUBSCRIPTION_TOOL_GUIDANCE,
            traceRegistry = AgentToolTraceRegistry(emptyMap()),
            attachmentStore = session.attachmentStore,
            inputRequestStore = session.inputRequestStore,
        )
    }

    private fun localSubscriptionStrategy(request: FlareAgentRequest) =
        strategy<Prompt, String>(request.strategyName) {
            val nodeAnalyze by node<Prompt, Message.Assistant>(request.analyzeNodeName) { prompt ->
                llm.writeSession {
                    appendPrompt {
                        messages(prompt.messages)
                    }
                    requestLLM()
                }
            }
            val nodeExecuteTools by nodeExecuteTools(request.executeToolsNodeName)
            val nodeSendToolResults by
                if (request.finishAfterToolResults) {
                    nodeLLMSendToolResultsWithoutTools(request.sendToolResultsNodeName)
                } else {
                    ai.koog.agents.core.dsl.extension
                        .nodeLLMSendToolResults(request.sendToolResultsNodeName)
                }

            edge(nodeStart forwardTo nodeAnalyze)
            edge(nodeAnalyze forwardTo nodeExecuteTools onToolCalls { true })
            edge(nodeAnalyze forwardTo nodeFinish onTextMessage { true })
            edge(nodeExecuteTools forwardTo nodeSendToolResults)
            if (!request.finishAfterToolResults) {
                edge(nodeSendToolResults forwardTo nodeExecuteTools onToolCalls { true })
            }
            edge(nodeSendToolResults forwardTo nodeFinish onTextMessage { true })
        }

    private fun subscriptionPrompt(userInput: String): Prompt =
        Prompt.build("local-subscription-confirmation") {
            user {
                text(
                    """
                    User message:
                    $userInput
                    """.trimIndent(),
                )
            }
        }

    private data class LocalAiConfig(
        val serverUrl: String,
        val apiKey: String,
        val model: String,
    ) {
        fun toAppAiConfig(): dev.dimension.flare.data.datastore.model.AppSettings.AiConfig =
            dev.dimension.flare.data.datastore.model.AppSettings.AiConfig(
                type =
                    dev.dimension.flare.data.datastore.model.AppSettings.AiConfig.Type.OpenAI(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        model = model,
                    ),
            )

        companion object {
            fun load(): LocalAiConfig? {
                val properties =
                    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                        .flatMap { dir ->
                            sequenceOf(
                                File(dir, "local.properties"),
                                File(dir, "local.propreties"),
                            )
                        }.firstOrNull { it.isFile }
                        ?.inputStream()
                        ?.use { input ->
                            Properties().apply { load(input) }
                        } ?: return null
                val serverUrl = properties.firstValue("ai.url", "ai.openai.serverUrl", "openai.url", "OPENAI_BASE_URL")
                val apiKey = properties.firstValue("ai.key", "ai.openai.apiKey", "openai.apiKey", "OPENAI_API_KEY")
                val model = properties.firstValue("ai.model", "ai.openai.model", "openai.model", "OPENAI_MODEL")
                if (serverUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                    return null
                }
                return LocalAiConfig(
                    serverUrl = serverUrl.toHttpUrl(),
                    apiKey = apiKey,
                    model = model,
                )
            }
        }
    }

    private class LocalSubscriptionDataSource : SubscriptionDataSource {
        var detectCalled: Boolean = false
        var saved: Boolean = false

        override suspend fun listSources(): List<UiRssSource> = emptyList()

        override suspend fun detectSource(input: String): SubscriptionSourceDetection {
            detectCalled = true
            assertTrue(input.contains("mastodon.social"), "The agent should inspect mastodon.social.")
            return SubscriptionSourceDetection.SubscriptionInstance(
                host = "mastodon.social",
                instanceName = "mastodon.social",
                icon = null,
                availableTimelines = listOf(SubscriptionType.MASTODON_TRENDS),
            )
        }

        override fun createTimelineLoader(
            type: SubscriptionType,
            url: String,
        ): CacheableRemoteLoader<UiTimelineV2> =
            object : CacheableRemoteLoader<UiTimelineV2> {
                override val pagingKey: String = "local-subscription-test"

                override suspend fun load(
                    pageSize: Int,
                    request: PagingRequest,
                ): PagingResult<UiTimelineV2> = PagingResult(endOfPaginationReached = true)
            }

        override suspend fun saveSource(input: SubscriptionSourceInput): UiRssSource {
            saved = true
            return UiRssSource(
                id = 1,
                url = input.url,
                title = input.title,
                lastUpdate = Instant.DISTANT_PAST.toUi(),
                favIcon = input.icon,
                displayMode = input.displayMode,
                type = input.type,
            )
        }

        override suspend fun deleteSource(id: Int): UiRssSource? = null

        override suspend fun loadRssArticle(
            url: String,
            descriptionHtml: String?,
            descriptionTitle: String?,
        ): DocumentData =
            DocumentData(
                title = descriptionTitle.orEmpty(),
                content = descriptionHtml.orEmpty(),
                textContent = descriptionHtml.orEmpty(),
                length = null,
                excerpt = null,
                byline = null,
                dir = null,
                siteName = null,
                lang = null,
                publishedTime = null,
            )
    }

    private companion object {
        const val LOCAL_AGENT_SYSTEM_PROMPT =
            """
            You are Flare's local integration-test assistant.
            Respond in Chinese.
            Use tools for subscription add/save requests.
            Return only the final user-visible answer.
            """

        const val LOCAL_SUBSCRIPTION_TOOL_GUIDANCE =
            """
            Subscription confirmation rules:
            - When the user asks to add or save a subscription source, call detect_subscription_source if the type is unclear.
            - Then call save_subscription_source with confirmed=false.
            - Do not call save_subscription_source with confirmed=true until the latest user message explicitly confirms.
            - After confirmed=false, return the confirmation content from the tool and do not return an empty answer.
            - Let Flare show the confirmation button from the structured input request.
            """
    }
}

private fun Properties.firstValue(vararg names: String): String =
    names
        .firstNotNullOfOrNull { name ->
            getProperty(name)?.trim()?.removeWrappingQuotes()?.takeIf { it.isNotBlank() }
        }.orEmpty()

private fun String.toHttpUrl(): String =
    when {
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true) -> this
        else -> "https://$this"
    }

private fun String.removeWrappingQuotes(): String =
    when {
        length >= 2 && first() == '"' && last() == '"' -> substring(1, lastIndex)
        length >= 2 && first() == '\'' && last() == '\'' -> substring(1, lastIndex)
        else -> this
    }.trim()
