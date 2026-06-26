package dev.dimension.flare.feature.agent.presenter

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AgentChatRunRegistryTest {
    @AfterTest
    fun tearDown() {
        AgentChatRunRegistry.resetForTesting()
    }

    @Test
    fun idleRuntimeIsReleasedWhenLastPresenterReleases() {
        val conversationId = "agent-runtime:idle"
        val runtime = AgentChatRunRegistry.retainRuntime(conversationId)

        assertTrue(AgentChatRunRegistry.hasRuntime(conversationId))

        AgentChatRunRegistry.releaseRuntime(conversationId, runtime)

        assertFalse(AgentChatRunRegistry.hasRuntime(conversationId))
        assertEquals(0, AgentChatRunRegistry.activeRuntimeCount())
    }

    @Test
    fun runtimeTaskKeepsRuntimeUntilTaskCompletes() =
        runTest {
            val conversationId = "agent-runtime:task"
            val runtime = AgentChatRunRegistry.retainRuntime(conversationId)
            val taskCanFinish = CompletableDeferred<Unit>()
            val task =
                AgentChatRunRegistry.launchRuntimeTask(conversationId, runtime) {
                    taskCanFinish.await()
                }

            AgentChatRunRegistry.releaseRuntime(conversationId, runtime)

            assertTrue(AgentChatRunRegistry.hasRuntime(conversationId))

            taskCanFinish.complete(Unit)
            task.join()

            assertFalse(AgentChatRunRegistry.hasRuntime(conversationId))
        }

    @Test
    fun runKeepsRuntimeUntilRunCompletes() =
        runTest {
            val conversationId = "agent-runtime:run"
            val runtime = AgentChatRunRegistry.retainRuntime(conversationId)
            val runCanFinish = CompletableDeferred<Unit>()
            val runJob =
                AgentChatRunRegistry.launchRun(conversationId, runtime) {
                    runCanFinish.await()
                }

            AgentChatRunRegistry.releaseRuntime(conversationId, runtime)

            assertTrue(AgentChatRunRegistry.hasRuntime(conversationId))

            runCanFinish.complete(Unit)
            runJob.join()

            assertFalse(AgentChatRunRegistry.hasRuntime(conversationId))
        }

    @Test
    fun titleGenerationKeepsRuntimeUntilTitleJobCompletes() =
        runTest {
            val conversationId = "agent-runtime:title"
            val runtime = AgentChatRunRegistry.retainRuntime(conversationId)
            val titleCanFinish = CompletableDeferred<Unit>()
            val titleJob =
                AgentChatRunRegistry.launchTitleGeneration(conversationId, runtime) {
                    titleCanFinish.await()
                }

            AgentChatRunRegistry.releaseRuntime(conversationId, runtime)

            assertTrue(AgentChatRunRegistry.hasRuntime(conversationId))

            titleCanFinish.complete(Unit)
            titleJob.join()

            assertFalse(AgentChatRunRegistry.hasRuntime(conversationId))
        }
}
