package dev.dimension.flare.feature.agent.common

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.dimension.flare.feature.agent.database.AGENT_CONTEXT_MIGRATION
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
import kotlin.test.assertFailsWith
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

    @Test
    fun checkpointsSurviveReopenWithoutChangingVisibleHistoryAndClearWithTheConversation() =
        runTest {
            val directory = Files.createTempDirectory("flare-agent-checkpoint-")
            val path = directory.resolve("agent.db").toString()
            val messages =
                listOf(
                    user("Original requirement", "q1"),
                    assistant("Completed step", "a1"),
                    user("Recent question", "q2"),
                    assistant("Recent answer", "a2"),
                )
            try {
                var ids: List<String> = emptyList()
                Fixture(path).use { fixture ->
                    fixture.history.store(CONVERSATION, messages)
                    ids =
                        fixture.history
                            .observeMessages(CONVERSATION)
                            .first()
                            .map { it.id }
                    fixture.history.storeCheckpoint(CONVERSATION, messages, AgentContextCheckpoint("Saved progress", "a1"))
                    assertEquals(
                        ids,
                        fixture.history
                            .observeMessages(CONVERSATION)
                            .first()
                            .map { it.id },
                    )
                }
                Fixture(path).use { fixture ->
                    val loaded = fixture.history.load(CONVERSATION)
                    assertEquals(listOf(AGENT_CONTEXT_SUMMARY_ID, "q2", "a2"), loaded.map { it.id })
                    assertTrue(loaded.first().textContent().contains("Saved progress"))
                    val next = assistant("Continued", "a3")
                    fixture.history.store(CONVERSATION, loaded + next)
                    assertEquals(
                        ids,
                        fixture.history
                            .observeMessages(CONVERSATION)
                            .first()
                            .drop(1)
                            .map { it.id },
                    )
                    assertFalse(
                        fixture.history
                            .observeMessages(CONVERSATION)
                            .first()
                            .any { it.parts.agentMessageText().contains("Saved progress") },
                    )
                    fixture.history.storeCheckpoint(CONVERSATION, loaded + next, AgentContextCheckpoint("Updated progress", "a2"))
                    assertEquals(listOf(AGENT_CONTEXT_SUMMARY_ID, "a3"), fixture.history.load(CONVERSATION).map { it.id })
                    fixture.history.clear(CONVERSATION)
                    assertTrue(fixture.history.load(CONVERSATION).isEmpty())
                    fixture.history.storeCheckpoint(CONVERSATION, messages, AgentContextCheckpoint("New progress", "a1"))
                    fixture.history.deleteConversation(CONVERSATION)
                    assertTrue(fixture.history.load(CONVERSATION).isEmpty())
                }
            } finally {
                Files.list(directory).use { paths -> paths.forEach(Files::delete) }
                Files.delete(directory)
            }
        }

    @Test
    fun invalidCheckpointRollsBackBothTheBoundaryAndNewMessages() =
        runTest {
            Fixture().use { fixture ->
                val original = listOf(user("Question", "q"), assistant("Answer", "a"))
                fixture.history.storeCheckpoint(CONVERSATION, original, AgentContextCheckpoint("Existing summary", "q"))
                val before = fixture.history.load(CONVERSATION)
                assertFailsWith<NoSuchElementException> {
                    fixture.history.storeCheckpoint(
                        CONVERSATION,
                        original + assistant("Must not persist", "new"),
                        AgentContextCheckpoint("Invalid", "missing"),
                    )
                }
                assertEquals(before.map { it.textContent() }, fixture.history.load(CONVERSATION).map { it.textContent() })
                assertEquals(
                    2,
                    fixture.history
                        .observeMessages(CONVERSATION)
                        .first()
                        .size,
                )
            }
        }

    @Test
    fun replayKeepsCompleteToolExchangesAndDropsOrphanCallsAndResults() =
        runTest {
            Fixture().use { fixture ->
                val call = assistant("", "call").copy(parts = listOf(MessagePart.Tool.Call("tool-1", "lookup", "{}")))
                val result =
                    Prompt
                        .build("results") {
                            user { toolResult(MessagePart.Tool.Result("tool-1", "lookup", "Found item")) }
                        }.messages
                        .single()
                        .withAgentMessageId("result")
                val orphan =
                    assistant(
                        "Visible progress",
                        "orphan",
                    ).copy(parts = listOf(MessagePart.Text("Visible progress"), MessagePart.Tool.Call("tool-2", "lookup", "{}")))
                fixture.history.storeCheckpoint(
                    CONVERSATION,
                    listOf(user("Requirement", "old"), call, result, orphan),
                    AgentContextCheckpoint("Requirement", "old"),
                )
                val replay = fixture.history.load(CONVERSATION)
                assertEquals(1, replay.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Call>().size)
                assertEquals(1, replay.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>().size)
                assertEquals("Visible progress", replay.last().textContent())
                assertEquals(
                    2,
                    fixture.history
                        .observeMessages(CONVERSATION)
                        .first()
                        .size,
                )
            }
        }

    @Test
    fun versionNineMigrationPreservesExistingChatData() =
        runTest {
            val directory = Files.createTempDirectory("flare-agent-migration-")
            val path = directory.resolve("agent.db").toString()
            try {
                BundledSQLiteDriver().open(path).use { connection ->
                    connection.execSQL(
                        "CREATE TABLE agent_conversations (conversationId TEXT NOT NULL PRIMARY KEY, title TEXT, titleGenerated INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isRunning INTEGER NOT NULL, errorMessage TEXT)",
                    )
                    connection.execSQL(
                        "CREATE TABLE agent_messages (conversationId TEXT NOT NULL, position INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, contentJson TEXT NOT NULL, messageJson TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(conversationId, position))",
                    )
                    connection.execSQL("INSERT INTO agent_conversations VALUES ('conversation', 'Existing conversation', 1, 0, 0, 0, NULL)")
                    connection.execSQL("INSERT INTO agent_messages VALUES ('conversation', 0, 'User', 'Existing question', '', '', 0)")
                    connection.execSQL("PRAGMA user_version = 9")
                }
                Fixture(path).use { fixture ->
                    assertEquals(
                        "Existing conversation",
                        fixture.history
                            .observeRoom(CONVERSATION)
                            .first()
                            ?.title,
                    )
                    assertEquals(
                        "Existing question",
                        fixture.history
                            .observeMessages(CONVERSATION)
                            .first()
                            .single()
                            .parts
                            .agentMessageText(),
                    )
                    val messages = listOf(user("Next", "q"), assistant("Answer", "a"))
                    fixture.history.storeCheckpoint(CONVERSATION, messages, AgentContextCheckpoint("Migrated", "q"))
                    assertTrue(
                        fixture.history
                            .load(CONVERSATION)
                            .first()
                            .textContent()
                            .contains("Migrated"),
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
                .addMigrations(AGENT_CONTEXT_MIGRATION)
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
