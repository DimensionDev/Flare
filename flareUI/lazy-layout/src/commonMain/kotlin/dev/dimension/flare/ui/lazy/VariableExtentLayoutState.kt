@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Sparse main-axis geometry for a variable-size lazy list.
 *
 * Unknown items use an estimate. Real measurements are retained by stable key across data-set
 * resets. A sparse ordered index keeps corrections, positional edits, and viewport lookups
 * logarithmic in the number of visited items. Offscreen metadata is validated on demand.
 */
internal class VariableExtentLayoutState(
    private val defaultEstimatedExtent: Double = DEFAULT_ESTIMATED_EXTENT,
    private val measurementTolerance: Double = DEFAULT_MEASUREMENT_TOLERANCE,
    private val maxCachedMeasurements: Int = DEFAULT_MEASUREMENT_CACHE_SIZE,
) {
    private val assignedExtents = SparseExtentIndex(defaultEstimatedExtent)
    private val exactMeasurements = LinkedHashMap<MeasurementKey, Double>()
    private val estimators = mutableMapOf<Any, RollingMedian>()
    private var environment: Any? = UnsetEnvironment
    private var provider: LazyItemProvider? = null
    private var generation = 0L

    var revision: Long = 0L
        private set

    var itemCount: Int = 0
        private set

    var spacing: Double = 0.0
        private set

    val contentExtent: Double
        get() {
            if (itemCount == 0) return 0.0
            return itemCount * defaultEstimatedExtent +
                (itemCount - 1) * spacing +
                assignedExtents.totalDelta
        }

    init {
        require(defaultEstimatedExtent.isFinite() && defaultEstimatedExtent > 0.0) {
            "The default lazy item estimate must be finite and positive."
        }
        require(measurementTolerance.isFinite() && measurementTolerance >= 0.0) {
            "The lazy item measurement tolerance must be finite and non-negative."
        }
        require(maxCachedMeasurements > 0) {
            "The lazy item measurement cache must retain at least one entry."
        }
    }

    /** Starts a new index space while retaining compatible stable-key measurements. */
    fun reset(
        itemCount: Int,
        spacing: Double,
        environment: Any?,
    ) {
        require(itemCount >= 0) { "Lazy list item count must be non-negative." }
        require(spacing.isFinite() && spacing >= 0.0) {
            "Lazy list spacing must be finite and non-negative."
        }
        if (this.environment != environment) {
            this.environment = environment
            exactMeasurements.clear()
            estimators.clear()
        }
        this.itemCount = itemCount
        this.spacing = spacing
        provider = null
        assignedExtents.clear()
        revision++
    }

    /** Publishes metadata without walking browsing history; native splices shift its index lazily. */
    fun update(
        provider: LazyItemProvider,
        spacing: Double,
        environment: Any?,
        splice: LazyIndexSplice? = null,
    ) {
        require(provider.itemCount >= 0) { "Lazy list item count must be non-negative." }
        require(spacing.isFinite() && spacing >= 0.0) { "Lazy list spacing must be finite and non-negative." }
        if (this.environment != environment) {
            reset(provider.itemCount, spacing, environment)
        } else if (itemCount != provider.itemCount) {
            if (splice != null) {
                require(splice.index in 0..itemCount && splice.removed in 0..(itemCount - splice.index))
                require(provider.itemCount == itemCount - splice.removed + splice.inserted)
                assignedExtents.splice(splice)
            } else {
                // Arbitrary replacement has no positional mapping. Exact sizes remain keyed
                // and are recovered when items are requested in the new index space.
                assignedExtents.clear()
            }
        }
        this.itemCount = provider.itemCount
        this.spacing = spacing
        this.provider = provider
        generation++
        // A zero-sized offscreen item otherwise cannot re-enter a range query to reveal a new
        // layoutVersion. Restore positive provisional sizes in O(1), then validate on discovery.
        assignedExtents.invalidateCollapsed()
        revision++
    }

    /** Applies an exact cached extent or a content-type estimate to one item. */
    fun resolve(
        index: Int,
        key: Any,
        layoutVersion: Any?,
        contentType: Any?,
    ): ExtentChange? {
        requireIndex(index)
        val measurementKey = MeasurementKey(key, layoutVersion)
        val assigned = assignedExtents[index]
        if (assigned?.measurementKey == measurementKey && assigned.generation != INVALIDATED_COLLAPSED_GENERATION) {
            if (assigned.generation != generation) {
                assignedExtents[index] = assigned.copy(generation = generation)
            }
            return null
        }
        val extent =
            if (assigned?.measurementKey == measurementKey && assigned.generation == INVALIDATED_COLLAPSED_GENERATION) {
                0.0
            } else {
                exactMeasurement(measurementKey)
                    ?: estimators[contentType.cacheKey()]?.median
                    ?: defaultEstimatedExtent
            }
        return assign(index, measurementKey, extent)
    }

    /** Records a native measurement and returns the local main-axis correction, if any. */
    fun record(
        index: Int,
        key: Any,
        layoutVersion: Any?,
        contentType: Any?,
        extent: Double,
    ): ExtentChange? {
        requireIndex(index)
        if (!extent.isFinite() || extent < 0.0) return null
        val measurementKey = MeasurementKey(key, layoutVersion)
        val previousMeasurement = exactMeasurements[measurementKey]
        cacheMeasurement(measurementKey, extent)
        val estimatorKey = contentType.cacheKey()
        // Collapsed items have an exact zero size, but cannot predict the size of unseen content.
        if (extent > 0.0 &&
            (
                previousMeasurement == null ||
                    abs(previousMeasurement - extent) > measurementTolerance ||
                    estimatorKey !in estimators
            )
        ) {
            estimators.getOrPut(estimatorKey) { RollingMedian() }.record(extent)
        }
        return assign(index, measurementKey, extent)
    }

    fun itemExtent(index: Int): Double {
        requireIndex(index)
        validate(index)
        return assignedExtents[index]?.extent ?: defaultEstimatedExtent
    }

    fun hasExactMeasurement(
        key: Any,
        layoutVersion: Any?,
    ): Boolean = MeasurementKey(key, layoutVersion) in exactMeasurements

    fun itemStart(index: Int): Double {
        requireIndex(index)
        validate(index)
        return index * (defaultEstimatedExtent + spacing) + assignedExtents.prefixSum(index)
    }

    fun visibleRange(
        viewportStart: Double,
        viewportEnd: Double,
        overscan: Double = 0.0,
    ): IntRange = calculateVisibleRange(viewportStart, viewportEnd, overscan) {}

    /** Test-only complexity seam; production calls inline an empty node-visit callback. */
    internal fun visibleRangeWithSearchNodeVisitsForTesting(
        viewportStart: Double,
        viewportEnd: Double,
        overscan: Double = 0.0,
    ): Pair<IntRange, Int> {
        var nodeVisits = 0
        val range =
            calculateVisibleRange(viewportStart, viewportEnd, overscan) {
                nodeVisits += 1
            }
        return range to nodeVisits
    }

    private inline fun calculateVisibleRange(
        viewportStart: Double,
        viewportEnd: Double,
        overscan: Double,
        onSearchNodeVisited: () -> Unit,
    ): IntRange {
        if (itemCount == 0) return IntRange.EMPTY
        require(overscan.isFinite() && overscan >= 0.0) {
            "Lazy list overscan must be finite and non-negative."
        }
        val start = max(0.0, min(viewportStart, viewportEnd) - overscan)
        val end = max(viewportStart, viewportEnd) + overscan
        val first = indexAtOffset(start, onSearchNodeVisited)
        val last = indexAtOffset(end, onSearchNodeVisited)
        return first..last
    }

    private inline fun indexAtOffset(
        offset: Double,
        onSearchNodeVisited: () -> Unit,
    ): Int =
        assignedExtents.indexAtOrBefore(
            offset = offset,
            estimatedStride = defaultEstimatedExtent + spacing,
            itemCount = itemCount,
            onNodeVisited = onSearchNodeVisited,
        )

    private fun validate(index: Int) {
        val provider = provider ?: return
        // Native attribute queries can include unseen neighbors. Keep their default geometry
        // stable until the controller explicitly resolves them for its viewport.
        val assigned = assignedExtents[index] ?: return
        if (assigned.generation == generation) return
        resolve(index, provider.key(index), provider.layoutVersion(index), provider.contentType(index))
    }

    private fun assign(
        index: Int,
        measurementKey: MeasurementKey,
        extent: Double,
    ): ExtentChange? {
        val previous = assignedExtents[index]?.extent ?: defaultEstimatedExtent
        val delta = extent - previous
        if (extent != 0.0 && previous != 0.0 && abs(delta) <= measurementTolerance) {
            assignedExtents[index] = AssignedExtent(measurementKey, previous, generation)
            return null
        }
        assignedExtents[index] = AssignedExtent(measurementKey, extent, generation)
        if (delta != 0.0) revision++
        return if (delta == 0.0) null else ExtentChange(index, previous, extent)
    }

    private fun exactMeasurement(key: MeasurementKey): Double? {
        val value = exactMeasurements.remove(key) ?: return null
        exactMeasurements[key] = value
        return value
    }

    private fun cacheMeasurement(
        key: MeasurementKey,
        extent: Double,
    ) {
        exactMeasurements.remove(key)
        exactMeasurements[key] = extent
        while (exactMeasurements.size > maxCachedMeasurements) {
            val oldest = exactMeasurements.keys.first()
            exactMeasurements.remove(oldest)
        }
    }

    private fun requireIndex(index: Int) {
        require(index in 0 until itemCount) {
            "Lazy list index $index is outside 0 until $itemCount."
        }
    }
}

internal data class ExtentChange(
    val index: Int,
    val previous: Double,
    val current: Double,
) {
    val delta: Double
        get() = current - previous
}

private data class MeasurementKey(
    val key: Any,
    val layoutVersion: Any?,
)

private data class AssignedExtent(
    val measurementKey: MeasurementKey,
    val extent: Double,
    val generation: Long,
)

/** A treap over visited positions. Subtree shifts avoid rebuilding a prefix index after splices. */
private class SparseExtentIndex(
    private val defaultExtent: Double,
) {
    private var root: Node? = null
    private val priorities = Random(0xF1A2E)

    val totalDelta: Double get() = root.sum()

    fun clear() {
        root = null
    }

    fun invalidateCollapsed() {
        root?.invalidateCollapsed()
    }

    operator fun get(index: Int): AssignedExtent? {
        var node = root
        while (node != null) {
            node.push()
            node =
                when {
                    index < node.index -> node.left
                    index > node.index -> node.right
                    else -> return node.value
                }
        }
        return null
    }

    operator fun set(
        index: Int,
        value: AssignedExtent,
    ) {
        root = put(root, index, value)
    }

    fun splice(splice: LazyIndexSplice) {
        val (before, rest) = split(root, splice.index)
        val (_, after) = split(rest, splice.index + splice.removed)
        after?.shift(splice.inserted - splice.removed)
        root = merge(before, after)
    }

    fun prefixSum(endExclusive: Int): Double {
        var node = root
        var result = 0.0
        while (node != null) {
            node.push()
            node =
                if (endExclusive <= node.index) {
                    node.left
                } else {
                    result += node.left.sum() + node.delta
                    node.right
                }
        }
        return result
    }

    /** Searches measured positions once, then interpolates any remaining unmeasured gap. */
    inline fun indexAtOrBefore(
        offset: Double,
        estimatedStride: Double,
        itemCount: Int,
        onNodeVisited: () -> Unit,
    ): Int {
        var node = root
        var before = 0.0
        var lower = 0
        var upper = itemCount - 1
        while (node != null) {
            onNodeVisited()
            node.push()
            val start = node.index * estimatedStride + before + node.left.sum()
            node =
                if (start <= offset) {
                    lower = node.index
                    before += node.left.sum() + node.delta
                    node.right
                } else {
                    upper = node.index - 1
                    node.left
                }
        }
        return floor((offset - before) / estimatedStride).toInt().coerceIn(lower, max(lower, upper))
    }

    private fun put(
        node: Node?,
        index: Int,
        value: AssignedExtent,
    ): Node {
        if (node == null) return Node(index, value, priorities.nextInt())
        node.push()
        when {
            index < node.index -> {
                val left = put(node.left, index, value)
                node.left = left
                if (left.priority > node.priority) {
                    node.left = left.right
                    left.right = node.refresh()
                    return left.refresh()
                }
            }

            index > node.index -> {
                val right = put(node.right, index, value)
                node.right = right
                if (right.priority > node.priority) {
                    node.right = right.left
                    right.left = node.refresh()
                    return right.refresh()
                }
            }

            else -> {
                node.value = value
            }
        }
        return node.refresh()
    }

    private fun split(
        node: Node?,
        index: Int,
    ): Pair<Node?, Node?> {
        if (node == null) return null to null
        node.push()
        return if (node.index < index) {
            val (before, after) = split(node.right, index)
            node.right = before
            node.refresh() to after
        } else {
            val (before, after) = split(node.left, index)
            node.left = after
            before to node.refresh()
        }
    }

    private fun merge(
        left: Node?,
        right: Node?,
    ): Node? {
        if (left == null) return right
        if (right == null) return left
        left.push()
        right.push()
        return if (left.priority > right.priority) {
            left.right = merge(left.right, right)
            left.refresh()
        } else {
            right.left = merge(left, right.left)
            right.refresh()
        }
    }

    inner class Node(
        var index: Int,
        var value: AssignedExtent,
        val priority: Int,
    ) {
        var left: Node? = null
        var right: Node? = null
        var pendingShift = 0
        var pendingCollapsedInvalidation = false
        var collapsedCount = if (value.extent == 0.0) 1 else 0
        val delta: Double get() = value.extent - defaultExtent
        var totalDelta: Double = delta

        fun refresh(): Node {
            totalDelta = left.sum() + delta + right.sum()
            collapsedCount = (left?.collapsedCount ?: 0) + (right?.collapsedCount ?: 0) + if (value.extent == 0.0) 1 else 0
            return this
        }

        fun invalidateCollapsed() {
            if (collapsedCount == 0) return
            totalDelta += collapsedCount * defaultExtent
            if (value.extent == 0.0) value = value.copy(extent = defaultExtent, generation = INVALIDATED_COLLAPSED_GENERATION)
            collapsedCount = 0
            pendingCollapsedInvalidation = true
        }

        fun shift(delta: Int) {
            index += delta
            pendingShift += delta
        }

        fun push() {
            if (pendingShift != 0) {
                left?.shift(pendingShift)
                right?.shift(pendingShift)
                pendingShift = 0
            }
            if (pendingCollapsedInvalidation) {
                left?.invalidateCollapsed()
                right?.invalidateCollapsed()
                pendingCollapsedInvalidation = false
            }
        }
    }

    private fun Node?.sum(): Double = this?.totalDelta ?: 0.0
}

private class RollingMedian(
    private val capacity: Int = DEFAULT_ESTIMATOR_SAMPLE_SIZE,
) {
    private val samples = DoubleArray(capacity)
    private var nextIndex: Int = 0
    private var sampleCount: Int = 0

    val median: Double
        get() {
            val sorted = samples.copyOf(sampleCount).apply(DoubleArray::sort)
            val middle = sampleCount / 2
            return if (sampleCount % 2 == 1) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2.0
            }
        }

    fun record(value: Double) {
        samples[nextIndex] = value
        nextIndex = (nextIndex + 1) % capacity
        sampleCount = min(sampleCount + 1, capacity)
    }
}

private fun Any?.cacheKey(): Any = this ?: NullContentType

private data object NullContentType

private data object UnsetEnvironment

private const val DEFAULT_ESTIMATED_EXTENT = 48.0
private const val INVALIDATED_COLLAPSED_GENERATION = -1L
private const val DEFAULT_MEASUREMENT_TOLERANCE = 0.5
private const val DEFAULT_MEASUREMENT_CACHE_SIZE = 4_096
private const val DEFAULT_ESTIMATOR_SAMPLE_SIZE = 15
