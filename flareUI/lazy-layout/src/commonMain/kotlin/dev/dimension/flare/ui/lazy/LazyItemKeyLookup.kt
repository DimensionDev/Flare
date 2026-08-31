@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

/**
 * Resolves an anchor key with O(1) append/prepend and nearby-move fast paths.
 *
 * The final scan preserves stable-key anchor semantics for arbitrary reorder until providers expose
 * an inverse key index. It is intentionally reached only after the common candidates miss.
 */
internal fun LazyItemProvider.findIndexByKey(
    key: Any,
    expectedIndex: Int,
    previousItemCount: Int,
): Int {
    if (itemCount == 0) return -1

    val shiftedIndex = expectedIndex.toLong() + itemCount.toLong() - previousItemCount.toLong()
    if (shiftedIndex in 0L until itemCount.toLong()) {
        val index = shiftedIndex.toInt()
        if (this.key(index) == key) return index
    }

    val center = expectedIndex.coerceIn(0, itemCount - 1)
    if (center.toLong() != shiftedIndex && this.key(center) == key) return center

    fun searchNear(candidate: Int): Int {
        repeat(minOf(LOCAL_KEY_SEARCH_DISTANCE, itemCount)) { distanceOffset ->
            val distance = distanceOffset + 1
            val before = candidate - distance
            if (before >= 0 && this.key(before) == key) return before
            val after = candidate.toLong() + distance
            if (after < itemCount.toLong() && this.key(after.toInt()) == key) return after.toInt()
        }
        return -1
    }

    if (shiftedIndex in 0L until itemCount.toLong()) {
        searchNear(shiftedIndex.toInt()).takeIf { it >= 0 }?.let { return it }
    }
    if (center.toLong() != shiftedIndex) {
        searchNear(center).takeIf { it >= 0 }?.let { return it }
    }

    repeat(itemCount) { index ->
        if (this.key(index) == key) return index
    }
    return -1
}

private const val LOCAL_KEY_SEARCH_DISTANCE: Int = 64
