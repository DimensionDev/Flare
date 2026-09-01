@file:OptIn(dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class)

package dev.dimension.flare.ui.navigation

internal enum class NavigationCoordinatorState {
    Idle,
    Executing,
    Interacting,
    AwaitingAcknowledgement,
    Paused,
    Disposed,
}

/** Time-based grace period for an asynchronous reducer to acknowledge a committed native back. */
internal const val NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_MILLIS: Long = 5_000L

/** Platform-independent ordering and acknowledgement state for one NavigationDisplay. */
internal class NavigationCoordinator(
    private val emitCommand: (NavigationCommand) -> Unit,
    private val onRetainedEntriesChanged: (List<ResolvedNavigationEntry>) -> Unit = {},
    private val onOperationDeferred: (NavigationCommand) -> Unit = {},
    private val onOperationFailed: (NavigationCommand, Throwable) -> Unit = { _, _ -> },
    private val onProjectionMismatch: (NavigationProjectionMismatch) -> Unit = {},
    private val onBackMismatch: (NavigationBackMismatch) -> Unit = {},
) {
    private var model: NavigationModel? = null
    private var declaredStack: List<ResolvedNavigationEntry> = emptyList()
    private var projectedStack: List<ResolvedNavigationEntry> = emptyList()
    private var stackRevision: Long = 0L
    private var nextCommandToken: Long = 0L
    private var nextGestureToken: Long = 0L
    private var inFlight: NavigationCommand? = null
    private var interaction: NavigationInteraction? = null
    private var pendingAcknowledgement: PendingNavigationAcknowledgement? = null
    private var uncertainProjectionEntries: List<ResolvedNavigationEntry> = emptyList()
    private var projectionDirty: Boolean = false
    private var recoveringFailedOperation: Boolean = false
    private var operationsPaused: Boolean = false
    private var disposed: Boolean = false

    val state: NavigationCoordinatorState
        get() =
            when {
                disposed -> NavigationCoordinatorState.Disposed
                inFlight != null -> NavigationCoordinatorState.Executing
                interaction != null -> NavigationCoordinatorState.Interacting
                pendingAcknowledgement != null -> NavigationCoordinatorState.AwaitingAcknowledgement
                operationsPaused -> NavigationCoordinatorState.Paused
                else -> NavigationCoordinatorState.Idle
            }

    internal val declaredEntries: List<ResolvedNavigationEntry>
        get() = declaredStack

    internal val projectedEntries: List<ResolvedNavigationEntry>
        get() = projectedStack

    /** Whether an adapter still needs to keep its acknowledgement deadline scheduled. */
    internal val hasPendingAcknowledgement: Boolean
        get() = pendingAcknowledgement != null

    fun setModel(value: NavigationModel) {
        check(!disposed) { "NavigationCoordinator is already disposed." }
        validateStablePresentations(
            previous = retainedEntriesForPresentationValidation(),
            current = value.entries,
        )

        val topologyChanged = !declaredStack.hasSameTopologyAs(value.entries)
        model = value
        declaredStack = value.entries
        if (topologyChanged) stackRevision += 1L

        projectedStack = projectedStack.rebindCommonPrefixFrom(declaredStack)
        publishRetainedEntries()

        val acknowledgement = pendingAcknowledgement
        if (acknowledgement != null) {
            when {
                declaredStack.hasSameTopologyAs(acknowledgement.target) -> {
                    pendingAcknowledgement = null
                    projectedStack = declaredStack
                    publishRetainedEntries()
                }

                declaredStack.hasSameTopologyAs(acknowledgement.base) -> {
                    return
                }

                else -> {
                    pendingAcknowledgement = null
                    projectionDirty = true
                    onBackMismatch(
                        acknowledgement.toMismatch(
                            actual = declaredStack,
                            reason = NavigationBackMismatchReason.ModelMismatch,
                        ),
                    )
                }
            }
        }

        if (interaction != null || inFlight != null || operationsPaused) return
        reconcile()
    }

    /** Completes the current programmatic native operation; stale delegate callbacks are ignored. */
    fun completeCommand(
        token: Long,
        result: NavigationOperationResult,
    ): Boolean {
        if (disposed) return false
        val command = inFlight ?: return false
        if (command.token != token) return false
        inFlight = null

        when (result) {
            is NavigationOperationResult.Succeeded -> {
                val expectedTopology = command.operation.targetStack.topology()
                val observedTopology = result.observedTopology ?: expectedTopology
                if (observedTopology == expectedTopology) {
                    projectedStack = command.operation.targetStack.rebindCommonPrefixFrom(declaredStack)
                    if (command.operation is NavigationOperation.Reconstruct) {
                        projectionDirty = false
                        recoveringFailedOperation = false
                        uncertainProjectionEntries = emptyList()
                    }
                } else {
                    val observedProjection =
                        resolveObservedProjection(
                            observedTopology = observedTopology,
                            command = command,
                        )
                    if (observedProjection != null) {
                        projectedStack = observedProjection
                        if (command.operation is NavigationOperation.Reconstruct) {
                            uncertainProjectionEntries = emptyList()
                        }
                    } else {
                        retainUncertainProjection(command)
                    }
                    projectionDirty = true
                    // A topology mismatch means the native operation completed without proving
                    // its requested projection. Give it one authoritative reconstruction, but
                    // bound repeated synchronous mismatches just like repeated native failures.
                    // Without this guard, an adapter that consistently reports a different
                    // topology can recurse through emitCommand/completeCommand until the stack
                    // overflows on the UI thread.
                    operationsPaused = command.operation is NavigationOperation.Reconstruct && recoveringFailedOperation
                    recoveringFailedOperation = true
                    onProjectionMismatch(
                        NavigationProjectionMismatch(
                            command = command,
                            expected = expectedTopology,
                            observed = observedTopology,
                        ),
                    )
                }
                publishRetainedEntries()
                reconcile()
            }

            NavigationOperationResult.Deferred -> {
                operationsPaused = true
                publishRetainedEntries()
                onOperationDeferred(command)
            }

            is NavigationOperationResult.Failed -> {
                retainUncertainProjection(command)
                projectionDirty = true
                // A failed native operation leaves the physical projection uncertain. Recover
                // immediately with one authoritative, non-animated reconstruction instead of
                // waiting indefinitely for a platform lifecycle event that may never arrive.
                // Bound the automatic recovery at one attempt. The flag tracks the recovery
                // cycle rather than the operation type so an initial or model-driven
                // reconstruction also receives one retry. If that recovery attempt fails, pause
                // until the adapter reports that native operations are safe again.
                operationsPaused = recoveringFailedOperation
                recoveringFailedOperation = true
                publishRetainedEntries()
                try {
                    onOperationFailed(command, result.cause)
                } finally {
                    if (!operationsPaused) reconcile()
                }
            }
        }
        return true
    }

    /** Resumes reconciliation after the platform reports that native operations are safe again. */
    fun resumeOperations(): Boolean {
        if (disposed || !operationsPaused) return false
        operationsPaused = false
        reconcile()
        return true
    }

    /** Returns whether a native back interaction can begin, without changing coordinator state. */
    fun canBeginUserBack(popCount: Int = 1): Boolean {
        require(popCount > 0) { "A navigation back interaction must pop at least one entry." }
        if (state != NavigationCoordinatorState.Idle) return false
        if (projectionDirty) return false
        if (!projectedStack.hasSameTopologyAs(declaredStack)) return false
        return projectedStack.size - popCount >= 1
    }

    /** Begins a user-owned native back interaction without changing the declared model. */
    fun beginUserBack(popCount: Int = 1): NavigationInteractionHandle? {
        if (!canBeginUserBack(popCount)) return null

        val handle = NavigationInteractionHandle(nextGestureToken++)
        interaction =
            NavigationInteraction(
                handle = handle,
                revision = stackRevision,
                base = projectedStack,
                target = projectedStack.dropLast(popCount),
                popCount = popCount,
            )
        return handle
    }

    /** Reports that the active user interaction returned to its original projection. */
    fun cancelUserBack(handle: NavigationInteractionHandle): Boolean {
        if (disposed || interaction?.handle != handle) return false
        interaction = null
        reconcile()
        return true
    }

    /**
     * Reports that an adapter could not safely finish or restore an active native interaction.
     *
     * The physical projection is now uncertain, so the next operation is one authoritative
     * reconstruction. If that recovery also fails, normal failed-operation handling pauses until a
     * later platform retry opportunity.
     */
    fun failUserBack(handle: NavigationInteractionHandle): Boolean {
        if (disposed) return false
        val failedInteraction = interaction?.takeIf { it.handle == handle } ?: return false
        interaction = null
        uncertainProjectionEntries =
            mergeEntriesByIdentity(
                uncertainProjectionEntries + failedInteraction.base + failedInteraction.target,
            )
        projectionDirty = true
        recoveringFailedOperation = true
        publishRetainedEntries()
        reconcile()
        return true
    }

    /**
     * Reports a committed native interaction.
     *
     * A non-null result means the coordinator is waiting for a later exact target model or explicit
     * rejection. Re-delivering the unchanged base keeps a controlled request pending. The adapter
     * must eventually pass the returned handle to [acknowledgementDeadlineReached] if the request is
     * unresolved. A concurrent matching model update or synchronous rejection returns null.
     */
    fun commitUserBack(handle: NavigationInteractionHandle): NavigationAcknowledgementHandle? {
        if (disposed) return null
        val committedInteraction = interaction?.takeIf { it.handle == handle } ?: return null
        interaction = null
        projectedStack = committedInteraction.target.rebindCommonPrefixFrom(declaredStack)
        publishRetainedEntries()

        if (stackRevision != committedInteraction.revision) {
            if (declaredStack.hasSameTopologyAs(committedInteraction.target)) {
                projectedStack = declaredStack
                publishRetainedEntries()
            } else {
                projectionDirty = true
            }
            reconcile()
            return null
        }

        val acknowledgementHandle = NavigationAcknowledgementHandle(committedInteraction.handle.token)
        val acknowledgement =
            PendingNavigationAcknowledgement(
                handle = acknowledgementHandle,
                baseRevision = committedInteraction.revision,
                base = committedInteraction.base,
                target = committedInteraction.target,
                popCount = committedInteraction.popCount,
            )
        pendingAcknowledgement = acknowledgement
        val request =
            NavigationBackRequest(
                requestId = acknowledgementHandle.token,
                baseRevision = acknowledgement.baseRevision,
                base = acknowledgement.base.topology(),
                target = acknowledgement.target.topology(),
                popCount = acknowledgement.popCount,
                isActiveRequest = { isBackRequestActive(acknowledgementHandle) },
                acceptRequest = { acceptBackRequest(acknowledgementHandle) },
                rejectRequest = { rejectBackRequest(acknowledgementHandle) },
                abortAcceptedRequest = { abortAcceptedBackRequest(acknowledgementHandle) },
            )
        try {
            checkNotNull(model).onBack(request)
        } catch (error: Throwable) {
            pendingAcknowledgement = null
            projectionDirty = true
            reconcile()
            throw error
        }
        return pendingAcknowledgement?.handle
    }

    /**
     * Expires a pending native back and restores the latest declaration.
     *
     * The request carries its own identity and exact base/target topology, so expiry can close it
     * permanently without quarantining an unrelated future interaction.
     */
    fun acknowledgementDeadlineReached(handle: NavigationAcknowledgementHandle): Boolean {
        if (disposed) return false
        val acknowledgement = pendingAcknowledgement?.takeIf { it.handle == handle } ?: return false
        pendingAcknowledgement = null
        projectionDirty = true
        onBackMismatch(
            acknowledgement.toMismatch(
                actual = declaredStack,
                reason = NavigationBackMismatchReason.DeadlineReached,
            ),
        )
        reconcile()
        return true
    }

    private fun isBackRequestActive(handle: NavigationAcknowledgementHandle): Boolean =
        !disposed &&
            pendingAcknowledgement?.let { acknowledgement ->
                acknowledgement.handle == handle && !acknowledgement.accepted
            } == true

    private fun acceptBackRequest(handle: NavigationAcknowledgementHandle): Boolean {
        if (disposed) return false
        val acknowledgement = pendingAcknowledgement?.takeIf { it.handle == handle } ?: return false
        if (acknowledgement.accepted) return false
        pendingAcknowledgement = acknowledgement.copy(accepted = true)
        return true
    }

    private fun rejectBackRequest(handle: NavigationAcknowledgementHandle): Boolean {
        if (disposed) return false
        val acknowledgement = pendingAcknowledgement?.takeIf { it.handle == handle } ?: return false
        if (acknowledgement.accepted) return false
        restoreAfterRejectedBack(acknowledgement)
        return true
    }

    private fun abortAcceptedBackRequest(handle: NavigationAcknowledgementHandle): Boolean {
        if (disposed) return false
        val acknowledgement = pendingAcknowledgement?.takeIf { it.handle == handle } ?: return false
        if (!acknowledgement.accepted) return false
        restoreAfterRejectedBack(acknowledgement)
        return true
    }

    private fun restoreAfterRejectedBack(acknowledgement: PendingNavigationAcknowledgement) {
        pendingAcknowledgement = null
        projectionDirty = true
        onBackMismatch(
            acknowledgement.toMismatch(
                actual = declaredStack,
                reason = NavigationBackMismatchReason.ExplicitlyRejected,
            ),
        )
        reconcile()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        model = null
        declaredStack = emptyList()
        projectedStack = emptyList()
        inFlight = null
        interaction = null
        pendingAcknowledgement = null
        uncertainProjectionEntries = emptyList()
        projectionDirty = false
        recoveringFailedOperation = false
        operationsPaused = false
        onRetainedEntriesChanged(emptyList())
    }

    private fun reconcile() {
        if (disposed || model == null || inFlight != null || interaction != null ||
            pendingAcknowledgement != null || operationsPaused
        ) {
            return
        }
        if (!projectionDirty && projectedStack.hasSameTopologyAs(declaredStack)) {
            projectedStack = declaredStack
            publishRetainedEntries()
            return
        }

        val operation =
            checkNotNull(
                calculateNextNavigationOperation(
                    projectedStack = projectedStack,
                    declaredStack = declaredStack,
                    forceReconstruction = projectionDirty,
                ),
            ) {
                "Navigation reconciliation expected an operation for different topologies."
            }
        val command =
            NavigationCommand(
                token = nextCommandToken++,
                sourceStack = projectedStack,
                operation = operation,
            )
        inFlight = command
        publishRetainedEntries()
        try {
            emitCommand(command)
        } catch (error: Throwable) {
            if (inFlight?.token == command.token) {
                completeCommand(command.token, NavigationOperationResult.Failed(error))
            }
        }
    }

    private fun publishRetainedEntries() {
        val latestByIdentity = declaredStack.associateBy(ResolvedNavigationEntry::identity)
        val retainedByIdentity = linkedMapOf<NavigationEntryIdentity, ResolvedNavigationEntry>()
        val candidates =
            projectedStack +
                uncertainProjectionEntries +
                (inFlight?.operation?.targetStack ?: emptyList())
        candidates.forEach { entry ->
            val identity = entry.identity()
            retainedByIdentity[identity] = latestByIdentity[identity] ?: entry
        }
        onRetainedEntriesChanged(retainedByIdentity.values.toList())
    }

    private fun retainedEntriesForPresentationValidation(): List<ResolvedNavigationEntry> =
        mergeEntriesByIdentity(
            declaredStack +
                projectedStack +
                uncertainProjectionEntries +
                (inFlight?.operation?.targetStack ?: emptyList()),
        )

    private fun retainUncertainProjection(command: NavigationCommand) {
        uncertainProjectionEntries =
            mergeEntriesByIdentity(
                uncertainProjectionEntries + command.sourceStack + command.operation.targetStack,
            )
    }

    private fun resolveObservedProjection(
        observedTopology: List<NavigationEntryIdentity>,
        command: NavigationCommand,
    ): List<ResolvedNavigationEntry>? {
        val candidatesByIdentity =
            (
                projectedStack +
                    uncertainProjectionEntries +
                    command.sourceStack +
                    command.operation.targetStack +
                    declaredStack
            ).associateBy(ResolvedNavigationEntry::identity)
        val observed =
            observedTopology.map { identity ->
                candidatesByIdentity[identity] ?: return null
            }
        if (observed.isEmpty()) return observed
        if (observed.first().presentation != NavigationPresentation.Page) return null
        val contentKeys = mutableSetOf<Any>()
        var foundOverlay = false
        observed.forEach { entry ->
            if (!contentKeys.add(entry.contentKey)) return null
            if (entry.presentation == NavigationPresentation.Page) {
                if (foundOverlay) return null
            } else {
                foundOverlay = true
            }
        }
        return observed
    }
}

private fun mergeEntriesByIdentity(entries: List<ResolvedNavigationEntry>): List<ResolvedNavigationEntry> {
    val result = linkedMapOf<NavigationEntryIdentity, ResolvedNavigationEntry>()
    entries.forEach { entry -> result[entry.identity()] = entry }
    return result.values.toList()
}

internal data class NavigationInteractionHandle(
    val token: Long,
)

internal data class NavigationAcknowledgementHandle(
    val token: Long,
)

internal enum class NavigationBackMismatchReason {
    DeadlineReached,
    ExplicitlyRejected,
    ModelMismatch,
}

internal data class NavigationBackMismatch(
    val token: Long,
    val base: List<ResolvedNavigationEntry>,
    val expected: List<ResolvedNavigationEntry>,
    val actual: List<ResolvedNavigationEntry>,
    val popCount: Int,
    val reason: NavigationBackMismatchReason,
)

internal data class NavigationProjectionMismatch(
    val command: NavigationCommand,
    val expected: List<NavigationEntryIdentity>,
    val observed: List<NavigationEntryIdentity>,
)

private data class NavigationInteraction(
    val handle: NavigationInteractionHandle,
    val revision: Long,
    val base: List<ResolvedNavigationEntry>,
    val target: List<ResolvedNavigationEntry>,
    val popCount: Int,
)

private data class PendingNavigationAcknowledgement(
    val handle: NavigationAcknowledgementHandle,
    val baseRevision: Long,
    val base: List<ResolvedNavigationEntry>,
    val target: List<ResolvedNavigationEntry>,
    val popCount: Int,
    val accepted: Boolean = false,
) {
    fun toMismatch(
        actual: List<ResolvedNavigationEntry>,
        reason: NavigationBackMismatchReason,
    ): NavigationBackMismatch =
        NavigationBackMismatch(
            token = handle.token,
            base = base,
            expected = target,
            actual = actual,
            popCount = popCount,
            reason = reason,
        )
}
