package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentConversationEvent
import dev.dimension.flare.feature.agent.common.AgentRetryMode
import dev.dimension.flare.feature.agent.common.AgentRetryableException
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.database.AgentDatabase
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
                backgroundScope.launch(Dispatchers.Default) {
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
