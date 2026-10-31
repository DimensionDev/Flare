package dev.dimension.flare.feature.agent.presenter

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentConversationEvent
import dev.dimension.flare.feature.agent.common.AgentPendingInputRequest
import dev.dimension.flare.feature.agent.common.AgentRetryMode
import dev.dimension.flare.feature.agent.common.AgentRetryableException
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.common.FlareAgentUnavailableException
import dev.dimension.flare.feature.agent.database.AgentDatabase
import dev.dimension.flare.feature.agent.runtime.AgentAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentChatRetryTest {
    @Test
    fun confirmationIsLimitedToTheSelectedAttemptEvenWhenStartupFails() =
        runTest {
            for (failurePoint in listOf("submission", "runtime", "cancellation", "success")) {
                val database =
                    Room
                        .inMemoryDatabaseBuilder<AgentDatabase>()
                        .setDriver(BundledSQLiteDriver())
                        .setQueryCoroutineContext(Dispatchers.IO)
                        .build()
                val history = AgentChatHistoryProvider(database) { null }
                val conversationId = "confirmation-$failurePoint"
                val pending =
                    AgentPendingInputRequest(
                        "delete",
                        listOf(
                            AgentPendingInputRequest.Option(
                                "confirm",
                                "Confirm deleting this plugin and its accounts.",
                                confirmationKey = "delete_plugin:fixture:0",
                            ),
                        ),
                    )
                val reply = (Prompt.build("request") { assistant("") }.messages.single() as Message.Assistant).copy(id = "request")
                history.storeAssistantResponse(conversationId, listOf(reply), emptyList(), pending)
                val option =
                    history
                        .observeMessages(conversationId)
                        .first()
                        .single()
                        .parts
                        .filterIsInstance<AgentMessagePart.Actions>()
                        .single()
                        .request.options
                        .single()
                val finished = MutableStateFlow(0)
                val selected = CompletableDeferred<Unit>()
                val contextReady = CompletableDeferred<Unit>()
                val context =
                    flow {
                        emit(Unit)
                        contextReady.complete(Unit)
                    }
                var runtimeAttempts = 0
                val firstGrant = CompletableDeferred<String?>()
                val nextGrant = CompletableDeferred<String?>()
                val states = MutableStateFlow<AgentChatPresenterController<Unit, Unit>?>(null)
                val roomFlow = history.observeRoom(conversationId)
                startKoin { modules(module { single { history } }) }
                val collection =
                    backgroundScope.launch {
                        moleculeFlow(RecompositionMode.Immediate) {
                            val room by roomFlow.collectAsState(null)
                            rememberAgentChatPresenterController<Unit, Unit>(
                                key = conversationId,
                                conversationId = conversationId,
                                room = room ?: AgentChatRoom.empty(conversationId),
                                contextFlow = context,
                                autoRunOnContext = false,
                                runAgent = { _, input, _, _ ->
                                    flow<AgentConversationEvent<Unit, AgentTrace>> {
                                        runtimeAttempts++
                                        if (input == option.value) {
                                            if (failurePoint == "runtime") {
                                                throw FlareAgentUnavailableException(
                                                    AgentAvailability.Unavailable(AgentAvailability.Reason.MissingOpenAIModel),
                                                )
                                            }
                                            firstGrant.complete(history.consumeConfirmation(conversationId))
                                        } else {
                                            nextGrant.complete(history.consumeConfirmation(conversationId))
                                        }
                                    }
                                },
                                onUserMessageSubmitted = { history.storeUserUiMessage(conversationId, it) },
                                onInputRequestSelected = { requestId, optionId ->
                                    history.markInputRequestSelected(conversationId, requestId, optionId)
                                    selected.complete(Unit)
                                },
                                onInputRequestOptionSubmitted = {
                                    when (failurePoint) {
                                        "submission" -> error("Failed to save the selected option")
                                        "cancellation" -> throw CancellationException("Submission cancelled")
                                        else -> history.storeUserUiInputRequestOption(conversationId, it)
                                    }
                                },
                                onRoomRuntimeStateChanged = {
                                    history.updateRoomState(conversationId, isRunning = it, updateErrorMessage = false)
                                    if (!it) finished.value++
                                },
                                onRoomStateChanged = { history.updateRoomState(conversationId, errorMessage = it) },
                                missingContextError = { error("Context missing") },
                            )
                        }.collect { states.value = it }
                    }
                try {
                    contextReady.await()
                    states.filterNotNull().first { it.messages is PagingState.Success }.selectInputRequestOption(option)
                    selected.await()
                    finished.first { it == 1 }
                    assertEquals(if (failurePoint in listOf("runtime", "success")) 1 else 0, runtimeAttempts, failurePoint)
                    if (failurePoint == "success") assertEquals("delete_plugin:fixture:0", firstGrant.await())
                    assertEquals(null, history.consumeConfirmation(conversationId), failurePoint)
                    states.filterNotNull().first { !it.room.isRunning }.setInput("Unrelated question")
                    Snapshot.sendApplyNotifications()
                    states.filterNotNull().first { it.canSend }.sendMessage()
                    assertEquals(null, nextGrant.await(), failurePoint)
                    finished.first { it == 2 }
                } finally {
                    collection.cancelAndJoin()
                    AgentChatRunRegistry.resetForTesting()
                    stopKoin()
                    database.close()
                }
            }
        }

    @Test
    fun delayedPersistenceAndRetryPreserveTheNextDraftWithoutSubmittingTheUserTwice() =
        runTest {
            val database =
                Room
                    .inMemoryDatabaseBuilder<AgentDatabase>()
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val history = AgentChatHistoryProvider(database) { null }
            val persistenceStarted = CompletableDeferred<Unit>()
            val allowPersistence = CompletableDeferred<Unit>()
            val requests = mutableListOf<Pair<String?, AgentRetryMode?>>()
            val states = MutableStateFlow<AgentChatPresenterController<Unit, Unit>?>(null)
            val contextReady = CompletableDeferred<Unit>()
            val context =
                flow {
                    emit(Unit)
                    contextReady.complete(Unit)
                }
            val roomFlow = history.observeRoom("retry-test")
            startKoin { modules(module { single { history } }) }
            val collection =
                backgroundScope.launch {
                    moleculeFlow(RecompositionMode.Immediate) {
                        val room by roomFlow.collectAsState(null)
                        rememberAgentChatPresenterController<Unit, Unit>(
                            key = "retry-test",
                            conversationId = "retry-test",
                            room = room ?: AgentChatRoom.empty("retry-test"),
                            contextFlow = context,
                            autoRunOnContext = false,
                            runAgent = { _, input, _, retryMode ->
                                flow<AgentConversationEvent<Unit, AgentTrace>> {
                                    requests += input to retryMode
                                    if (requests.size == 1) {
                                        throw AgentRetryableException(IllegalStateException("Provider failure"), AgentRetryMode.Resume)
                                    }
                                }
                            },
                            onUserMessageSubmitted = { text ->
                                persistenceStarted.complete(Unit)
                                allowPersistence.await()
                                history.storeUserUiMessage("retry-test", text)
                            },
                            onRoomRuntimeStateChanged = {
                                history.updateRoomState(
                                    "retry-test",
                                    isRunning = it,
                                    updateErrorMessage = false,
                                )
                            },
                            onRoomStateChanged = { history.updateRoomState("retry-test", errorMessage = it) },
                            missingContextError = { error("Context missing") },
                        )
                    }.collect { states.value = it }
                }
            try {
                contextReady.await()
                states.filterNotNull().first().setInput("Original question")
                Snapshot.sendApplyNotifications()
                states.filterNotNull().first { it.canSend }.sendMessage()
                persistenceStarted.await()
                Snapshot.sendApplyNotifications()
                states.filterNotNull().first { it.input.isEmpty() }.setInput("My next draft")
                Snapshot.sendApplyNotifications()
                allowPersistence.complete(Unit)
                val failed = states.filterNotNull().first { it.room.error?.canRetry == true && !it.room.isRunning }
                assertEquals("My next draft", failed.input)
                failed.retry()
                failed.retry()
                Snapshot.sendApplyNotifications()
                val retried = states.filterNotNull().first { requests.size == 2 && !it.room.isRunning && it.room.error == null }
                assertEquals("My next draft", retried.input)
                assertEquals<List<Pair<String?, AgentRetryMode?>>>(
                    listOf("Original question" to null, "Original question" to AgentRetryMode.Resume),
                    requests,
                )
                assertEquals(1, history.observeMessages("retry-test").first().count { it.isUser })
                assertTrue(retried.canSend)
            } finally {
                collection.cancelAndJoin()
                AgentChatRunRegistry.resetForTesting()
                stopKoin()
                database.close()
            }
        }
}
