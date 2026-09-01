@file:OptIn(dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class)

package dev.dimension.flare.ui.navigation

internal sealed interface NavigationOperation {
    val targetStack: List<ResolvedNavigationEntry>
    val animated: Boolean

    data class PushPage(
        val entry: ResolvedNavigationEntry,
        override val targetStack: List<ResolvedNavigationEntry>,
    ) : NavigationOperation {
        override val animated: Boolean = true
    }

    data class PopPage(
        val entry: ResolvedNavigationEntry,
        override val targetStack: List<ResolvedNavigationEntry>,
    ) : NavigationOperation {
        override val animated: Boolean = true
    }

    data class PresentOverlay(
        val entry: ResolvedNavigationEntry,
        override val targetStack: List<ResolvedNavigationEntry>,
    ) : NavigationOperation {
        override val animated: Boolean = true
    }

    data class DismissOverlay(
        val entry: ResolvedNavigationEntry,
        override val targetStack: List<ResolvedNavigationEntry>,
    ) : NavigationOperation {
        override val animated: Boolean = true
    }

    data class Reconstruct(
        override val targetStack: List<ResolvedNavigationEntry>,
    ) : NavigationOperation {
        override val animated: Boolean = false
    }
}

internal fun calculateNavigationPlan(
    projectedStack: List<ResolvedNavigationEntry>,
    declaredStack: List<ResolvedNavigationEntry>,
    forceReconstruction: Boolean = false,
): List<NavigationOperation> {
    if (forceReconstruction) {
        return listOf(NavigationOperation.Reconstruct(declaredStack.toList()))
    }
    if (projectedStack.hasSameTopologyAs(declaredStack)) return emptyList()
    if (projectedStack.isEmpty()) {
        return listOf(NavigationOperation.Reconstruct(declaredStack.toList()))
    }

    if (projectedStack.isTopologyPrefixOf(declaredStack)) {
        if (declaredStack.size - projectedStack.size != 1) {
            return listOf(NavigationOperation.Reconstruct(declaredStack.toList()))
        }
        val entry = declaredStack.last()
        return listOf(
            if (entry.presentation == NavigationPresentation.Page) {
                NavigationOperation.PushPage(entry, declaredStack.toList())
            } else {
                NavigationOperation.PresentOverlay(entry, declaredStack.toList())
            },
        )
    }
    if (declaredStack.isTopologyPrefixOf(projectedStack)) {
        if (projectedStack.size - declaredStack.size != 1) {
            return listOf(NavigationOperation.Reconstruct(declaredStack.toList()))
        }
        val reboundProjection = projectedStack.rebindCommonPrefixFrom(declaredStack)
        val entry = reboundProjection.last()
        return listOf(
            if (entry.presentation == NavigationPresentation.Page) {
                NavigationOperation.PopPage(entry, declaredStack.toList())
            } else {
                NavigationOperation.DismissOverlay(entry, declaredStack.toList())
            },
        )
    }
    return listOf(NavigationOperation.Reconstruct(declaredStack.toList()))
}

/**
 * Calculates only the next serialized platform operation.
 *
 * The coordinator cannot execute more than one native operation at a time. Building every suffix
 * operation eagerly copies progressively larger target lists that are discarded before the next
 * reconciliation. Keeping this helper separate from [calculateNavigationPlan] preserves the full
 * planner for focused tests while making the production path proportional to the next target.
 */
internal fun calculateNextNavigationOperation(
    projectedStack: List<ResolvedNavigationEntry>,
    declaredStack: List<ResolvedNavigationEntry>,
    forceReconstruction: Boolean = false,
): NavigationOperation? {
    if (forceReconstruction) {
        return NavigationOperation.Reconstruct(declaredStack.toList())
    }
    if (projectedStack.hasSameTopologyAs(declaredStack)) return null
    if (projectedStack.isEmpty()) {
        return NavigationOperation.Reconstruct(declaredStack.toList())
    }

    if (projectedStack.isTopologyPrefixOf(declaredStack)) {
        if (declaredStack.size - projectedStack.size != 1) {
            return NavigationOperation.Reconstruct(declaredStack.toList())
        }
        val entry = declaredStack.last()
        return if (entry.presentation == NavigationPresentation.Page) {
            NavigationOperation.PushPage(entry, declaredStack.toList())
        } else {
            NavigationOperation.PresentOverlay(entry, declaredStack.toList())
        }
    }
    if (declaredStack.isTopologyPrefixOf(projectedStack)) {
        if (projectedStack.size - declaredStack.size != 1) {
            return NavigationOperation.Reconstruct(declaredStack.toList())
        }
        val entry = projectedStack.last()
        return if (entry.presentation == NavigationPresentation.Page) {
            NavigationOperation.PopPage(entry, declaredStack.toList())
        } else {
            NavigationOperation.DismissOverlay(entry, declaredStack.toList())
        }
    }
    return NavigationOperation.Reconstruct(declaredStack.toList())
}

internal data class NavigationCommand(
    val token: Long,
    val sourceStack: List<ResolvedNavigationEntry>,
    val operation: NavigationOperation,
)

internal sealed interface NavigationOperationResult {
    /** Null means the adapter observed exactly the operation's requested target. */
    data class Succeeded(
        val observedTopology: List<NavigationEntryIdentity>? = null,
    ) : NavigationOperationResult

    /** The platform did not start this operation and its projection is still unchanged. */
    data object Deferred : NavigationOperationResult

    /** The operation may have partially changed the platform projection. */
    data class Failed(
        val cause: Throwable,
    ) : NavigationOperationResult
}

/** Stable native projection identity for one active navigation entry. */
@ExperimentalFlareNavigation
public data class NavigationEntryIdentity(
    val contentKey: Any,
    val presentation: NavigationPresentation,
)

internal fun ResolvedNavigationEntry.identity(): NavigationEntryIdentity =
    NavigationEntryIdentity(
        contentKey = contentKey,
        presentation = presentation,
    )

internal fun List<ResolvedNavigationEntry>.topology(): List<NavigationEntryIdentity> = map(ResolvedNavigationEntry::identity)
