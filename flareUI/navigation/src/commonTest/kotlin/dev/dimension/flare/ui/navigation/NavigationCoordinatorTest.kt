@file:OptIn(
    ExperimentalFlareNavigation::class,
    dev.dimension.flare.ui.LowLevelFlareApi::class,
)

package dev.dimension.flare.ui.navigation

import androidx.navigation3.runtime.NavEntry
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

public class NavigationCoordinatorTest {
    @Test
    public fun rebindsRetainedEntriesWithoutEmittingAPlatformCommand() {
        val commands = mutableListOf<NavigationCommand>()
        val retainedSnapshots = mutableListOf<List<ResolvedNavigationEntry>>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onRetainedEntriesChanged = { retainedSnapshots += it },
            )
        val initialEntry = entry("home")
        coordinator.setModel(model(initialEntry))
        completeLatest(coordinator, commands)
        val commandCount = commands.size

        val updatedEntry = entry("home")
        coordinator.setModel(model(updatedEntry))

        assertEquals(commandCount, commands.size)
        assertSame(updatedEntry, retainedSnapshots.last().single().entry)
        assertSame(updatedEntry, coordinator.projectedEntries.single().entry)
    }

    @Test
    public fun rebindsAnEnteringEntryWhileItsCommandIsInFlight() {
        val commands = mutableListOf<NavigationCommand>()
        val retainedSnapshots = mutableListOf<List<ResolvedNavigationEntry>>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onRetainedEntriesChanged = { retainedSnapshots += it },
            )
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail"))

        val updatedHome = entry("home")
        val updatedDetail = entry("detail")
        coordinator.setModel(model(updatedHome, updatedDetail))

        assertEquals(listOf("home", "detail"), retainedSnapshots.last().contentKeys())
        assertSame(updatedHome, retainedSnapshots.last()[0].entry)
        assertSame(updatedDetail, retainedSnapshots.last()[1].entry)
    }

    @Test
    public fun disposeReleasesPreparedEntriesAndIgnoresTheirCompletion() {
        val commands = mutableListOf<NavigationCommand>()
        val retainedSnapshots = mutableListOf<List<ResolvedNavigationEntry>>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onRetainedEntriesChanged = { retainedSnapshots += it },
            )
        coordinator.setModel(model("home"))
        val initial = commands.single()
        assertEquals(listOf("home"), retainedSnapshots.last().contentKeys())

        coordinator.dispose()

        assertEquals(emptyList(), retainedSnapshots.last())
        assertEquals(NavigationCoordinatorState.Disposed, coordinator.state)
        assertFalse(coordinator.completeCommand(initial.token, NavigationOperationResult.Succeeded()))
    }

    @Test
    public fun committedUserBackUsesTheLatestModelCallback() {
        val commands = mutableListOf<NavigationCommand>()
        var staleCalls = 0
        var latestCalls = 0
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail", onBack = { staleCalls += it }))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail", onBack = { latestCalls += it }))

        val interaction = beginBack(coordinator)
        assertNotNull(coordinator.commitUserBack(interaction))

        assertEquals(0, staleCalls)
        assertEquals(1, latestCalls)
    }

    @Test
    public fun synchronousBackAcknowledgementReturnsNoPendingHandle() {
        val commands = mutableListOf<NavigationCommand>()
        lateinit var coordinator: NavigationCoordinator
        coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(
            model(
                "home",
                "detail",
                onBack = { coordinator.setModel(model("home")) },
            ),
        )
        completeLatest(coordinator, commands)
        val commandCount = commands.size

        assertNull(coordinator.commitUserBack(beginBack(coordinator)))

        assertFalse(coordinator.hasPendingAcknowledgement)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertEquals(commandCount, commands.size)
    }

    @Test
    public fun synchronousBackMismatchReconstructsWithoutQuarantiningFutureBack() {
        val commands = mutableListOf<NavigationCommand>()
        lateinit var coordinator: NavigationCoordinator
        coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(
            model(
                "home",
                "detail",
                onBack = { coordinator.setModel(model("home", "replacement")) },
            ),
        )
        completeLatest(coordinator, commands)

        assertNull(coordinator.commitUserBack(beginBack(coordinator)))

        assertFalse(coordinator.hasPendingAcknowledgement)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        completeLatest(coordinator, commands)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertTrue(coordinator.canBeginUserBack())
    }

    @Test
    public fun serializesCommandsAndReconcilesToTheLatestModel() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)

        coordinator.setModel(model("home", "detail"))
        assertIs<NavigationOperation.PushPage>(commands.last().operation)

        coordinator.setModel(model("home", "replacement"))
        assertEquals(2, commands.size)
        completeLatest(coordinator, commands)

        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "replacement"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
        completeLatest(coordinator, commands)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
    }

    @Test
    public fun failedOperationAutomaticallyReconstructsLatestDeclaration() {
        val commands = mutableListOf<NavigationCommand>()
        val failures = mutableListOf<Throwable>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onOperationFailed = { _, error -> failures += error },
            )
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)

        coordinator.setModel(model("home", "detail"))
        val failedCommand = commands.last()
        coordinator.setModel(model("home", "replacement"))
        coordinator.completeCommand(
            failedCommand.token,
            NavigationOperationResult.Failed(IllegalStateException("state saved")),
        )

        assertEquals(NavigationCoordinatorState.Executing, coordinator.state)
        assertEquals(1, failures.size)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "replacement"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
        completeLatest(coordinator, commands)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
    }

    @Test
    public fun failedOperationCallbackCannotStartBackAgainstADirtyProjection() {
        val commands = mutableListOf<NavigationCommand>()
        var reentrantInteraction: NavigationInteractionHandle? = null
        lateinit var coordinator: NavigationCoordinator
        coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onOperationFailed = { _, _ ->
                    reentrantInteraction = coordinator.beginUserBack()
                },
            )
        coordinator.setModel(model("home", "detail"))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail", "editor"))
        val push = commands.last()
        coordinator.setModel(model("home", "detail"))

        coordinator.completeCommand(
            push.token,
            NavigationOperationResult.Failed(IllegalStateException("native projection is unknown")),
        )

        assertNull(reentrantInteraction)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
    }

    @Test
    public fun failedRecoveryPausesUntilThePlatformCanRetryReconstruction() {
        val commands = mutableListOf<NavigationCommand>()
        val failures = mutableListOf<Throwable>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onOperationFailed = { _, error -> failures += error },
            )
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail"))

        val push = commands.last()
        assertTrue(
            coordinator.completeCommand(
                push.token,
                NavigationOperationResult.Failed(IllegalStateException("push failed")),
            ),
        )
        val recovery = commands.last()
        assertIs<NavigationOperation.Reconstruct>(recovery.operation)
        assertTrue(
            coordinator.completeCommand(
                recovery.token,
                NavigationOperationResult.Failed(IllegalStateException("recovery failed")),
            ),
        )

        assertEquals(NavigationCoordinatorState.Paused, coordinator.state)
        assertEquals(listOf("push failed", "recovery failed"), failures.map(Throwable::message))
        assertTrue(coordinator.resumeOperations())
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
    }

    @Test
    public fun failedInitialReconstructionGetsOneAutomaticRetry() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home"))

        val initial = commands.single()
        assertIs<NavigationOperation.Reconstruct>(initial.operation)
        assertTrue(
            coordinator.completeCommand(
                initial.token,
                NavigationOperationResult.Failed(IllegalStateException("initial install failed")),
            ),
        )

        assertEquals(NavigationCoordinatorState.Executing, coordinator.state)
        assertEquals(2, commands.size)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)

        assertTrue(
            coordinator.completeCommand(
                commands.last().token,
                NavigationOperationResult.Failed(IllegalStateException("retry failed")),
            ),
        )
        assertEquals(NavigationCoordinatorState.Paused, coordinator.state)
        assertEquals(2, commands.size)
    }

    @Test
    public fun cancelledUserBackDoesNotCallTheModel() {
        val commands = mutableListOf<NavigationCommand>()
        var backCalls = 0
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail", onBack = { backCalls += it }))
        completeLatest(coordinator, commands)

        val interaction = beginBack(coordinator)
        assertTrue(coordinator.cancelUserBack(interaction))

        assertEquals(0, backCalls)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertEquals(listOf("home", "detail"), coordinator.projectedEntries.contentKeys())
    }

    @Test
    public fun failedUserBackReconstructsTheAuthoritativeDeclaration() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail"))
        completeLatest(coordinator, commands)

        val interaction = beginBack(coordinator)
        assertTrue(coordinator.failUserBack(interaction))

        assertEquals(NavigationCoordinatorState.Executing, coordinator.state)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "detail"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
        assertFalse(coordinator.failUserBack(interaction))

        completeLatest(coordinator, commands)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
    }

    @Test
    public fun failedUserBackRecoveryPausesAfterOneReconstructionAttempt() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail"))
        completeLatest(coordinator, commands)

        val interaction = beginBack(coordinator)
        assertTrue(coordinator.failUserBack(interaction))
        val recovery = commands.last()
        val commandCount = commands.size
        assertIs<NavigationOperation.Reconstruct>(recovery.operation)

        assertTrue(
            coordinator.completeCommand(
                recovery.token,
                NavigationOperationResult.Failed(IllegalStateException("native recovery failed")),
            ),
        )

        assertEquals(NavigationCoordinatorState.Paused, coordinator.state)
        assertEquals(commandCount, commands.size)
        assertFalse(coordinator.failUserBack(interaction))
    }

    @Test
    public fun committedUserBackWaitsThroughUnchangedModelsAndAcceptsTheTarget() {
        val commands = mutableListOf<NavigationCommand>()
        val popCounts = mutableListOf<Int>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail", onBack = popCounts::add))
        completeLatest(coordinator, commands)
        val commandCount = commands.size

        val interaction = beginBack(coordinator)
        val acknowledgement = assertNotNull(coordinator.commitUserBack(interaction))
        assertEquals(listOf(1), popCounts)
        assertEquals(NavigationCoordinatorState.AwaitingAcknowledgement, coordinator.state)
        assertTrue(coordinator.hasPendingAcknowledgement)
        assertEquals(listOf("home"), coordinator.projectedEntries.contentKeys())

        coordinator.setModel(model("home", "detail", onBack = popCounts::add))

        assertEquals(NavigationCoordinatorState.AwaitingAcknowledgement, coordinator.state)
        assertTrue(coordinator.hasPendingAcknowledgement)
        assertEquals(commandCount, commands.size)

        coordinator.setModel(model("home", onBack = popCounts::add))

        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertFalse(coordinator.hasPendingAcknowledgement)
        assertEquals(commandCount, commands.size)
        assertFalse(coordinator.acknowledgementDeadlineReached(acknowledgement))
    }

    @Test
    public fun timedOutBackRestoresThenAllowsLateTopologyAsANewModelChange() {
        val commands = mutableListOf<NavigationCommand>()
        val mismatches = mutableListOf<NavigationBackMismatch>()
        lateinit var request: NavigationBackRequest<NavigationEntryIdentity>
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onBackMismatch = mismatches::add,
            )
        coordinator.setModel(
            modelWithBackRequest("home", "detail", onBack = { request = it }),
        )
        completeLatest(coordinator, commands)

        val interaction = beginBack(coordinator)
        val acknowledgement = assertNotNull(coordinator.commitUserBack(interaction))
        assertEquals(acknowledgement.token, request.requestId)
        assertEquals(listOf("home", "detail"), request.base.map { it.contentKey })
        assertEquals(listOf("home"), request.target.map { it.contentKey })
        assertTrue(request.isActive)
        assertTrue(coordinator.acknowledgementDeadlineReached(acknowledgement))
        assertFalse(coordinator.hasPendingAcknowledgement)
        assertFalse(request.isActive)
        assertFalse(request.accept())
        assertFalse(request.reject())
        val staleStack = request.base.toMutableList()
        assertFalse(request.applyTo(staleStack))
        assertEquals(request.base, staleStack)

        assertEquals(1, mismatches.size)
        assertEquals(NavigationBackMismatchReason.DeadlineReached, mismatches.single().reason)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "detail"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )

        completeLatest(coordinator, commands)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertTrue(coordinator.canBeginUserBack())
        coordinator.setModel(model("home", "detail"))
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertTrue(coordinator.canBeginUserBack())

        // A late reducer result is ordinary programmatic navigation after the request's deadline;
        // it cannot revive or resolve the stale token.
        coordinator.setModel(model("home"))
        assertIs<NavigationOperation.PopPage>(commands.last().operation)
        completeUntilIdle(coordinator, commands)
        assertFalse(request.isActive)
    }

    @Test
    public fun mismatchingModelSupersedesRequestAndRestoresBackAvailability() {
        val commands = mutableListOf<NavigationCommand>()
        lateinit var request: NavigationBackRequest<NavigationEntryIdentity>
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(
            modelWithBackRequest("home", "detail", onBack = { request = it }),
        )
        completeLatest(coordinator, commands)

        val interaction = beginBack(coordinator)
        assertNotNull(coordinator.commitUserBack(interaction))
        coordinator.setModel(model("home", "replacement"))
        assertFalse(coordinator.hasPendingAcknowledgement)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        completeLatest(coordinator, commands)

        assertFalse(request.isActive)
        assertFalse(request.accept())
        assertFalse(request.reject())
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertTrue(coordinator.canBeginUserBack())

        coordinator.setModel(model("home"))
        assertIs<NavigationOperation.PopPage>(commands.last().operation)
        completeLatest(coordinator, commands)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
    }

    @Test
    public fun explicitBackRejectionRestoresAndDoesNotQuarantineFutureBack() {
        val commands = mutableListOf<NavigationCommand>()
        val mismatches = mutableListOf<NavigationBackMismatch>()
        lateinit var request: NavigationBackRequest<NavigationEntryIdentity>
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onBackMismatch = mismatches::add,
            )
        coordinator.setModel(
            modelWithBackRequest("home", "detail", onBack = { request = it }),
        )
        completeLatest(coordinator, commands)

        assertNotNull(coordinator.commitUserBack(beginBack(coordinator)))
        assertTrue(request.reject())
        assertFalse(request.isActive)
        assertFalse(request.reject())
        assertEquals(NavigationBackMismatchReason.ExplicitlyRejected, mismatches.single().reason)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)

        completeLatest(coordinator, commands)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertTrue(coordinator.canBeginUserBack())
    }

    @Test
    public fun acceptedBackRequestIsClaimedAndCannotBeRejected() {
        val commands = mutableListOf<NavigationCommand>()
        lateinit var request: NavigationBackRequest<NavigationEntryIdentity>
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(
            modelWithBackRequest("home", "detail", onBack = { request = it }),
        )
        completeLatest(coordinator, commands)

        assertNotNull(coordinator.commitUserBack(beginBack(coordinator)))
        assertTrue(request.isActive)
        assertTrue(request.accept())
        assertFalse(request.isActive)
        assertFalse(request.accept())
        assertFalse(request.reject())
        assertTrue(coordinator.hasPendingAcknowledgement)

        coordinator.setModel(model("home"))
        assertFalse(coordinator.hasPendingAcknowledgement)
    }

    @Test
    public fun mappedBackRequestAppliesAtMostOnceAgainstItsExactBase() {
        val commands = mutableListOf<NavigationCommand>()
        lateinit var topologyRequest: NavigationBackRequest<NavigationEntryIdentity>
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(
            modelWithBackRequest("home", "detail", onBack = { topologyRequest = it }),
        )
        completeLatest(coordinator, commands)
        assertNotNull(coordinator.commitUserBack(beginBack(coordinator)))

        val request =
            topologyRequest.mapValues(
                base = listOf("home", "detail"),
                target = listOf("home"),
            )
        val routes = mutableListOf("home", "detail")
        assertEquals(topologyRequest.requestId, request.requestId)
        assertEquals(topologyRequest.baseRevision, request.baseRevision)
        assertTrue(request.applyTo(routes))
        assertEquals(listOf("home"), routes)
        assertFalse(request.applyTo(mutableListOf("home", "detail")))
        assertFalse(request.isActive)

        coordinator.setModel(model("home"))
        assertFalse(request.isActive)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
    }

    @Test
    public fun failedBackStackMutationAbortsTheClaimAndRestoresTheNativeProjection() {
        val commands = mutableListOf<NavigationCommand>()
        val mismatches = mutableListOf<NavigationBackMismatch>()
        lateinit var topologyRequest: NavigationBackRequest<NavigationEntryIdentity>
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onBackMismatch = mismatches::add,
            )
        coordinator.setModel(
            modelWithBackRequest("home", "detail", onBack = { topologyRequest = it }),
        )
        completeLatest(coordinator, commands)
        assertNotNull(coordinator.commitUserBack(beginBack(coordinator)))
        val request =
            topologyRequest.mapValues(
                base = listOf("home", "detail"),
                target = listOf("home"),
            )
        val routes = RemovalFailingMutableList("home", "detail")

        val failure =
            assertFailsWith<IllegalStateException> {
                request.applyTo(routes)
            }

        assertEquals("Back-stack removal failed.", failure.message)
        assertEquals(listOf("home", "detail"), routes.toList())
        assertFalse(request.isActive)
        assertFalse(request.accept())
        assertFalse(request.reject())
        assertFalse(coordinator.hasPendingAcknowledgement)
        assertEquals(NavigationBackMismatchReason.ExplicitlyRejected, mismatches.single().reason)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "detail"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
    }

    @Test
    public fun backRequestNeverMutatesAMismatchingBase() {
        val commands = mutableListOf<NavigationCommand>()
        lateinit var request: NavigationBackRequest<NavigationEntryIdentity>
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(
            modelWithBackRequest("home", "detail", onBack = { request = it }),
        )
        completeLatest(coordinator, commands)
        assertNotNull(coordinator.commitUserBack(beginBack(coordinator)))

        val routes = mutableListOf("home", "replacement")
        assertFalse(
            request.applyTo(routes) { route ->
                NavigationEntryIdentity(route, NavigationPresentation.Page)
            },
        )
        assertEquals(listOf("home", "replacement"), routes)
        assertTrue(request.reject())
    }

    @Test
    public fun concurrentModelChangeDuringGestureNeverCallsBackAgainstTheOldBase() {
        val commands = mutableListOf<NavigationCommand>()
        var backCalls = 0
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail", onBack = { backCalls += it }))
        completeLatest(coordinator, commands)

        val interaction = beginBack(coordinator)
        coordinator.setModel(model("home", "replacement", onBack = { backCalls += it }))
        assertNull(coordinator.commitUserBack(interaction))

        assertEquals(0, backCalls)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "replacement"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
    }

    @Test
    public fun matchingConcurrentModelChangeAcknowledgesGestureWithoutCallingBack() {
        val commands = mutableListOf<NavigationCommand>()
        var backCalls = 0
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail", onBack = { backCalls += it }))
        completeLatest(coordinator, commands)
        val commandCount = commands.size

        val interaction = beginBack(coordinator)
        coordinator.setModel(model("home", onBack = { backCalls += it }))
        assertNull(coordinator.commitUserBack(interaction))

        assertEquals(0, backCalls)
        assertEquals(commandCount, commands.size)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
    }

    @Test
    public fun staleGestureCallbacksCannotAffectTheCurrentInteraction() {
        val commands = mutableListOf<NavigationCommand>()
        var backCalls = 0
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail", onBack = { backCalls += it }))
        completeLatest(coordinator, commands)

        val stale = beginBack(coordinator)
        assertTrue(coordinator.cancelUserBack(stale))
        val current = beginBack(coordinator)

        assertNull(coordinator.commitUserBack(stale))
        assertFalse(coordinator.cancelUserBack(stale))
        assertEquals(NavigationCoordinatorState.Interacting, coordinator.state)
        assertNotNull(coordinator.commitUserBack(current))
        assertEquals(1, backCalls)
    }

    @Test
    public fun staleAcknowledgementDeadlineCannotExpireTheCurrentBack() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail", "editor"))
        completeLatest(coordinator, commands)

        val firstInteraction = beginBack(coordinator)
        val staleAcknowledgement = assertNotNull(coordinator.commitUserBack(firstInteraction))
        coordinator.setModel(model("home", "detail"))

        val secondInteraction = beginBack(coordinator)
        val currentAcknowledgement = assertNotNull(coordinator.commitUserBack(secondInteraction))

        assertFalse(coordinator.acknowledgementDeadlineReached(staleAcknowledgement))
        assertEquals(NavigationCoordinatorState.AwaitingAcknowledgement, coordinator.state)
        assertTrue(coordinator.acknowledgementDeadlineReached(currentAcknowledgement))
    }

    @Test
    public fun canBeginBackHasNoStateSideEffects() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "detail"))
        completeLatest(coordinator, commands)

        assertTrue(coordinator.canBeginUserBack())
        assertTrue(coordinator.canBeginUserBack())
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
        assertFalse(coordinator.canBeginUserBack(popCount = 2))
    }

    @Test
    public fun deferredOperationReplansLatestModelWithoutDirtyReconstruction() {
        val commands = mutableListOf<NavigationCommand>()
        val deferred = mutableListOf<NavigationCommand>()
        val retained = mutableListOf<List<ResolvedNavigationEntry>>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onRetainedEntriesChanged = { retained += it },
                onOperationDeferred = deferred::add,
            )
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail"))
        val original = commands.last()

        assertTrue(coordinator.completeCommand(original.token, NavigationOperationResult.Deferred))
        assertEquals(NavigationCoordinatorState.Paused, coordinator.state)
        assertEquals(listOf("home"), retained.last().contentKeys())
        coordinator.setModel(model("home", "replacement"))
        assertTrue(coordinator.resumeOperations())

        assertEquals(listOf(original), deferred)
        assertIs<NavigationOperation.PushPage>(commands.last().operation)
        assertEquals(listOf("home"), commands.last().sourceStack.contentKeys())
        assertEquals(
            listOf("home", "replacement"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
    }

    @Test
    public fun observedProjectionMismatchForcesReconstruction() {
        val commands = mutableListOf<NavigationCommand>()
        val mismatches = mutableListOf<NavigationProjectionMismatch>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onProjectionMismatch = mismatches::add,
            )
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail"))
        val push = commands.last()

        coordinator.completeCommand(
            push.token,
            NavigationOperationResult.Succeeded(observedTopology = push.sourceStack.topology()),
        )

        assertEquals(1, mismatches.size)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "detail"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
    }

    @Test
    public fun persistentSynchronousReconstructionMismatchPausesAfterABoundedRetry() {
        val commands = mutableListOf<NavigationCommand>()
        val mismatches = mutableListOf<NavigationProjectionMismatch>()
        lateinit var coordinator: NavigationCoordinator
        coordinator =
            NavigationCoordinator(
                emitCommand = { command ->
                    commands += command
                    coordinator.completeCommand(
                        command.token,
                        NavigationOperationResult.Succeeded(
                            observedTopology = command.sourceStack.topology(),
                        ),
                    )
                },
                onProjectionMismatch = mismatches::add,
            )

        coordinator.setModel(model("home"))

        assertEquals(2, commands.size)
        assertEquals(2, mismatches.size)
        assertEquals(NavigationCoordinatorState.Paused, coordinator.state)
    }

    @Test
    public fun unresolvableObservedProjectionRetainsEntriesUntilReconstructionSucceeds() {
        val commands = mutableListOf<NavigationCommand>()
        val retained = mutableListOf<List<ResolvedNavigationEntry>>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onRetainedEntriesChanged = { retained += it },
            )
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail"))
        val stalePush = commands.last()
        coordinator.setModel(model("home", "replacement"))
        val snapshotCountBeforeCompletion = retained.size

        coordinator.completeCommand(
            stalePush.token,
            NavigationOperationResult.Succeeded(
                observedTopology =
                    listOf(
                        NavigationEntryIdentity(
                            contentKey = "unknown-native-entry",
                            presentation = NavigationPresentation.Page,
                        ),
                    ),
            ),
        )

        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertTrue(
            retained
                .drop(snapshotCountBeforeCompletion)
                .all { "detail" in it.contentKeys() },
        )
        assertEquals(
            listOf("home", "detail", "replacement"),
            retained.last().contentKeys(),
        )

        completeLatest(coordinator, commands)
        assertEquals(listOf("home", "replacement"), retained.last().contentKeys())
    }

    @Test
    public fun resolvableReconstructionMismatchReplacesPreviousUncertainty() {
        val commands = mutableListOf<NavigationCommand>()
        val retained = mutableListOf<List<ResolvedNavigationEntry>>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onRetainedEntriesChanged = { retained += it },
            )
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail"))
        val failedPush = commands.last()
        coordinator.completeCommand(
            failedPush.token,
            NavigationOperationResult.Failed(IllegalStateException("projection unknown")),
        )
        coordinator.setModel(model("home", "replacement"))
        val reconstruction = commands.last()
        assertIs<NavigationOperation.Reconstruct>(reconstruction.operation)
        val snapshotCountBeforeCompletion = retained.size
        val commandCountBeforeCompletion = commands.size

        coordinator.completeCommand(
            reconstruction.token,
            NavigationOperationResult.Succeeded(
                observedTopology = reconstruction.sourceStack.topology(),
            ),
        )

        assertTrue(
            retained
                .drop(snapshotCountBeforeCompletion)
                .all { "detail" !in it.contentKeys() },
        )
        assertEquals(listOf("home"), retained.last().contentKeys())
        assertEquals(commandCountBeforeCompletion, commands.size)
        assertEquals(NavigationCoordinatorState.Paused, coordinator.state)

        assertTrue(coordinator.resumeOperations())
        assertEquals(listOf("home", "replacement"), retained.last().contentKeys())
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "replacement"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
        completeLatest(coordinator, commands)
        assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
    }

    @Test
    public fun batchesDeepPageAndOverlaySuffixChangesIntoSingleReconstruction() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)

        val forwardStart = commands.size
        coordinator.setModel(
            model(
                entry("home"),
                entry("detail"),
                entry("sheet", NavigationPresentation.Sheet),
                entry("dialog", NavigationPresentation.Dialog),
            ),
        )
        completeUntilIdle(coordinator, commands)
        assertEquals(
            listOf("reconstruct"),
            commands.drop(forwardStart).map { it.operation.kind() },
        )

        val backwardStart = commands.size
        coordinator.setModel(model("home"))
        completeUntilIdle(coordinator, commands)
        assertEquals(
            listOf("reconstruct"),
            commands.drop(backwardStart).map { it.operation.kind() },
        )
        assertEquals(listOf("home"), coordinator.projectedEntries.contentKeys())
    }

    @Test
    public fun failedSecondStepRetainsUncertainEntriesUntilReconstructionSucceeds() {
        val commands = mutableListOf<NavigationCommand>()
        val retained = mutableListOf<List<ResolvedNavigationEntry>>()
        val coordinator =
            NavigationCoordinator(
                emitCommand = commands::add,
                onRetainedEntriesChanged = { retained += it },
            )
        coordinator.setModel(model("home"))
        completeLatest(coordinator, commands)
        coordinator.setModel(model("home", "detail"))
        completeLatest(coordinator, commands)
        coordinator.setModel(
            model(
                entry("home"),
                entry("detail"),
                entry("sheet", NavigationPresentation.Sheet),
            ),
        )
        val present = commands.last()
        assertIs<NavigationOperation.PresentOverlay>(present.operation)

        coordinator.completeCommand(
            present.token,
            NavigationOperationResult.Failed(IllegalStateException("presentation interrupted")),
        )
        assertEquals(listOf("home", "detail", "sheet"), retained.last().contentKeys())
        coordinator.setModel(model("home", "replacement"))

        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "detail", "sheet"),
            retained.last().contentKeys(),
        )
        assertEquals(
            listOf("home", "detail", "sheet"),
            commands
                .last()
                .operation.targetStack
                .contentKeys(),
        )
        completeLatest(coordinator, commands)
        assertIs<NavigationOperation.Reconstruct>(commands.last().operation)
        assertEquals(
            listOf("home", "detail", "sheet", "replacement"),
            retained.last().contentKeys(),
        )
        completeLatest(coordinator, commands)
        assertEquals(listOf("home", "replacement"), retained.last().contentKeys())
    }

    @Test
    public fun presentationMutationFailsBeforeAPlatformCommandIsEmitted() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "editor"))
        completeLatest(coordinator, commands)
        val commandCount = commands.size

        assertFailsWith<IllegalArgumentException> {
            coordinator.setModel(
                model(
                    entry("home"),
                    entry("editor", NavigationPresentation.Sheet),
                ),
            )
        }
        assertEquals(commandCount, commands.size)
    }

    @Test
    public fun presentationCannotChangeUntilThePreviousIncarnationIsReleased() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home", "editor"))
        completeLatest(coordinator, commands)

        coordinator.setModel(model("home"))
        val pop = commands.last()
        assertIs<NavigationOperation.PopPage>(pop.operation)

        assertFailsWith<IllegalArgumentException> {
            coordinator.setModel(
                model(
                    entry("home"),
                    entry("editor", NavigationPresentation.Sheet),
                ),
            )
        }

        assertTrue(coordinator.completeCommand(pop.token, NavigationOperationResult.Succeeded()))
        coordinator.setModel(
            model(
                entry("home"),
                entry("editor", NavigationPresentation.Sheet),
            ),
        )
        assertIs<NavigationOperation.PresentOverlay>(commands.last().operation)
    }

    @Test
    public fun duplicateOrStalePlatformCompletionsAreIgnored() {
        val commands = mutableListOf<NavigationCommand>()
        val coordinator = NavigationCoordinator(emitCommand = commands::add)
        coordinator.setModel(model("home"))
        val initial = commands.single()

        assertTrue(coordinator.completeCommand(initial.token, NavigationOperationResult.Succeeded()))
        assertFalse(coordinator.completeCommand(initial.token, NavigationOperationResult.Succeeded()))
        assertFalse(coordinator.completeCommand(initial.token + 100, NavigationOperationResult.Succeeded()))
    }
}

private fun model(
    vararg keys: String,
    onBack: (Int) -> Unit = {},
): NavigationModel = model(keys.map(::entry), onBack)

private fun model(
    vararg entries: NavEntry<String>,
    onBack: (Int) -> Unit = {},
): NavigationModel = model(entries.toList(), onBack)

private fun model(
    entries: List<NavEntry<String>>,
    onBack: (Int) -> Unit,
): NavigationModel =
    NavigationModel(
        entries = resolveNavigationEntries(entries),
        onBack = { request -> onBack(request.popCount) },
        subcompositions = UnusedSubcompositionFactory,
    )

private fun modelWithBackRequest(
    vararg keys: String,
    onBack: (NavigationBackRequest<NavigationEntryIdentity>) -> Unit,
): NavigationModel =
    NavigationModel(
        entries = resolveNavigationEntries(keys.map(::entry)),
        onBack = onBack,
        subcompositions = UnusedSubcompositionFactory,
    )

private fun entry(
    key: String,
    presentation: NavigationPresentation = NavigationPresentation.Page,
): NavEntry<String> =
    NavEntry(
        key = key,
        contentKey = key,
        metadata =
            if (presentation == NavigationPresentation.Page) {
                emptyMap()
            } else {
                mapOf(NavigationPresentationMetadata.toString() to presentation)
            },
    ) {}

private fun completeLatest(
    coordinator: NavigationCoordinator,
    commands: List<NavigationCommand>,
) {
    val command = commands.last()
    assertTrue(coordinator.completeCommand(command.token, NavigationOperationResult.Succeeded()))
}

private fun beginBack(coordinator: NavigationCoordinator): NavigationInteractionHandle = assertNotNull(coordinator.beginUserBack())

private fun completeUntilIdle(
    coordinator: NavigationCoordinator,
    commands: List<NavigationCommand>,
) {
    while (coordinator.state == NavigationCoordinatorState.Executing) {
        completeLatest(coordinator, commands)
    }
    assertEquals(NavigationCoordinatorState.Idle, coordinator.state)
}

private fun NavigationOperation.kind(): String =
    when (this) {
        is NavigationOperation.PushPage -> "push"
        is NavigationOperation.PopPage -> "pop"
        is NavigationOperation.PresentOverlay -> "present"
        is NavigationOperation.DismissOverlay -> "dismiss"
        is NavigationOperation.Reconstruct -> "reconstruct"
    }

private fun List<ResolvedNavigationEntry>.contentKeys(): List<Any> = map(ResolvedNavigationEntry::contentKey)

private class RemovalFailingMutableList<T : Any>(
    vararg values: T,
) : AbstractMutableList<T>() {
    private val delegate = values.toMutableList()

    override val size: Int
        get() = delegate.size

    override fun get(index: Int): T = delegate[index]

    override fun set(
        index: Int,
        element: T,
    ): T = delegate.set(index, element)

    override fun add(
        index: Int,
        element: T,
    ) {
        delegate.add(index, element)
    }

    override fun removeAt(index: Int): T = error("Back-stack removal failed.")
}

private object UnusedSubcompositionFactory : FlareSubcompositionFactory {
    override fun create(root: FlareChildren): FlareSubcomposition = error("Coordinator tests never realize entry content.")
}
