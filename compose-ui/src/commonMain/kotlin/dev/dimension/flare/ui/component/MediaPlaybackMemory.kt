package dev.dimension.flare.ui.component

/** One in-memory progress record per media resource, shared by all pages and accounts. */
internal class MediaPlaybackMemory {
    private val generations = mutableMapOf<String, Int>()

    fun generation(uri: String): Int = generations[uri] ?: 0

    fun reset(uri: String) {
        generations[uri] = generation(uri) + 1
        positions.remove(uri)
    }

    private val positions = mutableMapOf<String, Double>()

    fun position(uri: String): Double = positions[uri] ?: 0.0

    fun save(
        uri: String,
        seconds: Double,
    ) {
        if (seconds.isFinite() && seconds >= 0) positions[uri] = seconds
    }

    companion object {
        val shared = MediaPlaybackMemory()
    }
}

internal class TimelineMediaSelections {
    private data class Collection(
        val urls: List<String>,
        val select: (Int) -> Unit,
    )

    private val collections = mutableMapOf<Any, Collection>()
    private val selections = mutableMapOf<List<String>, Int>()

    fun register(
        id: Any,
        urls: List<String>,
        select: (Int) -> Unit,
    ) {
        collections[id] = Collection(urls.toList(), select)
        selections.remove(urls)?.let(select)
    }

    fun remove(id: Any) {
        collections.remove(id)
    }

    fun returned(
        urls: List<String>,
        selectedIndex: Int,
    ) {
        if (selectedIndex !in urls.indices) return
        val matching = collections.values.toList().filter { it.urls == urls }
        if (matching.isEmpty()) selections[urls.toList()] = selectedIndex else selections.remove(urls)
        matching.forEach { it.select(selectedIndex) }
    }
}
