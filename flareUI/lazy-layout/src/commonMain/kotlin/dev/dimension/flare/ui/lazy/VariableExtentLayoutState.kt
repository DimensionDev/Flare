package dev.dimension.flare.ui.lazy

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Sparse main-axis geometry for a variable-size lazy list.
 *
 * Unknown items use an estimate. Real measurements are retained by stable key across data-set
 * resets, while the sparse Fenwick tree makes one measured-size correction O(log itemCount).
 */
internal class VariableExtentLayoutState(
    private val defaultEstimatedExtent: Double = DEFAULT_ESTIMATED_EXTENT,
    private val measurementTolerance: Double = DEFAULT_MEASUREMENT_TOLERANCE,
    private val maxCachedMeasurements: Int = DEFAULT_MEASUREMENT_CACHE_SIZE,
) {
    private val extentDeltas = SparseFenwickTree()
    private val assignedExtents = mutableMapOf<Int, AssignedExtent>()
    private val exactMeasurements = LinkedHashMap<MeasurementKey, Double>()
    private val estimators = mutableMapOf<Any, RollingMedian>()
    private var environment: Any? = UnsetEnvironment

    var itemCount: Int = 0
        private set

    var spacing: Double = 0.0
        private set

    val contentExtent: Double
        get() {
            if (itemCount == 0) return 0.0
            return itemCount * defaultEstimatedExtent +
                (itemCount - 1) * spacing +
                extentDeltas.prefixSum(itemCount)
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
        assignedExtents.clear()
        extentDeltas.reset(itemCount)
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
        if (assigned?.measurementKey == measurementKey) return null
        val extent =
            exactMeasurement(measurementKey)
                ?: estimators[contentType.cacheKey()]?.median
                ?: defaultEstimatedExtent
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
        if (!extent.isFinite() || extent <= 0.0) return null
        val measurementKey = MeasurementKey(key, layoutVersion)
        val previousMeasurement = exactMeasurements[measurementKey]
        cacheMeasurement(measurementKey, extent)
        val estimatorKey = contentType.cacheKey()
        if (previousMeasurement == null ||
            abs(previousMeasurement - extent) > measurementTolerance ||
            estimatorKey !in estimators
        ) {
            estimators.getOrPut(estimatorKey) { RollingMedian() }.record(extent)
        }
        return assign(index, measurementKey, extent)
    }

    fun itemExtent(index: Int): Double {
        requireIndex(index)
        return assignedExtents[index]?.extent ?: defaultEstimatedExtent
    }

    fun hasExactMeasurement(
        key: Any,
        layoutVersion: Any?,
    ): Boolean = MeasurementKey(key, layoutVersion) in exactMeasurements

    fun itemStart(index: Int): Double {
        requireIndex(index)
        return index * (defaultEstimatedExtent + spacing) + extentDeltas.prefixSum(index)
    }

    fun visibleRange(
        viewportStart: Double,
        viewportEnd: Double,
        overscan: Double = 0.0,
    ): IntRange {
        if (itemCount == 0) return IntRange.EMPTY
        require(overscan.isFinite() && overscan >= 0.0) {
            "Lazy list overscan must be finite and non-negative."
        }
        val start = max(0.0, min(viewportStart, viewportEnd) - overscan)
        val end = max(viewportStart, viewportEnd) + overscan
        val first = indexAtOffset(start)
        val last = indexAtOffset(end)
        return first..last
    }

    private fun indexAtOffset(offset: Double): Int {
        var low = 0
        var high = itemCount - 1
        var result = 0
        while (low <= high) {
            val middle = low + (high - low) / 2
            if (itemStart(middle) <= offset) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }

    private fun assign(
        index: Int,
        measurementKey: MeasurementKey,
        extent: Double,
    ): ExtentChange? {
        val previous = assignedExtents[index]?.extent ?: defaultEstimatedExtent
        val delta = extent - previous
        if (abs(delta) <= measurementTolerance) {
            assignedExtents[index] = AssignedExtent(measurementKey, previous)
            return null
        }
        assignedExtents[index] = AssignedExtent(measurementKey, extent)
        extentDeltas.add(index, delta)
        return ExtentChange(index, previous, extent)
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
)

private class SparseFenwickTree {
    private val nodes = mutableMapOf<Int, Double>()
    private var size: Int = 0

    fun reset(size: Int) {
        this.size = size
        nodes.clear()
    }

    fun add(
        index: Int,
        delta: Double,
    ) {
        var node = index + 1
        while (node <= size) {
            val value = (nodes[node] ?: 0.0) + delta
            if (abs(value) <= SPARSE_ZERO_TOLERANCE) {
                nodes.remove(node)
            } else {
                nodes[node] = value
            }
            node += node and -node
        }
    }

    fun prefixSum(endExclusive: Int): Double {
        var node = endExclusive
        var result = 0.0
        while (node > 0) {
            result += nodes[node] ?: 0.0
            node -= node and -node
        }
        return result
    }
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
private const val DEFAULT_MEASUREMENT_TOLERANCE = 0.5
private const val DEFAULT_MEASUREMENT_CACHE_SIZE = 4_096
private const val DEFAULT_ESTIMATOR_SAMPLE_SIZE = 15
private const val SPARSE_ZERO_TOLERANCE = 0.000_001
