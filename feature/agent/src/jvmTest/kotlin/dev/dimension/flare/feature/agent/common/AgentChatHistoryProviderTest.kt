package dev.dimension.flare.feature.agent.common

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.feature.agent.database.AgentDatabase
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.feature.agent.presenter.agentMessageText
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentChatHistoryProviderTest {
    @Test
    fun trimmedModelMemoryDoesNotRemoveVisibleRepliesOrChangeTheirIds() =
        runTest {
            Fixture().use { fixture ->
                val history = fixture.history
                val question = user("Check that.", "question")
                val first = assistant("I will check that.", "first")
                val final = assistant("Here is the result.", "final")
                history.store(CONVERSATION, listOf(question, first, final))
                val before = history.observeMessages(CONVERSATION).first()

                history.store(CONVERSATION, listOf(final))

                val after = history.observeMessages(CONVERSATION).first()
                assertEquals(listOf("Here is the result.", "I will check that.", "Check that."), after.map { it.parts.agentMessageText() })
                assertEquals(before.map { it.id }, after.map { it.id })
            }
        }

    @Test
    fun optimisticUserMessageKeepsItsIdAndSystemPromptStaysFirst() =
        runTest {
            Fixture().use { fixture ->
                val history = fixture.history
                history.storeUserUiMessage(CONVERSATION, "Check that.")
                val before = history.observeMessages(CONVERSATION).first().single()
                val messages =
                    Prompt
                        .build("input") {
                            system("Be helpful.")
                            user("Check that.")
                            assistant("I will check that.")
                        }.messages
                        .mapIndexed { index, message -> message.withAgentMessageId("message-$index") }

                history.store(CONVERSATION, messages)

                assertEquals(
                    listOf(Message.Role.System, Message.Role.User, Message.Role.Assistant),
                    history.load(CONVERSATION).map { it.role },
                )
                val after = history.observeMessages(CONVERSATION).first()
                assertEquals(listOf("I will check that.", "Check that."), after.map { it.parts.agentMessageText() })
                assertEquals(before.id, after.last().id)
            }
        }

    @Test
    fun legacyMessagesAndIdenticalNewRepliesKeepSeparateStableIds() =
        runTest {
            Fixture().use { fixture ->
                val history = fixture.history
                val legacy = assistant("Same answer.", "legacy").copy(id = null)
                history.store(CONVERSATION, listOf(legacy))
                val before = history.observeMessages(CONVERSATION).first().single()
                val loaded = history.load(CONVERSATION)
                val next = legacy.copy(id = "next")

                history.store(CONVERSATION, loaded + next)
                history.store(CONVERSATION, listOf(next))

                val after = history.observeMessages(CONVERSATION).first()
                assertEquals(listOf("Same answer.", "Same answer."), after.map { it.parts.agentMessageText() })
                assertEquals(before.id, after.last().id)
                assertEquals(2, after.map { it.id }.distinct().size)
            }
        }

    @Test
    fun toolAndReasoningOnlyRepliesDoNotCreateVisibleMessagesOrActions() =
        runTest {
            Fixture().use { fixture ->
                val response =
                    assistant("", "tools").copy(
                        parts = listOf(MessagePart.Reasoning("private reasoning"), MessagePart.Tool.Call("call-1", "lookup", "{}")),
                    )
                val pending =
                    AgentPendingInputRequest(
                        requestId = "selection",
                        options = listOf(AgentPendingInputRequest.Option("post", "selected", postPreview = post())),
                    )

                fixture.history.storeAssistantResponse(CONVERSATION, listOf(response), emptyList(), pending)

                assertTrue(
                    fixture.history
                        .observeMessages(CONVERSATION)
                        .first()
                        .isEmpty(),
                )
                assertTrue(fixture.history.load(CONVERSATION).isEmpty())
            }
        }

    @Test
    fun cardsAndSelectedActionsSurviveCompletionFailureAndDatabaseReopen() =
        runTest {
            val directory = Files.createTempDirectory("flare-agent-history-")
            val path = directory.resolve("agent.db").toString()
            val post = post()
            val pending =
                AgentPendingInputRequest(
                    requestId = "confirmation",
                    options = listOf(AgentPendingInputRequest.Option("confirm", "confirmed=true")),
                )
            val metadata =
                """<!-- flare-agent-actions {"requestId":"confirmation","options":[{"id":"confirm","label":"Confirm","buttonType":"Primary"}]} -->"""
            val first =
                assistant("Found ${post.agentAttachmentMarker()} $metadata", "first").let {
                    it.copy(
                        parts =
                            it.parts +
                                MessagePart.Reasoning("private reasoning") +
                                MessagePart.Tool.Call("call-1", "lookup", "{\"secret\":true}"),
                    )
                }
            val final = assistant("Confirm ${post.agentAttachmentMarker()} $metadata", "final")
            var expectedIds: List<String> = emptyList()
            var intermediateId: String? = null
            try {
                Fixture(path).use { fixture ->
                    val history = fixture.history
                    history.storeAssistantResponse(CONVERSATION, listOf(first), listOf(AgentMessagePart.PostCard(post)), pending)
                    val intermediate = history.observeMessages(CONVERSATION).first().single()
                    assertEquals(
                        post.statusKey,
                        intermediate.parts
                            .filterIsInstance<AgentMessagePart.PostCard>()
                            .single()
                            .post.statusKey,
                    )
                    assertTrue(intermediate.parts.none { it is AgentMessagePart.Actions })
                    assertEquals("Found", intermediate.parts.agentMessageText())
                    intermediateId = intermediate.id
                    history.updateRoomState(CONVERSATION, errorMessage = "Model request failed", isRunning = false)
                }

                Fixture(path).use { fixture ->
                    val history = fixture.history
                    assertEquals(
                        intermediateId,
                        history
                            .observeMessages(CONVERSATION)
                            .first()
                            .single()
                            .id,
                    )
                    assertEquals("Model request failed", history.observeRoom(CONVERSATION).first()?.errorMessage)
                    history.storeAssistantResponse(CONVERSATION, listOf(first, final), listOf(AgentMessagePart.PostCard(post)), pending)
                    val completed = history.observeMessages(CONVERSATION).first()
                    assertEquals(intermediateId, completed.last().id)
                    val actions = assertIs<AgentMessagePart.Actions>(completed.first().parts.last())
                    assertEquals(
                        "Confirm",
                        actions.request.options
                            .single()
                            .label,
                    )
                    history.markInputRequestSelected(CONVERSATION, pending.requestId, "confirm")
                    history.store(CONVERSATION, listOf(final))
                    history.updateRoomState(CONVERSATION, errorMessage = "Next request failed", isRunning = false)
                    expectedIds = history.observeMessages(CONVERSATION).first().map { it.id }
                }

                Fixture(path).use { fixture ->
                    val history = fixture.history
                    val reopened = history.observeMessages(CONVERSATION).first()
                    assertEquals(expectedIds, reopened.map { it.id })
                    assertEquals(2, reopened.size)
                    assertTrue(reopened.all { message -> message.parts.any { it is AgentMessagePart.PostCard } })
                    assertTrue(assertIs<AgentMessagePart.Actions>(reopened.first().parts.last()).selected)
                    assertFalse(reopened.joinToString { it.parts.agentMessageText() }.contains("flare-agent-actions"))
                    assertEquals("Next request failed", history.observeRoom(CONVERSATION).first()?.errorMessage)
                    assertTrue(
                        history.load(CONVERSATION).all { message ->
                            message.parts.none {
                                it is MessagePart.Tool ||
                                    it is MessagePart.Reasoning
                            }
                        },
                    )
                }
            } finally {
                Files.list(directory).use { paths -> paths.forEach(Files::delete) }
                Files.delete(directory)
            }
        }

    private class Fixture(
        path: String? = null,
    ) : AutoCloseable {
        private val database =
            (if (path == null) Room.inMemoryDatabaseBuilder<AgentDatabase>() else Room.databaseBuilder<AgentDatabase>(path))
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        val history = AgentChatHistoryProvider(database) { null }

        override fun close() = database.close()
    }

    private fun assistant(
        text: String,
        id: String,
    ): Message.Assistant = (Prompt.build(id) { assistant(text) }.messages.single() as Message.Assistant).copy(id = id)

    private fun user(
        text: String,
        id: String,
    ): Message.User = (Prompt.build(id) { user(text) }.messages.single() as Message.User).copy(id = id)

    private fun post(): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformId = "Mastodon",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText("fixture".toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey("post", "example.social"),
            card = null,
            createdAt =
                kotlin.time.Clock.System
                    .now()
                    .toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(MicroBlogKey("viewer", "example.social")),
        )

    private companion object {
        const val CONVERSATION = "conversation"
    }
}
