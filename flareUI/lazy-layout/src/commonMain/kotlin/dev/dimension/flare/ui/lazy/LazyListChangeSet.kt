@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

internal data class LazyListMove(
    val fromIndex: Int,
    val toIndex: Int,
)

internal data class LazyListChangeSet(
    val removedIndices: List<Int>,
    val insertedIndices: List<Int>,
    val moves: List<LazyListMove>,
    val reloadedIndices: List<Int>,
) {
    val isEmpty: Boolean
        get() =
            removedIndices.isEmpty() &&
                insertedIndices.isEmpty() &&
                moves.isEmpty() &&
                reloadedIndices.isEmpty()
}

internal fun calculateLazyListChangeSet(
    previous: LazyItemProvider,
    current: LazyItemProvider,
): LazyListChangeSet {
    val oldSnapshot = previous.snapshot("previous")
    val newSnapshot = current.snapshot("current")
    val removed = oldSnapshot.keys.indices.filter { oldSnapshot.keys[it] !in newSnapshot.indicesByKey }
    val inserted = newSnapshot.keys.indices.filter { newSnapshot.keys[it] !in oldSnapshot.indicesByKey }
    val retainedKeys = oldSnapshot.keys.filter { it in newSnapshot.indicesByKey }
    val targetRetainedKeys = newSnapshot.keys.filter { it in oldSnapshot.indicesByKey }
    val moves = calculateMoves(oldSnapshot, newSnapshot, retainedKeys, targetRetainedKeys)
    val reloaded =
        retainedKeys.mapNotNull { key ->
            val oldIndex = checkNotNull(oldSnapshot.indicesByKey[key])
            val newIndex = checkNotNull(newSnapshot.indicesByKey[key])
            if (oldSnapshot.contentTypes[oldIndex] == newSnapshot.contentTypes[newIndex]) {
                null
            } else {
                newIndex
            }
        }
    return LazyListChangeSet(
        removedIndices = removed,
        insertedIndices = inserted,
        moves = moves,
        reloadedIndices = reloaded,
    )
}

private fun calculateMoves(
    oldSnapshot: LazyListSnapshot,
    newSnapshot: LazyListSnapshot,
    retainedKeys: List<Any>,
    targetRetainedKeys: List<Any>,
): List<LazyListMove> {
    // Appending, prepending, removing, and content-only updates all retain relative order. Avoid
    // the indexed-list simulation in that overwhelmingly common path: it is otherwise O(n²).
    if (retainedKeys == targetRetainedKeys) return emptyList()

    val workingKeys = retainedKeys.toMutableList()
    return buildList {
        targetRetainedKeys.forEachIndexed { targetIndex, key ->
            val currentIndex = workingKeys.indexOf(key)
            check(currentIndex >= 0)
            if (currentIndex != targetIndex) {
                add(
                    LazyListMove(
                        fromIndex = checkNotNull(oldSnapshot.indicesByKey[key]),
                        toIndex = checkNotNull(newSnapshot.indicesByKey[key]),
                    ),
                )
                workingKeys.removeAt(currentIndex)
                workingKeys.add(targetIndex, key)
            }
        }
    }
}

private fun LazyItemProvider.snapshot(label: String): LazyListSnapshot {
    val keys = ArrayList<Any>(itemCount)
    val contentTypes = ArrayList<Any?>(itemCount)
    val indicesByKey = HashMap<Any, Int>(itemCount)
    repeat(itemCount) { index ->
        val key = key(index)
        val previousIndex = indicesByKey.put(key, index)
        check(previousIndex == null) {
            "Lazy list key $key occurs more than once in the $label item provider " +
                "(indices $previousIndex and $index)."
        }
        keys += key
        contentTypes += contentType(index)
    }
    return LazyListSnapshot(keys, contentTypes, indicesByKey)
}

private class LazyListSnapshot(
    val keys: List<Any>,
    val contentTypes: List<Any?>,
    val indicesByKey: Map<Any, Int>,
)
