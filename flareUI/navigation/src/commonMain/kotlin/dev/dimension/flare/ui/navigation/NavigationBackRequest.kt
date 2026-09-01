@file:OptIn(dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class)

package dev.dimension.flare.ui.navigation

/**
 * One committed native back request against an exact navigation stack.
 *
 * The request remains claimable until [accept], [reject], or [applyTo] claims it; its [target] is
 * delivered to [NavigationDisplay]; a different topology supersedes it; or the platform
 * acknowledgement deadline expires. An accepted request may remain pending while the coordinator
 * waits for its target model, but it is no longer [isActive] and cannot be claimed again. A retained
 * request may be completed asynchronously, but completion and any associated back-stack mutation
 * must run on the host UI thread.
 *
 * Callers must apply the request only when their authoritative back stack still represents [base].
 * [applyTo] provides that compare-and-remove operation for mutable back stacks. The high-level
 * [NavigationDisplay] overload exposes route values here; only the low-level decorated-entry
 * overload exposes [NavigationEntryIdentity].
 */
@ExperimentalFlareNavigation
public class NavigationBackRequest<T : Any> internal constructor(
    public val requestId: Long,
    public val baseRevision: Long,
    public val base: List<T>,
    public val target: List<T>,
    public val popCount: Int,
    private val isActiveRequest: () -> Boolean,
    private val acceptRequest: () -> Boolean,
    private val rejectRequest: () -> Boolean,
    private val abortAcceptedRequest: () -> Boolean,
) {
    /** Whether this request is unclaimed and may still be accepted, rejected, or applied. */
    public val isActive: Boolean
        get() = isActiveRequest()

    /**
     * Claims this request as accepted.
     *
     * Acceptance does not replace model delivery: [target] must still be delivered to
     * [NavigationDisplay]. A successful call makes [isActive] false. Returns false for a stale,
     * already accepted, or rejected request.
     */
    public fun accept(): Boolean = acceptRequest()

    /**
     * Rejects the request and restores the latest declared projection.
     *
     * Returns false for a stale, accepted, or already rejected request.
     */
    public fun reject(): Boolean = rejectRequest()

    /**
     * Applies this suffix removal only if [backStack] still exactly equals [base].
     *
     * No mutation occurs when the request is stale, was already accepted, or the stack no longer
     * matches. This operation also accepts the request, so callers must not call [accept] first.
     */
    public fun applyTo(backStack: MutableList<T>): Boolean = applyTo(backStack) { it }

    /**
     * Applies this suffix removal after mapping [backStack] values into this request's identity.
     *
     * This is useful at adapter seams whose mutable stack stores controllers or wrappers. Most
     * application callers should use [applyTo] directly with their route back stack.
     */
    public fun <K : Any> applyTo(
        backStack: MutableList<K>,
        identityOf: (K) -> T,
    ): Boolean {
        if (!isActive) return false
        if (backStack.map(identityOf) != base) return false
        val proposed = backStack.dropLast(popCount)
        if (proposed.map(identityOf) != target) return false
        // Claim the one permitted mutation before touching an observable list. The pending request
        // remains tracked until model delivery, rejection, supersession, or the deadline.
        if (!accept()) return false

        try {
            backStack.subList(target.size, backStack.size).clear()
        } catch (error: Throwable) {
            // Public reject is deliberately unavailable after the request has been claimed. This
            // private rollback seam is reachable only from the apply operation that performed the
            // claim, so an unsuccessful mutation cannot strand the native projection.
            abortAcceptedRequest()
            throw error
        }
        return true
    }

    internal fun <R : Any> mapValues(
        base: List<R>,
        target: List<R>,
    ): NavigationBackRequest<R> {
        check(base.size - target.size == popCount) {
            "A mapped NavigationBackRequest must preserve popCount $popCount."
        }
        return NavigationBackRequest(
            requestId = requestId,
            baseRevision = baseRevision,
            base = base,
            target = target,
            popCount = popCount,
            isActiveRequest = isActiveRequest,
            acceptRequest = acceptRequest,
            rejectRequest = rejectRequest,
            abortAcceptedRequest = abortAcceptedRequest,
        )
    }

    override fun toString(): String =
        "NavigationBackRequest(requestId=$requestId, baseRevision=$baseRevision, " +
            "base=$base, target=$target, popCount=$popCount, isActive=$isActive)"
}
